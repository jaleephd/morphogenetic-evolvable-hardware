//			 CellResourcesMatrix.java
//
// this class stores the resources (both TFs and JBits) for each cell,
// and provides methods for setting, aging (TFs only), querying the
// binding of resources in a given cell, and for loading and saving the
// state of both JBits and TF resources
//
// this class also requires the local classes:
// 		JBitsResources, TFResources, StringSplitter
//
// 		written Dec 2003, J.A. Lee



// for IO
import java.io.*;
// for collection classes (eg Map) & Random class
import java.util.*;


// for JBits object
import com.xilinx.JBits.Virtex.JBits;


class CellResourcesMatrix {

  JBitsResources jbits;
  TFResources tfs;

  // this indicates whether cells are mapped to logic elements (1)
  // or slices (2), and so is used to map cell coords properly
  int cellVGran;	// vert map cells to CLB (2) or LE (1)
  
  int cellHGran=1;	// horizontally map cells to slices

  int numCellRows;
  int numCellCols;

  int clbRowOffset;	// needed for mapping from cell to CLB
  int clbColOffset;	// needed for mapping from cell to CLB/slice

  // locus is only used for bound or produced TFs, so when we need a locus
  // for cytoplasmic determinants, or for consistency on function calls
  // (eg with JBits resources) this is used. Note that must be > 0 or will
  // create probs with TFCell.bindTF() rejecting binding
  static final long locusIgnored=Long.MAX_VALUE;

