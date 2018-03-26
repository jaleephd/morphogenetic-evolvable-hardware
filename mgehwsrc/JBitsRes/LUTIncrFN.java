//			LUTIncrFN 
//
// this class implements all the required functionality for setting and
// comparing LUTs using incremental function expressions.
//
// 		written December 2003, J.A. Lee
//
// LUT arrays should be supplied (and are returned) as stored on the FPGA,
// that is, inverted and least significant bit first
//
// expressions are in the format of setop,boolop,lines
//
//  	setop	- specifies how to compare the existing LUT with the table
//		  produced by the boolean operation (boolop). setop is one of:
//		    SET - all entries must match,
//		    AND - at least 1 entry from each group must match,
//		    OR - at least 1 entry from 1 group must match, and
//		    XOR - at least 1 group must not have any matches
// 	boolop	- specifies which boolean function to perform on the input
//		  lines. boolop is one of AND, OR, XOR, NAND, NOR, XNOR,
//		  MJR0, MJR1. The latter 2 ops are majority functions, with
//		  tie-breaks on 0 or 1, respectively.
// 	lines	- 4 char template string that specifies how the 4 input lines
//		  are used by the boolean operation to generate a truth table
//		  lines is comprised of '-','0','1' characters, one for each
//		  input, with 0 indicating invert input, 1 direct input, and
//		  a '-' indicating ignore input.
//		  note that the inputs are in order 4,3,2,1


// for String's etc
import java.lang.*;
import java.util.*;

// this class also requires local class: StringSplitter



