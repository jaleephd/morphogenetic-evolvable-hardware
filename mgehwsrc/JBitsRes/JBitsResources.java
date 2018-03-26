
//			 JBitsResources
//
// this class acts as an interface to all the JBits resources, taking care
// of contention checking (via ContentionLookup), translation of higher-level
// constructs to JBits settings (via this class and LUTIncrFN), and conversion
// of strings to actual JBits parameters (int[][] and int[] via JBitsLookup)
// 
// this class also requires local classes:
// 	StringSplitter, JBitsLookup, ContentionLookup, LUTIncrFN
//
// 		written December 2003, J.A. Lee



// for Strings, and reflection
import java.lang.*;
import java.lang.reflect.*;

// for collection classes (eg List)
import java.util.*;


// for low level JBits
import  com.xilinx.JBits.Virtex.ConfigurationException;
import  com.xilinx.JBits.Virtex.ReadbackCommand;
import  com.xilinx.JBits.Virtex.Devices;
import  com.xilinx.JBits.Virtex.JBits;
import  com.xilinx.JBits.Virtex.ConfigurationException;
import  com.xilinx.JBits.Virtex.Bits.*;
import  com.xilinx.JBits.Virtex.Expr;
import  com.xilinx.JBits.Virtex.Util;

// for IO
import java.io.*;


class JBitsResources {

	boolean recording;	// when true, all set() calls are recorded
	String recordfilename;
	File outputfile;	// file that calls are recorded in
	FileWriter fwriter;
	BufferedWriter bufferedwriter;// for buffered access to file

	String bitstreamFile;	// name of the (null) bitstream to init FPGA
	ContentionLookup ctable;// for looking up contentious resources
	Map sramidxtable;	// gives index of S?RAM resource in bitmap
	String[] allramres;	// gives name of S?RAM resource, given index
	JBits jbits;		// the FPGA resources
	int numRows;
	int numCols;

	// activeFNopMatrix stores ActiveFN op for each configured LUT
	// note op is one of AND, OR, XOR, NAND, NOR, XNOR, MJR0, MJR1;
	String[][] activeFNopMatrix=null;

	// activeFNinvMatrix stores the inversion mask for the LUT inputs
	// with '0' indicating invert, and '1' indicating uninverted
	String[][] activeFNinvMatrix=null;

	// activeLinematrix stores active lines for LUTActiveFNs, generally
	// only within the evolvable region of the FPGA CLB matrix. the lower
	// corner of the CLB matrix is given by activeRowOff,activeColOff.
	// note that we represent a CLB with 4 array elements: 2 rows of LEs
	// and 2 cols of slices; and then for each of these there are 4 lines
	// represented in the 3rd dimension of the array.
	// this matrix is generated by the ActiveInputLEs class, but if it
	// isn't provided then active lines are determined simply by testing
	// if input is connected
	int[][][] activeLinematrix=null;

	// these give the CLB coords of the lower corner of activeLinematrix
	// ie of activeLinematrix[0-1][0-1][*]
	int activeRowOff=0;
	int activeColOff=0;