  // the default time for a morphogen to spread to the neighbouring cell
  static final int defaultMorphogenPropDelay=1;

// this is for testing purposes only
public static void main(String[] args)
{
  int lifespan=8;
  int ageratef=3;
  int agerateb=5;
  if (args.length<4) {
    System.err.println("synopsis: CellResourcesMatrix devname bitfile contentionfile cytoplasmfile");
    System.err.println("eg: CellResourcesMatrix XCV50 null50GCLK1.bit contentionInfo.txt cytobits.txt");
    System.exit(1);
  }

  String devname=args[0];			// eg "XCV50";
  String bitfile=args[1];			// eg null50GCLK1.bit
  String contentious=args[2];
  String cyto=args[3];

  CellResourcesMatrix cells=new CellResourcesMatrix(devname,5,5,6,6,
      contentious,bitfile,cyto,lifespan,ageratef,agerateb,new Random());

  String initialstate0=cells.getTFstate(0,0);
  String initialstate1=cells.getTFstate(0,1);

  String[] prods={
	"TF=Local,,332132",
	"TF=Local,,01132",
	"jbits=OutMuxToSingle.OUT0_TO_SINGLE_SOUTH3,OutMuxToSingle.ON",
	"jbits=OutMuxToSingle.OUT4_TO_SINGLE_NORTH12,OutMuxToSingle.OFF",
	"jbits=OUT7.OUT7,OUT7.OFF/OUT7.OUT7,OUT7.OFF",
	"jbits=S0BX.S0BX,S0BX.SINGLE_WEST14/S1BX.S1BX,S1BX.SINGLE_SOUTH12",
	"jbits=S0F1.S0F1,S0F1.OFF/S1F1.S1F1,S1F1.SINGLE_SOUTH9",
	"jbits=S0SR.S0SR,S0SR.SINGLE_SOUTH1/S1SR.S1SR,S1SR.SINGLE_SOUTH1",
	"LUT=LUT.SLICE0_F,0111111110010000/LUT.SLICE1_F,0111111110010000",
	"LUTIncrFN=LUT.SLICE0_F,AND,NAND,01-0/LUT.SLICE1_F,AND,NAND,01-0",
	"SliceRAM=S0RAM,DUAL_MODE,LUT_MODE/S1RAM,DUAL_MODE,LUT_MODE",
	"SliceRAM=S0RAM,F_LUT_SHIFTER,G_LUT_SHIFTER/S1RAM,F_LUT_SHIFTER,G_LUT_SHIFTER"
  };

  String[] binds={
	"TF=TF,01,223",
	"TF=TF,0,1223",
	"TF=TF,11,1223",
	"TF=TF,,01132",
	"jbits=S0BX.S0BX,S0BX.SINGLE_WEST14/S1BX.S1BX,S1BX.SINGLE_SOUTH12",
	"jbits=S0F1.S0F1,S0F1.OFF/S1F1.S1F1,S1F1.SINGLE_SOUTH9",
	"jbits=OUT7.OUT7,OUT7.S0_YB/OUT7.OUT7,OUT7.S1_YB",
	"LUT=LUT.SLICE0_G,1111011000100001/LUT.SLICE1_G,1111011000100001",
	"LUTIncrFN=LUT.SLICE0_G,AND,AND,00-0/LUT.SLICE1_G,AND,AND,00-0",
	"SliceRAM=S0RAM,DUAL_MODE,LUT_MODE/S1RAM,DUAL_MODE,LUT_MODE"
  };

  for (int i=0; i<prods.length; i++) {
    System.out.println("setting cell 0,0: "+prods[i]);
    cells.set(0,0,prods[i],i*10);
    if (i%2==1) {
      System.out.println("setting cell 0,1: "+prods[i]);
      cells.set(0,1,prods[i],i*10);
    }
  }

  boolean bound=false;
  for (int i=0; i<binds.length; i++) {
    System.out.println("testing binding of: "+binds[i]+" ...");
    bound=cells.isBound(0,0,binds[i],i*10);
    System.out.println("cell 0,0: "+bound);
    bound=cells.isBound(0,1,binds[i],i*10);
    System.out.println("cell 0,1: "+bound);
  }

  System.out.println("\n");

  String[] states=new String[8];

  states[0]=cells.getTFstate(0,0);
  states[1]=cells.getTFstate(0,1);
  cells.updateTFs();
  states[2]=cells.getTFstate(0,0);
  states[3]=cells.getTFstate(0,1);
  cells.clearTFs(0,0);
  states[4]=cells.getTFstate(0,0);
  states[5]=cells.getTFstate(0,1);
  cells.clear();
  cells.setTFstate(0,0,initialstate0);
  states[6]=cells.getTFstate(0,0);
  states[7]=cells.getTFstate(0,1);

  for (int i=0; i<states.length/2; i++) {
    System.out.println("TF states...");
    if (i==1) { System.out.println("TFs aged and morphogen release updated.."); }
    if (i==2) { System.out.println("cell (0,0) TFs cleared.."); }
    if (i==3) { System.out.println("cells cleared and (0,0) reloaded.."); }
    System.out.println("cell(0,0)="+states[i*2]);
    System.out.println("cell(0,1)="+states[(i*2)+1]);
    System.out.println("\n");
  }

}

CellResourcesMatrix(String deviceName,
    	int clbrowmin, int clbcolmin, int clbrowmax, int clbcolmax,
    	String contentionFile, String bitfile, String cytoplasmFile, 
        long tflifespan, int tfageratefree, int tfageratebound, Random rnd)
{
  this (deviceName,clbrowmin,clbcolmin,clbrowmax,clbcolmax,contentionFile,bitfile,cytoplasmFile,tflifespan,tflifespan,tfageratefree,tfageratebound,defaultMorphogenPropDelay,1,2,rnd);
}

CellResourcesMatrix(String deviceName,
    	int clbrowmin, int clbcolmin, int clbrowmax, int clbcolmax,
    	String contentionFile, String bitfile, String cytoplasmFile, 
        long mglifespan, long tflifespan,
	int tfageratefree, int tfageratebound, int mgpropdelay,
        Random rnd)
{
  this (deviceName,clbrowmin,clbcolmin,clbrowmax,clbcolmax,contentionFile,bitfile,cytoplasmFile,mglifespan,tflifespan,tfageratefree,tfageratebound,mgpropdelay,1,2,rnd);
}

CellResourcesMatrix(String deviceName,
    	int clbrowmin, int clbcolmin, int clbrowmax, int clbcolmax,
    	String contentionFile, String bitfile, String cytoplasmFile, 
        long mglifespan, long tflifespan,
	int tfageratefree, int tfageratebound, int mgpropdelay,
	int hgran, Random rnd)
{
  this (deviceName,clbrowmin,clbcolmin,clbrowmax,clbcolmax,contentionFile,bitfile,cytoplasmFile,mglifespan,tflifespan,tfageratefree,tfageratebound,mgpropdelay,hgran,1,rnd);
}

// creates a cell matrix of #CLBrows x 2/vertgran X #CLBcols x 2/horizgran
// horizgran indicates slice (1) or CLB (2) granularity
// vertgran indicates LE (1) or Slice/CLB (2) granularity
// Note: if there is no need for a contention file, or a cytoplasm file
//       then these fields should be passed with values of null or ""
CellResourcesMatrix(String deviceName,
    	int clbrowmin, int clbcolmin, int clbrowmax, int clbcolmax,
    	String contentionFile, String bitfile, String cytoplasmFile, 
        long mglifespan, long tflifespan,
	int tfageratefree, int tfageratebound, int mgpropdelay,
	int horizgran, int vertgran, Random rnd)
{
  jbits=new JBitsResources(deviceName,contentionFile,bitfile);

  try {
    if (clbrowmin<0 || clbrowmax>jbits.getRows() || clbcolmin<0 || clbcolmax>jbits.getCols()) {
      throw new Exception("Invalid min/max CLB row/col specification for this device! must be from 0,0 to "+jbits.getRows()+","+jbits.getCols());
    }

    if (horizgran==1 || horizgran==2) {
      cellHGran=horizgran;
    } else {
      throw new Exception("Invalid horizontal granularity ("+horizgran+"), must be 1 or 2");
    }

    if (vertgran==1 || vertgran==2) {
      cellVGran=vertgran;
    } else {
      throw new Exception("Invalid vertical granularity ("+vertgran+"), must be 1 or 2");
    }

    if (cellVGran==1 && cellHGran==2) {
      throw new Exception("Can't have vertical LE granularity without horizontal slice granularity");
    }

  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // now constrain the area of the FPGA that is being used
  clbRowOffset=clbrowmin;
  clbColOffset=clbcolmin;

  numCellRows=(clbrowmax-clbrowmin+1)*(2/cellVGran); // 2 LEs per CLB row
  numCellCols=(clbcolmax-clbcolmin+1)*(2/cellHGran); // 2 slices per CLB col

  // create the TFs
  tfs=new TFResources(numCellRows,numCellCols,mglifespan,tflifespan,tfageratefree,tfageratebound,mgpropdelay,rnd);

  // set the initial states of resources
  if (cytoplasmFile!=null && cytoplasmFile.length()>0) {
    loadCytoFile(cytoplasmFile);
  }
}


// this is used to gain direct access to the FPGA resources,
// and so should be used with care
public JBits getJBits() { return jbits.getJBits(); }
// this for access via the JBitsResources class
public JBitsResources getJBitsResources() { return jbits; }

public int getVGran() { return cellVGran; }
public int getHGran() { return cellHGran; }

public int getCellRows() { return numCellRows; }
public int getCellCols() { return numCellCols; }

public int getCLBRows() { return jbits.getRows(); }
public int getCLBCols() { return jbits.getCols(); }


boolean isValidCellCoord(int row, int col)
{
  return (row>=0 && col>=0 && row<numCellRows && col<numCellCols);
}

void validateCellCoord(int row, int col)
{
  try {
    boolean valid=isValidCellCoord(row,col);
    if (!valid) {
      throw new Exception("cell row ("+row+") or col ("+col+") out of range (0.."+(numCellRows-1)+", 0.."+(numCellCols-1)+")");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}

// the following configure JBits or TF resources
// for when they are expressed as gene products

public void set(int row, int col, String str)
{
  set(row,col,str,locusIgnored);
}

public void set(int row, int col, String str, long locus)
{
  String[] fields=StringSplitter.split(str,"=");
  set(row,col,fields[0],fields[1], locus);
}


// type is one of: TF, jbits, SliceRAM, LUT, LUTIncrFN
public void set(int row, int col, String type, String details)
{
  set(row,col,type,details,locusIgnored);
}

public void set(int row, int col, String type, String details, long locus)
{
  validateCellCoord(row,col);
  if (type.equals("TF")) {
    setTFs(row,col,details,locus);
  } else {
    // note we don't use/need locus for setting JBits resources
    setJBits(row,col,type,details);
  }
}


void setTFs(int row, int col, String details, long locus)
{

  // details consists of:
  //    TF type (local, morphogen, or cytoplasm)
  //	spread from source (for morphogens)
  //	binding sequence

  // TF details is in form: TFtype,spreadfromsrc,bindseq
  String[] tfdets=StringSplitter.trimsplit(details,",");
  try { 
    if (tfdets.length==3) {
      tfs.createTF(row,col,tfdets[2],tfdets[0],tfdets[1],locus);
    } else {
    throw new Exception("setTFs: wrong number of fields in details: '"+details+"'! Should be type,dist,seq");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}


void setJBits(int row, int col, String type, String details)
{
  int[] clbcoords=cellCoordsToCLBSlice(row,col);
  String dets=extractDetails(row,col,details);
  // if no such resource for this cell, do nothing
  if (dets!=null) {
    jbits.set(clbcoords[0],clbcoords[1],type,dets);
  }
}



// the following are for testing if TF or JBits resources
// are bound to the regulatory regions of a gene
// the supplied string is the resource details of the bindsite element,
// indicating what resource may bind there

boolean isBound(int row, int col, String str)
{
  return isBound(row,col,str,locusIgnored);
}

boolean isBound(int row, int col, String str, long locus)
{
  String[] fields=StringSplitter.split(str,"=");
  return isBound(row,col,fields[0],fields[1],locus);
}

boolean isBound(int row, int col, String type, String details)
{
  return isBound(row,col,type,details,locusIgnored);
}


// type may be "connect", which indicates a resource connecting from another
// CLB, or one of: TF, jbits, SliceRAM, LUT, LUTIncrFN
// details format depends on the type:
//   TF: TFtype,distfromsrc,bindseq
//   jbits/LUT/LUTIncrFN: bits "," val [ "/" bits "," val ] (optionally) X 3
//   SliceRAM: S0RAM,bitsconst1,bitsconst2,../S1RAM,bitsconst1,bitsconst2,..
// note that locus is ignored for non-TFs,
// for TFs, if the TF isn't yet bound, then it will be bound to that locus
// (and return a value of true), but if the TF is already bound then the
// the locus has to match that of the already-bound TF

boolean isBound(int row, int col, String type, String details, long locus)
{
  validateCellCoord(row,col);
  if (type.equals("connect")) {
    return isBoundConnection(row,col,details,locus);
  } else if (type.equals("TF")) {
    return isBoundTF(row,col,details,locus);
  } else {
    // we don't use/need locus for querying JBits resources
    return isBoundJBits(row,col,type,details);
  }
}


// TFs don't automatically bind, so here we check if there is one
// bound already, and if not then we check if any free TFs that can
// be bound, and if so bind it
// TF details is in form: TFtype,distfromsrc,bindseq
// the distfromsrc field hould be empty, it's only used by morphogens

boolean isBoundTF(int row, int col, String details, long locus)
{
  String[] tfdets=StringSplitter.trimsplit(details,",");
  try {
    if (tfdets.length!=3) {
    throw new Exception("wrong number of fields in TF details: '"+details+"'! Should be type,dist,seq");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  String dist=tfdets[1];
  String seq=tfdets[2];

  // the following checks if a TF already bound here
  String tf=tfs.getBoundTF(row,col,dist,seq,locus);
//System.out.println("isBoundTF() - getBoundTF() returns: "+tf);
  if (tf==null) {
    // otherwise find if any matching free (and close) TFs can bind here
    String ftf=tfs.getFreeTF(row,col,dist,seq,locus);
//System.out.println("isBoundTF() - getFreeTF() returns: "+ftf);
    if (ftf!=null) {
//System.out.println("matching TF - binding free TF here...");
//tfs.printTFs(row,col);
      // there are matching TFs so bind a free TF to this location
      String btf=tfs.bindTF(row,col,dist,seq,locus);
      if (btf!=null) {
//System.out.println("bound TF..."); 
//tfs.printTFs(row,col);
        // site is now bound to by a TF
        tf=btf;
      }
//else { System.out.println("failed to bind TF..."); }
    }
  }

  // return if this site is bound to by a TF
  return (tf!=null);
}


// connecting resource from another CLB. details should be in format:
//	type,rowoffset,coloffset,bits,value
//	[ / rowoffset,coloffset,bits,value ] (optionally X 3)
// with offsets being a +ve or -ve integer, or 0
// so, we extract the details, and then check if this resource binds
// (exists in this state) in the connecting CLB
boolean isBoundConnection(int row, int col, String details, long locus)
{
  String[] typeNalldetails=StringSplitter.split(details,",",1);
  try {
    if (!typeNalldetails[0].equals("jbits")) {
      throw new Exception("Invalid type for a connecting resource ("+typeNalldetails+"), should be 'jbits'.\nFor connections using 'TF's use a TF with non-empty distance field (morphogen).");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
//System.out.println("isBoundConnection: details="+details+" .. real type="+typeNalldetails[0]+", real details="+typeNalldetails[1]);
  // we don't use/need locus for querying JBits resources
  return isBoundJBits(row,col,typeNalldetails[0],typeNalldetails[1],true);
}


boolean isBoundJBits(int row, int col, String type, String details)
{
  return isBoundJBits(row,col,type,details,false);
}


// details should be in format:
//	[rowoffset,coloffset,] bits,value
//	[ / [rowoffset,coloffset,] bits,value ] (optionally X 3)
// with offsets being a +ve or -ve integer, or 0
// so, we extract the details, and then check if this resource binds
// (exists in this state) in the connecting CLB
boolean isBoundJBits(int row, int col, String type, String details, boolean connection)
{
  int roff, coff;
  int[] value=null;
  // get CLB coords for this cell
  int[] clbcoords=cellCoordsToCLBSlice(row,col);
  int clbrow=clbcoords[0];
  int clbcol=clbcoords[1];
  String[] fields;
  String offsetdets=null;

  String dets=extractDetails(row,col,details);
  if (dets!=null) {
    // extract the bits/class field (eg S0RAM, LUT.SLICE0_F, OUT0.OUT0, etc)
    fields=StringSplitter.trimsplit(dets,",",2);
    // sometimes there is no connecting resource, so would be: "0,0,"
    if (connection && fields.length<3) {
      // if no such connecting resource, then no bind possible
      return false;
    } else if (connection) {
      // if a connection then the first 2 fields are CLB offsets
      roff=StringSplitter.parseint(fields[0]);
      coff=StringSplitter.parseint(fields[1]);
      offsetdets=fields[2];
      fields=StringSplitter.trimsplit(offsetdets,",",1);
//System.out.println("isBoundJBits: dets="+dets+", roff="+roff+", coff="+coff+", offsetdets="+offsetdets);
      // if it's a non-connectable non-local resource-setting then no bind
      if (roff==0 && coff==0) { return false; }
      // get current value for the appropriate slice
      value=jbits.get(clbrow+roff,clbcol+coff,type,fields[0]);
      // are the settings equivalent?
      return jbits.isEquiv(clbrow+roff,clbcol+coff,value,type,offsetdets);
    } else {
      // get current value for the appropriate slice
      value=jbits.get(clbrow,clbcol,type,fields[0]);
      // are the settings equivalent?
      return jbits.isEquiv(clbrow,clbcol,value,type,dets);
    }
  } else {
    // if no such resource for this cell, then no bind possible
    return false;
  }
}


// details is in form:
//   clbdetails, or
//   slice0details "/" slice1details, or 
//   S0-GYdetails "/" S0-FXdetails "/" S1-GYdetails "/" S1-FXdetails 
// do a split on '/', and then if slice- or LE-specific, use appropriate
// slice or LE (logic element), noting that some slice- or LE- specific
// resource fields may be empty (indicated with a "" or "-") if there isn't a
// corresponding resource for that slice or LE, in this case null is returned

String extractDetails(int row, int col, String details)
{
  int slice=(cellHGran==1) ? col%2 : -1;
  int le=(cellVGran==1) ? row%2 : -1;
  String dets=null;

  String[] sledets=StringSplitter.trimsplit(details,"/");
  try {
    if (sledets.length==1) { // CLB-wide
      dets=details;
    } else if (sledets.length==2) { // slice-specific
      if (cellHGran==1) {
        dets=sledets[slice];
      } else {
        throw new Exception("Invalid number of details fields (2) for a non-slice granularity cell in: "+details);
      }
    } else if (sledets.length==4) { // logic element-specific
      if (cellVGran==1) {
        dets=sledets[(slice*2)+le];
      } else {
        throw new Exception("Invalid number of details fields (4) for a non-LE granularity cell in: "+details);
      }
    } else {
      throw new Exception("Invalid number of details fields in: "+details);
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // check in case there is no corresponding resource for this LE
  if (dets.equals("") || dets.equals("-")) { dets=null; }

  return dets;
}


// the following are for converting between cell-based coordinates
// and CLB/slice/logic-element based coordinates.
// note that cell coords run from 0,0 at min CLB coords (slice 0)
// to max row, max col at max CLB coords (slice 1)

// return 2,3 or 4 element int array of clbRow,clbCol,[Slice[,logic element]]
int[] cellCoordsToCLB(int row, int col)
{
  if (cellHGran==2) {
    return new int[] { row+clbRowOffset, col+clbColOffset };
  } else {
    return cellCoordsToCLBSlice(row,col);
  }
}

// return 3 or 4 element int array of clbRow, clbCol, Slice [, logic element ]
int[] cellCoordsToCLBSlice(int row, int col)
{
  if (cellVGran==2) {
    return new int[] { row+clbRowOffset, (col/2)+clbColOffset, col%2 };
  } else {
    return cellCoordsToCLBSliceLE(row,col);
  }
}

int[] cellCoordsToCLBSliceLE(int row, int col)
{
  return new int[] { (row/2)+clbRowOffset,(col/2)+clbColOffset,col%2,row%2 };
}


// return 2 element int array of cell row, cell col
int[] CLBToCellCoords(int row, int col)
{
  return CLBSliceToCellCoords(row,col,0);
}

int[] CLBSliceToCellCoords(int row, int col, int slice)
{
  return CLBSliceLEToCellCoords(row,col,slice,0);
}

int[] CLBSliceLEToCellCoords(int row, int col, int slice, int le)
{
  int[] coords=new int[] {
    ((row-clbRowOffset)*2/cellVGran)+le, ((col-clbColOffset)*2/cellHGran)+slice
  };
  return coords;
}



// age all the TFs in the system, release morphogens, & remove dead TFs
public void updateTFs()
{
  tfs.updateTFs();
}

// age the TFs in the given cell, release morphogens, & remove dead TFs
public void updateTFs(int row, int col)
{
  validateCellCoord(row,col);
  tfs.updateTFs(row,col);
}



// clear the state for entire system - actually, we clear TF state
// and init JBits resources with a (null) bitstream
public void clear()
{
  clearTFs();
  initJBits();
}

// re-initialise the FPGA with the supplied bitstream 
public void initJBits()
{
  jbits.init();
}

// clear the TFs (ie remove them) for all cells
public void clearTFs()
{
  tfs.clear();
}


// clear the state for a given cell - actually, we only clear TF state
// as we only init JBits resources with a (null) bitstream
public void clear(int row, int col)
{
  clearTFs(row,col);
}

// clear the TFs (ie remove them) for the given cell
public void clearTFs(int row, int col)
{
  validateCellCoord(row,col);
  tfs.clear(row,col);
}




// save the FPGA's current configuration to file
public void writeBitstream(String outfile) { jbits.writeBitstream(outfile); }

// get the TFs state for a given cell
public String getTFstate(int row, int col)
{
  validateCellCoord(row,col);
  return tfs.getState(row,col); 
}

// set the TFs state for a given cell
public void setTFstate(int row, int col, String statestr)
{
  validateCellCoord(row,col);
  tfs.setState(row,col,statestr);
}







// read the file in format:
//   cell-row, cell-col: resource-type "=" resource-details
// where:
// 	resource-type is one of:
// 		TF, jbits, LUT, LUTIncrFN, SliceRAM
//	resource-details for TF is in the form of:
//		TF-type,distfromsrc,bind-seq
//	resource-details for SliceRAM is in the form:
//		slice0class,bitsconst1,bitsconst2,../
//		slice1class,bitsconst1,bitsconst2,..
//	resource-details for jbits, LUT, LUTIncrFN is in the form of:
//		bits,val [ "/" bits,val [ "/" bits,val "/" bits,val ] ]

void loadCytoFile(String cytoplasmFile)
{
  InputStream inputstream=null;
  InputStreamReader inputstreamreader=null;
  BufferedReader bufferedreader=null;

  int linenum=0;
  String line=null;
  String[] fields;
  String coords, typeNdetails, type, details;
  int row,col;

  try {
    // open the file and create a buffered input stream reader
    inputstream = new FileInputStream(cytoplasmFile);
    inputstreamreader=new InputStreamReader(inputstream);
    bufferedreader = new BufferedReader(inputstreamreader);
  } catch (IOException ioe) {
    System.err.println("Error opening " + cytoplasmFile + "\n");
    System.err.println(ioe);
    ioe.printStackTrace();
    System.exit(1);
  }

  try {
    while ((line = bufferedreader.readLine()) != null) {
      linenum++;
      line=line.trim();		// strip leading & trailing whitespace
//System.out.println(line);
      // ignore empty lines, and lines starting with a '#' indicating a comment
      if (line.length()>0 && line.charAt(0)!='#') {
	// get coords and type+details
        fields=StringSplitter.trimsplit(line,":");
	if (fields.length!=2) {
          throw new Exception("Invalid format on line "+linenum+" of "+cytoplasmFile+"! ..\nShould be row,col : type=details");
	}
	coords=fields[0];
	typeNdetails=fields[1];

	// get row and col coords
        fields=StringSplitter.trimsplit(coords,",");
	if (fields.length!=2) {
          throw new Exception("Invalid coords format on line "+linenum+" of "+cytoplasmFile+"! ..\nShould be row, col : type=details");
	}
	row=Integer.parseInt(fields[0]);
	col=Integer.parseInt(fields[1]);

	// get type and details fields
        fields=StringSplitter.trimsplit(typeNdetails,"=");
	if (fields.length!=2) {
          throw new Exception("Invalid type and details format on line "+linenum+" of "+cytoplasmFile+"! ..\nShould be row, col : type=details");
	}
	type=fields[0];
	details=fields[1];

        // now store this
        set(row,col,type,details);
      }
    }
  } catch (IOException ioe) {
    System.err.println("Error reading file "+cytoplasmFile+"!");
    System.err.println(ioe);
    ioe.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }


  try { 
    // finished with file, so close it
    inputstream.close();
  } catch (IOException ioe) {
    System.err.println("Error closing file "+cytoplasmFile+"! exiting...");
    System.exit(1);
  }
 
}





} // end class



