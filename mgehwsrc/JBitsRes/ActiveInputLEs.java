
//			 ActiveInputLEs.java
//
// this class is used to determine which LEs within the evolvable region of
// a FPGA's CLB matrix are active, and which inputs to their LUTs are active
// this is so that inactive lines can be removed from LUT functions
// (by LUTActiveFNs)
//
// 		written Aug 2004, J.A. Lee
//
// this class also requires the local classes:
// 			LEcoord, StringSplitter, JBitsResources 


// for String's etc
import java.lang.*;
// for collection classes (eg List)
import java.util.*;
// for IO
import java.io.*;
// for formatting decimal numbers for output (like printf)
import java.text.DecimalFormat;
// for JBits object
import com.xilinx.JBits.Virtex.JBits;






public class ActiveInputLEs {

  	boolean VERBOSE;

	// constants defining the 4 possible states
	static public final int OUTOFRANGE=-1;
	static public final int UNKNOWN=0;
	static public final int UNDEREVAL=1;
	static public final int INACTIVE=4;
	static public final int ACTIVE=0xF;

	JBits jbits;
	JBitsResources jbitsres;

	public int minrow;		// bounds of evolvable region
	public int mincol;
	public int maxrow;
	public int maxcol;

	int numrows;
	int numcols;

	// note matrix is rows*2 x cols*2 from minrow,mincol-maxrow,maxcol:
        // we assign slice granularity to cols, and LE granularity to rows

	public int[][]   stateMatrix;		// stores LE (active) state
	public int[][][] lutinStateMatrix;	// state of each LE's inputs

	// a default list of active input lines at given locations
	LEcoord[] activeInStartLocs=null;

