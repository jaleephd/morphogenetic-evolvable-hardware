//			 BuildEhw.java
//
// this class takes a decoded chromosome (the output of ndecode.pl post-
// processed by resattrset2jbits.pl) that being a list of slice/CLB move
// commands, each being followed by a set of Virtex resource settings.
// These are then used to move around within the evolvable region of the FPGA,
// and to configure the resources according to the settings in the decoded
// chromosome. ie it builds a cct based on a sequence of move,
// set-resources instructions.
//
// this class also requires the local classes:
// 				JBitsResources, StringSplitter
//
// 		written Jan 2004, J.A. Lee



// for IO
import java.io.*;

// for JBits class
import  com.xilinx.JBits.Virtex.JBits;


class BuildEhw {

public static void main(String args[])
{

  int verbose=0;
  int flags=0;
  int vgran=2;		// slice granularity

  if (args.length>0 && args[0].equals("-v")) { flags=verbose=1; }

  if (args.length-flags>0 && args[0+flags].equals("-le")) {
    vgran=1;		// logic element granularity
    flags++;
  }

  if (args.length-flags<7 || args.length-flags>8) {
    System.err.println("Synopsis: BuildEhw [-v] [-le] devname bitfile contentionfile decodedchromfile minCLB maxCLB startCLBslice[LE] [outfile]");
    System.exit(1);
  }


  String devname=args[0+flags];
  String bitfile=args[1+flags];
  String contentionFile=args[2+flags];
  String dchromfile=args[3+flags];
  int[] mins=StringSplitter.str2ints(args[4+flags],",");
  int[] maxs=StringSplitter.str2ints(args[5+flags],",");
  int[] start=StringSplitter.str2ints(args[6+flags],",");

  if (mins.length!=2 || maxs.length!=2) {
    System.err.println("Illegal min/max CLB coordinate");
    System.exit(1);
  }

  if (vgran==2 && start.length!=3) {
    System.err.println("Illegal start CLB/slice coordinate");
    System.exit(1);
  } else if (vgran==1 && start.length!=4) {
    System.err.println("Illegal start CLB/slice/LE coordinate");
    System.exit(1);
  }

  String outfile=null;
  String recordfile=null;
  if (args.length-flags==8) {
    outfile=args[7+flags];
    recordfile=outfile+".record.txt";
  }

  // this will read in the chromosome and execute the build instructions
  BuildEhw ehw;
  if (vgran==2) {
    ehw=new BuildEhw(devname,bitfile,contentionFile,dchromfile,mins[0],mins[1],maxs[0],maxs[1],start[0],start[1],start[2],recordfile,(verbose==1));
  } else {
    ehw=new BuildEhw(devname,bitfile,contentionFile,dchromfile,mins[0],mins[1],maxs[0],maxs[1],start[0],start[1],start[2],start[3],1,recordfile,(verbose==1));
  }

  // save resulting configuration
  if (outfile!=null) {
    ehw.writeBitstream(outfile);
    ehw.closeRecordFile();
  }

} // end main



		// the JBits resources to be set by the build instructions
		JBitsResources 		jbits;

		// current clb row, col, and slice, and optionally logic elem
		int 			row;
		int 			col;
		int			slice;
		int			le;

		int			vertgran; // 2 for slice, 1 for LE-gran

		// boundaries of evolvable region of FPGA
		int 			minRow;
		int 			maxRow;
		int 			minCol;
		int 			maxCol;

		// for logging jbits set() instructions to file
		String			recordfile;

