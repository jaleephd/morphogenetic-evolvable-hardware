//			StringSplitter 
//
// this class implements a String split utility, with aditional functions
// for slitting and trimming whitespace, and for splitting and parsing
// fields into an array of int or long
// this split() is needed as JBits uses jdk 1.2.2 and String.split() isn't
// supported before jdk 1.4
//
// 		written December 2003, J.A. Lee


// for String's, Exception's etc
import java.lang.*;
// for collection classes (eg Map) & Random class
import java.util.*;


class StringSplitter {

public static void main(String args[])
{

  if (args.length<3) {
    System.err.println("too few args. need: split|trimsplit|str2ints|str2longs str delim [max]");
    System.exit(1);
  }

  String fn=args[0];
  String str=args[1];
  String delim=args[2];  
  int max=-1;

  if (args.length>3) {
    max=Integer.parseInt(args[3]);
  }

  if (fn.equals("split")) {
    String[] fields=StringSplitter.split(str,delim,max);
    System.out.print("result="); 
    for (int i=0; i<fields.length;i++) {
      System.out.print("["+fields[i]+"]"); 
    }
  } else if (fn.equals("trimsplit")) {
    String[] fields=StringSplitter.trimsplit(str,delim,max);
    System.out.print("result="); 
    for (int i=0; i<fields.length;i++) {
      System.out.print("["+fields[i]+"]"); 
    }
  } else if (fn.equals("str2ints")) {
    int[] ints=StringSplitter.str2ints(str,delim,max);
    System.out.print("result="); 
    for (int i=0; i<ints.length;i++) {
      System.out.print("["+ints[i]+"]"); 
    }
  } else if (fn.equals("str2longs")) {
    long[] longs=StringSplitter.str2longs(str,delim,max);
    System.out.print("result="); 
    for (int i=0; i<longs.length;i++) {
      System.out.print("["+longs[i]+"]"); 
    }
  } else {
    System.err.println("function must be one of: split trimsplit str2ints str2longs");
    System.exit(1);
  }

  System.out.println();

}


// this will split a string on the delimiter and return an array
// of strings (some possibly empty) minus the delimiter
// its behaviour is the same as perl's split function

public static String[] split(String str, String delim)
{
  return split(str,delim,-1); // maxsplits of -1 indicates no maximum number
}

public static String[] split(String str, String delim, int maxsplits)
{
  // maxsplits of -1 indicates no maximum number
  String [] retstrs=null;
  List strlist = new ArrayList();
  int scnt=0;
  int idx=0;

  while ((scnt<maxsplits || maxsplits<0 ) && (idx=str.indexOf(delim))>-1) {
    strlist.add(str.substring(0,idx)); // begin index to end index-1
    str=str.substring(idx+delim.length());
    scnt++;
  }

  if (str.length()>0) {		// get trailing substring
    strlist.add(str);
    scnt++;
  }

  retstrs = new String[strlist.size()];

  for (int i=0;i<strlist.size();i++) {
    retstrs[i]=(String)strlist.get(i);
  }

  return retstrs;
}



// split on delimiter, and then trim of leading and trailing whitespace
public static String[] trimsplit(String str, String delim)
{
  return trimsplit(str,delim,-1); // maxsplits of -1 indicates no max number
}

public static String[] trimsplit(String str, String delim, int maxsplits)
{
  String[] strs=split(str,delim,maxsplits);
  for (int i=0;i<strs.length;i++) {
    strs[i]=strs[i].trim();
  }
  return strs;
}



// split on delimiter, and then convert to an array of int
public static int[] str2ints(String str, String delim)
{
  return str2ints(str,delim,-1);
}

public static int[] str2ints(String str, String delim, int maxsplits)
{
  String[] fields=trimsplit(str,delim,maxsplits);
  // if there is a limit on number of splits, then the trailing string
  // should not be processed as there is no guarantee that it is an int
  // it may have the delimiter within it
  if (maxsplits>0) {
    String[] tfields=fields;
    // keep all but last field
    fields=new String[tfields.length-1];
    for (int i=0; i<tfields.length-1; i++) { fields[i]=tfields[i]; }
  }

  // parse the fields to generate an array of ints
  int[] ifields=new int[fields.length];
  for (int i=0; i<fields.length; i++) {
    ifields[i]=parseint(fields[i]);
  }
  return ifields;
}


// converts a string (possibly with sign extension) to an int
// needed because Integer.parseInt() doesn't handle sign extensions
public static int parseint(String str)
{
  int result=0;;

  try {
    if (str==null || str.length()<1) {
      throw new Exception("illegal null or empty argument!");
    }
    char sign=str.charAt(0);
    if (sign=='+' || sign=='-') { str=str.substring(1); } // remove sign
    result=Integer.parseInt(str);
    if (sign=='-') { result=result*-1; }
  } catch (Exception e) {
    e.printStackTrace();
    System.exit(1);
  }

  return result;
}



// split on delimiter, and then convert to an array of long
public static long[] str2longs(String str, String delim)
{
  return str2longs(str,delim,-1);
}

public static long[] str2longs(String str, String delim, int maxsplits)
{
  String[] fields=trimsplit(str,delim,maxsplits);
  // if there is a limit on number of splits, then the trailing string
  // should not be processed as there is no guarantee that it is an int
  // it may have the delimiter within it
  if (maxsplits>0) {
    String[] tfields=fields;
    // keep all but last field
    fields=new String[tfields.length-1];
    for (int i=0; i<tfields.length-1; i++) { fields[i]=tfields[i]; }
  }

  // parse the fields to generate an array of longs
  long[] lfields=new long[fields.length];
  for (int i=0; i<fields.length; i++) {
    lfields[i]=parselong(fields[i]);
  }
  return lfields;
}


// converts a string (possibly with sign extension) to a long
public static long parselong(String str)
{
  long result=0;

  try {
    if (str==null || str.length()<1) {
      throw new Exception("illegal null or empty argument!");
    }
    char sign=str.charAt(0);
    if (sign=='+' || sign=='-') { str=str.substring(1); } // remove sign
    result=Long.parseLong(str);
    if (sign=='-') { result=result*-1; }
  } catch (Exception e) {
    e.printStackTrace();
    System.exit(1);
  }

  return result;
}


}