        // a list of locations that are automatically set to inactive
        // because the start locs are typically outside of the evolvable
        // region, every other CLB in that CLB row/col shouldn't be used
	LEcoord[] inactiveLocs=null;





ActiveInputLEs(JBitsResources jbr, LEcoord mincoord, LEcoord maxcoord)
{
  this(jbr,mincoord,maxcoord,false);
}

ActiveInputLEs(JBitsResources jbr, LEcoord mincoord, LEcoord maxcoord, boolean vb)
{
  this(jbr,mincoord,maxcoord,null,null,vb);
}


ActiveInputLEs(JBitsResources jbr, LEcoord mincoord, LEcoord maxcoord, LEcoord[] activeinlocs, LEcoord[] inactivelist, boolean vb)
{
  VERBOSE=vb;
 
  jbitsres=jbr;
  jbits=jbr.getJBits();

  activeInStartLocs=activeinlocs;
  inactiveLocs=inactivelist;

//System.err.print("*** DEBUG *** ActiveInStartlocs are: ");
//for (int i=0; i<activeinlocs.length; i++) {
//System.err.print(activeinlocs[i]+" ");
//}
//System.err.println();

//System.err.print("*** DEBUG *** inactiveLocs are: ");
//for (int i=0; i<inactiveLocs.length; i++) {
//System.err.print(inactiveLocs[i]+" ");
//}
//System.err.println();



  minrow=mincoord.row;
  mincol=mincoord.col;

  maxrow=maxcoord.row;
  maxcol=maxcoord.col;

  // each LE in evolvable region is given an (active) state
  // here we assign 2 slices to each col, and 2 LEs to each row
  numrows=maxrow-minrow+1;
  numcols=maxcol-mincol+1;
  stateMatrix=new int[numrows*2][numcols*2];
  lutinStateMatrix=new int[numrows*2][numcols*2][4]; // 4 inputs per LUT

//System.err.println("rows="+minrow+"-"+maxrow+", cols="+mincol+"-"+maxcol);

  // initialise the state of the LEs
  update();

  // now provide the LUT input line status matrix to the JBitsResource
  // class, so that it can use it with LUTActiveFNs
  jbitsres.addActiveLineMatrix(lutinStateMatrix,minrow,mincol);
  jbitsres.setActiveCode(ActiveInputLEs.ACTIVE);
}



// note that there are 2 slices to each col, and 2 LEs to each row
public void resetState()
{
  for (int i=0; i<numrows*2; i++) {
    for (int j=0; j<numcols*2; j++) {
      stateMatrix[i][j]=ActiveInputLEs.UNKNOWN;
      for (int k=0; k<4; k++) {
	lutinStateMatrix[i][j][k]=ActiveInputLEs.UNKNOWN;
      }
    }
  }
}



// because these are publically accessible,
// if LE is outside evaluated region return OUTOFRANGE
public int getState(LEcoord loc)
{
  if (!inRange(loc)) { return ActiveInputLEs.OUTOFRANGE; }
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  return stateMatrix[(r*2)+loc.LE][(c*2)+loc.slice];
}

// linenum is 1-4
public int getLUTinState(LEcoord loc, int linenum)
{
  if (!inRange(loc)) { return ActiveInputLEs.OUTOFRANGE; }
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  return lutinStateMatrix[(r*2)+loc.LE][(c*2)+loc.slice][linenum-1];
}

// return a matrix with the number of active LUT inputs in each LE
public int[][] getActiveCntMatrix()
{
  return getActiveCntMatrix(0,0);
}

// this will return a subset of the same matrix, if the offset is +ve
// starting at the given CLB row/col, or if negative then stopping at the
// give offset prior to the end (ie -1, would stop 1 CLB short of the end)
public int[][] getActiveCntMatrix(int rowoff, int coloff)
{
  int cnt;
  int nrows=(numrows-Math.abs(rowoff))*2; // in units of LEs now
  int ncols=(numcols-Math.abs(coloff))*2;
  int startrow=(rowoff<0) ? 0 : rowoff*2;
  int startcol=(coloff<0) ? 0 : coloff*2;
  int[][] activecnt=new int[nrows][ncols];

//System.err.println("rowoff="+rowoff+", startrow="+startrow+", nrows="+nrows+", coloff="+coloff+", startcol="+startcol+", ncols="+ncols);

  for (int i=startrow; i<startrow+nrows; i++) {
    for (int j=startcol; j<startcol+ncols; j++) {
      cnt=0;
      for (int k=0; k<4; k++) {
	if (lutinStateMatrix[i][j][k]==ActiveInputLEs.ACTIVE) { cnt++; }
      }
      activecnt[i-startrow][j-startcol]=cnt;
    }
  }
  return activecnt;
}


// starting with a list of active input lines for given locations
// determine which LEs are active, and which of their inputs are active
// then update the functions of the active LUTs so that they only use
// active inputs - ie prune inactive inputs from the LUT's boolean function
// Note that the active input LEs will also have their non-specified inputs
// evaluated. this should generally not be a problem (eg the input routing
// CLBs, which will have the other inputs disconnected).
// 'inactiveLocs' is a list of locations that are automatically set to
// INACTIVE, typically because the start locs are outside of the evolvable
// region, and so every other CLB in that CLB row/col shouldn't be examined

public void update() { update(activeInStartLocs,inactiveLocs); }

public void update(LEcoord[] activeStartList, LEcoord[] inactiveList)
{
  // set state of all LEs and their connected lines to UNKNOWN
  // and set unconnected lines to INACTIVE
  resetState();

  // these elements will never be examined/modified, they are set to INACTIVE
  if (inactiveList!=null) {
    for (int i=0; i<inactiveList.length; i++) {
      setState(inactiveList[i],ActiveInputLEs.INACTIVE);
    }
  }

  if (activeStartList==null) { return; }

  // these elements will never be examined/modified, they are set to ACTIVE
  for (int i=0; i<activeStartList.length; i++) {
    setState(activeStartList[i],ActiveInputLEs.ACTIVE);
    for (int j=1; j<=4; j++) {
      setLUTinState(activeStartList[i],j,ActiveInputLEs.INACTIVE);
    }
    setLUTinState(activeStartList[i],activeStartList[i].line,ActiveInputLEs.ACTIVE);
  }

  if (VERBOSE) {
    System.err.print("updating LUT activations, starting at: ");
    for (int k=0; k<activeStartList.length;k++) { System.err.print(activeStartList[k]+" "); }
    System.err.println();
  }

  // start eval at input LEs
  for (int i=0; i<activeStartList.length; i++) {
    // set the active state of specific LUT lines of specific LEs to be active
    // and then trace the LE outputs to mark other LE inputs as active 
    // there may be more than 1 entry per LE, each for a different active line
    // these would typically be the entry points to the circuit, and would be
    // found using TestInToOutRecurse for example.
    if (VERBOSE) { System.err.println("tracing active lines from "+activeStartList[i]+" line "+activeStartList[i].line+"..."); }
    // set this one to unknown, just to allow tracing through it
    setState(activeStartList[i],ActiveInputLEs.UNKNOWN);
    traceForwardActivations(activeStartList[i],activeStartList[i].line);
  }

  // now update the actual LUT entries in the evolvable region, to use
  // their existing function and line inversion mask, but with these only
  // applied to active inputs, as given by the just updated lutinStateMatrix
  // that the JBitsResources object has a copy of. Note that only active LEs
  // are updated, as they are the only ones that will have any effect anyway
  if (VERBOSE) { System.err.println("updating LUT active functions..."); }
  for (int i=0; i<numrows*2; i++) {
    for (int j=0; j<numcols*2; j++) {
      if (stateMatrix[i][j]==ActiveInputLEs.ACTIVE) {
	// update LUTActiveFN with LUT's existing boolean function
	// and inversion mask, but with updated active input list
        jbitsres.updateLUTActiveFN((i/2)+minrow,(j/2)+mincol,j%2,i%2);
      }
    }
  }

  if (VERBOSE) {
    System.err.println("active LUT input counts...");
    int[][] aic=getActiveCntMatrix(0,0);
    for (int i=aic.length-1; i>=0; i--) {
      System.err.print(i+" ");
      for (int j=0; j<aic[0].length; j++) {
        System.err.print("["+aic[i][j]+"]");
      }
      System.err.println();
    }
  }

}



// these will only be called from within this class
void setState(LEcoord loc, int state)
{
  int r=loc.row-minrow;
  int c=loc.col-mincol;

//System.err.println("loc="+loc+", r="+((r*2)+loc.LE)+", c="+((c*2)+loc.slice));
  stateMatrix[(r*2)+loc.LE][(c*2)+loc.slice]=state;
}

void setLUTinState(LEcoord loc, int linenum, int state)
{
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  lutinStateMatrix[(r*2)+loc.LE][(c*2)+loc.slice][linenum-1]=state;
}

boolean inRange(LEcoord loc)
{
  int r=loc.row;
  int c=loc.col;
//System.err.println(loc+" inRange=="+(r>=minrow && r<=maxrow && c>=mincol && c<=maxcol));
  return (r>=minrow && r<=maxrow && c>=mincol && c<=maxcol);
}



// starting with an active LUT input line (1-4) at a given location,
// recurse through circuit, marking all LEs that are connected from these
// as active. On the other input lines trace backwards to determine if
// any are active (eg an oscillating signal caused by a recurrent subcircuit
// NOTE that this assumes that GLCK is connected to the slice clk!)

void traceForwardActivations(LEcoord loc, int lutline)
{
  // make sure that don't get caught in loops or go outside of evolvable region
  if (getState(loc)!=ActiveInputLEs.UNKNOWN) { return; }

  if (VERBOSE) { System.err.println("forward tracing active LE "+loc+" from input line "+lutline+"..."); }

  // LE is active and so is the traced input line
  setState(loc,ActiveInputLEs.ACTIVE);
  setLUTinState(loc,lutline,ActiveInputLEs.ACTIVE);

  LEcoord[] nbours=null;

  LEcoord nbour=null;
  int nbstate=ActiveInputLEs.UNKNOWN;

  // trace backwards on each unevaluated connected input line
  for (int i=1; i<=4; i++) {
    if (VERBOSE) { System.err.print("testing input line "+i+"... "); }
    if (getLUTinState(loc,i)==ActiveInputLEs.UNKNOWN) {
      nbour=inputDrivenByLE(loc,i);
      if (nbour!=null && inRange(nbour)) {
        if (VERBOSE) { System.err.println("tracing backwards to "+nbour+" to find status..."); }
	// nbour will be input originating location plus out bus line
        nbstate=traceBackActivations(nbour,nbour.line);
        if (VERBOSE) { System.err.println("returned from backwards trace: "+nbour+" has status "+nbstate); }
        setLUTinState(loc,i,nbstate);
      } else {
        if (VERBOSE) { System.err.println("inactive!"); }
        setLUTinState(loc,i,ActiveInputLEs.INACTIVE);
      }
    } else {
      if (VERBOSE) { System.err.println("already evaluated!"); }
    }
  }

  if (VERBOSE) {
    System.err.print(loc+" input states 1-4=");
    for (int i=1; i<=4; i++) { System.err.print(" "+getLUTinState(loc,i)); }
    System.err.println();
  }

  // trace forward from LUT registered output thru each connected OUT bus line
  if (VERBOSE) { System.err.println("tracing active LE "+loc+" outputs.."); }
  int[] outlines=connectedOUTbuses(loc);
  if (outlines==null) {
    if (VERBOSE) { System.err.println("output not connected to any out bus lines.."); }
    return;
  }

  for (int o=0; o<outlines.length; o++) {
    if (VERBOSE) { System.err.println(loc+" FF -> OUT "+outlines[o]+" .. "); }
    nbours=OUTtoSingleConnectedLE(loc,outlines[o]);
    if (nbours!=null && nbours.length>0) {
      if (VERBOSE) {
         System.err.println("single(s) connected to nbour(s).. ");
         for (int k=0; k<nbours.length;k++) { System.err.print(nbours[k]+" "); }
	 System.err.println();
      }
      // trace each connected single to its neighbour entry location
      for (int n=0; n<nbours.length; n++) {
        // don't go outside evolvable region
        if (inRange(nbours[n])) {
          if (VERBOSE) { System.err.println("connection from "+loc+" to "+nbours[n]+" .. "); }
	  traceForwardActivations(nbours[n],nbours[n].line);
	}
      }
    }
  }
}


// this LE drives an input into another LUT, but don't know if that input
// is active, so need to determine whether this LE is active or not
// if any of its LUT inputs come from an active LE, or an LE under evaluation
// (indicating a feedback loop), then it is ACTIVE
// otherwise it is INACTIVE.
// if active, trace forward on each unevaluated line an mark as active
// return's either ACTIVE or INACTIVE
// NOTE: even though we count feedback loops as active, in reality they are
// only active if there is some kind of signal inversion (creating an
// oscillator), but for now we ignore this necessity, which would require
// analysing each LUT FN to determine if this is occuring
int traceBackActivations(LEcoord loc, int outline)
{
  LEcoord[] nbours=null;
  LEcoord nbour=null;
  int state=getState(loc); // get state of LE
  if (state==ActiveInputLEs.ACTIVE || state==ActiveInputLEs.INACTIVE) {
    return state;
  } else if (state==ActiveInputLEs.UNDEREVAL) { // feedback loop, so active
    setState(loc,ActiveInputLEs.ACTIVE);
    return ActiveInputLEs.ACTIVE;
  } else if (state==ActiveInputLEs.UNKNOWN) {
    setState(loc,ActiveInputLEs.UNDEREVAL);
    state=ActiveInputLEs.UNDEREVAL;
  } else { // out of evolvable region, so shouldn't be active
    return ActiveInputLEs.INACTIVE;
  }
  int instate,nbstate;

  for (int i=1; i<=4; i++) {
    if (VERBOSE) { System.err.print("testing input line "+i+"... "); }
    // note line states = UNKNOWN, UNDEREVAL, INACTIVE, ACTIVE
    instate=getLUTinState(loc,i);
    if (instate==ActiveInputLEs.ACTIVE) {
      state=ActiveInputLEs.ACTIVE;
      if (VERBOSE) { System.err.println("active!"); }
    } else if (instate==ActiveInputLEs.UNDEREVAL) {   // this shouldn't happen
      setLUTinState(loc,i,ActiveInputLEs.ACTIVE);     // as it would imply that
      state=ActiveInputLEs.ACTIVE;                    // we are back in the
      if (VERBOSE) { System.err.println("active!"); } // originating LE!
    } else if (instate==ActiveInputLEs.UNKNOWN) {
      setLUTinState(loc,i,ActiveInputLEs.UNDEREVAL);
      // trace backwards on each unevaluated connected input line
      nbour=inputDrivenByLE(loc,i);
      if (nbour!=null && inRange(nbour)) {
	// nbour will be input originating location plus out bus line
        if (VERBOSE) { System.err.println("tracing backwards to "+nbour+" to find status..."); }
        nbstate=traceBackActivations(nbour,nbour.line);
        if (VERBOSE) { System.err.println("returned from backwards trace: "+nbour+" has status "+nbstate); }
        setLUTinState(loc,i,nbstate);
	if (nbstate==ActiveInputLEs.ACTIVE) {
          state=ActiveInputLEs.ACTIVE;
          if (VERBOSE) { System.err.println("updated LE status to active!"); }
        } else if (instate==ActiveInputLEs.UNDEREVAL) { // should this happen??
          state=ActiveInputLEs.ACTIVE;
          if (VERBOSE) { System.err.println("updated LE status to active!"); }
	}
      } else {
        setLUTinState(loc,i,ActiveInputLEs.INACTIVE);
        if (VERBOSE) { System.err.println("inactive!"); }
      }
    }
  }

  if (VERBOSE) {
    System.err.print(loc+" input states 1-4=");
    for (int i=1; i<=4; i++) { System.err.print(" "+getLUTinState(loc,i)); }
    System.err.println();
  }

  if (state==ActiveInputLEs.ACTIVE) {
    // trace forward on each unevaluated line an mark as active
    // trace forward from LUT output thru connected OUT bus lines
    // to unevaluated neighbours
    int[] outlines=connectedOUTbuses(loc);
    for (int o=0; o<outlines.length; o++) {
      if (VERBOSE) { System.err.println(loc+" -> OUT "+outlines[o]+" .. "); }
      if (outlines[o]!=outline) {
        nbours=OUTtoSingleConnectedLE(loc,outlines[o]);
        if (nbours!=null && nbours.length>0) {
          // trace each connected single to its neighbour entry location
	  // and mark it as active if its current state is unknown
          for (int n=0; n<nbours.length; n++) {
            // don't go outside evolvable region
            if (inRange(nbours[n])) {
	      traceForwardActivations(nbours[n],nbours[n].line);
	    }
	  }
        }
      } else {
        if (VERBOSE) { System.err.println(loc+" -> OUT "+outline+" .. to backtrack caller"); }
      }
    }
  } else {
    state=ActiveInputLEs.INACTIVE;
  }

  return state; // state is either ACTIVE or INACTIVE
}



// this returns the location of the source of the LUT line's input (if any)
// the source CLB row & col, slice, le, and the OUT bus line that drives the
// LUT's input (possibly via a single)
// if LUT's input is not driven, NULL is returned
// note that it is also possible that LUT's input is driven by a single that
// is driven from the output of the same CLB

LEcoord inputDrivenByLE(LEcoord loc, int lutline)
{
  int row=loc.row;
  int col=loc.col;
  int slice=loc.slice;
  int le=loc.LE;
  // class name, eg is S0F1.S0F1
  String classname=new String("S"+slice+((le==0)?"G":"F")+lutline);
  // get current val of LUT input, eg S0F1.OFF
  int[] inval=jbitsres.get(row,col,"jbits",classname+"."+classname);
  // convert from bit array to string representation (eg OFF)
  String input=JBitsLookup.getName(classname,inval);
  if (VERBOSE) { System.err.println(classname+" connected to "+input); }

  // check if connected at all
  if (input.equals("OFF")) { return null; }

  // otherwise connection be one of: SINGLE_direction#, OUT_(EAST#|WEST#)
  // NB: not checking recurrent slice outputs (eg S1_Y), Hex lines, etc
 
  String[] srcfields=StringSplitter.split(input,"_");
  char dir='?';
  int outline=-1;
  int nbslice=-1;
  int nble=-1;
  int nrow=-1,ncol=-1;
  int[] val;

  try {
    if (srcfields[0].equals("OUT")) { // direct OUT line from that dir
      dir=srcfields[1].charAt(0);
      nrow=row; // direct OUT line connections are always horizontal
      ncol=(dir=='E') ? col-1: col+1;
      // out line is a single digit
      outline=Integer.parseInt(srcfields[1].substring(srcfields[1].length()-1,srcfields[1].length()));
      if (VERBOSE) { System.err.println("direct connection from OUT line "+outline+" to "+dir); }
    } else if (srcfields[0].equals("SINGLE")) { // single from that dir
      dir=srcfields[1].charAt(0);
      if (VERBOSE) { System.err.println("single line connection from "+dir); }
    } else { // don't know what it is
      throw new Exception("Invalid or unsupported source ("+input+") at "+loc+". single and direct out lines only supported");
    }
    if (dir != 'N' && dir != 'S' && dir != 'E' && dir != 'W') {
      throw new Exception("Invalid input source direction ("+dir+") in input ("+input+") at "+loc+".");
    }
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }

  // if it's a single line, need to find which out line, if any, drives it
  // remembering that in the source, its direction will be opposite
  if (outline<0) { // can only be a single for this to be true
    String singledir=null;
    int singlenum;

    if (dir=='N') { 
      nrow=row+1; ncol=col;
      singledir=new String("SOUTH");
      singlenum=Integer.parseInt(srcfields[1].substring(5));
    } else if (dir=='S') {
      nrow=row-1; ncol=col;
      singledir=new String("NORTH");
      singlenum=Integer.parseInt(srcfields[1].substring(5));
    } else if (dir=='E') {
      nrow=row; ncol=col+1;
      singledir=new String("WEST");
      singlenum=Integer.parseInt(srcfields[1].substring(4));
    } else { // if (dir=='W')
      nrow=row; ncol=col-1;
      singledir=new String("EAST");
      singlenum=Integer.parseInt(srcfields[1].substring(4)); // WEST
    }

//if (singledir.equals("NORTH") && singlenum==16) {
//  System.err.println("*** DEBUG *** "+loc+"; lutline "+lutline);
//  System.err.println("*** DEBUG *** "+classname+" connected to "+input);
//  System.err.println("*** DEBUG *** "+srcfields[0]+"; "+srcfields[1]);
//}
    outline=getDrivingOutline(singledir,singlenum);
    val=jbitsres.getbits(nrow,ncol,"OutMuxToSingle.OUT"+outline+"_TO_SINGLE_"+singledir+singlenum);
    if (VERBOSE) { System.err.println("neighbour CLB "+nrow+","+ncol+" OutMuxToSingle.OUT"+outline+"_TO_SINGLE_"+singledir+singlenum+" is "+((val[0]==0)?"OFF":"ON")); }
    if (jbitsres.isEquivVal(nrow,ncol,val,"OutMuxToSingle.OFF")) {
      outline=-1;
      // if single isn't driven by a neighbour, it still might be driven by an
      // output from the same CLB, (for contentious singles only) so revert
      // single direction, and test local
      if (((dir=='N' || dir=='S') && 
           (singlenum==0 || singlenum==1 || singlenum==12 || singlenum==13)) ||
	  ((dir=='E' || dir=='W') &&
           (singlenum==5 || singlenum==11 || singlenum==17 || singlenum==23))){
        nrow=row; ncol=col;
        if (dir=='N') {
          singledir=new String("NORTH");
        } else if (dir=='S') {
          singledir=new String("SOUTH");
        } else if (dir=='E') {
          singledir=new String("EAST");
        } else { // if (dir=='W')
          singledir=new String("WEST");
        }
        outline=getDrivingOutline(singledir,singlenum);
        val=jbitsres.getbits(nrow,ncol,"OutMuxToSingle.OUT"+outline+"_TO_SINGLE_"+singledir+singlenum);
        if (VERBOSE) { System.err.println("local CLB "+nrow+","+ncol+" OutMuxToSingle.OUT"+outline+"_TO_SINGLE_"+singledir+singlenum+" is "+((val[0]==0)?"OFF":"ON")); }
        if (jbitsres.isEquivVal(nrow,ncol,val,"OutMuxToSingle.OFF")) {
          outline=-1; // out line doesn't drive the single line
	}
      }
    }
  }

  // if driven by an outline, then need to check that this outline is
  // driven by a slice output, which will be one of S#_(X|Y)[Q]

  if (outline>-1) {
    if (VERBOSE) { System.err.println("testing if OUT"+outline+" is connected to a slice output in CLB "+nrow+","+ncol); }
    classname=new String("OUT"+outline);
    val=jbitsres.getbits(nrow,ncol,"OUT"+outline+".OUT"+outline);
    String valstr=JBitsLookup.getName(classname,val);
    if (VERBOSE) { System.err.println("OUT"+outline+" = "+valstr); }
    if (valstr.equals("OFF")) {
      outline=-1;
    } else { // out line is driven by a slice output
      // result will be S#_%[Q]; where # is slice#, % is X or Y
      nbslice=(valstr.charAt(1)=='0') ? 0 : 1;
      nble=(valstr.charAt(3)=='Y') ? 0 : 1; // Y is LE 0, X is LE 1 in LEcoord
      return new LEcoord(nrow,ncol,nbslice,nble,outline);
    }
  }

  return null;
}



//		Output Bus to Singles	(class OutMuxToSingle)
//
// OUT0 ->	E2      	N0+  N1+  	S1+  S3  	W7
// OUT1 ->	E3  E5*  	N2      	S0+      	W4  W5*
// OUT2 ->	E6      	N6   N8  	S5   S7  	W9
// OUT3 ->	E8  E11* 	N9      	S10     	W10 W11*
// OUT4 ->	E14     	N12+ N13+ 	S13+ S15 	W19
// OUT5 ->	E15 E17* 	N14     	S12+     	W16 W17*
// OUT6 ->	E18     	N18  N20 	S17  S19 	W21
// OUT7 ->	E20 E23* 	N21     	S22     	W22 W23*
//
// '*' and '+' indicate contention can occur between these

int getDrivingOutline(String singledir, int singlenum)
{
  int outline=-1;

  try {
    // find the out bus line that is able to drive this single
    if (singledir.equals("NORTH")) {
      if (singlenum==0 || singlenum==1) {
	outline=0;
      } else if (singlenum==2) {
	outline=1;
      } else if (singlenum==6 || singlenum==8) {
	outline=2;
      } else if (singlenum==9) {
	outline=3;
      } else if (singlenum==12 || singlenum==13) {
	outline=4;
      } else if (singlenum==14) {
	outline=5;
      } else if (singlenum==18 || singlenum==20) {
	outline=6;
      } else if (singlenum==21) {
	outline=7;
      } else {
        throw new Exception("Invalid single(SINGLE_"+singledir+singlenum+"). direct single lines only supported");
      }
    } else if (singledir.equals("SOUTH")) {
      if (singlenum==1 || singlenum==3) {
	outline=0;
      } else if (singlenum==0) {
	outline=1;
      } else if (singlenum==5 || singlenum==7) {
	outline=2;
      } else if (singlenum==10) {
	outline=3;
      } else if (singlenum==13 || singlenum==15) {
	outline=4;
      } else if (singlenum==12) {
	outline=5;
      } else if (singlenum==17 || singlenum==19) {
	outline=6;
      } else if (singlenum==22) {
	outline=7;
      } else {
        throw new Exception("Invalid single(SINGLE_"+singledir+singlenum+"). direct single lines only supported");
      }
    } else if (singledir.equals("EAST")) {
      if (singlenum==2) {
	outline=0;
      } else if (singlenum==3 || singlenum==5) {
	outline=1;
      } else if (singlenum==6) {
	outline=2;
      } else if (singlenum==8 || singlenum==11) {
	outline=3;
      } else if (singlenum==14) {
	outline=4;
      } else if (singlenum==15 || singlenum==17) {
	outline=5;
      } else if (singlenum==18) {
	outline=6;
      } else if (singlenum==20 || singlenum==23) {
	outline=7;
      } else {
        throw new Exception("Invalid single(SINGLE_"+singledir+singlenum+"). direct single lines only supported");
      }
    } else if (singledir.equals("WEST")) {
      if (singlenum==7) {
	outline=0;
      } else if (singlenum==4 || singlenum==5) {
	outline=1;
      } else if (singlenum==9) {
	outline=2;
      } else if (singlenum==10 || singlenum==11) {
	outline=3;
      } else if (singlenum==19) {
	outline=4;
      } else if (singlenum==16 || singlenum==17) {
	outline=5;
      } else if (singlenum==21) {
	outline=6;
      } else if (singlenum==22 || singlenum==23) {
	outline=7;
      } else {
        throw new Exception("Invalid single(SINGLE_"+singledir+singlenum+"). direct single lines only supported");
      }
    }
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }

  return outline;
}


int[] connectedOUTbuses(LEcoord loc)
{
  int row=loc.row;
  int col=loc.col;
  int[] val=null;

  int[] o=new int[] { 0,0,0,0,0,0,0,0 };
  int ocnt=0;
  int[] outs=null;

  if (loc.slice==0 && loc.LE==0) {      // S0_Y[Q] -> OUTx
    for (int i=0; i<8; i++) {
      val=jbitsres.getbits(row,col,"OUT"+i+".OUT"+i);
      if (jbitsres.isEquivVal(row,col,val,"OUT"+i+".S0_YQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT"+i+".S0_Y")) {
	o[i]=1; ocnt++;
      }
    }
  } else if (loc.slice==0 && loc.LE==1) { // S0_X[Q] -> OUTx
    for (int i=0; i<8; i++) {
      val=jbitsres.getbits(row,col,"OUT"+i+".OUT"+i);
      if (jbitsres.isEquivVal(row,col,val,"OUT"+i+".S0_XQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT"+i+".S0_X")) {
	o[i]=1; ocnt++;
      }
    }
  } else if (loc.slice==1 && loc.LE==0) { // S1_Y[Q] -> OUTx
    for (int i=0; i<8; i++) {
      val=jbitsres.getbits(row,col,"OUT"+i+".OUT"+i);
      if (jbitsres.isEquivVal(row,col,val,"OUT"+i+".S1_YQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT"+i+".S1_Y")) {
	o[i]=1; ocnt++;
      }
    }
  } else { // (loc.slice==1 && loc.LE==1) // S1_X[Q] -> OUTx
    for (int i=0; i<8; i++) {
      val=jbitsres.getbits(row,col,"OUT"+i+".OUT"+i);
      if (jbitsres.isEquivVal(row,col,val,"OUT"+i+".S1_XQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT"+i+".S1_X")) {
	o[i]=1; ocnt++;
      }
    }
  }

  if (ocnt>0) {
    if (VERBOSE) { System.err.println(loc+" drives "+ocnt+" OUT bus lines.."); }
    if (VERBOSE) { System.err.print(loc+" -> "); }
    outs=new int[ocnt];
    int j=0;
    for (int i=0; i<8; i++) {
      if (o[i]==1) { // it's connected
        if (VERBOSE) { System.err.print("OUT"+i+" "); }
	outs[j]=i;   // store
	j++;
	if (j>=ocnt) { break; } // no more connected lines
      }
    }
    if (VERBOSE) { System.err.println(); }
    if (VERBOSE) { System.err.println(loc+" stored "+j+" OUT bus lines in outs[].."); }
  } else {
    if (VERBOSE) { System.err.println(loc+" has no connections to OUT bus!"); }
  }

  return outs;
}


//    LUT-input			direct connect line
//
//  S0G1/S0F1/S1G4/S1F4:
//	OFF N15 S6 W5 N19 S12 W17 N22 S13 W18 S21 OUT_WEST1
//
//  S0G2/S0F2/S1G3/S1F3:
//	OFF E11 N12 S1 W2 E23 N17 W20 OUT_WEST0
//
//  S0G3/S0F3/S1G2/S1F2:
//	OFF E5 N13 S0 W6 E7 S14 W8 E9 S20 W15 E10 W23 E21 OUT_EAST7
//
//  S0G4/S0F4/S1G1/S1F1:
//	OFF E4 N0 S2 W3 E16 N1 S8 W11 E17 N3 S9 W14 E19 N5 S18 E22 N7 N10
//	    OUT_EAST6

// note that in LEcoord's LE==0 is G-Y LE, and LE==1 is F-X
// NB. one line may drive multiple LUT inputs
LEcoord[] OUTtoSingleConnectedLE(LEcoord loc, int outline)
{
  List nbours=new ArrayList();
  int row=loc.row;
  int col=loc.col;
  int cnt=0;

  if (outline==0) {

    // OUT0 ->          OUT_WEST0 -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,0,"OUT_WEST0","S0G2")) {
      nbours.add(new LEcoord(row,col+1,0,0,2));
    }
    if (outlineToLUTConnected(row,col,0,"OUT_WEST0","S0F2")) {
      nbours.add(new LEcoord(row,col+1,0,1,2));
    }
    if (outlineToLUTConnected(row,col,0,"OUT_WEST0","S1G3")) {
      nbours.add(new LEcoord(row,col+1,1,0,3));
    }
    if (outlineToLUTConnected(row,col,0,"OUT_WEST0","S1F3")) {
      nbours.add(new LEcoord(row,col+1,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT0->OUT_WEST0"); cnt++; }

    // OUT0 -> EAST2 -> WEST2     -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,0,"EAST",2,"S0G2")) {
      nbours.add(new LEcoord(row,col+1,0,0,2));
    }
    if (outlineToLUTConnected(row,col,0,"EAST",2,"S0F2")) {
      nbours.add(new LEcoord(row,col+1,0,1,2));
    }
    if (outlineToLUTConnected(row,col,0,"EAST",2,"S1G3")) {
      nbours.add(new LEcoord(row,col+1,1,0,3));
    }
    if (outlineToLUTConnected(row,col,0,"EAST",2,"S1F3")) {
      nbours.add(new LEcoord(row,col+1,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT0->EAST2"); cnt++; }

    // OUT0 -> NORTH0 -> SOUTH0 -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,0,"NORTH",0,"S0G3")) {
      nbours.add(new LEcoord(row+1,col,0,0,3));
    }
    if (outlineToLUTConnected(row,col,0,"NORTH",0,"S0F3")) {
      nbours.add(new LEcoord(row+1,col,0,1,3));
    }
    if (outlineToLUTConnected(row,col,0,"NORTH",0,"S1G2")) {
      nbours.add(new LEcoord(row+1,col,1,0,2));
    }
    if (outlineToLUTConnected(row,col,0,"NORTH",0,"S1F2")) {
      nbours.add(new LEcoord(row+1,col,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT0->NORTH0"); cnt++; }

    // OUT0 -> NORTH1 -> SOUTH1 -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,0,"NORTH",1,"S0G2")) {
      nbours.add(new LEcoord(row+1,col,0,0,2));
    }
    if (outlineToLUTConnected(row,col,0,"NORTH",1,"S0F2")) {
      nbours.add(new LEcoord(row+1,col,0,1,2));
    }
    if (outlineToLUTConnected(row,col,0,"NORTH",1,"S1G3")) {
      nbours.add(new LEcoord(row+1,col,1,0,3));
    }
    if (outlineToLUTConnected(row,col,0,"NORTH",1,"S1F3")) {
      nbours.add(new LEcoord(row+1,col,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT0->NORTH1"); cnt++; }

    // OUT0 -> SOUTH1 -> NORTH1 -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,0,"SOUTH",1,"S0G4")) {
      nbours.add(new LEcoord(row-1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,0,"SOUTH",1,"S0F4")) {
      nbours.add(new LEcoord(row-1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,0,"SOUTH",1,"S1G1")) {
      nbours.add(new LEcoord(row-1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,0,"SOUTH",1,"S1F1")) {
      nbours.add(new LEcoord(row-1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT0->SOUTH1"); cnt++; }

    // OUT0 -> SOUTH3 -> NORTH3 -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,0,"SOUTH",3,"S0G4")) {
      nbours.add(new LEcoord(row-1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,0,"SOUTH",3,"S0F4")) {
      nbours.add(new LEcoord(row-1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,0,"SOUTH",3,"S1G1")) {
      nbours.add(new LEcoord(row-1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,0,"SOUTH",3,"S1F1")) {
      nbours.add(new LEcoord(row-1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT0->SOUTH3"); cnt++; }

    // OUT0 -> WEST7 -> EAST7 -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,0,"WEST",7,"S0G3")) {
      nbours.add(new LEcoord(row,col-1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,0,"WEST",7,"S0F3")) {
      nbours.add(new LEcoord(row,col-1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,0,"WEST",7,"S1G2")) {
      nbours.add(new LEcoord(row,col-1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,0,"WEST",7,"S1F2")) {
      nbours.add(new LEcoord(row,col-1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT0->WEST7"); cnt++; }

  } else if (outline==1) {

    // OUT1     ->	OUT_WEST1 -> SOG1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,1,"OUT_WEST1","S0G1")) {
      nbours.add(new LEcoord(row,col+1,0,0,1));
    }
    if (outlineToLUTConnected(row,col,1,"OUT_WEST1","S0F1")) {
      nbours.add(new LEcoord(row,col+1,0,1,1));
    }
    if (outlineToLUTConnected(row,col,1,"OUT_WEST1","S1G4")) {
      nbours.add(new LEcoord(row,col+1,1,0,4));
    }
    if (outlineToLUTConnected(row,col,1,"OUT_WEST1","S1F4")) {
      nbours.add(new LEcoord(row,col+1,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT1->WEST_1"); cnt++; }

    // OUT1 -> EAST3 -> WEST3     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,1,"EAST",3,"S0G4")) {
      nbours.add(new LEcoord(row,col+1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,1,"EAST",3,"S0F4")) {
      nbours.add(new LEcoord(row,col+1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,1,"EAST",3,"S1G1")) {
      nbours.add(new LEcoord(row,col+1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,1,"EAST",3,"S1F1")) {
      nbours.add(new LEcoord(row,col+1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT1->EAST3"); cnt++; }

    // OUT1 -> EAST5 -> WEST5     -> SOG1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,1,"EAST",5,"S0G1")) {
      nbours.add(new LEcoord(row,col+1,0,0,1));
    }
    if (outlineToLUTConnected(row,col,1,"EAST",5,"S0F1")) {
      nbours.add(new LEcoord(row,col+1,0,1,1));
    }
    if (outlineToLUTConnected(row,col,1,"EAST",5,"S1G4")) {
      nbours.add(new LEcoord(row,col+1,1,0,4));
    }
    if (outlineToLUTConnected(row,col,1,"EAST",5,"S1F4")) {
      nbours.add(new LEcoord(row,col+1,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT1->EAST5"); cnt++; }

    // OUT1 -> NORTH2 -> SOUTH2     -> S0F4 S0G4 S1F1 S1G1
    if (outlineToLUTConnected(row,col,1,"NORTH",2,"S0G4")) {
      nbours.add(new LEcoord(row+1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,1,"NORTH",2,"S0F4")) {
      nbours.add(new LEcoord(row+1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,1,"NORTH",2,"S1G1")) {
      nbours.add(new LEcoord(row+1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,1,"NORTH",2,"S1F1")) {
      nbours.add(new LEcoord(row+1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT1->NORTH2"); cnt++; }

    // OUT1 -> SOUTH0 -> NORTH0     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,1,"SOUTH",0,"S0G4")) {
      nbours.add(new LEcoord(row-1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,1,"SOUTH",0,"S0F4")) {
      nbours.add(new LEcoord(row-1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,1,"SOUTH",0,"S1G1")) {
      nbours.add(new LEcoord(row-1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,1,"SOUTH",0,"S1F1")) {
      nbours.add(new LEcoord(row-1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT1->SOUTH0"); cnt++; }

    // OUT1 -> WEST4 -> EAST4     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,1,"WEST",4,"S0G4")) {
      nbours.add(new LEcoord(row,col-1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,1,"WEST",4,"S0F4")) {
      nbours.add(new LEcoord(row,col-1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,1,"WEST",4,"S1G1")) {
      nbours.add(new LEcoord(row,col-1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,1,"WEST",4,"S1F1")) {
      nbours.add(new LEcoord(row,col-1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT1->WEST4"); cnt++; }

    // OUT1 -> WEST5 -> EAST5 -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,1,"WEST",5,"S0G3")) {
      nbours.add(new LEcoord(row,col-1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,1,"WEST",5,"S0F3")) {
      nbours.add(new LEcoord(row,col-1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,1,"WEST",5,"S1G2")) {
      nbours.add(new LEcoord(row,col-1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,1,"WEST",5,"S1F2")) {
      nbours.add(new LEcoord(row,col-1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT1->WEST5"); cnt++; }

  } else if (outline==2) {

    // OUT2 -> EAST6 -> WEST6 -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,2,"EAST",6,"S0G3")) {
      nbours.add(new LEcoord(row,col+1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,2,"EAST",6,"S0F3")) {
      nbours.add(new LEcoord(row,col+1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,2,"EAST",6,"S1G2")) {
      nbours.add(new LEcoord(row,col+1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,2,"EAST",6,"S1F2")) {
      nbours.add(new LEcoord(row,col+1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT2->EAST6"); cnt++; }

    // OUT2 -> NORTH6 -> SOUTH6     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,2,"NORTH",6,"S0G1")) {
      nbours.add(new LEcoord(row+1,col,0,0,1));
    }
    if (outlineToLUTConnected(row,col,2,"NORTH",6,"S0F1")) {
      nbours.add(new LEcoord(row+1,col,0,1,1));
    }
    if (outlineToLUTConnected(row,col,2,"NORTH",6,"S1G4")) {
      nbours.add(new LEcoord(row+1,col,1,0,4));
    }
    if (outlineToLUTConnected(row,col,2,"NORTH",6,"S1F4")) {
      nbours.add(new LEcoord(row+1,col,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT2->NORTH6"); cnt++; }

    // OUT2 -> NORTH8 -> SOUTH8     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,2,"NORTH",8,"S0G4")) {
      nbours.add(new LEcoord(row+1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,2,"NORTH",8,"S0F4")) {
      nbours.add(new LEcoord(row+1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,2,"NORTH",8,"S1G1")) {
      nbours.add(new LEcoord(row+1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,2,"NORTH",8,"S1F1")) {
      nbours.add(new LEcoord(row+1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT2->NORTH8"); cnt++; }

    // OUT2 -> SOUTH5 -> NORTH5     -> S0G4 S0F4 S1G1 S1G1
    if (outlineToLUTConnected(row,col,2,"SOUTH",5,"S0G4")) {
      nbours.add(new LEcoord(row-1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,2,"SOUTH",5,"S0F4")) {
      nbours.add(new LEcoord(row-1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,2,"SOUTH",5,"S1G1")) {
      nbours.add(new LEcoord(row-1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,2,"SOUTH",5,"S1F1")) {
      nbours.add(new LEcoord(row-1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT2->SOUTH5"); cnt++; }

    // OUT2 -> SOUTH7 -> NORTH7     -> S0G4 S0F4 S1G1 S1G1
    if (outlineToLUTConnected(row,col,2,"SOUTH",7,"S0G4")) {
      nbours.add(new LEcoord(row-1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,2,"SOUTH",7,"S0F4")) {
      nbours.add(new LEcoord(row-1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,2,"SOUTH",7,"S1G1")) {
      nbours.add(new LEcoord(row-1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,2,"SOUTH",7,"S1F1")) {
      nbours.add(new LEcoord(row-1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT2->SOUTH7"); cnt++; }

    // OUT2 -> WEST9 -> EAST6 -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,2,"WEST",9,"S0G3")) {
      nbours.add(new LEcoord(row,col-1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,2,"WEST",9,"S0F3")) {
      nbours.add(new LEcoord(row,col-1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,2,"WEST",9,"S1G2")) {
      nbours.add(new LEcoord(row,col-1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,2,"WEST",9,"S1F2")) {
      nbours.add(new LEcoord(row,col-1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT2->WEST9"); cnt++; }

  } else if (outline==3) {

    // OUT3 -> EAST8 -> WEST8     -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,3,"EAST",8,"S0G3")) {
      nbours.add(new LEcoord(row,col+1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,3,"EAST",8,"S0F3")) {
      nbours.add(new LEcoord(row,col+1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,3,"EAST",8,"S1G2")) {
      nbours.add(new LEcoord(row,col+1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,3,"EAST",8,"S1F2")) {
      nbours.add(new LEcoord(row,col+1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT3->EAST8"); cnt++; }

    // OUT3 -> EAST11 -> WEST11     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,3,"EAST",11,"S0G4")) {
      nbours.add(new LEcoord(row,col+1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,3,"EAST",11,"S0F4")) {
      nbours.add(new LEcoord(row,col+1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,3,"EAST",11,"S1G1")) {
      nbours.add(new LEcoord(row,col+1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,3,"EAST",11,"S1F1")) {
      nbours.add(new LEcoord(row,col+1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT3->EAST11"); cnt++; }

    // OUT3 -> NORTH9 -> SOUTH9     -> S0F4 S0G4 S1F1 S1G1
    if (outlineToLUTConnected(row,col,3,"NORTH",9,"S0G4")) {
      nbours.add(new LEcoord(row+1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,3,"NORTH",9,"S0F4")) {
      nbours.add(new LEcoord(row+1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,3,"NORTH",9,"S1G1")) {
      nbours.add(new LEcoord(row+1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,3,"NORTH",9,"S1F1")) {
      nbours.add(new LEcoord(row+1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT3->NORTH9"); cnt++; }

    // OUT3 -> SOUTH10 -> NORTH10     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,3,"SOUTH",10,"S0G4")) {
      nbours.add(new LEcoord(row-1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,3,"SOUTH",10,"S0F4")) {
      nbours.add(new LEcoord(row-1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,3,"SOUTH",10,"S1G1")) {
      nbours.add(new LEcoord(row-1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,3,"SOUTH",10,"S1F1")) {
      nbours.add(new LEcoord(row-1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT3->SOUTH10"); cnt++; }

    // OUT3 -> WEST10 -> EAST10 -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,3,"WEST",10,"S0G3")) {
      nbours.add(new LEcoord(row,col-1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,3,"WEST",10,"S0F3")) {
      nbours.add(new LEcoord(row,col-1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,3,"WEST",10,"S1G2")) {
      nbours.add(new LEcoord(row,col-1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,3,"WEST",10,"S1F2")) {
      nbours.add(new LEcoord(row,col-1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT3->WEST10"); cnt++; }

    // OUT3 -> WEST11 -> EAST11 -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,3,"WEST",11,"S0G2")) {
      nbours.add(new LEcoord(row,col-1,0,0,2));
    }
    if (outlineToLUTConnected(row,col,3,"WEST",11,"S0F2")) {
      nbours.add(new LEcoord(row,col-1,0,1,2));
    }
    if (outlineToLUTConnected(row,col,3,"WEST",11,"S1G3")) {
      nbours.add(new LEcoord(row,col-1,1,0,3));
    }
    if (outlineToLUTConnected(row,col,3,"WEST",11,"S1F3")) {
      nbours.add(new LEcoord(row,col-1,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT3->WEST11"); cnt++; }

  } else if (outline==4) {

    // OUT4 -> EAST14 -> WEST14     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,4,"EAST",14,"S0G4")) {
      nbours.add(new LEcoord(row,col+1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,4,"EAST",14,"S0F4")) {
      nbours.add(new LEcoord(row,col+1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,4,"EAST",14,"S1G1")) {
      nbours.add(new LEcoord(row,col+1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,4,"EAST",14,"S1F1")) {
      nbours.add(new LEcoord(row,col+1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT4->EAST14"); cnt++; }

    // OUT4 -> NORTH12 -> SOUTH12     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,4,"NORTH",12,"S0G1")) {
      nbours.add(new LEcoord(row+1,col,0,0,1));
    }
    if (outlineToLUTConnected(row,col,4,"NORTH",12,"S0F1")) {
      nbours.add(new LEcoord(row+1,col,0,1,1));
    }
    if (outlineToLUTConnected(row,col,4,"NORTH",12,"S1G4")) {
      nbours.add(new LEcoord(row+1,col,1,0,4));
    }
    if (outlineToLUTConnected(row,col,4,"NORTH",12,"S1F4")) {
      nbours.add(new LEcoord(row+1,col,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT4->NORTH12"); cnt++; }

    // OUT4 -> NORTH13 -> SOUTH13     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,4,"NORTH",13,"S0G1")) {
      nbours.add(new LEcoord(row+1,col,0,0,1));
    }
    if (outlineToLUTConnected(row,col,4,"NORTH",13,"S0F1")) {
      nbours.add(new LEcoord(row+1,col,0,1,1));
    }
    if (outlineToLUTConnected(row,col,4,"NORTH",13,"S1G4")) {
      nbours.add(new LEcoord(row+1,col,1,0,4));
    }
    if (outlineToLUTConnected(row,col,4,"NORTH",13,"S1F4")) {
      nbours.add(new LEcoord(row+1,col,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT4->NORTH13"); cnt++; }

    // OUT4 -> SOUTH13 -> NORTH13     -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,4,"SOUTH",13,"S0G3")) {
      nbours.add(new LEcoord(row-1,col,0,0,3));
    }
    if (outlineToLUTConnected(row,col,4,"SOUTH",13,"S0F3")) {
      nbours.add(new LEcoord(row-1,col,0,1,3));
    }
    if (outlineToLUTConnected(row,col,4,"SOUTH",13,"S1G2")) {
      nbours.add(new LEcoord(row-1,col,1,0,2));
    }
    if (outlineToLUTConnected(row,col,4,"SOUTH",13,"S1F2")) {
      nbours.add(new LEcoord(row-1,col,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT4->SOUTH13"); cnt++; }

    // OUT4 -> SOUTH15 -> NORTH15     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,4,"SOUTH",15,"S0G1")) {
      nbours.add(new LEcoord(row-1,col,0,0,1));
    }
    if (outlineToLUTConnected(row,col,4,"SOUTH",15,"S0F1")) {
      nbours.add(new LEcoord(row-1,col,0,1,1));
    }
    if (outlineToLUTConnected(row,col,4,"SOUTH",15,"S1G4")) {
      nbours.add(new LEcoord(row-1,col,1,0,4));
    }
    if (outlineToLUTConnected(row,col,4,"SOUTH",15,"S1F4")) {
      nbours.add(new LEcoord(row-1,col,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT4->SOUTH15"); cnt++; }

    // OUT4 -> WEST19 -> EAST19     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,4,"WEST",19,"S0G4")) {
      nbours.add(new LEcoord(row,col-1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,4,"WEST",19,"S0F4")) {
      nbours.add(new LEcoord(row,col-1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,4,"WEST",19,"S1G1")) {
      nbours.add(new LEcoord(row,col-1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,4,"WEST",19,"S1F1")) {
      nbours.add(new LEcoord(row,col-1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT4->WEST19"); cnt++; }

  } else if (outline==5) {

    // OUT5 -> EAST15 -> WEST15     -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,5,"EAST",15,"S0G3")) {
      nbours.add(new LEcoord(row,col+1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,5,"EAST",15,"S0F3")) {
      nbours.add(new LEcoord(row,col+1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,5,"EAST",15,"S1G2")) {
      nbours.add(new LEcoord(row,col+1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,5,"EAST",15,"S1F2")) {
      nbours.add(new LEcoord(row,col+1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT5->EAST15"); cnt++; }

    // OUT5 -> EAST17 -> WEST17     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,5,"EAST",17,"S0G1")) {
      nbours.add(new LEcoord(row,col+1,0,0,1));
    }
    if (outlineToLUTConnected(row,col,5,"EAST",17,"S0F1")) {
      nbours.add(new LEcoord(row,col+1,0,1,1));
    }
    if (outlineToLUTConnected(row,col,5,"EAST",17,"S1G4")) {
      nbours.add(new LEcoord(row,col+1,1,0,4));
    }
    if (outlineToLUTConnected(row,col,5,"EAST",17,"S1F4")) {
      nbours.add(new LEcoord(row,col+1,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT5->EAST17"); cnt++; }

    // OUT5 -> NORTH14 -> SOUTH14     -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,5,"NORTH",14,"S0G3")) {
      nbours.add(new LEcoord(row+1,col,0,0,3));
    }
    if (outlineToLUTConnected(row,col,5,"NORTH",14,"S0F3")) {
      nbours.add(new LEcoord(row+1,col,0,1,3));
    }
    if (outlineToLUTConnected(row,col,5,"NORTH",14,"S1G2")) {
      nbours.add(new LEcoord(row+1,col,1,0,2));
    }
    if (outlineToLUTConnected(row,col,5,"NORTH",14,"S1F2")) {
      nbours.add(new LEcoord(row+1,col,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT5->NORTH14"); cnt++; }

    // OUT5 -> SOUTH12 -> NORTH12     -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,5,"SOUTH",12,"S0G2")) {
      nbours.add(new LEcoord(row-1,col,0,0,2));
    }
    if (outlineToLUTConnected(row,col,5,"SOUTH",12,"S0F2")) {
      nbours.add(new LEcoord(row-1,col,0,1,2));
    }
    if (outlineToLUTConnected(row,col,5,"SOUTH",12,"S1G3")) {
      nbours.add(new LEcoord(row-1,col,1,0,3));
    }
    if (outlineToLUTConnected(row,col,5,"SOUTH",12,"S1F3")) {
      nbours.add(new LEcoord(row-1,col,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT5->SOUTH12"); cnt++; }

    // OUT5 -> WEST16 -> EAST16     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,5,"WEST",16,"S0G4")) {
      nbours.add(new LEcoord(row,col-1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,5,"WEST",16,"S0F4")) {
      nbours.add(new LEcoord(row,col-1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,5,"WEST",16,"S1G1")) {
      nbours.add(new LEcoord(row,col-1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,5,"WEST",16,"S1F1")) {
      nbours.add(new LEcoord(row,col-1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT5->WEST16"); cnt++; }

    // OUT5 -> WEST17 -> EAST17     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,5,"WEST",17,"S0G4")) {
      nbours.add(new LEcoord(row,col-1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,5,"WEST",17,"S0F4")) {
      nbours.add(new LEcoord(row,col-1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,5,"WEST",17,"S1G1")) {
      nbours.add(new LEcoord(row,col-1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,5,"WEST",17,"S1F1")) {
      nbours.add(new LEcoord(row,col-1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT5->WEST17"); cnt++; }

  } else if (outline==6) {

    // OUT6 -> EAST18 -> WEST18     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,6,"EAST",18,"S0G1")) {
      nbours.add(new LEcoord(row,col+1,0,0,1));
    }
    if (outlineToLUTConnected(row,col,6,"EAST",18,"S0F1")) {
      nbours.add(new LEcoord(row,col+1,0,1,1));
    }
    if (outlineToLUTConnected(row,col,6,"EAST",18,"S1G4")) {
      nbours.add(new LEcoord(row,col+1,1,0,4));
    }
    if (outlineToLUTConnected(row,col,6,"EAST",18,"S1F4")) {
      nbours.add(new LEcoord(row,col+1,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT6->EAST18"); cnt++; }

    // OUT6 -> NORTH18 -> SOUTH18     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,6,"NORTH",18,"S0G4")) {
      nbours.add(new LEcoord(row+1,col,0,0,4));
    }
    if (outlineToLUTConnected(row,col,6,"NORTH",18,"S0F4")) {
      nbours.add(new LEcoord(row+1,col,0,1,4));
    }
    if (outlineToLUTConnected(row,col,6,"NORTH",18,"S1G1")) {
      nbours.add(new LEcoord(row+1,col,1,0,1));
    }
    if (outlineToLUTConnected(row,col,6,"NORTH",18,"S1F1")) {
      nbours.add(new LEcoord(row+1,col,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT6->NORTH18"); cnt++; }

    // OUT6 -> NORTH20 -> SOUTH20     -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,6,"NORTH",20,"S0G3")) {
      nbours.add(new LEcoord(row+1,col,0,0,3));
    }
    if (outlineToLUTConnected(row,col,6,"NORTH",20,"S0F3")) {
      nbours.add(new LEcoord(row+1,col,0,1,3));
    }
    if (outlineToLUTConnected(row,col,6,"NORTH",20,"S1G2")) {
      nbours.add(new LEcoord(row+1,col,1,0,2));
    }
    if (outlineToLUTConnected(row,col,6,"NORTH",20,"S1F2")) {
      nbours.add(new LEcoord(row+1,col,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT6->NORTH20"); cnt++; }

    // OUT6 -> SOUTH17 -> NORTH17     -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,6,"SOUTH",17,"S0G2")) {
      nbours.add(new LEcoord(row-1,col,0,0,2));
    }
    if (outlineToLUTConnected(row,col,6,"SOUTH",17,"S0F2")) {
      nbours.add(new LEcoord(row-1,col,0,1,2));
    }
    if (outlineToLUTConnected(row,col,6,"SOUTH",17,"S1G3")) {
      nbours.add(new LEcoord(row-1,col,1,0,3));
    }
    if (outlineToLUTConnected(row,col,6,"SOUTH",17,"S1F3")) {
      nbours.add(new LEcoord(row-1,col,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT6->SOUTH17"); cnt++; }

    // OUT6 -> SOUTH19 -> NORTH19     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,6,"SOUTH",19,"S0G1")) {
      nbours.add(new LEcoord(row-1,col,0,0,1));
    }
    if (outlineToLUTConnected(row,col,6,"SOUTH",19,"S0F1")) {
      nbours.add(new LEcoord(row-1,col,0,1,1));
    }
    if (outlineToLUTConnected(row,col,6,"SOUTH",19,"S1G4")) {
      nbours.add(new LEcoord(row-1,col,1,0,4));
    }
    if (outlineToLUTConnected(row,col,6,"SOUTH",19,"S1F4")) {
      nbours.add(new LEcoord(row-1,col,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT6->SOUTH19"); cnt++; }

    // OUT6 -> WEST21 -> EAST21 -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,6,"WEST",21,"S0G3")) {
      nbours.add(new LEcoord(row,col-1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,6,"WEST",21,"S0F3")) {
      nbours.add(new LEcoord(row,col-1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,6,"WEST",21,"S1G2")) {
      nbours.add(new LEcoord(row,col-1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,6,"WEST",21,"S1F2")) {
      nbours.add(new LEcoord(row,col-1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT6->WEST21"); cnt++; }

    // OUT6 	-> OUT_EAST6     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,6,"OUT_EAST6","S0G4")) {
      nbours.add(new LEcoord(row,col-1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,6,"OUT_EAST6","S0F4")) {
      nbours.add(new LEcoord(row,col-1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,6,"OUT_EAST6","S1G1")) {
      nbours.add(new LEcoord(row,col-1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,6,"OUT_EAST6","S1F1")) {
      nbours.add(new LEcoord(row,col-1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT6->OUT_EAST6"); cnt++; }

  } else { // (outline==7)

    // OUT7 -> EAST20 -> WEST20     -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,7,"EAST",20,"S0G2")) {
      nbours.add(new LEcoord(row,col+1,0,0,2));
    }
    if (outlineToLUTConnected(row,col,7,"EAST",20,"S0F2")) {
      nbours.add(new LEcoord(row,col+1,0,1,2));
    }
    if (outlineToLUTConnected(row,col,7,"EAST",20,"S1G3")) {
      nbours.add(new LEcoord(row,col+1,1,0,3));
    }
    if (outlineToLUTConnected(row,col,7,"EAST",20,"S1F3")) {
      nbours.add(new LEcoord(row,col+1,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT7->EAST20"); cnt++; }

    // OUT7 -> EAST23 -> WEST23     -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,7,"EAST",23,"S0G3")) {
      nbours.add(new LEcoord(row,col+1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,7,"EAST",23,"S0F3")) {
      nbours.add(new LEcoord(row,col+1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,7,"EAST",23,"S1G2")) {
      nbours.add(new LEcoord(row,col+1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,7,"EAST",23,"S1F2")) {
      nbours.add(new LEcoord(row,col+1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT7->EAST23"); cnt++; }

    // OUT7 -> NORTH21 -> SOUTH21     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,7,"NORTH",21,"S0G1")) {
      nbours.add(new LEcoord(row+1,col,0,0,1));
    }
    if (outlineToLUTConnected(row,col,7,"NORTH",21,"S0F1")) {
      nbours.add(new LEcoord(row+1,col,0,1,1));
    }
    if (outlineToLUTConnected(row,col,7,"NORTH",21,"S1G4")) {
      nbours.add(new LEcoord(row+1,col,1,0,4));
    }
    if (outlineToLUTConnected(row,col,7,"NORTH",21,"S1F4")) {
      nbours.add(new LEcoord(row+1,col,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT7->NORTH21"); cnt++; }

    // OUT7 -> SOUTH22 -> NORTH22     -> S0G1 S0F1 S1G4 S1F4
    if (outlineToLUTConnected(row,col,7,"SOUTH",22,"S0G1")) {
      nbours.add(new LEcoord(row-1,col,0,0,1));
    }
    if (outlineToLUTConnected(row,col,7,"SOUTH",22,"S0F1")) {
      nbours.add(new LEcoord(row-1,col,0,1,1));
    }
    if (outlineToLUTConnected(row,col,7,"SOUTH",22,"S1G4")) {
      nbours.add(new LEcoord(row-1,col,1,0,4));
    }
    if (outlineToLUTConnected(row,col,7,"SOUTH",22,"S1F4")) {
      nbours.add(new LEcoord(row-1,col,1,1,4));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT7->SOUTH22"); cnt++; }

    // OUT7 -> WEST22 -> EAST22     -> S0G4 S0F4 S1G1 S1F1
    if (outlineToLUTConnected(row,col,7,"WEST",22,"S0G4")) {
      nbours.add(new LEcoord(row,col-1,0,0,4));
    }
    if (outlineToLUTConnected(row,col,7,"WEST",22,"S0F4")) {
      nbours.add(new LEcoord(row,col-1,0,1,4));
    }
    if (outlineToLUTConnected(row,col,7,"WEST",22,"S1G1")) {
      nbours.add(new LEcoord(row,col-1,1,0,1));
    }
    if (outlineToLUTConnected(row,col,7,"WEST",22,"S1F1")) {
      nbours.add(new LEcoord(row,col-1,1,1,1));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT7->WEST22"); cnt++; }

    // OUT7 -> WEST23 -> EAST23 -> S0G2 S0F2 S1G3 S1F3
    if (outlineToLUTConnected(row,col,7,"WEST",23,"S0G2")) {
      nbours.add(new LEcoord(row,col-1,0,0,2));
    }
    if (outlineToLUTConnected(row,col,7,"WEST",23,"S0F2")) {
      nbours.add(new LEcoord(row,col-1,0,1,2));
    }
    if (outlineToLUTConnected(row,col,7,"WEST",23,"S1G3")) {
      nbours.add(new LEcoord(row,col-1,1,0,3));
    }
    if (outlineToLUTConnected(row,col,7,"WEST",23,"S1F3")) {
      nbours.add(new LEcoord(row,col-1,1,1,3));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT7->WEST23"); cnt++; }

    // OUT7 	-> OUT_EAST7     -> S0G3 S0F3 S1G2 S1F2
    if (outlineToLUTConnected(row,col,7,"OUT_EAST7","S0G3")) {
      nbours.add(new LEcoord(row,col-1,0,0,3));
    }
    if (outlineToLUTConnected(row,col,7,"OUT_EAST7","S0F3")) {
      nbours.add(new LEcoord(row,col-1,0,1,3));
    }
    if (outlineToLUTConnected(row,col,7,"OUT_EAST7","S1G2")) {
      nbours.add(new LEcoord(row,col-1,1,0,2));
    }
    if (outlineToLUTConnected(row,col,7,"OUT_EAST7","S1F2")) {
      nbours.add(new LEcoord(row,col-1,1,1,2));
    }
    if (VERBOSE && nbours.size()>cnt) { System.err.println("OUT7->OUT_EAST7"); cnt++; }

  }

//if(nbours.size()>0) { System.err.print("<nbours="+nbours+">"); }

  // note use of empty array param to the ArrayList.toArray() method
  // this ensures that (from API) "a new array is allocated with the
  // runtime type of the specified array and the size of this list."
  // otherwise a java.lang.ClassCastException is thrown

  return (LEcoord[])nbours.toArray(new LEcoord[0]);
}



boolean outlineToLUTConnected(int row, int col, int busline, String singledir, int singlenum, String lutmux) 
{
  int roff=0;
  int coff=0;
  String lutsingle=null;
  if (singledir.equals("EAST")) { coff=1; lutsingle="WEST"; }
  if (singledir.equals("WEST")) { coff=-1; lutsingle="EAST"; }
  if (singledir.equals("NORTH")) { roff=1; lutsingle="SOUTH"; }
  if (singledir.equals("SOUTH")) { roff=-1; lutsingle="NORTH"; }

  int[] val=jbitsres.getbits(row,col,"OutMuxToSingle.OUT"+busline+"_TO_SINGLE_"+singledir+singlenum);
  int[] nbval=jbitsres.getbits(row+roff,col+coff,lutmux+"."+lutmux);
  return (jbitsres.isEquivVal(row,col,val,"OutMuxToSingle.ON") &&
	  jbitsres.isEquivVal(row+roff,col+coff,nbval,lutmux+".SINGLE_"+lutsingle+singlenum));

}


boolean outlineToLUTConnected(int row, int col, int busline, String directout, String lutmux) 
{
  // if OUT_WEST0 || OUT_WEST1: coff=1; if OUT_EAST6 || OUT_EAST7: coff=-1
  int coff=(busline==0 || busline==1) ? 1 : -1;
  int[] nbval=jbitsres.getbits(row,col+coff,lutmux+"."+lutmux);
  return jbitsres.isEquivVal(row,col+coff,nbval,lutmux+"."+directout);
}




} // end class