		boolean			VERBOSE;



BuildEhw(String deviceName, String bitfile, String contentionFile,
       String dchromfile, int clbrowmin, int clbcolmin, int clbrowmax,
      int clbcolmax, int startrow, int startcol, int startslice,
      String rcrdfile, boolean dbg)
{
  this(deviceName,bitfile,contentionFile,dchromfile,clbrowmin,clbcolmin,
       clbrowmax,clbcolmax,startrow,startcol,startslice,0,2,rcrdfile,false);
}

BuildEhw(String deviceName, String bitfile, String contentionFile,
       String dchromfile, int clbrowmin, int clbcolmin, int clbrowmax,
      int clbcolmax, int startrow, int startcol, int startslice, int startLE,
      String rcrdfile, boolean dbg)
{
  this(deviceName,bitfile,contentionFile,dchromfile,clbrowmin,clbcolmin,
       clbrowmax,clbcolmax,startrow,startcol,startslice,startLE,1,rcrdfile,false);
}

BuildEhw(String deviceName, String bitfile, String contentionFile,
       String dchromfile, int clbrowmin, int clbcolmin, int clbrowmax,
      int clbcolmax, int startrow, int startcol, int startslice, int startLE,
      int vgran, String rcrdfile, boolean dbg)
{
  VERBOSE=dbg;

  jbits=new JBitsResources(deviceName,contentionFile,bitfile);

  try {
    if (clbrowmin<0 || clbrowmax>jbits.getRows() || clbcolmin<0 || clbcolmax>jbits.getCols()) {
      throw new Exception("Invalid min/max CLB row/col specification for this device! must be from 0,0 to "+jbits.getRows()+","+jbits.getCols());
    }

    minRow=clbrowmin;
    minCol=clbcolmin;
    maxRow=clbrowmax;
    maxCol=clbcolmax;

    row=startrow;
    col=startcol;
    slice=startslice;
    le=startLE;

    vertgran=vgran;

    if (row<minRow || row>maxRow || col<minCol || col>maxCol) {
      throw new Exception("start coords lie outside of supplied min/max bounds in BuildEhw constructor!");
    }

    if (slice<0 || slice>1) {
      throw new Exception("invalid slice coords in BuildEhw constructor; must be 0 or 1!");
    }

    if (le<0 || le>1) {
      throw new Exception("invalid logic element coords in BuildEhw constructor; must be 0 or 1!");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }


  recordfile=rcrdfile;
  if (recordfile!=null) {
    recordToFile(recordfile);
  }

  if (dchromfile!=null) {
    build(dchromfile);
  }
}

public void build(String dchromfile, int startrow, int startcol, int startslice)
{
  build(dchromfile,startrow,startcol,startslice,0);
}

public void build(String dchromfile, int startrow, int startcol,
                  int startslice, int startLE)
{
  try {

    if (startrow<minRow || startrow>maxRow || startcol<minCol || startcol>maxCol) {
      throw new Exception("start coords lie outside of BuildEhw's defined min/max boundaries!");
    }

    if (startslice<0 || startslice>1) {
      throw new Exception("invalid slice coords in BuildEhw constructor; must be 0 or 1!");
    }

    if (startLE<0 || startLE>1) {
      throw new Exception("invalid logic element coords in BuildEhw constructor; must be 0 or 1!");
    }
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  row=startrow;
  col=startcol;
  slice=startslice;
  le=startLE;
  build(dchromfile);
}


public void build(String dchromfile)
{
  loadInstructions(dchromfile);
}



public void recordToFile(String rcrdfile)
{
  recordfile=rcrdfile;
if (VERBOSE) { System.out.println("logging jbits set() instructions to file "+recordfile+" .. "); }
  jbits.recordToFile(recordfile);
}

// close the set() transcript file
public void closeRecordFile()
{
  jbits.closeRecordFile();
if (VERBOSE) { System.err.println("closed jbits set() transcript file: "+recordfile+"."); }
  recordfile=null;
}


public JBitsResources getJBitsResources() { return jbits; }
public JBits getJBits() { return jbits.getJBits(); }


public void writeBitstream(String outfile)
{
if (VERBOSE) { System.out.print("writing bitstream to file "+outfile+" .. "); }
  jbits.writeBitstream(outfile); 
if (VERBOSE) { System.out.println("done!"); }
}


public boolean setResources(String[] list)
{
  for (int i=0; i<list.length; i++) {
    if (!setResource(list[i])) { return false; }
  }

  return true;
}


// resource-type "=" resource-details
// details may be "/" delimited for slice/LE-specific resources
// and some of these may be empty, if resource not available for that slice/LE
public boolean setResource(String typeNdetails)
{
  // get type and details fields
  String[] fields=StringSplitter.trimsplit(typeNdetails,"=");
  if (fields.length!=2) { return false; }
  String bitsval=null;

  String[] dets=StringSplitter.split(fields[1],"/");
  if (dets.length==4) { // logic element-specific
    bitsval=dets[(slice*2)+le];
  } else if (dets.length==2) { // slice-specific
    bitsval=dets[slice];
  } else if (dets.length==1) { // CLB resource
    bitsval=fields[1];
  } else {
    return false;
  }

  // slice/LE specific resource may not be avail, which should be indicated by
  // an empty field, or a '-'. the latter is better, especially if the last
  // field is empty it will result in a missing field when the split is done
  // which will be detected as an error above
  if (!(bitsval.equals("") | bitsval.equals("-"))) {
if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", "+bitsval+")"); }
    jbits.set(row,col,fields[0],bitsval);
if (VERBOSE) { System.out.println("... OK"); }
  }

  return true;
}



public boolean move(String rowmove, String colmove, String slicemove)
{
  if (rowmove.equals("+") && colmove.equals("+") && slicemove.equals("+")) {
    moveToNext();
    return true;
  }

  if (rowmove.equals("@") && colmove.equals("@") && slicemove.equals("@")) {
    moveToStartCorner();
    return true;
  }

  // note the leading sign extension that Integer.parseInt won't handle
  int rm=StringSplitter.parseint(rowmove);
  int cm=StringSplitter.parseint(colmove);
  return move(rm,cm,slicemove);
}


// this is used in conjunction with moveToNext()
// it just ensures that we start at the appropriate corner
public void moveToStartCorner()
{
if (VERBOSE) {
  if (vertgran==2) { System.out.print("moving from ("+row+","+col+","+slice+").. "); }
  else { System.out.print("moving from ("+row+","+col+","+slice+","+le+").. "); }
}
  le=slice=0;
  col=minCol;
  row=minRow;

if (VERBOSE) {
  if (vertgran==2) { System.out.println("to ("+row+","+col+","+slice+").."); }
  else { System.out.println("to ("+row+","+col+","+slice+","+le+").."); }
}

}



// move to the next CLB/slice/LE in the evolvable region
// for now we just use the ordering of JBits, ie start at CLB 0,0
// and traverse each row from col 0..N-1, then each row to row N-1
// within each CLB traverse from slice 0 to slice 1, and if LE-granularity
// then do slice 0 - LE 0, LE 1, then slice 1 - LE 0, LE 1
// if reach the end of the evolvable area, then wrap around back to the start

public void moveToNext()
{
if (VERBOSE) {
  if (vertgran==2) { System.out.print("moving from ("+row+","+col+","+slice+").. "); }
  else { System.out.print("moving from ("+row+","+col+","+slice+","+le+").. "); }
}
  if (vertgran==1) {		// LE granularity
    if (le==0) {
      le=1;			// move to next LE
    } else {
      le=0;			// move to next LE
      slice++;			// in next slice
    }
  } else {			// slice granularity
    slice++;			// move to next slice, which may be in next CLB
  }

  if (slice>1) {		// move to first slice in next CLB
    slice=0;
    col++;
  }

  if (col>maxCol) {		// move to first column in next CLB row
    col=minCol;
    row++;
  }
 
  if (row>maxRow) {		// move back to first CLB row
    row=minRow;
  }

if (VERBOSE) {
  if (vertgran==2) { System.out.println("to ("+row+","+col+","+slice+").."); }
  else { System.out.println("to ("+row+","+col+","+slice+","+le+").."); }
}

}



public boolean move(int rowmove, int colmove, String slicemove)
{
if (VERBOSE) {
  if (vertgran==2) { System.out.print("moving from ("+row+","+col+","+slice+").. "); }
  else { System.out.print("moving from ("+row+","+col+","+slice+","+le+").. "); }
}
  row=row+rowmove;
  col=col+colmove;

  if (row>maxRow) { row=minRow+(row-(maxRow+1)); }
  else if (row<minRow) { row=maxRow-(minRow-(row+1)); }

  if (col>maxCol) { col=minCol+(col-(maxCol+1)); }
  else if (col<minCol) { col=maxCol-(minCol-(col+1)); }

  if (slicemove.equals("0G")) { slice=le=0; }
  else if (slicemove.equals("0F")) { slice=0; le=1; }
  else if (slicemove.equals("1G")) { slice=1; le=0; }
  else if (slicemove.equals("1F")) { slice=1; le=1; }
  else if (slicemove.equals("0")) { slice=0; }
  else if (slicemove.equals("1")) { slice=1; }
  else if (slicemove.equals("G")) { le=0; }
  else if (slicemove.equals("F")) { le=1; }
  else if (slicemove.equals("-")) { slice=(slice==1) ? 0 : 1; }
  else if (slicemove.equals("%")) { le=(le==1) ? 0 : 1; }
  else if (slicemove.equals(".")) { // do nothing
  } else { // invalid
if (VERBOSE) { System.out.println(); }
    return false;
  }

if (VERBOSE) {
  if (vertgran==2) { System.out.println("to ("+row+","+col+","+slice+").."); }
  else { System.out.println("to ("+row+","+col+","+slice+","+le+").."); }
}
  return true;
}


// read the decoded chromosome file, containing instructions in format:
//   horizmove,vertmove,slicemove: details
// - horizmove and vertmove are one of -N,0,+N (N is an integer)
//   indicating respectively no movement, move +N CLBs, move -N CLBs
// - slicemove is "0", "1", "G", "F", "0G", "0F", "1G", "1F", "-", "%", or "."
//   indicating respectively slice 0, 1, logic element G, F, slice-and-LE
//   S0-GY, S0-FX, S1-GY, S1-FX, swap slice, swap LE, and lastly, no move
// alternatively, "+,+,+" may be used to indicate, move to next cell
// (slice or LE), according to some ordering (eg W to E, S to N), and
// "@,@,@" is used to indicate move to start corner (according to the same
// ordering used above)
//
// details is a semi-colon delimited list of elements, each in format:
//   		resource-type "=" resource-details
// where:
// 	resource-type is one of (we don't have TFs here):
// 		jbits, LUT, LUTIncrFN, SliceRAM
//	resource-details for SliceRAM is in the form:
//		slice0class,bitsconst1,bitsconst2,../
//		slice1class,bitsconst1,bitsconst2,..
//	resource-details for jbits, LUT, LUTIncrFN is in the form of:
//		bits "," val [ "/" bits "," val ]

void loadInstructions(String file)
{
  InputStream inputstream=null;
  InputStreamReader inputstreamreader=null;
  BufferedReader bufferedreader=null;

  int linenum=0;
  String line=null;
  String[] fields;
  String moves, details;

  try {
    // open the file and create a buffered input stream reader
    inputstream = new FileInputStream(file);
    inputstreamreader=new InputStreamReader(inputstream);
    bufferedreader = new BufferedReader(inputstreamreader);
  } catch (Exception e) {
    System.err.println("Error opening " + file + "\n" + e);
    e.printStackTrace();
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
        // if not settings with this move, then will get an empty details field
	// which will give a fields.length of 1 - so need to allow either
	if (fields.length<1 || fields.length>2) {
          throw new Exception("Invalid format on line "+linenum+" of "+file+"! ..\nShould be horizmove,vertmove,slice/LEmove : [details]");
	}
	moves=fields[0];
	if (fields.length==2) {
	  details=fields[1];
	} else {
	  details=null;
	}

	// get move instruction
        fields=StringSplitter.trimsplit(moves,",");
	if (fields.length!=3) {
          throw new Exception("Invalid move instruction format on line "+linenum+" of "+file+"! ..\nShould be horizmove,vertmove,slice/LEmove");
	}

	// now perform the move instruction
if (VERBOSE) { System.out.println("line: "+linenum+". performing move instruction..."); }
	if (!move(fields[0],fields[1],fields[2])) {
          throw new Exception("Invalid move instruction on line "+linenum+" of "+file+"! ..\n");
	}

	if (details!=null) {
	  // get all the resource settings, and set them
          fields=StringSplitter.trimsplit(details,";");
if (VERBOSE) { System.out.println("line: "+linenum+". performing set resource instructions..."); }
	  if (!setResources(fields)) {
            throw new Exception("Invalid type and details format on line "+linenum+" of "+file+"! ..\nShould be type=details");
	  }
        }
      }
    }
  } catch (Exception e) {
    System.err.println("Error in build instructions file "+file+"!");
    System.err.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }


  try { 
    // finished with file, so close it
    inputstream.close();
  } catch (Exception e) {
    System.err.println("Error closing file "+file+"! exiting...");
    System.exit(1);
  }
 
}


} // end class


