
//			 CellCoords.java
//
// this class stores a cell's coordinates and provides methods for converting
// from CLB/slice[/LE] coordinates to cell-based (row,col) coordinates,
// as well as providing an equality test and a conversion to String method
//
// 		written Dec 2003, J.A. Lee
//


class CellCoords {


public static void main(String args[])
{
  int flags=0;
  int legran=0;

  // this is to indicate logic element granularity instead of just slice level
  if (args.length>0 && args[0].equals("-le")) { 
    flags++;
    legran=1;
  }

  if (args.length+flags<2) {
    System.err.println("CellCoords converts from CLB-slice based coordinates to cell based.\nSynopsis:\n\tCellCoords [-le] minCLBcoord maxCLBcoord [ CLBcoord CLBcoord .. ]\nmin/max CLB coords are in form: CLBrow,CLBcol\nCLB coords to convert to cell-based are in form: CLBrow,CLBcol,slice# or\nCLBrow,CLBcol,slice#,logic-element#");
    System.exit(1);
  }

  String[] min=StringSplitter.split(args[0+flags],",");
  String[] max=StringSplitter.split(args[1+flags],",");

  if (min.length!=2 || max.length!=2) {
    System.err.println("invalid min/max coord: must be row,col");
    System.exit(1);
  }

  int minCLBrow=Integer.parseInt(min[0]);
  int minCLBcol=Integer.parseInt(min[1]);
  int maxCLBrow=Integer.parseInt(max[0]);
  int maxCLBcol=Integer.parseInt(max[1]);

  CellCoords cell;
  String[] fields;
  int r;
  int c;
  int s;
  int l;

  // get the cell matrix boundaries
  CellCoords mincell;
  CellCoords maxcell;
  if (legran>0) {
    mincell=CellCoords.CLBtoCellCoords(minCLBrow,minCLBcol,0,0,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
    maxcell=CellCoords.CLBtoCellCoords(maxCLBrow,maxCLBcol,1,1,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
  } else {
    mincell=CellCoords.CLBtoCellCoords(minCLBrow,minCLBcol,0,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
    maxcell=CellCoords.CLBtoCellCoords(maxCLBrow,maxCLBcol,1,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
  }

  System.out.println("cell matrix boundaries: "+mincell+" to "+maxcell);

  // convert all the given CLB-slice coords to cell-based
  for (int i=2+flags; i<args.length; i++) {
    fields=StringSplitter.split(args[i],",");
    if (fields.length<2 || fields.length>3+legran) {
      if (legran==1) { 
        System.err.println("invalid CLB-slice input coord: must be row,col [,slice#[,le#]]");
      } else {
        System.err.println("invalid CLB-slice input coord: must be row,col [,slice#]");
      }
      System.exit(1);
    }
    r=Integer.parseInt(fields[0]);
    c=Integer.parseInt(fields[1]);
    s=0;
    l=0;
    if (fields.length>2) {
      s=Integer.parseInt(fields[2]);
      if (fields.length>3) {
        l=Integer.parseInt(fields[3]);
      }
    }
    if (legran==0) {
      cell=CellCoords.CLBtoCellCoords(r,c,s,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
      System.out.println("CLB-Slice ["+r+","+c+","+s+"] = cell "+cell);
    } else {
      cell=CellCoords.CLBtoCellCoords(r,c,s,l,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
      System.out.println("CLB-Slice ["+r+","+c+","+s+","+l+"] = cell "+cell);
    }
  }

}




	public int row;
	public int col;
 
public CellCoords(int r, int c) { row=r; col=c; }
public boolean equals(CellCoords c) { return (c.row==row && c.col==col); }
public String toString() { return new String("("+row+","+col+")"); }

// cell coords run from 0,0 at min CLB coords (slice 0)
// to max row, max col at max CLB coords (slice 1)
public static CellCoords CLBtoCellCoords(int[] coords,
    int minCLBrow, int minCLBcol, int maxCLBrow, int maxCLBcol)
{
  CellCoords cc=null;
  try {
    if (coords.length==2) {
      cc=CLBtoCellCoords(coords[0],coords[1],minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
    } else if (coords.length==3) {
      cc=CLBtoCellCoords(coords[0],coords[1],coords[2],minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
    } else if (coords.length==4) {
      cc=CLBtoCellCoords(coords[0],coords[1],coords[2],coords[3],minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
    } else {
      throw new Exception("Invalid number of fields ("+coords.length+") supplied. must be 2, 3 or 4.");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  return cc;
}

// this assumes CLB granularity
public static CellCoords CLBtoCellCoords(int r, int c, 
    int minCLBrow, int minCLBcol, int maxCLBrow, int maxCLBcol)
{
  try { 
    if (r<minCLBrow || r>maxCLBrow) {
      throw new Exception("CLB row ("+r+") is out of cell matrix CLB row bounds: "+minCLBrow+"-"+maxCLBrow);
    } else if (c<minCLBcol || c>maxCLBcol) {
      throw new Exception("CLB col ("+c+") is out of cell matrix CLB col bounds: "+minCLBcol+"-"+maxCLBcol);
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // note each cell maps to a CLB
  return new CellCoords(r-minCLBrow,c-minCLBcol); 
}


// this assumes slice granularity hirizontally, and CLB granularity vertically
public static CellCoords CLBtoCellCoords(int r, int c, int s,
    int minCLBrow, int minCLBcol, int maxCLBrow, int maxCLBcol)
{
  try { 
    if (r<minCLBrow || r>maxCLBrow) {
      throw new Exception("CLB row ("+r+") is out of cell matrix CLB row bounds: "+minCLBrow+"-"+maxCLBrow);
    } else if (c<minCLBcol || c>maxCLBcol) {
      throw new Exception("CLB col ("+c+") is out of cell matrix CLB col bounds: "+minCLBcol+"-"+maxCLBcol);
    } else if (s<0 || s>1) {
      throw new Exception("CLB slice ("+s+") is invalid, must be 0 or 1");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // note each CLB is 2 slices wide - each cell is a slice
  return new CellCoords(r-minCLBrow,((c-minCLBcol)*2)+s); 
}


// this assumes slice granularity hirizontally, and LE granularity vertically
public static CellCoords CLBtoCellCoords(int r, int c, int s, int le,
    int minCLBrow, int minCLBcol, int maxCLBrow, int maxCLBcol)
{
  try { 
    if (r<minCLBrow || r>maxCLBrow) {
      throw new Exception("CLB row ("+r+") is out of cell matrix CLB row bounds: "+minCLBrow+"-"+maxCLBrow);
    } else if (c<minCLBcol || c>maxCLBcol) {
      throw new Exception("CLB col ("+c+") is out of cell matrix CLB col bounds: "+minCLBcol+"-"+maxCLBcol);
    } else if (s<0 || s>1) {
      throw new Exception("CLB slice ("+s+") is invalid, must be 0 or 1");
    } else if (le<0 || le>1) {
      throw new Exception("CLB logic element ("+le+") is invalid, must be 0 or 1");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  // note each CLB is 2 slices wide and 2 logic elements high
  return new CellCoords(((r-minCLBrow)*2)+le,((c-minCLBcol)*2)+s); 
}


}

