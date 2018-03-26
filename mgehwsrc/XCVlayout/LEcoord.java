
//			 LEcoord.java
//
// this class stores a logic element (LE)'s coordinates and provides
// constructors that accepts coordinates as either 4 ints, a string of
// 4 comma-delimited ints, or as string  in the same format as its
// string representation (minus the enclosing brackets). It also provides
// an equality test
//
// 		written Jan 2004, J.A. Lee
//
// this class also requires the local class: StringSplitter


// for String's etc
import java.lang.*;


class LEcoord {

// this is for testing only
public static void main(String args[])
{
  LEcoord[] le=new LEcoord[4];
  le[0]= new LEcoord(1,1,1,1);
  le[1]= new LEcoord("1,1,1,0");
  le[2]= new LEcoord("(1,1,S1G-Y)");
  le[3]= new LEcoord("1,1,S0F-X");

  for (int i=0;i<le.length;i++) { System.out.println("LE["+i+"] = "+le[i].toIntString()+" == "+le[i]); }
  if (le[0].equals(le[1])) { System.out.println("LE[0]==LE[1]"); }
  if (le[0].equals(le[2])) { System.out.println("LE[0]==LE[2]"); }
  if (le[0].equals(le[3])) { System.out.println("LE[0]==LE[3]"); }
  if (le[1].equals(le[2])) { System.out.println("LE[1]==LE[2]"); }
  if (le[1].equals(le[3])) { System.out.println("LE[1]==LE[3]"); }
  if (le[2].equals(le[3])) { System.out.println("LE[2]==LE[3]"); }

}

  
  public int row;		// CLB row
  public int col;		// CLB col
  public int slice;		// slice 0 or 1
  public int LE;		// 0 = G LUT, Y/YQ out; 1 = F LUT, X/XQ out

  // this is an optional extra field, that can be used to specify some extra
  // information, eg to specify which LUT line is being connected to, or
  // which out bus line is driving output (for example)
  // it is not included in equivalence tests, toString, etc. as the
  // information it contains is domain specific, and not strictly
  // part of its coordinates
  public int line;
 

// accepts string in formats:
// 	- int,int,int,int
// 	- int,int,S[0|1][GF]-[YX]
LEcoord(String str)
{
  if (str==null) {
    System.err.println("LEcoord(): invalid null String parameter!");
    System.exit(1);
  }

  int[] coord=null;
  // if there's surrounding parentheses, remove them
  if (str.charAt(0)=='(' && str.charAt(str.length()-1)==')') {
    str=str.substring(1,str.length()-1);
  }

  String[] scoord=StringSplitter.split(str,",");

  // if its int the format of: int,int,int,int
  if (scoord.length==4) {
    coord=StringSplitter.str2ints(str,",");
    if (coord.length!=4) {
      str=null;
    } else {
      row=coord[0];
      col=coord[1];
      slice=coord[2];
      LE=coord[3];
    }
  } else if (scoord.length==3 && scoord[2].length()>=3) {
    // its in the format of: int,int,S[01][GF]-[YX]
    row=Integer.parseInt(scoord[0]);
    col=Integer.parseInt(scoord[1]);
    slice=(scoord[2].charAt(1)=='0') ? 0 : 1;
    LE=(scoord[2].charAt(2)=='G') ? 0 : 1;
    coord=new int[0]; // to indicate success
  }

  if (coord==null) {
      System.err.println("LEcoord("+str+"): parameter has invalid format!");
      System.exit(1);
  } else if (row<0 || col<0 || slice<0 || LE<0 || slice>1 || LE>1) {
      System.err.println("Out of range logic element coordinate in LEcoord("+str+")!");
      System.exit(1);
  }
}

LEcoord(LEcoord le)
{
  this(le.row,le.col,le.slice,le.LE,le.line);
}

LEcoord(int r, int c, int s, int l)
{
  this(r,c,s,l,-1);
}

LEcoord(int r, int c, int s, int l, int ln)
{
  if (r<0 || c<0 || s<0 || l<0 || s>1 || l>1) {
    System.err.println("Out of range logic element coordinate in LEcoord("+r+","+c+","+s+","+"l"+")!");
    System.exit(1);
  }

  row=r; col=c; slice=s; LE=l; line=ln;
}


public String toIntString()
{
  return new String(row+","+col+","+slice+","+LE);
}


public String toString()
{
  return new String("("+row+","+col+",S"+slice+((LE==0)?"G-Y":"F-X")+")");
}

public boolean equals(LEcoord lec) 
{
  return (lec.row==row && lec.col==col && lec.slice==slice && lec.LE==LE); 
}

public boolean equals(int r, int c, int s, int l) 
{
  return (r==row && c==col && s==slice && l==LE); 
}


}