	int ACTIVE=0xf;	// should be the same as defined in ActiveInputLEs 


public static void main(String args[])
{

  int flags=0;
  int argc=args.length;
  String setfile=null;
  String outbit=null;
  JBitsResources jbits;

  if (argc>=5 && args[3].equals("load")) { // load settings from recorded file
    setfile=args[4];
    if (argc>5) { outbit=args[5]; }
    jbits=new JBitsResources(args[+0],args[2],args[1]);
    System.out.print("loading set instructions from file: "+setfile+" .. ");
    jbits.loadRecordFile(setfile);
    System.out.println("done.");
  } else {
    if (argc<7 || (args[5].equals("isEquiv") && argc<8)) {
      System.err.println("synopsis: JBitsResources devname bitfile contentionfile  (load setfile [ outbit ]) | (row col (get type=bits | getvname type=bits | set type=bits,val [ outbit ] | isEquiv bits type=bits,equivVal))");
      System.exit(1);
    }

    jbits=new JBitsResources(args[+0],args[2],args[1]);
    int row=Integer.parseInt(args[3]);
    int col=Integer.parseInt(args[4]);
    String fn=args[5];
    String expr=args[6];
    String[] fields=StringSplitter.trimsplit(expr,"=");
    if (fields.length<2) {
      System.err.println("exprn must be of form: type=bits or type=bits,value");
      System.exit(1);
    }
    String type=fields[0];
    String bitsval=fields[1];

    if (fn.equals("get")) {
      int[] value=jbits.get(row,col,type,bitsval);
      System.out.println(Util.IntArrayToString(value));
    } else if (fn.equals("getvname")) { // get value as named const if possible
      String valname=jbits.getvname(row,col,type,bitsval);
      System.out.println(valname);
    } else if (fn.equals("set")) {
      if (argc>7) {
	outbit=args[7];
	jbits.recordToFile(outbit+".record.txt");
        System.out.println("recording set() to file: "+outbit+".record.txt ..");
      }
      fields=StringSplitter.trimsplit(bitsval,",");
      String bits=fields[0];
      int[] value1=jbits.get(row,col,type,bits);
      jbits.set(row,col,type,bitsval);
      int[] value2=jbits.get(row,col,type,bits);
      System.out.print(Util.IntArrayToString(value1));
      System.out.println(" -> "+Util.IntArrayToString(value2));
      if (argc>7) {
	jbits.closeRecordFile();
        System.out.println("closed file: "+outbit+".record.txt ..");
      }
    } else if (fn.equals("isEquiv")) {
      int[] value=jbits.get(row,col,type,bitsval);
      fields=StringSplitter.trimsplit(args[7],"=");
      if (fields.length<2) {
        System.err.println("exprn must be of form: type=bits,value");
        System.exit(1);
      }
      boolean equiv=jbits.isEquiv(row,col,value,fields[0],fields[1]);
      System.out.println(equiv);
    } else {
      System.err.println("invalid function name. must be one of: get, set, isEquiv");
     System.exit(1);
    }
  }

  if (outbit!=null) {
    System.out.print("writing bitstream to file: "+outbit+" .. ");
    jbits.writeBitstream(outbit);
    System.out.println("done.");
  }

}



JBitsResources(String deviceName, String contentionFile, String bitfile)
{
  recording=false;
  recordfilename=null;
  bitstreamFile=bitfile;
  int deviceType = Devices.getDeviceType(deviceName);
  numCols=Devices.getClbColumns(deviceType);
  numRows=Devices.getClbRows(deviceType);
  activeFNopMatrix=new String[numRows*2][numCols*2]; // 4 LUTs per CLB: 2 LEs
  activeFNinvMatrix=new String[numRows*2][numCols*2];// per row & 2 slice/col
  jbits = new JBits(deviceType);
  loadBitstream(bitfile);
  ctable=new ContentionLookup(contentionFile);
  sramidxtable=new HashMap();

  // Note: the order in the mapping is the same as that of the fields in the
  // javadoc for S0RAM/S1RAM
  // 	DUAL_MODE, F_LUT_RAM, F_LUT_SHIFTER, G_LUT_RAM, G_LUT_SHIFTER,
  // 	LUT_MODE, RAM_32_X_1

  allramres=new String[] {"DUAL_MODE","F_LUT_RAM","F_LUT_SHIFTER","G_LUT_RAM","G_LUT_SHIFTER","LUT_MODE","RAM_32_X_1"};

  sramidxtable.put("DUAL_MODE",new Integer(0));
  sramidxtable.put("F_LUT_RAM",new Integer(1));
  sramidxtable.put("F_LUT_SHIFTER",new Integer(2));
  sramidxtable.put("G_LUT_RAM",new Integer(3));
  sramidxtable.put("G_LUT_SHIFTER",new Integer(4));
  sramidxtable.put("LUT_MODE",new Integer(5));
  sramidxtable.put("RAM_32_X_1",new Integer(6));
}


// this is used to gain direct access to the FPGA resources,
// and so should be used with care
public JBits getJBits() { return jbits; }

public int getCols() { return numCols; }
public int getRows() { return numRows; }

public boolean inRange(int row, int col)
{
  return (row>=0 && col>=0 && row<numRows && col<numCols);
}


// return the settings for a given JBits resource
// note that LUTBitFN, LUTIncrFN and LUTActiveFN will return the LUT's bit
// settings, while SliceRAM needs to be treated differently as we need to
// query 2 or 3 resources
// type is one of: jbits, LUT, LUTIncrFN, LUTActiveFN, SliceRAM
// bitsvalstr is a combination of the bits and value (etc) settings for
//   that resource in comma delimited format - we ignore all but first field
//   which gives the bits field (eg LUT.SLICE0_G) or classname (eg S0RAM)

// this uses type=resource format
public int[] get(int row, int col, String typeNresource)
{
  String[] fields=null;

  try {
    fields=StringSplitter.trimsplit(typeNresource,"=");
    if (fields.length!=2) { throw new Exception("type=resource format required in typeNresource; was: "+typeNresource); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  return get(row,col,fields[0],fields[1]);
}

public int[] get(int row, int col, String type, String bitsvalstr)
{
  // extract the bits (or class) portion of the sting (the first field)
  String[] fields=StringSplitter.trimsplit(bitsvalstr,",");
  if (type.equals("SliceRAM")) {
    return getSliceRAM(row,col,fields[0]);
  } else {
    return getbits(row,col,fields[0]);
  }
}

// this is for JBits/LUT (LUTIncrFN is treated as LUT) resources, not SliceRAM
public int[] getbits(int row, int col, String strbits)
{
  int[][] bits=JBitsLookup.getBits(strbits);
  return get(row,col,bits);
}



int[] get(int row, int col, int[][] bits)
{
  int[] value=null;
  try {
    if (!inRange(row,col)) {
      throw new ConfigurationException("CLB row or col out of valid range for device");
    }
    value=jbits.get(row,col,bits);
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  return value;
}

// strclassbits is a string of either S0RAM or S1RAM
// as we manipulate SliceRAM at a slightly higher level which involves
// setting a few resources (to ON) in conjunction, need to know which of
// these are set to ON. so return an int[] of 0's and 1's for the settings of
// the resources, in the following order (same order as fields in javadoc):
// 	DUAL_MODE, F_LUT_RAM, F_LUT_SHIFTER, G_LUT_RAM, G_LUT_SHIFTER,
// 	LUT_MODE, RAM_32_X_1
int[] getSliceRAM(int row, int col, String strclassbits)
{
  int[] retval=new int[allramres.length];
  // set all of retval to 0's
  for (int i=0; i<retval.length;i++) { retval[i]=0; }

  Integer idx;
  int[][] bits;
  int[] currentvalue;
  String on=strclassbits+".ON";		// S0RAM.ON, or S1RAM.ON
  int[] onvalue=JBitsLookup.getValue(on);
//System.err.println(on+"="+Util.IntArrayToString(onvalue));
  String strbits;
  // now we query all the associated resources
  for (int i=1; i<allramres.length;i++) {
    // prepend the class name to the resource
    strbits=strclassbits+"."+allramres[i];
    // check resource's current value
    bits=JBitsLookup.getBits(strbits);
    currentvalue=get(row,col,bits);
//System.err.println("strbits="+strbits+" current value="+Util.IntArrayToString(currentvalue));
    // if current value is ON
    if (Arrays.equals(currentvalue,onvalue)) {
      // set the appropriate bit for that resource
      retval[i]=1;
    }
  }

  // return int[] with 1's for each resource that is ON, and 0's otherwise
  return retval;
}



// this is for returning the value as a named constant, if type=jbits
// or a string representation of the LUT's value otherwise

// this uses type=resource format
public String getvname(int row, int col, String typeNresource)
{
  String[] fields=null;

  try {
    fields=StringSplitter.trimsplit(typeNresource,"=");
    if (fields.length!=2) { throw new Exception("type=resource format required in typeNresource; was: "+typeNresource); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  return getvname(row,col,fields[0],fields[1]);
}


// type is one of: jbits, LUT, LUTIncrFN, LUTActiveFN, SliceRAM
// bitsvalstr is a combination of the bits and value (etc) settings for
//   that resource in comma delimited format - we ignore all but first field
//   which gives the bits field (eg LUT.SLICE0_G) or classname (eg S0RAM)

public String getvname(int row, int col, String type, String bitsvalstr)
{
  int[] bits;
  String vname;

  // extract the bits (or S?RAM class) portion of the string (the 1st field)
  String[] fields=StringSplitter.trimsplit(bitsvalstr,",");
  if (type.equals("SliceRAM")) {
    // convert from bits to SliceRAM setting names
    // vname is: bitsconst1,bitsconst2,.. reprenting which of the
    // S?RAM resources to set to S?RAM.ON (S?RAM is S0RAM or S1RAM)
    // bits is an int[] of 0's and 1's for the settings of the resources,
    // the associated resource names are stored in allramres[]
    bits=getSliceRAM(row,col,fields[0]);
    // extract the class name
    String bitconsts=null;
    for (int i=0; i<bits.length; i++) {
      if (bits[i]==1) {
	if (bitconsts==null) {
	  bitconsts=new String(allramres[i]);
	} else {
	  bitconsts=bitconsts + "," + allramres[i];
	}
      }
    }
    // *** Note: this assumes at least 1 SliceRAM setting must be set to on
    vname=bitconsts;
  } else { // its a jbits or LUT* type
    bits=getbits(row,col,fields[0]);
    if (type.substring(0,3).equals("LUT")) {
      // if type is a LUT* then get string representation of the bit array
      vname=Util.IntArrayToString(bits);
    } else {
      // otherwise look up the value constant name for the given class
      String[] bitsfields=StringSplitter.split(fields[0],".");
      String strclass=bitsfields[0];
      if (bitsfields.length>2) { // inner class
        strclass=strclass + "." + bitsfields[1];
      }
      vname=JBitsLookup.getName(strclass,bits);
      vname=strclass + "." + vname;
    }
  }
  return vname;
}



// configure the specified JBits resource (the first field of bitsvalstr)
// according to the following fields (of bitsvalstr)
//
// type is one of: jbits, LUT, LUTIncrFN, LUTActiveFN, SliceRAM
// bitsvalstr is a combination of the bits and value (etc) settings for
//   that resource in comma delimited format

// this uses type=resourcesetting format
public void set(int row, int col, String typeNresourceset)
{
  String[] fields=null;
  try {
    fields=StringSplitter.trimsplit(typeNresourceset,"=");
    if (fields.length!=2) { throw new Exception("type=resourceset format required in typeNresourceset; was: "+typeNresourceset); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  set(row,col,fields[0],fields[1]);
}

public void set(int row, int col, String type, String bitsvalstr)
{
  String[] fields=null;
  // extract the bits and value portion of the string (1st & 2nd fields)
  try {
    fields=StringSplitter.trimsplit(bitsvalstr,",",1);
    if (fields.length<2) { throw new Exception("bit,value format required in bitsvalstr; was: "+bitsvalstr); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  if (recording) { record(row,col,type,bitsvalstr); }
  
  if (type.substring(0,3).equals("LUT")) {
    // this is needed because of LUTActiveFNs, so that when we use a LUTbitFN
    // or LUTIncrFN it won't get updated too, so we put a null entry here
    // fields[0] should be LUT.SLICE#_F/G
    int slice=(fields[0].charAt(9)=='0') ? 0 : 1;
    int le=(fields[0].charAt(11)=='G') ? 0 : 1;	// LE G-Y is 0, F-X is 1
    activeFNopMatrix[(row*2)+le][(col*2)+slice]=null;
  }

  if (type.equals("LUTActiveFN")) {
    setLUTActiveFN(row,col,bitsvalstr);
  } else if (type.equals("LUTIncrFN")) {
    setLUTIncrFN(row,col,bitsvalstr);
  } else if (type.equals("SliceRAM")) {
    setSliceRAM(row,col,bitsvalstr);
  } else {
    // for jbits and LUT types fields will be bits and value
    setJBits(row,col,fields[0],fields[1]);
  }
}


// record the set command's details to file
void record(int row, int col, String type, String bitsvalstr)
{
  String setstr=new String("set: "+row+","+col+": "+type+"="+bitsvalstr);
  writeToRecordFile(setstr); 
}


// this is for JBits/LUT resources, not SliceRAM or LUTIncrFN
public void setJBits(int row, int col, String strbits, String strval)
{
  int[][] bits;
  int[] value;

  // need to treat LUT's differently as strval will be a string of
  // 16-bit binary digits (already inverted for storage), rather than
  // the name of a JBits constant array
  if (strbits.substring(0,3).equals("LUT")) {
    bits=JBitsLookup.getBits(strbits);
    value=LUTIncrFN.binStringToIntArray(strval);
    set(row,col,bits,value);
  } else {
    // need to do contention checking before setting resources
    if (!isContentious(row,col,strbits,strval)) {
      bits=JBitsLookup.getBits(strbits);
      value=JBitsLookup.getValue(strval);
      set(row,col,bits,value);
    }
  }
}


// this is the interface to use to add the matrix from ActiveInputLEs
// so as to be able to determine which lines are actually active
// because the matrix usually only covers the evolvable region of the FPGA
// need to provide the CLB row & col offsets for it
public void addActiveLineMatrix(int[][][] alm, int rowoff, int coloff)
{
  activeLinematrix=alm;
  activeRowOff=rowoff;
  activeColOff=coloff;
}

public void addActiveLineMatrix(int[][][] alm)
{
  addActiveLineMatrix(alm,0,0);
}

// in case ACTIVE code gets changed in ActiveInputLEs, we can change it here
public void setActiveCode(int activecode)
{
  ACTIVE=activecode;
}


// update the LUT, so that it retains the same function, but updated to
// only use the currently active lines
public void updateLUTActiveFN(int row, int col, int slice, int le)
{
  String boolop=activeFNopMatrix[(row*2)+le][(col*2)+slice];
  // if not configured before with an ActiveFN op, then we do nothing
  if (boolop!=null) {
    String linesinv=activeFNinvMatrix[(row*2)+le][(col*2)+slice];
    String active=getActiveLinesTemplate(row,col,slice,le,linesinv);
    String lutname=new String("LUT.SLICE"+slice+"_"+((le==0)?"G":"F"));
    setLUTIncrFN(row,col,lutname+",SET,"+boolop+","+active);
  }
}


// this implements a 'SET' type LUTIncrFN based on the currently active inputs
public void setLUTActiveFN(int row, int col, String bitsnexpr)
{
  String[] fields=null;
  try {
    fields=StringSplitter.trimsplit(bitsnexpr,",");
    // fields are: LUT.SLICE#_F/G, boolop, lines
    if (fields.length!=3) { throw new Exception("bits,boolop,lines format required in bitsnexpr; was: "+bitsnexpr); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

//System.err.println("LUT="+fields[0]+", boolop="+fields[1]+", linesinv="+fields[2]);
  // field[0] should be LUT.SLICE#_F/G
  int slice=(fields[0].charAt(9)=='0') ? 0 : 1;
  int le=(fields[0].charAt(11)=='G') ? 0 : 1;	// LE G-Y is 0, F-X is 1

  // generate a lines template based on which lines are active (using the
  // information generated elsewhere by ActiveInputLEs, or locally if absent)
  // and the line inversion template
  String active=getActiveLinesTemplate(row,col,fields[0],fields[2]);
  // now implement it using LUTIncrFN
  setLUTIncrFN(row,col,fields[0]+",SET,"+fields[1]+","+active);

  // store the boolean function, so that can update LUT fn according to
  // active lines, also store its line inversion template
  activeFNopMatrix[(row*2)+le][(col*2)+slice]=fields[1]; // fields[1] is boolop
  activeFNinvMatrix[(row*2)+le][(col*2)+slice]=fields[2]; // fields[2]=linesinv
}



public void setLUTIncrFN(int row, int col, String bitsnexpr)
{
  String[] fields=null;
  try {
    fields=StringSplitter.trimsplit(bitsnexpr,",");
    // fields are: LUT.SLICE#_F/G, setop, boolop, lines
    if (fields.length<4) { throw new Exception("bits,setop,boolop,lines format required in bitsnexpr; was: "+bitsnexpr); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  int[][] bits=JBitsLookup.getBits(fields[0]);
  int[] currentval=get(row,col,bits);
  int[] value=LUTIncrFN.newValue(currentval,fields[1],fields[2],fields[3]);
  set(row,col,bits,value);
}



void set(int row, int col, int[][] bits, int[] value)
{
  try {
    if (!inRange(row,col)) { throw new ConfigurationException("CLB row or col out of valid range for device"); }
    jbits.set(row,col,bits,value);
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}



// here we use a higher level approach, as several resources need to be set
// in conjunction.
// strclassbits is: S?RAM, bitsconst1,bitsconst2,.. reprenting which of the
// S?RAM resources to set to S?RAM.ON (S?RAM is S0RAM or S1RAM depending on
// the slice) the resources that can be set are:
// 	DUAL_MODE, F_LUT_RAM, F_LUT_SHIFTER, G_LUT_RAM, G_LUT_SHIFTER,
// 	LUT_MODE, RAM_32_X_1
public void setSliceRAM(int row, int col, String strclassbits)
{
  int[][] bits;
  int[] value;
  String[] fields=StringSplitter.trimsplit(strclassbits,",");
  // fields[0] is the classname (S0RAM or S1RAM)
  String strclass=fields[0];
  String on=strclass+".ON";		// S0RAM.ON, or S1RAM.ON
  String off=strclass+".OFF";		// S0RAM.OFF, or S1RAM.OFF
  String strbits;

  // reset all S?RAM resources to OFF
  for (int i=0; i<allramres.length;i++) {
    strbits=strclass+"."+allramres[i];
    bits=JBitsLookup.getBits(strbits);
    value=JBitsLookup.getValue(off);
    set(row,col,bits,value);
  }

  // set all selected resources to ON
  for (int i=1; i<fields.length;i++) {
    strbits=strclass+"."+fields[i];
    bits=JBitsLookup.getBits(strbits);
    value=JBitsLookup.getValue(on);
    set(row,col,bits,value);
  }
}


// so that all set commands can be logged
public void recordToFile(String file)
{
  recording=true;
  recordfilename=file;

  try {
    // create the file, a file writer, and buffered writer
    outputfile=new File(file);
    fwriter=new FileWriter(outputfile);
    bufferedwriter=new BufferedWriter(fwriter);
  } catch (Exception e) {
    System.err.println("Error creating " + file + "\n" + e);
    e.printStackTrace();
    System.exit(1);
  }
}

public void closeRecordFile() 
{
  try {
    bufferedwriter.close();
  } catch (Exception e) {
    System.err.println("Error closing " + recordfilename + "\n" + e);
    e.printStackTrace();
    System.exit(1);
  }

  recordfilename=null;
}

public void recordOff() { recording=false; }
public void recordOn()
{
  try {
    if (recordfilename==null) { throw new Exception("recordToFile() must be called first!"); }
    recording=true;
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}


// load recorded set commands, and use them to configure the FPGA
// if recording is on, then these will also be recorded
public void loadRecordFile(String file)
{
  loadAndSetFromFile(file);
}


// checks if a given setting (strval) for a resource (strbits) will result
// in contention occuring.
// strbits is the name of the bits field (eg OutMuxToSingle.SINGLE_EAST5)
// strval is the namue of the value field (eg OutMuxToSingle.ON)

public boolean isContentious(int row, int col, String strbits, String strval)
{
  // possibly contentious resource & setting
  int[][] bits=JBitsLookup.getBits(strbits);
  int[] value=JBitsLookup.getValue(strval);

  ContentionEntry ce;
  int[][] cbits;
  int[] cval;
  int[] currentval;
  boolean contentious=false;

  // check for an entry for this resource & setting
  List clist=ctable.getList(strbits, strval);
  if (clist!=null) {
    // iterate through the list of possible contentious settings
    ListIterator iter = clist.listIterator();
    while (iter.hasNext()) {
      // get an entry for a competing resource
      ce=(ContentionEntry)iter.next();
      // if on FPGA borders, need to check that don't exceed them to check
      // contentious values on a out-of-range CLB
      if (row+ce.rowOffset>=0 && row+ce.rowOffset<=numRows-1 &&
	  col+ce.colOffset>=0 && row+ce.rowOffset<=numCols-1) {
        cbits=JBitsLookup.getBits(ce.bitString);
        cval=JBitsLookup.getValue(ce.valString);
        // check if this competing resource has a contentious value
        currentval=get(row+ce.rowOffset,col+ce.colOffset,cbits);
        if (Arrays.equals(cval,currentval)) {
	  // current value of this resource will create contention
          contentious=true;
          break;
        }
      }
    }
  }

  return contentious;
}



// this is used by LUTActiveFNs for generating a template that can be used
// with LUTIncrFNs, based on the currently active LUT inputs and the
// lines inversion template

String getActiveLinesTemplate(int row, int col, String lutname, String linesinv)
{
  int slice=-1, le=-1;

  try {
    // lutname should be LUT.SLICE#_F/G
    if (lutname.equals("LUT.SLICE0_G")) {
      slice=le=0;
    } else if (lutname.equals("LUT.SLICE0_F")) {
      slice=0; le=1;
    } else if (lutname.equals("LUT.SLICE1_G")) {
      slice=1; le=0;
    } else if (lutname.equals("LUT.SLICE1_F")) {
      slice=le=1;
    } else {
      throw new Exception("Invalid lutname ("+lutname+") should be LUT.SLICE[01]_[FG]!");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  return getActiveLinesTemplate(row,col,slice,le,linesinv);
}


String getActiveLinesTemplate(int row, int col, int slice, int le, String linesinv)
{
  String inprefix=null;
  if (slice==0 && le==0) { 
    inprefix=new String("S0G");
  } else if (slice==0 && le==1) { 
    inprefix=new String("S0F");
  } else if (slice==1 && le==0) { 
    inprefix=new String("S1G");
  } else { // if (slice==1 && le==1)
    inprefix=new String("S1F");
  }


  // NB: order of lines in template is 4,3,2,1
  StringBuffer active=new StringBuffer("----");

  // if we have information on active status of LUT input lines, as generated
  // elsewhere by ActiveInputLEs class, then use this, otherwise will just
  // check if input is connected or not
  if (activeLinematrix!=null) {
    int arow=((row-activeRowOff)*2)+le;
    int acol=((col-activeColOff)*2)+slice;
    try {
      if (arow<0 || arow>activeLinematrix.length ||
          acol<0 || acol>activeLinematrix[0].length) {
	throw new Exception("("+row+","+col+",S"+slice+((le==0)?"G-Y":"F-X")+") is outside of activeLinematrix range 0-"+activeLinematrix.length+",0-"+activeLinematrix[0].length+" using CLB offset "+activeRowOff+","+activeColOff+"!");
      }
    } catch (Exception e) {
      System.err.println(e);
      e.printStackTrace();
      System.exit(1);
    }

    for (int i=1; i<=4; i++) {
      if (activeLinematrix[arow][acol][i-1]==ACTIVE) {
        active.setCharAt(4-i,linesinv.charAt(4-i));
      }
    }
  } else {
    String inputname=null;
    int[][] bits=null;
    int[] inputval=null;

    // check each LUT input to see if it is connected or not
    for (int i=1; i<=4; i++) {
      inputname=new String(inprefix+i);
      bits=JBitsLookup.getBits(inputname+"."+inputname);
      inputval=get(row,col,bits);
      // check that it's not a disconnected input
      if (!isEquiv(row,col,inputval,inputname+"."+inputname+","+inputname+".OFF")) {
        // set the corresponding input to be direct or inverted
        active.setCharAt(4-i,linesinv.charAt(4-i));
//System.err.println("line "+i+" is active. template ->"+active);
      } else {
//System.err.println("line "+i+" is OFF");
      }
    }
  }

//System.err.println("active template="+active);

  return active.toString();
}



// determine if the specified settings for a given resource (as given in
// bitsvalstr) produces the same setting (or is equivalent to) as the
// currentval of that resource (obtained from a get())
//
// currentval is an int array returned from a get() for the resource to be
//   tested for equivalence
// type is one of: jbits, LUT, LUTIncrFN, LUTActiveFN, SliceRAM
// bitsvalstr is a combination of the bits and value (etc) settings for
//   that resource in comma delimited format

public boolean isEquiv(int row, int col, int[] currentval, String type, String bitsvalstr)
{
  if (type.equals("LUTActiveFN")) {
    return isLUTActiveFNequiv(row,col,currentval,bitsvalstr);
  } else if (type.equals("LUTIncrFN")) {
    return isLUTIncrFNequiv(row,col,currentval,bitsvalstr);
  } else if (type.equals("SliceRAM")) {
    return isSliceRAMequiv(row,col,currentval,bitsvalstr);
  } else {
    return isEquiv(row,col,currentval,bitsvalstr);
  }
}


// this is for JBits/LUT resources, not SliceRAM or LUTIncrFN
public boolean isEquiv(int row, int col, int[] currentval, String bitsvalstr)
{
  String[] fields=null;
  try {
    // split into bits and value portions
    fields=StringSplitter.trimsplit(bitsvalstr,",",1);
    if (fields.length<2) { throw new Exception("bits,value format required in bitsvalstr; was: "+bitsvalstr); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  int[] value;

  // need to treat LUT's differently as strval will be a string of
  // 16-bit binary digits (already inverted for storage), rather than
  // the name of a JBits constant array
  if (fields[0].substring(0,3).equals("LUT")) {
    value=LUTIncrFN.binStringToIntArray(fields[1]);
  } else {
    // get the int[] that represents that value
    value=JBitsLookup.getValue(fields[1]);
  }
  // return whether that value equals the current (queried) value
  return (Arrays.equals(value,currentval));
}


// this is only for JBits resource values (eg S0F1.OFF)
public boolean isEquivVal(int row, int col, int[] currentval, String valstr)
{
  // get the int[] that represents that value
  int[] value=JBitsLookup.getValue(valstr);
  // return whether that value equals the current (queried) value
  return (Arrays.equals(value,currentval));
}


public boolean isLUTIncrFNequiv(int row, int col, int[] currentval, String bitsvalstr)
{
  String[] fields=null;
  try {
    // split into bits and exprn (set,op,lines) part, we only need the latter
    fields=StringSplitter.trimsplit(bitsvalstr,",",1);
    if (fields.length<2) { throw new Exception("bits,value format required in bitsvalstr; was: "+bitsvalstr); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // return whether that value equals the current (queried) value
  return (LUTIncrFN.isEquiv(currentval,fields[1]));
}


public boolean isLUTActiveFNequiv(int row, int col, int[] currentval, String bitsvalstr)
{
  String[] fields=null;
  try {
    // split into bits,op,lines
    fields=StringSplitter.trimsplit(bitsvalstr,",");
    if (fields.length<2) { throw new Exception("bits,value format required in bitsvalstr; was: "+bitsvalstr); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // generate lines template based on active lines & line inversion template
  String active=getActiveLinesTemplate(row,col,fields[0],fields[2]);

  // now implement it using LUTIncrFN
  return isLUTIncrFNequiv(row,col,currentval,fields[0]+",SET,"+fields[1]+","+active);
}


public boolean isSliceRAMequiv(int row, int col, int[] currentval, String bitsvalstr)
{
  String[] fields=null;
  try {
    // split into bits and value-list parts, we only need the latter
    fields=StringSplitter.trimsplit(bitsvalstr,",",1);
    if (fields.length<2) { throw new Exception("bits,value-list format required in bitsvalstr; was: "+bitsvalstr); }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // get the int[] that represents that value-list
  int[] value=getSliceRAMbitmap(fields[1]);
  // return whether that value equals the current (queried) value
  return (Arrays.equals(value,currentval));
}


// this takes a string of the form: bitsconst1,bitsconst2,..
// and returns an int array of 1's and 0's (a bitmap really), that provides
// the equivalent functionality to a
//      value=JBitsLookup.getValue(strval)
// but because we take a higher-level approach to S?RAM resources, which
// involves setting a few resources in conjunction, we return an int array of
// 0's and 1's for each of the resources in the class, instead, with only
// those entries specified by the bitsconst's having a 1, while the others
// are set to 0. The order in the array is the same as that of the fields in
// the javadoc:
// 	DUAL_MODE, F_LUT_RAM, F_LUT_SHIFTER, G_LUT_RAM, G_LUT_SHIFTER,
// 	LUT_MODE, RAM_32_X_1
int[] getSliceRAMbitmap(String strbits)
{
  int[] retval=new int[sramidxtable.size()];
  Integer idx;

  // set all of retval to 0's
  for (int i=0; i<retval.length;i++) { retval[i]=0; }
  // split string and set corresponding retval entry to a 1
  String[] resources=StringSplitter.trimsplit(strbits,",");
  for (int i=0; i<resources.length;i++) {
    idx=(Integer)sramidxtable.get(resources[i]);
    retval[idx.intValue()]=1;
  }

  return retval;
}


// initialise the JBits device with the (null) bitstream
public void init()
{
  loadBitstream(bitstreamFile);
}


public void loadBitstream(String bitfile)
{
//System.err.println("reading bitstream from file "+bitfile+" ...");
  try {
    jbits.read(bitfile);
  } catch (FileNotFoundException fe) {
    System.err.println("File "+bitfile+" not found!");
    System.err.println(fe);
    fe.printStackTrace();
    System.exit(1);
  } catch (IOException io) {
    System.err.println("Error reading the bitstream file");
    System.err.println(io);
    io.printStackTrace();
    System.exit(1);
   } catch (ConfigurationException ce) {
    System.err.println("Internal error detected in the bitstream");
    System.err.println(ce);
    ce.printStackTrace();
    System.exit(1);
   }
}



public void writeBitstream(String outfilename)
{
//System.err.println("writing bitstream to file "+outfilename+" ...");
  // Write the Bitstream to a file
  try {
    jbits.write(outfilename);
  } catch (IOException ioe) {
    System.err.println("Error writing file "+ outfilename + ". Exiting");
    System.err.println(ioe);
    ioe.printStackTrace();
    System.exit(1);
  }
}



void writeToRecordFile(String setstr)
{
  try {
    bufferedwriter.write(setstr);
    bufferedwriter.newLine();		// ensures platform independence
  } catch (Exception e) {
    System.err.println("Error writing to " + recordfilename + "\n" + e);
    e.printStackTrace();
    System.exit(1);
  }
}


// this loads set commands from a file, that would have been recorded
// before. it ignores empty lines, and lines starting with a '#'
// indicating a comment.
// the format of lines in the file is :
// 	[command:]row,col:type=resourceset
// command should be 'set', any other commands are ignored

void loadAndSetFromFile(String file)
{
  InputStream inputstream=null;
  InputStreamReader inputstreamreader=null;
  BufferedReader bufferedreader=null;

  String line;

  try {
    // open the file and create a buffered input stream reader
    inputstream = new FileInputStream(file);
    inputstreamreader=new InputStreamReader(inputstream);
    bufferedreader = new BufferedReader(inputstreamreader);
  } catch (Exception e) {
    System.err.println("Error opening " + file + "\n" + e);
    System.exit(1);
  }

  // read the file
  try {
    while ((line = bufferedreader.readLine()) != null) {
      line=line.trim();		// strip leading & trailing whitespace
//System.err.println(line);
      // ignore empty lines, and lines starting with a '#' indicating a comment
      if (line.length()>0 && line.charAt(0)!='#') {
	processReadLine(line);
      }
    }
  } catch (Exception e) {
    System.err.println("Error reading file!");
    System.err.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }


  try { 
    // finished with file, so close it
    inputstream.close();
  } catch (Exception e) {
    System.err.println("Error closing file! exiting...");
    e.printStackTrace();
    System.exit(1);
  }

}


// the format of lines in the file is :
// 	[command:]row,col:type=resourceset
// command should be 'set', any other commands are ignored

void processReadLine(String line)
{
  try {
    String[] fields=null;
    // split into command (optional), coordinate, and resource-setting
    fields=StringSplitter.trimsplit(line,":");
    if (fields.length<2) { throw new Exception("Invalid line format in file; should be [command:]row,col:type=resourceset was:\n"+line); }
    int ci=(fields.length==2) ? 0 : 1;
    int[] coords=StringSplitter.str2ints(fields[ci],",");
    if (coords.length<2) { throw new Exception("Invalid line format in file; should be [command:]row,col:type=resourceset was:\n"+line); }
    if (fields.length==2 || fields[0].equals("set")) {
      set(coords[0],coords[1],fields[ci+1]);
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}



}// end class