class LUTIncrFN {


public static void main(String args[])
{

  if (args.length<4) {
    System.err.println("too few args. need: LUT setop boolop lines");
    System.exit(1);
  }

  String lutstr=args[0];
  String setop=args[1];  
  String boolop=args[2];
  String lines=args[3];

  // String lutstr="0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1";
  // String setop="AND";
  // String boolop="MJR1";
  // String lines="1-1-";

  int[] lut=strLUTtoIntLUT(lutstr);
  int[] newlut=newValue(lut,setop,boolop,lines);
  boolean equiv=isEquiv(lut,setop,boolop,lines);

  System.out.println("the new LUT is: " + intArrayToString(newlut));
  System.out.println("the result of equivalence test is: " + equiv);

}



// newValue generates a new LUT configuration bitstream
// by creating a new boolean expression on the LUTs inputs as given by the
// lines template, and then combines this with the existing LUT in the
// manner specified by setop: applying an AND,OR,XOR, of their truth tables
// or SET which ignores existing LUT.

public static int[] newValue(int[] currentval, String exprn)
{
  String[] fields=strExprToSetOpLines(exprn);
  return newValue(currentval,fields[0],fields[1],fields[2]);
}



public static int[] newValue(int[] currentval, String setop, String boolop, String lines)
{
  // first verify that args are correct so don't get unforeseeable behaviour
  exitOnInvalidArgs(currentval,setop,boolop,lines);

  // LUT's are stored inverted, so need to uninvert to combine with exprn
  int[] oldlut=invert(currentval);

  // create new LUT by applying boolean operation to the desired lines
  int[] newlut=makeTruthTable(boolop,lines);

  if (!setop.equals("SET")) {		// ie if combining with old LUT
    int[] lbits=new int[2];
    // for each truth table entry, combine the old and the new
    for (int i=0; i<16; i++) {
      lbits[0]=oldlut[i];
      lbits[1]=newlut[i];
      // make the new LUT combo of old & new
      newlut[i]=booleanOp(setop,lbits);
    }
  }

  // need to invert LUT for storage
  return invert(newlut);
}



// isEquiv tests whether the boolop applied to the given lines is a valid
// component of the LUT's currentval according to setop's criterion
// Note that because reverse decomposition of the LUT is not really feasible,
// it just tests if boolop gives the same outputs on some subset of the LUT
// according to the lines template and setop's manner of judging equivalence

public static boolean isEquiv(int[] currentval, String exprn)
{
  String[] fields=strExprToSetOpLines(exprn);
  return isEquiv(currentval,fields[0],fields[1],fields[2]);
}



public static boolean isEquiv(int[] currentval, String setop, String boolop, String lines)
{
  // first verify that args are correct so don't get unforeseeable behaviour
  exitOnInvalidArgs(currentval,setop,boolop,lines);

  // LUT's are stored inverted, so need to uninvert to combine with exprn
  int[] lut=invert(currentval);

  // create truth table for this boolean operation applied to specified lines
  int[] truthtable=makeTruthTable(boolop,lines);

  // create a table indicating which elements are the same in the 2 LUTs
  int[] equivtable=new int[16];
  for (int i=0; i<16; i++) {
    equivtable[i]=(truthtable[i]==lut[i]) ? 1 : 0;
  }

//System.out.println("equivtable=["+intArrayToBinString(equivtable)+"] .. ");

  // count the number of active lines, ie those that are not ignored
  int nal=0;
  for (int i=0; i<lines.length(); i++) {
    if (lines.charAt(i)!='-') { nal++; }	// '-' indicates line not used
  }

  int[] grpelems;
  String grptemplate;
  // there will be 2^nal groups, each of which represents a combination
  // of active lines.
  int ngrps=(int)Math.round(Math.pow(2,nal));
//System.out.println("num groups="+ngrps);
  int[] groups=new int[ngrps];
  for (int i=0; i<ngrps;i++) {
    grptemplate=makeGroupTemplate(lines,nal,i);
    grpelems=getGroupElements(grptemplate,nal);
    groups[i]=evalGroup(grpelems,equivtable,setop);
//System.out.println("group i="+i+", group template="+grptemplate+", grpelems=["+intArrayToString(grpelems)+"].. "+setop+" evaluates to "+groups[i]);
  }

  // check if the generated truth table is a component of the existing LUT
  // by applying the set operator to the groups, and return true or false

  int result;
  if (setop.equals("SET") || setop.equals("AND")) {
    // for AND or SET, all groups must have a match
    result=booleanOp("AND",groups);
  } else if (setop.equals("XOR")) {
    // XOR acts as a NAND, ie there must be at least 1 non-matching group
    result=booleanOp("NAND",groups);
  } else { // if ((setop.equals("OR")) {
    // for OR, at least one group must have a match
    result=booleanOp("OR",groups);
  }

  return (result==1);
}



// split comma delimited string into an array of ints

public static int[] strLUTtoIntLUT(String value)
{
  String[] fields=StringSplitter.trimsplit(value,",");
  if (fields.length<16) {
    System.err.print("Error in class LUTIncrFN: ");
    System.err.println("too few entries for LUT, have "+fields.length+", need 16!");
    System.exit(1);
  }

  int[] lut=new int[fields.length];
  for (int i=0;i<fields.length;i++) {
    lut[i]=Integer.parseInt(fields[i]);
  }

  return lut;
}



// split exprn into setop, boolop, lines fields

public static String[] strExprToSetOpLines(String exprn)
{
  // split into fields and trim whitespace for consistency
  String[] fields=StringSplitter.trimsplit(exprn,",");
  if (fields.length<3) {
    System.err.print("Error in class LUTIncrFN: ");
    System.err.println("too few fields in exprn ("+exprn+"), was "+fields.length+", need 3!");
    System.exit(1);
  }

  return fields;
}



public static int[] binStringToIntArray(String binstr)
{
  int[] bin=new int[binstr.length()];
  for (int i=0;i<binstr.length();i++) {
    bin[i]=(binstr.charAt(i)=='0') ? 0 : 1;
  }
  return bin;
}


public static String intArrayToBinString(int[] binarr)
{
  String str=new String("");
  for (int i=0;i<binarr.length;i++) {
    str+=binarr[i];
  }
  return str;
}



public static String intArrayToString(int[] arr)
{
  String str=new String("");
  for (int i=0;i<arr.length-1;i++) {
    str+=arr[i]+",";
  }
  str+=arr[arr.length-1];

  return str;
}



public static int[] stringToIntArray(String str)
{
  String[] fields=StringSplitter.trimsplit(str,",");
  int[] arr=new int[fields.length];

  for (int i=0;i<fields.length;i++) {
    arr[i]=Integer.parseInt(fields[i]);
  }

  return arr;
}



public static int invert(int bin)
{
  return (bin==0) ? 1 : 0;
}



public static int[] invert(int[] binarr)
{
  int[] bin=new int[binarr.length];
  for (int i=0;i<binarr.length;i++) {
    bin[i]=(binarr[i]==0) ? 1 : 0;
  }
  return bin;
}



public static String invert(String binstr)
{
  String str=new String("");
  for (int i=0;i<binstr.length();i++) {
    str+=(binstr.charAt(i)=='0') ? "1" : "0";
  }
  return str;
}



public static String toBinary(int dec)
{
  String binstr=Integer.toBinaryString(dec);	// convert to binary string
  return binstr;
}



public static String toBinary(int dec, int ndigits)
{
  String binstr=Integer.toBinaryString(dec);	// convert to binary string

  // if too long, then keep least significant bits
  if (binstr.length()>ndigits) {
    binstr=binstr.substring(binstr.length()-ndigits);
  } else if (binstr.length()<ndigits) {
  // prepend with 0s till get the right number of digits
    for (int i=binstr.length(); i<ndigits; i++) { binstr="0"+binstr; }
  }

  return binstr;
}




// make sure all args are valid so don't get unexpected behaviour occuring

static void exitOnInvalidArgs(int[] lut, String setop, String boolop, String lines)
{
  if (lut.length!=16) {
    System.err.print("Error in class LUTIncrFN: ");
    System.err.println("LUT array is wrong size, "+lut.length+", should be 16!");
    System.err.println("exiting...");
    System.exit(1);
  } else if (!(setop.equals("SET") || setop.equals("AND") || setop.equals("OR")
				   || setop.equals("XOR"))) {
    System.err.print("Error in class LUTIncrFN: ");
    System.err.println("Invalid setop ("+setop+"), must be one of SET,AND,OR,XOR!");
    System.err.println("exiting...");
    System.exit(1);
  } else if (!(boolop.equals("MJR0") || boolop.equals("MJR1") ||
	       boolop.equals("AND") || boolop.equals("NAND") ||
	       boolop.equals("OR") || boolop.equals("NOR") ||
	       boolop.equals("XOR") || boolop.equals("XNOR"))) {
    System.err.print("Error in class LUTIncrFN: ");
    System.err.println("Invalid boolop ("+boolop+"), must be one of MJR0,MJR1,AND,NAND,OR,NOR,XOR,XNOR!");
    System.err.println("exiting...");
    System.exit(1);
  } else if (lines.length()!=4) {
    System.err.print("Error in class LUTIncrFN: ");
    System.err.println("lines template is wrong size, "+lines.length()+", should be 4!");
    System.err.println("exiting...");
    System.exit(1);
  }

  for (int i=0;i<16;i++) {
    if (lut[i]<0 || lut[i]>1) {
      System.err.print("Error in class LUTIncrFN: ");
      System.err.println("LUT["+i+"] has invalid non-boolean value "+lut[i]+"!"); 
      System.err.println("exiting...");
      System.exit(1);
    }
  }

  for (int i=0;i<4;i++) {
    if (lines.charAt(i)!='0' && lines.charAt(i)!='1' && lines.charAt(i)!='-') {
      System.err.print("Error in class LUTIncrFN: ");
      System.err.println("lines template has invalid value at char "+i+" ("+lines.charAt(i)+"),\n\tmust be one of '-','0','1'!");
      System.err.println("exiting...");
      System.exit(1);
    }
  }

}



static String makeGroupTemplate(String lines, int nal, int grpi)
{
  StringBuffer gtemplate=new StringBuffer(lines);

//System.out.println("nal="+nal);
  // we need a binary digit for each active line, ie 'nal' digits
  String bini=toBinary(grpi,nal);		// make binary number

  // replace digits in template with grpi's binary representation
  for (int j=0; j<4; j++) {
    // the inactive lines (-) are left, to create the elements of this group
    if (gtemplate.charAt(j)!='-') {
//System.out.println("grpi="+grpi+", mgt: j="+j+", bini="+bini+", gtemplate="+gtemplate);
      // replace existing digit with this group's digit
      gtemplate.setCharAt(j,bini.charAt(0));
      bini=bini.substring(1);			// get rid of leading char
    }
  }

  return gtemplate.toString();
}



// using the template which gives the fixed, and variable inputs
// generate indexes into the truth table of the group's elements
// (the inputs that match the template)

static int[] getGroupElements(String gtemplate, int nal)
{
  int grpsize=(int)Math.round(Math.pow(2,(4-nal)));
//System.out.println("group size (num elems)="+grpsize+" .. group template="+gtemplate+", nal="+nal);
  int[] grp=new int[grpsize];
  StringBuffer boolnum;
  String bini;
  int j;

  for (int i=0; i<grpsize; i++) {
    boolnum=new StringBuffer(gtemplate);
    // we need a binary digit to insert at each '-', ie we need 4-nal digits
    bini=toBinary(i,4-nal);
//System.out.println("bini="+bini);
    // insert i's binary representation into template replacing inactive lines 
    while (bini.length()>0) {
      j=(boolnum.toString()).indexOf('-');
      // insert 1st char of bini into boolnum where the inactive line '-' is
      boolnum.setCharAt(j,bini.charAt(0));
      bini=bini.substring(1);	// remove leading char - its been inserted
    }

    // boolnum gives a boolean number which when converted to decimal
    // gives the index into truth table (equivalence table actually)
    grp[i]=Integer.parseInt(boolnum.toString(),2); // convert binary to decimal
  }

  return grp;
}



// evaluate the group's elements to see if (any or all, depending on the
// set param) are the same in both the generated truth table, and the LUT
// and for each group produce a 1 to indicate success, or 0 for fail

static int evalGroup(int[] grpelems, int[] equivtable, String setop)
{
  int result;

  if (setop.equals("SET")) {
    // for the SET setop, all elements must match
    result=1;
    for (int i=0; i<grpelems.length; i++) {
      result=result & equivtable[grpelems[i]];
    }
  } else {
    // for OR,AND,XOR setop, only 1 element needs to match
    result=0;
    for (int i=0; i<grpelems.length; i++) {
      result=result | equivtable[grpelems[i]];
    }
  }

  return result;
}



// create truth table using the lines template for deciding which
// (active or inverted) lines to apply the boolean operator to
// ie iterate through entire truth table, ignoring inactive lines
// and inverting inverted lines when applying boolean operator

static int[] makeTruthTable(String boolop, String lines)
{

  int[] truthtable=new int[16];
  String boolnum;
  String bini;
  char t;

//System.out.println("lines="+lines);
  for (int i=0; i<16; i++) {		// create every 4 input combo
    bini=toBinary(i,4);			// as a 4 digit binary number

//System.out.print("i="+i+", bini="+bini+": ");
    boolnum="";
    for (int j=0; j<4; j++) {		// apply lines template to this bin #
//System.out.print("j="+j+" ");
      t=lines.charAt(j);
      if (t=='1') { boolnum += bini.substring(j,j+1); }
      else if (t=='0') { boolnum += (bini.charAt(j)=='1') ? "0" : "1"; }
      // else if t=='-' then ignore
    }
//System.out.println(".. boolnum="+boolnum);

    // apply the boolean operator to the resulting binary number
    // to generate a 1 or 0 in the corresponding truth table entry
    truthtable[i]=booleanOp(boolop,binStringToIntArray(boolnum));
//System.out.println(".. truthtable["+i+"]="+truthtable[i]);
  }

  return truthtable;
}



// perform the boolean operation on all the inputs as given by boolnum

static int booleanOp(String boolop, int[] boolnum)
{
  int cnt0, cnt1;
  int result=0;

  if (boolnum==null) {
    System.err.println("Invalid empty boolnum in LUTIncrFN:booleanOp("+boolop+")!");
    System.exit(1);
  } else if (boolnum.length<1) { // a no-input function
    // all non-inverted functions return 1, the rest 0
    result=(boolop.equals("AND") || boolop.equals("OR") ||
	    boolop.equals("XOR") || boolop.equals("MJR1")) ? 1 : 0;
    return result;
  }


  if (boolop.indexOf("MJR")>-1) {	// if a majority function
    // count the number of 1's, and 0's
    cnt1=0; for (int i=0;i<boolnum.length;i++) { cnt1+=boolnum[i]; }
    cnt0=boolnum.length-cnt1;
    result=(boolop.equals("MJR0") && cnt0>=cnt1 ||
	    boolop.equals("MJR1") && cnt1>=cnt0 ) ? 1 : 0;
  } else if (boolop.equals("AND") || boolop.equals("NAND")) {
    result=boolnum[0];
    for (int i=1;i<boolnum.length;i++) { result=result & boolnum[i]; }
    if (boolop.equals("NAND"))  { result=invert(result); }
  } else if (boolop.equals("OR") || boolop.equals("NOR")) {
    result=boolnum[0];
    for (int i=1;i<boolnum.length;i++) { result=result | boolnum[i]; }
    if (boolop.equals("NOR"))  { result=invert(result); }
  } else if (boolop.equals("XOR") || boolop.equals("XNOR")) {
    result=boolnum[0];
    for (int i=1;i<boolnum.length;i++) { result=result ^ boolnum[i]; }
    if (boolop.equals("XNOR"))  { result=invert(result); }
  } else {
    System.err.println("Invalid boolop ("+boolop+") in LUTIncrFN:booleanOp()!");
    System.exit(1);
  }

//System.out.println(".. boolop="+boolop+", boolnum=["+intArrayToBinString(boolnum)+"], result="+result);

  return result;
}



} // end class



