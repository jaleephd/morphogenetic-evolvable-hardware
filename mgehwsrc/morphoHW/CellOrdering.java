
//			 CellOrdering.java
//
// this class implements the ordering for cell updates in a matrix of cells
// given a set of input and ouput cells
//
// 		written Jan 2004, J.A. Lee
//
// this class also requires the local classes: CellCoords, and StringSplitter


// for String's etc
import java.lang.*;
// for collection classes (eg Map) & Random class
import java.util.*;
// for IO
import java.io.*;


class CellOrdering {


public static void main(String args[])
{

  if (args.length<4) {
    System.err.println("synopsis:\nCellOrdering rows cols in0row,in0col;in1row,in1col;.. out0row,out0col;..");
    System.exit(1);
  }

  int rows=Integer.parseInt(args[0]);
  int cols=Integer.parseInt(args[1]);

  String[] fields;
  int r;
  int c;


  String[] ins=StringSplitter.split(args[2],";");
  CellCoords[] incells=new CellCoords[ins.length];
  for (int i=0; i<ins.length; i++) {
    fields=StringSplitter.split(ins[i],",");
    if (fields.length<2) {
      System.err.println("invalid input coord: must be row,col");
      System.exit(1);
    }
    r=Integer.parseInt(fields[0]);
    c=Integer.parseInt(fields[1]);
    incells[i]=new CellCoords(r,c);
  }
  

  String[] outs=StringSplitter.split(args[3],";");
  CellCoords[] outcells=new CellCoords[outs.length];
  for (int i=0; i<outs.length; i++) {
    fields=StringSplitter.split(outs[i],",");
    if (fields.length<2) {
      System.err.println("invalid output coord: must be row,col");
      System.exit(1);
    }
    r=Integer.parseInt(fields[0]);
    c=Integer.parseInt(fields[1]);
    outcells[i]=new CellCoords(r,c);
  }

  CellOrdering ordering=new CellOrdering(rows,cols,incells,outcells);

  ordering.printMatrix();
  System.out.println("\nupdate order is: "+ordering.order);
}




  public List order;	// this will contain the order of cell updates
  int[][] coordinates;	// indicates the (un)assignment of cells
  int numRows;
  int numCols;
  public boolean DEBUG;



CellOrdering(int nrows, int ncols, CellCoords[] in, CellCoords[] out)
{
  this(nrows,ncols,in,out,false);
}

// create the ordering of cell updates, with updates spreading
// outwards from input/output cells
CellOrdering(int nrows, int ncols, CellCoords[] in, CellCoords[] out, boolean dbg)
{
  DEBUG=dbg;

  numRows=nrows;
  numCols=ncols;
  order=new ArrayList();
  coordinates=new int[numRows][numCols];

  for (int i=0; i<numRows; i++) {
    for (int j=0; j<numCols; j++) {
      coordinates[i][j]=0;			// indicates unassigned
    }
  }

  // add each input cell to the ordering, from which to propogate outwards
  for (int i=0; i<in.length; i++) {
    assignCoord(in[i]);
  }

  // add each input cell to the ordering, from which to propogate outwards
  for (int i=0; i<out.length; i++) {
    assignCoord(out[i]);
  }

  if (DEBUG) { System.out.println(" initialised coordinates to..."); }
  if (DEBUG) { printint2D(coordinates); }

  orderUpdates();

  if (DEBUG) { System.out.println("\n final coordinates are..."); }
  if (DEBUG) { printint2D(coordinates); }

  if (DEBUG) { System.out.println("update order is: "+order); }
}


public void printMatrix() { printint2D(coordinates); }


// create cell update ordering
void orderUpdates()
{
  while (order.size()<numRows*numCols) {
    addUpdates();
  }
}


void addUpdates()
{
  List nlist;

  // note use of empty array param to the ArrayList.toArray() method
  // this ensures that (from API) "a new array is allocated with the
  // runtime type of the specified array and the size of this list."
  // otherwise a java.lang.ClassCastException is thrown
  CellCoords[] assigned=(CellCoords[])order.toArray(new CellCoords[0]);
  for (int i=0; i<assigned.length; i++) {
    // get this cell's neighbours
    nlist=getNeighbours(assigned[i]);
    for (int j=0; j<nlist.size(); j++) {
      // add each unassigned cell to the update order
      assignCoord((CellCoords)nlist.get(j));
    }
if (DEBUG) { System.out.println("assigned..."); }
if (DEBUG) { printint2D(coordinates); }
  }


}



// create a list of neighbours for cell spreading out in pattern:
//			
//			6 7 8
//			5 C 1
//			4 3 2

List getNeighbours(CellCoords cell)
{
  int row=cell.row;
  int col=cell.col;

  CellCoords[] nb=new CellCoords[8];
  if (col+1<numCols) {
    nb[0]=new CellCoords(row,col+1);
    if (row-1>-1) { nb[1]=new CellCoords(row-1,col+1); }
    if (row+1<numRows) { nb[7]=new CellCoords(row+1,col+1); }
  }
  if (row-1>-1) {
    nb[2]=new CellCoords(row-1,col);
    if (col-1>-1) { nb[3]=new CellCoords(row-1,col-1); }
  }
  if (col-1>-1) {
    nb[4]=new CellCoords(row,col-1);
    if (row+1<numRows) { nb[5]=new CellCoords(row+1,col-1); }
  }
  if (row+1<numRows) {
    nb[6]=new CellCoords(row+1,col);
  }

  // return all the valid (in range) neighbours
  List nbours=new ArrayList();
  for (int i=0; i<8; i++) { if (nb[i]!=null) { nbours.add(nb[i]); } }

  return nbours;
}


// add this cell to the end of the ordering list if it isn't already assigned
boolean assignCoord(CellCoords cell)
{
  if (coordinates[cell.row][cell.col]==0) { // if unassigned
    order.add(cell);
    coordinates[cell.row][cell.col]=order.size();
    return true;
  } else {
    return false;
  }
}


// this is for debugging / viewing the update order
void printint2D(int [][] m)
{
  if (m.length>0) {
    System.out.print("  ");
    for (int j=0; j<m[0].length; j++) { System.out.print(" "+j+" "); }
    System.out.println();
  }

  for (int i=0; i<m.length; i++) {
    System.out.print(i);
    for (int j=0; j<m[i].length; j++) {
      System.out.print("["+m[i][j]+"]");
    }
    System.out.println();
  }
}


} // end class





