
//			 TFResources.java
//
// this class stores the TFs for each cell, and provides methods for accessing
// and manipulating the TFs in a given cell, such as creating and aging TFs,
// binding TFs to a locus on a chromosome, querying a bound or unbound TF,
// and for saving and restoring the state of TFs in a cell.
//
// this class also requires the local class: TFCell
//
// 		written Dec 2003, J.A. Lee



// for IO
import java.io.*;
// for collection classes (eg Map) & Random class
import java.util.*;


class TFResources {

  TFCell[][] tfmatrix;

  int numRows;
  int numCols;

  // the number of cell updates for a morphogen to propogate to the next cell
  int morphogenPropogationDelay;

  // this is used where there is no locus (for eg cytoplasm TFs)
  // or we don't care what the locus is, eg for checking if any free TFs
  static final long locusIgnored=-1;

  // the default time for a morphogen to spread to the neighbouring cell
  static final int defaultMorphogenPropDelay=1;

  Random rand;	// random number generator passed in from enclosing system

  boolean DEBUG;

  
// this is for testing purposes only
public static void main(String[] args)
{
  int rows=4;
  int cols=4;

  int lifespan=8;
  int ageratef=3;
  int agerateb=5;

  int row=2;
  int col=2;
  int row2=1;
  int col2=0;

  TFResources tfs=new TFResources(rows,cols,lifespan,lifespan,ageratef,agerateb,1,new Random(),true);

  String[] tflist= { "Local,,332132", 
  		     "Local,,2030322003100",
		     "Local,,01132",
		     "Local,,21223",
		     "Morphogen,11001000,21103",
		     "Morphogen,00001001,01301",
		     "Morphogen,00111101,11103",
		     "Morphogen,01000100,20123",
		     "cytoplasm,,21223"
                   };

  String seq,type,dist;
  String[] fields;
  long locus=1000;
  int ttr;

  for (int i=0; i<tflist.length; i++) {
    fields=StringSplitter.split(tflist[i],",");
    seq=fields[2];
    type=fields[0];
    dist=fields[1];
    locus+=i*10;
    if (!fields[0].equals("Morphogen")) {
      tfs.createTF(row,col,seq,type,dist,locus);
      if (i%2!=1) {
        tfs.createTF(row2,col2,seq,type,dist,locus-990);
      }
    } else {
      tfs.createMorphogen(row,col,seq,dist,locus);
    }
  }


  tfs.printTFs();
  fields=StringSplitter.split(tflist[0],",");
  dist=fields[1]; seq=fields[2];

  String TF0=tfs.getFreeTF(row,col,dist,seq,locus);
  String TFnull=tfs.getFreeTF(row,col,"011","32",locus);

  System.out.println("getFreeTF("+dist+","+seq+","+locus+"): "+TF0);
  System.out.println("getFreeTF(011,32,"+locus+"): "+TFnull);

  String bTF0=tfs.bindTF(row,col,dist,seq,locus-500);
  System.out.println("bindTF("+dist+"+"+seq+","+(locus-500)+"): "+bTF0);

  String gbTF0=tfs.getBoundTF(row,col,dist,seq,locus-500);
  TFnull=tfs.getBoundTF(row,col,"011","32",locus);

  System.out.println("getBoundTF("+dist+"+"+seq+","+(locus-500)+"): "+gbTF0);
  System.out.println("getBoundTF(011,32,"+locus+"): "+TFnull);

  fields=StringSplitter.split(tflist[4],",");
  dist=fields[1]; seq=fields[2];

  String bTF1=tfs.bindTF(row,col,dist,seq,locus-300);
  System.out.println("bindTF("+dist+"+"+seq+","+(locus-300)+"): "+bTF1);

  System.out.println("\n\n");
  tfs.printTFs();

  System.out.println("\n\nbeginning updates...\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  // create duplicate
  tfs.createTF(row,col,"01132","Local","",(long)1030);
  tfs.printTFs();

  // save state at this point
  System.out.println("saving state of cell 1 now...");
  String statestr=tfs.getState(row,col);
  System.out.println("\nsaved TF cell 1 state is..\n"+statestr);

  System.out.println("\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  System.out.println("saving state of cell 2 now...");
  String statestr2=tfs.getState(row2,col2);
  System.out.println("\nsaved TF cell 2 state is..\n"+statestr2);

  System.out.println("\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  System.out.println("\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  System.out.println("\nclearing TFs..");
  tfs.clear();
  tfs.printTFs();

  System.out.println("\nsaved TF cell 1 state was..\n"+statestr);
  System.out.println("restoring cell 1 state to cell 2...");
  tfs.setState(row2,col2,statestr);
  System.out.println("\nsaved TF cell 2 state was..\n"+statestr2);
  System.out.println("restoring cell 2 state to cell 1...");
  tfs.setState(row,col,statestr2);
  System.out.println("\nrestored TF state..\n");
  tfs.printTFs();

  System.out.println("\n\ndoing updates...\n\n");

  tfs.updateTFs();
  tfs.printTFs();

} // end main() test code



TFResources(int rows, int cols, long lifespan, int ageratef, int agerateb, Random rnd)
{
  this(rows,cols,lifespan,lifespan,ageratef,agerateb,rnd);
}

TFResources(int rows, int cols, long mglifespan, long tflifespan, int ageratef, int agerateb, Random rnd)
{
  this(rows,cols,mglifespan,tflifespan,ageratef,agerateb,defaultMorphogenPropDelay,rnd);
}

TFResources(int rows, int cols, long mglifespan, long tflifespan, int ageratef, int agerateb, int mpropdelay, Random rnd)
{
  this(rows,cols,mglifespan,tflifespan,ageratef,agerateb,defaultMorphogenPropDelay,rnd,false);
}

TFResources(int rows, int cols, long mglifespan, long tflifespan, int ageratef, int agerateb, int mgpropdelay, Random rnd, boolean dbg)
{
  DEBUG=dbg;
  numRows=rows;
  numCols=cols;
  morphogenPropogationDelay=mgpropdelay;
  rand=rnd;

  tfmatrix=new TFCell[numRows][numCols];
  for (int i=0; i<numRows; i++) {
    for (int j=0; j<numCols; j++) {
      tfmatrix[i][j]=new TFCell(mglifespan,tflifespan,ageratef,agerateb);
    }
  }
}


public int getCols() { return numCols; }
public int getRows() { return numRows; }

public boolean validCoord(int r, int c) 
{
  return (r>=0 && c>=0 && r<numRows && c<numCols);
}
 
// create an unbound TF in specified cell
public void createTF(int row, int col, String seq, String type, String distfromsrc, long locus)
{
  if (type.charAt(0)=='m' || type.charAt(0)=='M') { // morphogen
    createMorphogen(row,col,seq,distfromsrc,locus);
  } else {
    (tfmatrix[row][col]).createTF(seq,type,distfromsrc,locus);
  }
}

public void createTF(int row, int col, String seq, String type, String distfromsrc)
{
  createTF(row,col,seq,type,distfromsrc,locusIgnored);
}


// distribute morphogens starting at the source, with the degree of spread
// from the source (row,col) being given by spreadfromsrc
public void createMorphogen(int row, int col, String seq, String spreadfromsrc, long locus)
{
  if (DEBUG) { System.out.println("creating morphogens with seq='"+seq+"', spreadfromsrc='"+spreadfromsrc+"', spreading from "+row+","+col); }
  String distfromsrc;
  int r,c,ttr;
  CellCoords[] spread=createMorphogenSpread(row,col,spreadfromsrc);
  for (int i=0; i<spread.length; i++) {
    r=spread[i].row;
    c=spread[i].col;
    distfromsrc=genDistFromSrc(row,col,r,c);
    ttr=calcReleaseDelay(row,col,r,c);
    if (DEBUG) { System.out.println("placing morphogen TF '"+distfromsrc+"+"+seq+"' at "+r+","+c+" with ttr="+ttr); }
    tfmatrix[r][c].addMorphogen(ttr,seq,distfromsrc,locus);
  }
}


// 
// placement is done by following the axis out to the extent indicated by
// the row/col spread. then on the cells of the qudrants between axis, the
// distance metric is filled up to the lowest distance specified by the two,
// for example:
//
// a spread of row from -1 .. +3, col from -2 .. 0, would look like
//
//	7 . . . . . . . .
//	6 . . . 3 . . . .
//	5 . . . 2 . . . .
//	4 . . 2 1 . . . .
//	3 . 2 1 0 . . . .
//	2 . . . 1 . . . .
//	1 . . . . . . . .
//	0 . . . . . . . .
//	  0 1 2 3 4 5 6 7

CellCoords[] createMorphogenSpread(int row, int col, String spreadfromsrc)
{
  if (DEBUG) { System.out.println("creating morphogen spread from "+row+","+col+" with spreadfromsrc='"+spreadfromsrc+"'.."); }
  int x,y,rowp,rown,colp,coln;
  int[] spreads=decodeSpread(spreadfromsrc);
  int rspreadn=spreads[0];
  int rspreadp=spreads[1];
  int cspreadn=spreads[2];
  int cspreadp=spreads[3];

  List cellist=new ArrayList();
  
  cellist.add(new CellCoords(row,col));	// add source

  // add cells along row & col axis (so long as within valid cell range)
  for (int yoff=-rspreadn;yoff<=rspreadp;yoff++) {
    if (yoff!=0 && validCoord(row+yoff,col)) {
      cellist.add(new CellCoords(row+yoff,col));
    }
  }
  for (int xoff=-cspreadn;xoff<=cspreadp;xoff++) {
    if (xoff!=0 && validCoord(col+xoff,row)) {
      cellist.add(new CellCoords(row,col+xoff));
    }
  }

  // now do the quadrants ..

  int quad1mn=min(rspreadn,cspreadn);
  int quad2mn=min(rspreadp,cspreadn);
  int quad3mn=min(rspreadn,cspreadp);
  int quad4mn=min(rspreadp,cspreadp);

  if (DEBUG) { System.out.println("quad1mn="+quad1mn+", quad2mn="+quad2mn+", quad3mn="+quad3mn+", quad4mn="+quad4mn); }

  // quad1: row-1,col-1 down to manhattan dist quad1mn
  for (int yoff=1;yoff<=quad1mn;yoff++) {
    // decrease spread on 2nd axis as move further from center on 1st axis 
    for (int xoff=1;xoff<=quad1mn-yoff;xoff++) {
      if (validCoord(row-yoff,col-xoff)) {
	cellist.add(new CellCoords(row-yoff,col-xoff));
      }
    }
  }
  // quad2: row+1,col-1 up/down to manhattan dist quad2mn
  for (int yoff=1;yoff<=quad2mn;yoff++) {
    // decrease spread on 2nd axis as move further from center on 1st axis 
    for (int xoff=1;xoff<=quad2mn-yoff;xoff++) {
      if (validCoord(row+yoff,col-xoff)) {
	cellist.add(new CellCoords(row+yoff,col-xoff));
      }
    }
  }
  // quad3: row-1,col+1 down/up to manhattan dist quad3mn
  for (int yoff=1;yoff<=quad3mn;yoff++) {
    // decrease spread on 2nd axis as move further from center on 1st axis 
    for (int xoff=1;xoff<=quad3mn-yoff;xoff++) {
      if (validCoord(row-yoff,col+xoff)) {
	cellist.add(new CellCoords(row-yoff,col+xoff));
      }
    }
  }
  // quad4: row+1,col+1 up to manhattan dist quad4mn
  for (int yoff=1;yoff<=quad4mn;yoff++) {
    // decrease spread on 2nd axis as move further from center on 1st axis 
    for (int xoff=1;xoff<=quad4mn-yoff;xoff++) {
      if (validCoord(row+yoff,col+xoff)) {
	cellist.add(new CellCoords(row+yoff,col+xoff));
      }
    }
  }

  if (DEBUG) { System.out.println("generated morphogen spread: "+cellist); }
  // now convert from ArrayList to a standard array
  // note use of empty array param to the ArrayList.toArray() method
  return (CellCoords[])cellist.toArray(new CellCoords[0]);
}



//  spreadfromsrc, is an 8-digit binary number representing the spread
//  that the morphogen will do. this is used in the following manner:
//	- digits 0-3 represent the row spread in Gray code
//		- digits 0,1 gives extent of spread to rows in -ve direction
//		- digits 2,3 gives extent of spread to rows in +ve direction
//	- digits 4-7 represent the col spread in Gray code
//		- digits 4,5 gives extent of spread to cols in -ve direction
//		- digits 6,7 gives extent of spread to cols in +ve direction
//	- Gray code is 00=0, 01=1, 11=2, 10=3
//
// so for example: 0110 1100 would be translated as:
//	row spread = from -1 .. +3; col spread = from -2 .. 0

int[] decodeSpread(String spreadfromsrc)
{
  if (DEBUG) { System.out.println("decoding spread: "+spreadfromsrc+".."); }
  int[] spreadmetrics=new int[4];
  spreadmetrics[0]=binaryToDecimal(grayToBinary(spreadfromsrc.substring(0,2)));
  spreadmetrics[1]=binaryToDecimal(grayToBinary(spreadfromsrc.substring(2,4)));
  spreadmetrics[2]=binaryToDecimal(grayToBinary(spreadfromsrc.substring(4,6)));
  spreadmetrics[3]=binaryToDecimal(grayToBinary(spreadfromsrc.substring(6,8)));
  if (DEBUG) { System.out.println("row spread = -"+spreadmetrics[0]+" .. +"+spreadmetrics[1]+"; col spread = -"+spreadmetrics[2]+" .. +"+spreadmetrics[3]); }
  return spreadmetrics;
}


// to convert from a reflected "Gray code" 
// First label the bits of a binary-coded string B[i], where larger i's
// represent more significant bits, and similarly label the corresponding
// Gray-coded string G[i]. We convert one to the other as follows:
// 	Copy the most significant bit.
// 	Then for each smaller i do either
// 		G[i] = XOR(B[i+1], B[i])---to convert binary to Gray---
// 	     or B[i] = XOR(B[i+1], G[i])---to convert Gray to binary
// ref: The Hitch-Hiker's Guide to Evolutionary Computation
//	(FAQ for comp.ai.genetic), Issue 8.1, 29 March 2000 
//	edited by Jörg Heitkötter and David Beasley
//	Part6; Q21: What are Gray codes, and why are they used?

String grayToBinary(String graystr)
{
  if (graystr.length()==0) { return ""; }
  if (DEBUG) { System.out.print("decoding gray code: "+graystr+" to binary.. "); }
  StringBuffer bin=new StringBuffer(graystr);
  boolean gi,bi_1,bi;
  for (int i=1;i<graystr.length();i++) {
    gi=(graystr.charAt(i)=='1');
    bi_1=(bin.charAt(i-1)=='1'); // note index order is reversed to alg outline
    bi=gi ^ bi_1;		 // XOR
    bin.setCharAt(i,(bi==false)?'0':'1');
  }

  if (DEBUG) { System.out.println(bin); }
  return bin.toString();
}


// returns a decimal number from the binary parameter
int binaryToDecimal(String binstr)
{
  int dec=0;
  int b=1;

  for (int i=0; i<binstr.length(); i++) {
    if (binstr.charAt(binstr.length()-i-1)=='1') {
      dec=dec+b;
    }
    b=b*2;
  }

  return dec;
}


// generate a random binary number, distfromsrc, of the length indicated
// by the manhattan distance from the source
String genDistFromSrc(int srcrow, int srccol, int destrow, int destcol)
{
  if (DEBUG) { System.out.print("generating morphogen distfromsrc: src="+srcrow+","+srccol+" dest="+destrow+","+destcol+".. "); }
  int dist=calcManhattanDist(srcrow,srccol,destrow,destcol);
  StringBuffer binstr=new StringBuffer(dist);
  for (int i=0; i<dist; i++) {
    binstr.append(rand.nextBoolean() ? '1' : '0');
  }
  if (DEBUG) { System.out.println("'"+binstr+"'"); }

  return binstr.toString();
}


// calculate time to release (release delay) based on the manhattan distance
// from the morphogen source
int calcReleaseDelay(int srcrow, int srccol, int destrow, int destcol)
{
  int dist=calcManhattanDist(srcrow,srccol,destrow,destcol);
  int ttr = dist * morphogenPropogationDelay;
  return ttr;
}


int calcManhattanDist(int srcrow, int srccol, int destrow, int destcol)
{
  return Math.abs(srcrow-destrow)+Math.abs(srccol-destcol);
}


int min(int a, int b)
{
  return (a<b) ? a : b;
}

// return ANY matching free TF as String of dist+seq,locus, or NULL if none
// we don't care which TF (ie ignore locus) close or far, or what its ttl
// is - as this string won't be used except to indicate such a free TF exists

public String getFreeTF(int row, int col, String distNseq, long locus)
{
  return (tfmatrix[row][col]).getFreeTF(distNseq,locus);
}

public String getFreeTF(int row, int col, String dist, String seq, long locus)
{
  return (tfmatrix[row][col]).getFreeTF(dist,seq,locus);
}

public String getFreeTF(int row, int col, String distNseq)
{
  return getFreeTF(row,col,distNseq,locusIgnored);
}

public String getFreeTF(int row, int col, String dist, String seq)
{
  return getFreeTF(row,col,dist,seq,locusIgnored);
}


// return matching TF in format dist+seq,locus,ttl
// or null if not bound here

public String getBoundTF(int row,int col,String dist,String seq,long bindloc)
{
  return (tfmatrix[row][col]).getBoundTF(dist,seq,bindloc);
}

public String getBoundTF(int row, int col, String distNseq, long bindloc)
{
  return (tfmatrix[row][col]).getBoundTF(distNseq,bindloc);
}


// remove TF with matching distance & sequence from freetfs & add to boundtfs
// and return bound TF (if any) in format: dist+seq,locus,ttl
// note:
// - it just grabs the first matching TF, ignoring distance from bind locus,
//   and ttl - the first TF will probably be the oldest in list however
//   ie its ttl will be the least

public String bindTF(int row, int col, String dist, String seq, long bindloc)
{
  return (tfmatrix[row][col]).bindTF(dist,seq,bindloc);
}

public String bindTF(int row, int col, String distNseq, long bindloc)
{
  return (tfmatrix[row][col]).bindTF(distNseq,bindloc);
}



// age TFs, and remove dead TFs (those with ttl<=0) from population
public void updateTFs()
{
  for (int i=0; i<numRows; i++) {
    for (int j=0; j<numCols; j++) {
      updateTFs(i,j);
    }
  }
}


public void updateTFs(int row, int col)
{
  (tfmatrix[row][col]).updateTFs();
}



// print the state of the TF collections for debugging purposes

public void printTFs(int row, int col)
{
  (tfmatrix[row][col]).printTFs();
}


public void printTFs()
{
  for (int i=0; i<numRows; i++) {
    for (int j=0; j<numCols; j++) {
      System.out.println("TFmatrix["+i+"]["+j+"] :");
      printTFs(i,j);
    }
  }
}



// for clearing the state of all free & bound TFs in a cell
public void clear(int row, int col)
{
  tfmatrix[row][col].clear(); 
}

// for clearing the state of all free & bound TFs in all cells
public void clear()
{
  for (int i=0; i<numRows; i++) {
    for (int j=0; j<numCols; j++) {
      clear(i,j);
    }
  }
}


// for getting the state of all free & bound TFs in a cell
public String getState(int row, int col)
{
  return tfmatrix[row][col].getState(); 
}


// for setting the state of all free & bound TFs in a cell
public void setState(int row, int col, String statestr)
{
  tfmatrix[row][col].setState(statestr); 
}
 

} // end class


// helper classes




