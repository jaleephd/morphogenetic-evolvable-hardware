
//			 TestInToOutRecurse.java
//
// this class tests or attempts to grow a connected circuit that will reach
// the outputs. It checks for connectedness in the fastest possible manner,
// by using a cut down set of resources and settings in the evolved circuits
// (by default, but it can also use the full set of resources) and based on
// this, tracing connections from the inputs, until all connected LEs have
// been reached. Fitness is based on how close to the final outputs we are
// able to connect.
//
// 		written Feb 2004, J.A. Lee
//
// this class also requires the local classes:
// 			MhwMatrix, BuildEhw, JBitsResources,
// 			StringSplitter, LEcoord
//
// Known Bugs: doesn't seem to work properly if CLB region is not square
// unsure if this occurs in the fitness function (always generates 0) or
// in the connectivity tests. This should be fixed one day.
//
// Other issues: at command line only document use of XCV50, though have
// added a kludge to allow an XCV1000 to be used for CLB matrix of > 14x14
// this assumes that if a CLB matrix fits within an XCV50 then that is what
// the bitstream is that was provided, whereas if it doesn't fit, then an
// XCV1000 bitstream is assumed. An extra parameter should be added to
// indicate which device is being used - this isn't done at the moment to
// allow slim:RxC tests with >14x14 to be run without other changes to EHW
// system (to propogate the extra parameter)


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






public class TestInToOutRecurse {

  	boolean VERBOSE;

	boolean USEDIRECTOUT; // whether to allow routing via direct outputs
	boolean SHAREDOUTS; // true if each LE doesn't have specific OUT lines

	JBits jbits;
	JBitsResources jbitsres;
	static String deviceName1="XCV50";
	static String deviceName2="XCV1000";

	static final int MaxXCV50Row=13; // 15 -2 for EHW layout
	static final int MaxXCV50Col=21; // 23 -2 for EHW layout
	// KLUDGE Alert!!!
	// note, in reality we still need more space to route from IOBs to IOs
	// here we assume that we are just testing the bitstream, otherwise
	// use less than this for XVC50 or more than this for XCV1000

	char[] hex;			// for conversion to hex digits
	int[] lutalloff;		// value indicates LUT out 0 always
	int[] lutallon;			// value indicates LUT out 1 always

	public int direction;		// desired direction of cct growth
  	// direction constants:
  	public final int BtoT=0;	// bottom to top
  	public final int LtoR=1;	// left to right
  	public final int TtoB=2;	// top to bottom
  	public final int RtoL=3;	// right to left

	public int minrow;		// bounds of evolvable region
	public int mincol;
	public int maxrow;
	public int maxcol;

	public LEcoord[] inlocs;	// coords of input & output CLBS
	public LEcoord[] outlocs;

	public int inputwidth;		// width of input bus
	public int outputwidth;		// width of output bus

	// for storing states of all LEs in the CLB neighbouring an entry/exit
	// point in the evolvable region for non-slim model circuits
	int[] instates;
	int[] outstates;

	// note matrix is rows*2 x cols*2 from minrow,mincol-maxrow,maxcol:
        // we assign slice granularity to cols, and LE granularity to rows
	public int[][] stateMatrix;	// stores (connection) state

	// uses 4 bits to indicate if a given input line has been tested yet
	int[][] lineTestedMatrix;

	// these give the number of slice (or LE) layers from in CLBs to out
	// and the number of LE (or slice) entries per layer (ie other axis)
	public int numlayers;
	public int numentries;

	public double fitness;

	// this is for use with simulator probes (implemented externally)
	// to give information on whether outputs at each LE change when
	// signals are applied to the (inputs of the) circuit
	// Note that it needs to be set by the external implementation!
	public int[][] signalChangeMatrix=null;

	// if this is set to true, then for a LE to be considered to be
	// fully functional, its function must ignore all unused inputs,
	// that being all inputs that are either not connected, or connected
	// to a line that isn't driven by some function of the circuit inputs,
	// as determined by the line not having been tested for connectivity
	// during the recursive connectivity test. this is more restrictive
	// that the use of the signalChangeMatrix, as an unused input may be
	// ignored in effect according to the FN, for eg if it's an
	// unconnected input, held at logical 1, an AND function effectively
	// ignores it.
	// *** NOT YET IMPLEMENTED ***
	public boolean LUTIGNORESUNUSEDIN=false;

	// these are for fitness evaluation (backwards compatibility):
	// all in LEs must be connected to get 100% fitness
	// ie we expect to use all inputs in some manner
	public boolean NEEDSALLINPUTLES=true;
	// all middle layers must have sufficient spread to get 100% fitness
	// this is mainly to guide spread out to output LEs, so if out LEs
	// are all connected, then this is already achieved, so not necessary
	// unless we specifically want to force spread
	public boolean NEEDSMIDLAYERSPREAD=false;


public static void main (String[] args)
{
  int save=0;
  boolean verbose=false;
  boolean debug=false;	// this is used for more info than verbose
  int vgran=2;		// CLB granularity in vertical axis
  boolean shareouts=false;	// each LE is allocated specific OUT lines
  boolean usedirectout=true;	// allow use of OUT_WEST0/1 OUT_EAST6/7
  boolean strictiterlimit=false; // allow further iter if fitness increasing
  boolean forcespread=false;	// force mid-level spread req. for 100% fitness

  int transcriptionrate=4; // defaults
  double npoly2gene=0.3;
  double polythreshold=0.8;

  String devname=TestInToOutRecurse.deviceName1; // assume XCV50
  String outbit=null;
  String recordfile=null;	// this is for recording set() instructions

  String usage = "Usage: TestInToOutRecurse [-v | -d] [-le] [-forcespread] [-sharedouts] [-ndo] [-sil] [ -s outbit | -sr outbit ] XCV50CLK1.bit inIOBs outIOBs inCLBsliceLEs outCLBsliceLEs minCLB maxCLB contentionFile [ dchromfilelist | dchromfile cytofile numTestIter nGrowthStepsPerTest [ transcriptionRate npoly2gene polyThreshold ] [ seed ] ]";

  // Check the arguments
  int argc=args.length;
  int flags=0;

  while (argc>0 && args[0+flags].charAt(0)=='-') {
//System.err.println("got arg "+args[0+flags]);
    if (args[0+flags].equals("-d")) {
      debug=verbose=true;		// extra level of debugging info
      flags++; argc--;
    } else if (args[0+flags].equals("-v")) {
      verbose=true; flags++; argc--;
    } else if (args[0+flags].equals("-le")) {
      vgran=1;		// logic element granularity in vertical axis
      flags++; argc--;
    } else if (args[0+flags].equals("-forcespread")) {
      forcespread=true;
      flags++; argc--;
    } else if (args[0+flags].equals("-sharedouts")) {
      shareouts=true;
      flags++; argc--;
    } else if (args[0+flags].equals("-ndo")) {
      usedirectout=false;	// all connections are via single lines
      flags++; argc--;
    } else if (args[0+flags].equals("-sil")) {
      strictiterlimit=true;	// always stop at given test iter
      flags++; argc--;
    } else if (argc>1 && (args[0+flags].equals("-s") ||
	                  args[0+flags].equals("-sr"))) {
      outbit=args[1+flags];
      if (args[0+flags].equals("-sr")) {
        recordfile=new String(outbit+".record.txt");
      }
      save=1; flags+=2; argc-=2;
    } else {
      System.err.println("\nInvalid switch ("+args[0+flags]+")!\n"+usage);
      System.exit(1);
    }
  }

  if (argc<8 || argc==10 || argc==14) {
    System.err.println("\nmissing arguments!\n"+usage);
    System.exit(1);
  } else if (argc>16) {
    System.err.println("\ntoo many arguments ("+args.length+")!\n"+usage);
    System.exit(1);
  }

  String bitfile=args[0+flags];

  // we don't use these
  String[] inIOBs=StringSplitter.split(args[1+flags],";");
  String[] outIOBs=StringSplitter.split(args[2+flags],";");

  String[] inCLBsliceLEs=StringSplitter.split(args[3+flags],";");
  String[] outCLBsliceLEs=StringSplitter.split(args[4+flags],";");
  String minCLB=args[5+flags];
  String maxCLB=args[6+flags];
  String contentious=args[7+flags];

  String dchromfilelist=null;			// for non-dev only
  String dchromfile=null;
  String cytofile=null;
  int numIter=0;
  int ngrowthsteps=1;
  int numgenes=-1;
  long seed=System.currentTimeMillis(); 	// for morphogenesis only
  int[][] geneActivationMatrix=null;		//  "      "          "

  // this is for use with the non-developmental approach - for getting
  // the start locations for the build instructions
  LEcoord[] inoutlocs=new LEcoord[inCLBsliceLEs.length+outCLBsliceLEs.length];

  // need to ensure that all in/out CLB coords are in int,int,int,int format
  LEcoord le=null;
  for (int i=0; i<inCLBsliceLEs.length; i++) {
    le=new LEcoord(inCLBsliceLEs[i]);		// parse and store coords
    inCLBsliceLEs[i]=le.toIntString();		// store as string of ints
    inoutlocs[i]=le;
  }
  for (int i=0; i<outCLBsliceLEs.length; i++) {
    le=new LEcoord(outCLBsliceLEs[i]);		// parse and store coords
    outCLBsliceLEs[i]=le.toIntString();		// store as string of ints
    inoutlocs[i+inCLBsliceLEs.length]=le;
  }

  int[] mins=StringSplitter.str2ints(minCLB,",");
  int[] maxs=StringSplitter.str2ints(maxCLB,",");

  // KLUDGE alert!!! this is because we assumed that only using XCV50s
  // but now want to be able to support a larger CLB matrix, so
  // use XCV1000 to support these
  if (maxs[0]>TestInToOutRecurse.MaxXCV50Row ||
      maxs[1]>TestInToOutRecurse.MaxXCV50Col) {
    devname=TestInToOutRecurse.deviceName2; // assume XCV1000
  }

  MhwMatrix cells=null;
  JBits jbits=null;
  JBitsResources jbr=null;			// for recording set()'s

  if (argc==8) {				// just use supplied bitstream
    // create's jbits object and loads bitstream
    jbr=new JBitsResources(devname,contentious,bitfile);
    if (save>0) {
      if (verbose) { System.err.print("saving supplied circuit to "+outbit+" .. "); }
      jbr.writeBitstream(outbit);
      if (verbose) { System.err.println("Done."); }
    }
    jbits=jbr.getJBits();
  } else if (argc==9) {			// generate non-developmental
    if (verbose) {
      System.err.print("generating circuit ");
      if (recordfile!=null) {
        System.err.println("and recording set() instructions to file: "+recordfile+" ..");
      } else {
      System.err.println(".. ");
      }
    }

    int startrow=mins[0];
    int startcol=mins[1];
    int startslice=0;
    int startLE=0;

    BuildEhw ndehw;

    if (vgran==2) {
      ndehw=new BuildEhw(devname,bitfile,contentious,null,mins[0],mins[1],maxs[0],maxs[1],startrow,startcol,startslice,recordfile,debug);
    } else {
      ndehw=new BuildEhw(devname,bitfile,contentious,null,mins[0],mins[1],maxs[0],maxs[1],startrow,startcol,startslice,startLE,recordfile,debug);
    }

    dchromfilelist=args[8+flags];
    String[] dchromfiles=StringSplitter.split(args[8+flags],";");

    // for each decoded chromosome, execute its set of build instructions
    // using the in/out CLBs as the start location
    for (int i=0; i<dchromfiles.length; i++) {
      if (dchromfiles[i].equals("-") || dchromfiles[i].equals("")) {
	continue;
      }
      startrow=inoutlocs[i%inoutlocs.length].row;
      startcol=inoutlocs[i%inoutlocs.length].col;
      startslice=inoutlocs[i%inoutlocs.length].slice;
      startLE=inoutlocs[i%inoutlocs.length].LE;
      if (vgran==2) {
        if (verbose) { System.err.print("building circuit from instructions in "+dchromfiles[i]+" starting at "+startrow+","+startcol+","+startslice+" .. "); }
        ndehw.build(dchromfiles[i],startrow,startcol,startslice);
      } else {
        if (verbose) { System.err.print("building circuit from instructions in "+dchromfiles[i]+" starting at "+startrow+","+startcol+","+startslice+","+startLE+" .. "); }
        ndehw.build(dchromfiles[i],startrow,startcol,startslice,startLE);
      }
      if (verbose) { System.err.println("Done."); }
    }

    if (verbose) { System.err.println("finished generating circuit."); }

    if (save>0) {
      if (recordfile!=null) {
        // close the set() transcript file
        ndehw.closeRecordFile();
        if (verbose) { System.err.println("closed set() transcript file: "+recordfile+"."); }
      }
      if (verbose) { System.err.print("saving generated circuit to "+outbit+" .. "); }
      ndehw.writeBitstream(outbit);
      if (verbose) { System.err.println("Done."); }
    }
    jbits=ndehw.getJBits();
    jbr=ndehw.getJBitsResources();
  } else {					// morphogenesis
    dchromfile=args[8+flags];
    cytofile=args[9+flags];
    numIter=Integer.parseInt(args[10+flags]);
    ngrowthsteps=Integer.parseInt(args[11+flags]);
    if (args.length-flags==13) {
      seed=Long.parseLong(args[12+flags]);	// use supplied seed
    } else if (args.length-flags>13) {
      transcriptionrate=Integer.parseInt(args[12+flags]);
      npoly2gene=Double.parseDouble(args[13+flags]);
      polythreshold=Double.parseDouble(args[14+flags]);
      if (args.length-flags>15) {
        seed=Long.parseLong(args[15+flags]);	// use supplied seed
      }
    }

    // these are arbitrary values!
    int mglifespan=8;
    int tflifespan=8;
    int ageratefree=3;
    int ageratebound=5;
    int morphogenPropDelay=1;

    // create morphogenesis object
    if (verbose) { System.err.println("creating growable HW using seed "+seed+"...\n"); }

    // we only turn debug flag on here if want _extra_ debug info, as
    // morphogenesis system's debug generates _WAY_ too much data!!!
    cells=new MhwMatrix(seed,bitfile,devname,minCLB,maxCLB,
          inCLBsliceLEs,outCLBsliceLEs,contentious,dchromfile,cytofile, 
	  npoly2gene,polythreshold,transcriptionrate,mglifespan,tflifespan,
	  ageratefree,ageratebound,morphogenPropDelay,vgran,debug);

    numgenes=cells.numGenes();
    if (verbose) { System.err.println("chromosome contains "+numgenes+" genes...\n"); }

    jbr=cells.getJBitsResources();

    // to keep track of how many genes are currently active in each cell
    // if slice granularity, then still create as many rows as for LE gran
    // so that can use same matrix display routines as for LE states
    // but here we just double up on the activations
    geneActivationMatrix=new int[cells.getCellRows()*vgran][cells.getCellCols()];

    // if we are using the save option, the we save initial cct, even though
    // equivalent to the layout. this is for the benefit of the fitness test
    // executed by the GA - it will expect a bitstream produced, the best so
    // far being kept, even if it is actually just the layout! (it doesn't
    // realise this)
    if (save>0) {
      if (verbose) { System.err.print("saving initial circuit to "+outbit+"."+0+" .. "); }
      cells.writeBitstream(outbit+"."+0);
      if (verbose) { System.err.println("Done."); }

      if (recordfile!=null) {
        // we also log set() instructions to file
        jbr.recordToFile(recordfile);
        if (verbose) { System.err.println("recording set() instructions to file: "+recordfile+" .."); }
      }
    }
    jbits=cells.getJBits();
  }


  if (verbose) { System.err.println("creating circuit test...\n"); }
  TestInToOutRecurse test = new TestInToOutRecurse(jbr,minCLB,maxCLB,inCLBsliceLEs,outCLBsliceLEs,shareouts,usedirectout,debug);

  if (forcespread) { test.NEEDSMIDLAYERSPREAD=true; }

  DecimalFormat df = new DecimalFormat("0.0##");
 
  // if its a 1x1 CLB evolvable region then the constructor will default to
  // left to right, if this is not the case, we set the direction here
  // according to the IOB locations.
  if (test.minrow==test.maxrow && test.mincol==test.maxcol) { 
    // IOB sites are of form: SIDE,INDEX,SITE
    if (inIOBs[0].charAt(0)=='L') { 
      test.direction=test.LtoR; // left to right
    } else if (inIOBs[0].charAt(0)=='R') { 
      test.direction=test.RtoL; // right to left
    } else if (inIOBs[0].charAt(0)=='T') { 
      test.direction=test.TtoB; // top to bottom
    } else { // if (inIOBsites[0].charAt(0)=='B') { 
      test.direction=test.BtoT; // bottom to top
    }
  }

  // test this initial cct..
  double fitness=test.evalCCT();
  if (verbose) { System.err.println("initial fitness="+df.format(fitness)); }
  if (verbose) { System.err.println("connection states are..."); }
  if (verbose) { System.err.println(test.getStateMatrix()); }

  if (cells!=null && verbose) {
    for (int cr=0; cr<cells.getCellRows(); cr++) {
      for (int cc=0; cc<cells.getCellCols(); cc++) {
        if (vgran==1) { // LE granularity
          geneActivationMatrix[cr][cc]=cells.getActiveGeneCount(cr,cc);
        } else { // slice granularity, so double up on the rows
          geneActivationMatrix[cr*2][cc]=geneActivationMatrix[(cr*2)+1][cc]=cells.getActiveGeneCount(cr,cc);
        }
      }
    }
    System.err.println("active gene counts are...");
    System.err.println(test.getMatrix(geneActivationMatrix,false));
  }

  int giter=0;
  int maxfititer=0;		// iteration that highest fitness occured at
  double maxfitness=fitness;	// highest fitness achieved during growth
  double prevfit=fitness;
  // now do the growth steps if performing morphogenesis
  // if not strict iter limit and no more iter left, then still continue tests
  //  - if max fitness increased in last numIter/2 growth steps
  //  - or if fitness increasing
  for (int di=1; di<=numIter || (!strictiterlimit && maxfititer>0 && 
				(di<=maxfititer+((numIter+1)/2) ||
			         fitness>prevfit)); di++) {

    prevfit=fitness;
    giter=((di-1)*ngrowthsteps)+1;
    if (verbose) { System.err.println("\n\nentering growth phase "+giter+"..."); }
    cells.step(ngrowthsteps);
    giter+=ngrowthsteps-1;
    if (verbose) { System.err.println("completed growth phase "+giter+"..."); }
    fitness=test.evalCCT();
    if (verbose) { System.err.println("fitness="+df.format(fitness)+"        \t highest was "+df.format(maxfitness)+" at "+maxfititer); }
    if (verbose) { System.err.println("connection states are..."); }
    if (verbose) { System.err.println(test.getStateMatrix()); }

    if (verbose) {
      for (int cr=0; cr<cells.getCellRows(); cr++) {
        for (int cc=0; cc<cells.getCellCols(); cc++) {
	  if (vgran==1) { // LE granularity
	    geneActivationMatrix[cr][cc]=cells.getActiveGeneCount(cr,cc);
	  } else { // slice granularity, so double up on the rows
	    geneActivationMatrix[cr*2][cc]=geneActivationMatrix[(cr*2)+1][cc]=cells.getActiveGeneCount(cr,cc);
	  }
	}
      }
      System.err.println("active gene counts are...");
      System.err.println(test.getMatrix(geneActivationMatrix,false));
    }

    // if found a better cct, then record its fitness and when it occurred
    // and if save flag is set, then save it with growth iteration as ext
    if (fitness>maxfitness) {
      maxfitness=fitness; maxfititer=giter;
      if (save>0) {
        if (verbose) { System.err.print("saving circuit with highest tested fitness to "+outbit+"."+giter+" .. "); }
	cells.writeBitstream(outbit+"."+giter);
        if (verbose) { System.err.println("Done."); }
      }
    }
  }

  if (verbose && numIter>0) {
    System.err.println("\nfinished growth...\n");
  }

  if (recordfile!=null && numIter>0) {
    // close the set() transcript file
    jbr.closeRecordFile();
    if (verbose) { System.err.println("closed set() transcript file: "+recordfile+"."); }
  }

  // output highest fitness achieved, and when it occured
  if (verbose) {
    System.err.print("Highest fitness="+maxfitness);
    if (numIter>0) {
      System.err.print(" occurred at growth phase "+maxfititer); 
      System.err.print(" using seed "+seed); 
    }
    System.err.println();
  }
  
  System.out.print(maxfitness);
  if (numIter>0) {
    System.out.print(" @ "+maxfititer+" : "+seed+" ; "+numgenes); 
  }
  System.out.println();

  if (verbose) { System.err.println("\nDone. exiting..."); }

} // end main




TestInToOutRecurse(JBitsResources jbr, String mincoord, String maxcoord, String[] inClbLEs, String[] outClbLEs, boolean shareouts, boolean usedirectout)
{
  this(jbr,mincoord,maxcoord,inClbLEs,outClbLEs,shareouts,usedirectout,false);
}

TestInToOutRecurse(JBitsResources jbr, String mincoord, String maxcoord, String[] inClbLEs, String[] outClbLEs, boolean shareouts, boolean usedirectout, boolean vb)
{
  hex=new char[] { '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F' };
  VERBOSE=vb;
  SHAREDOUTS=shareouts;
  USEDIRECTOUT=usedirectout;
 
  jbitsres=jbr;
  jbits=jbr.getJBits();

  LEcoord coord=new LEcoord(mincoord+",0,0");
  minrow=coord.row;
  mincol=coord.col;

  coord=new LEcoord(maxcoord+",0,0");
  maxrow=coord.row;
  maxcol=coord.col;

  inputwidth=inClbLEs.length;
  outputwidth=outClbLEs.length;

  // convert to LEcoord types and store locations of IO CLBs
  inlocs=new LEcoord[inClbLEs.length];
  for (int i=0; i<inClbLEs.length; i++) { inlocs[i]=new LEcoord(inClbLEs[i]); }
  outlocs=new LEcoord[outClbLEs.length];
  for (int i=0;i<outClbLEs.length;i++){ outlocs[i]=new LEcoord(outClbLEs[i]); }

  instates=new int[4*inputwidth]; // 4 LEs per entry CLB
  outstates=new int[4*outputwidth];

  // each LE in evolvable region is given a connectivity state
  // here we assign 2 slices to each col, and 2 LEs to each row
  int numrows=maxrow-minrow+1;
  int numcols=maxcol-mincol+1;
  stateMatrix=new int[numrows*2][numcols*2];
  // input line test state, to stop lines being tested more than once
  lineTestedMatrix=new int[numrows*2][numcols*2];

  // now determine what the direction of growth should be ...
  direction=-1;

  // if region is 1x1, then we can't determine direction without more info
  // such as IOB edges, which aren't passed here. so we assume left to right
  // and leave it to caller to set it otherwise. it will have no effect here
  // anyway, as the only thing here that depends on it is calculating the
  // number of layers and entries, both of which will be interchangeable
  // as both have a value of 1

  // determine direction with a heuristic -
  // use the IOB edges to determine direction
  if (minrow==maxrow && mincol==maxcol) {
    direction=LtoR; // left to right

  // if region is 1xX then assume LtoR or RtoL
  } else if (maxrow==minrow) {
    if (inlocs[0].col==mincol && outlocs[0].col==maxcol) {
      direction=LtoR; // left to right
    } else if (inlocs[0].col==maxcol && outlocs[0].col==mincol) {
      direction=RtoL; // right to left
    }

  // if region is Yx1 then assume BtoT or TtoB
  } else if (maxcol==mincol) {
    if (inlocs[0].row==minrow && outlocs[0].row==maxrow) {
      direction=BtoT; // bottom to top
    } else if (inlocs[0].row==maxrow && outlocs[0].row==minrow) {
      direction=TtoB; // top to bottom
    }
  
  // otherwise use the following, which assumes region of at least 2x2
  } else {
    if (inlocs[0].row==minrow && outlocs[0].row==maxrow) {
      direction=BtoT; // bottom to top
    } else if (inlocs[0].row==maxrow && outlocs[0].row==minrow) {
      direction=TtoB; // top to bottom
    } else if (inlocs[0].col==mincol && outlocs[0].col==maxcol) {
      direction=LtoR; // left to right
    } else if (inlocs[0].col==maxcol && outlocs[0].col==mincol) {
      direction=RtoL; // right to left
    }
  }

  if (direction<0) {
    System.err.println("TestDelayInToOut: cannot divide evolvable region into layers for testing signal\npropogation! in and out CLBs must be on opposite side of evolvable region");
    System.exit(1);
  }

  // now that know what the axis of growth is, we can determine the
  // number of layers and entries per layer
  if (direction==LtoR || direction==RtoL) {
    numlayers=maxrow-minrow+1;
    numentries=maxcol-mincol+1;
  } else {
    numlayers=maxcol-mincol+1;
    numentries=maxrow-minrow+1;
  }

  // fitness is 0 until circuit has been evaluated
  fitness=0.0;

}


// this shows the connection state (from inputs) of LEs in the evolvable area
// state is displayed as a hexidecimal digit 1..F, 0's are displayed as a '.'
// note each CLB takes up 2 cols (slices) and 2 rows (Y/X) in the probe matrix
// also note that this can't fit more than 15 cols of CLBs on a 80 char line,
// or more than 12 rows of CLBs (excluding horizontal axis) on screen with a
// 24 line terminal
public String getStateMatrix() { return getMatrix(stateMatrix,true); }



public String getMatrix(int[][] matrix, boolean noZeros)
{
  String pmatrix;
  int numrows=maxrow-minrow+1;
  int numcols=maxcol-mincol+1;

//System.err.println("matrix is "+numrows+"x"+numcols);
  // make horizontal axis numbering and horizontal border
  String taxis="    ";
  String baxis="    ";
  String hborder="    ";
  for (int j=0; j<numcols; j++) {
    taxis=taxis+" 0 1 ";			// top axis shows slice number
    baxis=baxis+((j<10) ? "  " : " ")+j+"  ";	// bottom gives CLB
    hborder=hborder+"-----";
  }

  String vborder="";
  for (int j=0; j<numrows; j++) { vborder=vborder+"||"; }

  String tborder=hborder;
  String bborder=hborder;
  String lborder=vborder;
  String rborder=vborder;

  // mark input/output locs
  StringBuffer iborder;
  StringBuffer oborder;

  // copy the border strings into buffers to be modified at offsets of IO
  if (direction==LtoR || direction==RtoL) {
    iborder=new StringBuffer(vborder);
    oborder=new StringBuffer(vborder);
  } else {
    iborder=new StringBuffer(hborder);
    oborder=new StringBuffer(hborder);
  }

  // modify the chars in borders at offsets of IO clbs
  int offset;
  char ch;
  for (int i=0; i<inlocs.length; i++) {
    if (direction==LtoR || direction==RtoL) {
      offset=(inlocs[i].row-minrow)*2+inlocs[i].LE;
    } else {
      offset=(inlocs[i].col-mincol)*2+inlocs[i].slice;
      offset=(offset*2)+5;			// 2 chars per loc, start at 5
    }
    ch=iborder.charAt(offset);
    if (ch=='-' || ch=='|') {
      iborder.setCharAt(offset,'I');
    } else { 					// 2 inputs map to same loc
      iborder.setCharAt(offset,'H');
    }
  }

  for (int i=0; i<outlocs.length; i++) {
    if (direction==LtoR || direction==RtoL) {
      offset=(outlocs[i].row-minrow)*2+outlocs[i].LE;
    } else {
      offset=(outlocs[i].col-mincol)*2+outlocs[i].slice;
      offset=(offset*2)+5;			// 2 chars per loc, start at 5
    }
    ch=oborder.charAt(offset);
    if (ch=='-' || ch=='|') {
      oborder.setCharAt(offset,'O');
    } else { 					// 2 inputs map to same loc
      oborder.setCharAt(offset,'%');
    }
  }

  // assign the modified borders back to their respective edge borders
  if (direction==LtoR || direction==RtoL) {
    lborder=new String((direction==LtoR)?iborder:oborder);
    rborder=new String((direction==LtoR)?oborder:iborder);
  } else {
    tborder=new String((direction==TtoB)?iborder:oborder);
    bborder=new String((direction==TtoB)?oborder:iborder);
  }

  pmatrix="\n"+taxis+"\n"+tborder;
  String row;
  int r;
  // now do each row of the matrix starting at the top CLB row
  // note that there are 2 rows of LEs for each row of CLBs
  for (int i=(numrows*2)-1; i>=0; i--) { // row 0 is at bottom
    row="\n";
    // give CLB index for even LEs
    if (i%2==0) {
      if (i/2<10) { row=row+" "; }
      row=row+(i/2)+" "+lborder.charAt(i);
    } else {
      row=row+"   "+lborder.charAt(i);
    }

    for (int j=0; j<numcols*2; j++) { // for each slice
      // each row contains only 1 of the LEs (G-Y or F-X)
      r=matrix[i][j];
      if (j%2==0) { row=row+" "; }
      if (r<16) {
	if (noZeros && r==0) {
	  row=row+".";			// easier to spot changes then
	} else {
          row=row+hex[r];
	}
      } else {
        row=row+"*";
      }
      if (isInloc(i,j)) {
        row=row+"i";
      } else if (isOutloc(i,j)) {
	row=row+"o";
      } else {
	row=row+" ";
      }
    }
    row=row+rborder.charAt(i);
    row=row+" "+((i%2==0) ? "Y" : "X"); // right border shows if X or Y LE
    pmatrix=pmatrix+row;
  }

  pmatrix=pmatrix+"\n"+bborder+"\n"+baxis+"\n";

  return pmatrix;
}


// there are 2 slices to each col, and 2 LEs to each row
boolean isInloc(int mr, int mc)
{
//System.err.println("isInloc("+mr+","+mc+") .. ");
//System.err.print("instates[]={"); for (int i=0; i<instates.length; i++) { System.err.print(instates[i]+" "); }; System.err.println("}");

  int row,col,slice,le;
  for (int i=0; i<inlocs.length; i++) {
//System.err.println("inlocs["+i+"]="+inlocs[i]);
    row=inlocs[i].row;
    col=inlocs[i].col;
    slice=inlocs[i].slice;
    le=inlocs[i].LE;
    if (((int)mr/2)+minrow==row && ((int)mc/2)+mincol==col) { // this CLB
      if (!SHAREDOUTS) {
	// in slim model in loc is a specific LEs
	if (slice==mc%2 && le==mr%2) { return true; }
      } else {
//System.err.println("checking slice "+(mc%2)+", le "+(mr%2)+" against instates["+((i*4)+((mc%2)*2)+(mr%2))+"]="+instates[(i*4)+((mc%2)*2)+(mr%2)]);
	// in loc is any LE in this CLB that is connected to input
	if (instates[(i*4)+((mc%2)*2)+(mr%2)]>0) { return true; }
      }
    }
  }

  return false;
}

// there are 2 slices to each col, and 2 LEs to each row
boolean isOutloc(int mr, int mc)
{
  int row,col,slice,le;
  for (int i=0; i<outlocs.length; i++) {
    row=outlocs[i].row;
    col=outlocs[i].col;
    slice=outlocs[i].slice;
    le=outlocs[i].LE;
    if (((int)mr/2)+minrow==row && ((int)mc/2)+mincol==col) { // this CLB
      if (!SHAREDOUTS) {
	// in slim model in loc is a specific LEs
	if (slice==mc%2 && le==mr%2) { return true; }
      } else {
	// in loc is any LE in this CLB that connects to output
	if (outstates[(i*4)+((mc%2)*2)+(mr%2)]==0xf) { return true; }
      }
    }
  }

  return false;
}


// for next cct to be tested
public void resetState()
{
  int numrows=maxrow-minrow+1;
  int numcols=maxcol-mincol+1;

  for (int i=0; i<numrows*2; i++) {
    for (int j=0; j<numcols*2; j++) {
      stateMatrix[i][j]=0;
      lineTestedMatrix[i][j]=0;
    }
  }

  for (int i=0; i<instates.length; i++) { instates[i]=0; }
  for (int i=0; i<outstates.length; i++) { outstates[i]=0; }
}

// there are 2 slices to each col, and 2 LEs to each row
void setState(LEcoord loc, int state)
{
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  stateMatrix[(r*2)+loc.LE][(c*2)+loc.slice]=state;
}


int getState(LEcoord loc)
{
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  return stateMatrix[(r*2)+loc.LE][(c*2)+loc.slice];
}

// linenum is 1-4
void setInLineIsTested(LEcoord loc, int linenum)
{
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  lineTestedMatrix[(r*2)+loc.LE][(c*2)+loc.slice] |= (int)Math.round(Math.pow(2,linenum-1));
}

boolean isInLineTested(LEcoord loc, int linenum)
{
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  return lineTestedMatrix[(r*2)+loc.LE][(c*2)+loc.slice] >= (int)Math.round(Math.pow(2,linenum-1));
}


// idx indicates which of the (array of) input CLBs to retrieve the state of
// loc is only needed if using the slim model, otherwise it is ignored
public int getBestInState(LEcoord loc, int inidx)
{
  // if using slim model, then there can only be 1 LE that receives
  // the input signals
  if (!SHAREDOUTS) { return getState(loc); }

  // otherwise there could be up to 4 LEs in the CLB getting this input
  // so for fitness evaluation we use the best (most fully connected)

  int bs=0;
  for (int i=0; i<4; i++) { 
    if (instates[(inidx*4)+i]>bs) { bs=instates[(inidx*4)+i]; }
  }

  return bs;
}


// idx indicates which of the (array of) input CLBs to retrieve the state of
// loc is only needed if using the slim model, otherwise it is ignored
public int getBestOutState(LEcoord loc, int outidx)
{
  // if using slim model, then there can only be 1 LE that provides
  // the output signals
  if (!SHAREDOUTS) { return getState(loc); }

  // otherwise it could be any one of the 4 LEs in the CLB neighbouring
  // the output routing CLB that provides the output signal
  // so for fitness evaluation we use the best (most fully connected)

  int bs=0;
  for (int i=0; i<4; i++) { 
    if (outstates[(outidx*4)+i]>bs) { bs=outstates[(outidx*4)+i]; }
  }

  return bs;
}



// for each input, traverse circuit to reach all connected LEs,
// and mark these on matrix. for initial, check that connected to
// a single from the neighbouring routed CLB
public double evalCCT()
{
  resetState();
  LEcoord[] nbours=null;
  LEcoord routingLE=null;
  LEcoord le=null;
  int r, c, s, l;

  // check each input loc to see if its connected to the input bus
  for (int i=0; i<inlocs.length; i++) {
    if (!SHAREDOUTS) { // slim model
      if (VERBOSE) { System.err.println("checking inlocs["+i+"]: "+inlocs[i]+".."); }
      // there is only 1 possible entry point (LE) in slim model
      // check each in CLB single connecting to the given LUT input
      for (int j=1; j<=4; j++) {
        if (isLUTlineConnectedToInputBus(inlocs[i],j)) {
          if (VERBOSE) { System.err.println("inlocs["+i+"] is connected via LUT line "+j+"!"); }
          setState(inlocs[i],1);
          traverseCCT(inlocs[i],j);
        }
      }
    } else { // full model
      // routing CLB is neighbour, and uses one slice of the CLB, 
      routingLE=new LEcoord(inlocs[i]);
      if (direction==LtoR) { routingLE.col--; }
      else if (direction==RtoL) { routingLE.col++; }
      else if (direction==BtoT) { routingLE.row--; }
      else { routingLE.row++; }

      // there are up to 4 possible entry (LE) locations for each input:
      // S0G-Y, S0F-X, S1G-Y, S1F-X. set the states of each of these
      // in instates[] with 0 states also used to indicate not an entry loc
      for (int j=0; j<4; j++) { instates[(i*4)+j]=0; }

      if (VERBOSE) { System.err.println("checking inlocs["+i+"]: "+inlocs[i].row+","+inlocs[i].col+" from routing CLB.."); }
      // there are several LEs that routing CLB can connect to, so
      // check the neighbouring routing CLB to see which LEs it connects to
      // if it is slice 0, then routing CLB connects to OUT lines 0-3
      // if it is slice 1, then routing CLB connects to OUT lines 4-7
      // and then from these it connects to singles towards evolvable region
      for (int out=0+(routingLE.slice*4); out<4+(routingLE.slice*4); out++) {
	nbours=OUTtoSingleConnectedLE(routingLE,out);
	if (nbours!=null && nbours.length>0) {
	  for (int j=0; j<nbours.length; j++) {
	    traverseCCT(nbours[j],nbours[j].line);
	    s=nbours[j].slice;
	    l=nbours[j].LE;
            // this may set the same entry multiple times, inefficient but easy
             instates[(i*4)+(s*2)+l]=getState(nbours[j]);
//System.err.println("setting instates["+((i*4)+(s*2)+l)+"] to "+instates[(i*4)+(s*2)+l]+" ");
	  }
	}
      }

    }

    if (VERBOSE) { System.err.println(); }
  }

  // check each output loc to see if its connected to the output bus
  // which, in addition to the usual connection requirements for other LEs
  // also requires one of the outmux singles to be in the correct direction
  for (int i=0; i<outlocs.length; i++) {
    if (!SHAREDOUTS) { // slim model
      if (getState(outlocs[i])<0xf) { // we only need to check fully connected
        continue;			    // LEs, to add additional constraint
      }
      if (!isOutlineConnectedToOutputBus(outlocs[i])) {
        setState(outlocs[i],7);			// not connected to next CLB
        if (VERBOSE) { System.err.println("outlocs["+i+"] output not connected to output routing CLB - state reset to 7!"); }
      }
      if (VERBOSE) { System.err.println(); }
    } else {
      // there are 4 possible LEs that could connect to each of the OUT bus
      // lines on the CLB, we need one of these LEs to connect to an OUT bus
      // line, that then drives a single in the direction of the output
      // routing CLB

//System.err.println("checking all LEs in outlocs["+i+"] at: "+outlocs[i].row+","+outlocs[i].col+"..");
      le=new LEcoord(outlocs[i]);
      for (int j=0; j<4; j++) {
	le.slice=j/2;
	le.LE=j%2;
	outstates[(i*4)+j]=getState(le);
	// if LE isn't connected to output then modify its state in the
	// outstates array (for choosing best), but not in the state matrix
        if (outstates[(i*4)+j]==0xf) {
	  outstates[(i*4)+j]=(isOutlineConnectedToOutputBus(le)) ? 0xf : 7;
	}
      }
      if (VERBOSE) { System.err.println(); }
    }


  }

  return calcFitness();
}


// logic element state is given by adding the following:
//	- LUT input(s) connected to neighbour output(s)		=> 1
//	- LUT function uses a connected input		     }	=> 1 (or 2)
// (opt.) LUT's output is effected by a connected input	     }	=> 2 (or 0)
//	- LUT output connected to any OUT lines			=> 4
//	- OutToSingle for one of the out lines set to on has
//	  a single line connected to LUT input in neighbour	=> 8
// giving the LE a value from 0 .. F hex

// lutinput line is 1-4, which is which line the input is connecting to
// so can tell whether LUT fn ignores this line
void traverseCCT(LEcoord loc, int lutinputline)
{
  int state=getState(loc);
  
  // if this input line has already been evaluated (a line from another LE
  // connected here, but this input isn't used by the LUT), or another
  // input line evaluation has proceeded to a valid LUT FN
  // then nothing more to do for this LE
  if (isInLineTested(loc,lutinputline) || state>1) { return; }

  if (VERBOSE) { System.err.print("\nchecking state of "+loc+" from connected input line "+lutinputline+" .. "); }

  state=1;	// has to be 1 (an input line connected) to have reached here
  setState(loc,state);
  setInLineIsTested(loc,lutinputline); // mark this line as tested

  int[] outlines=null;
  LEcoord[] nbours=null;
  if (!LUTignoresInputs(loc,lutinputline)) {
    if (VERBOSE) { System.err.print("LUT fn uses input.. "); }
    state+=2;
    setState(loc,state);

    // WARNING KLUDGE ALERT!!!
    // even though we know the LUT uses the appropriate input line in its Fn
    // however, it may have unconnected inputs that influence the LUT output
    // thus, we optionally incorporate some extra information if available
    // (from running with simulator) to test if output signals change when
    // varying signals are applied to the inputs. If no signal changes occur
    // on the output, then we can deduce the LUT functionally ignores its
    // inputs, so go no further
    if (signalChangeMatrix!=null && !LUThasChangingSignalOutput(loc)) {
      state=2;
      setState(loc,state);
      if (VERBOSE) { System.err.println("but output has no signal change!"); }
      return;
    }
    if (VERBOSE) { System.err.println(); }

    outlines=connectedOUTbuses(loc);
    if (outlines!=null) {
      state+=4;
      setState(loc,state);
      for (int out=0; out<outlines.length; out++) {
        if (VERBOSE) { System.err.println("FF -> OUT "+outlines[out]+" .. "); }
	nbours=OUTtoSingleConnectedLE(loc,outlines[out]);
	if (nbours!=null && nbours.length>0) {
	  if (VERBOSE) {
            System.err.println("single(s) connected to nbour(s).. ");
            for (int k=0; k<nbours.length;k++) { System.err.print(nbours[k]+" "); } System.err.println();
	  }
	  state=0xF;
          setState(loc,state);
	  for (int i=0; i<nbours.length; i++) {
	    // don't go outside evolvable region
	    if (nbours[i].row>=minrow && nbours[i].row<=maxrow &&
		nbours[i].col>=mincol && nbours[i].col<=maxcol) {
              if (VERBOSE) { System.err.println("connection from "+loc+" to "+nbours[i]+" .. "); }
	      traverseCCT(nbours[i],nbours[i].line);
	    }
	  }
	}
      }
    }
  }
}


boolean LUTignoresInputs(LEcoord loc, int lutinputline)
{
  String lutstr=new String("LUT.SLICE"+loc.slice+"_"+((loc.LE==0)?"G":"F"));
  int[] lutval=jbitsres.get(loc.row,loc.col,"LUT",lutstr);
//System.err.print("(testing "+lutstr+" line "+lutinputline+" [");
//for (int i=0;i<16;i++) {System.err.print(lutval[i]);}
//System.err.print("] ");
  boolean ignor=true;
  for (int i=0; i<8 && ignor; i++) { // there are 8 equivalences to check
    if (lutinputline==1) {
      ignor=ignor && lutval[i*2]==lutval[(i*2)+1];
//System.err.print("lutval["+(i*2)+"]==lutval["+((i*2)+1)+"]="+ignor+" ");
    } else if (lutinputline==2) {
      ignor=ignor && lutval[(i%2)+((i/2)*4)]==lutval[(i%2)+((i/2)*4)+2];
    } else if (lutinputline==3) {
      ignor=ignor && (lutval[(i%4)+((i/4)*8)]==lutval[(i%4)+((i/4)*8)+4]);
    } else { // if (lutinputline==4)
      ignor=ignor && (lutval[i]==lutval[i+8]);
//System.err.print("lutval["+(i)+"]==lutval["+(i+8)+"]="+ignor+" ");
    }
  }
//System.err.print("ignor="+ignor+")");

  // if all 8 comparisons come out to be true, then line is ignored
  return ignor;
}


boolean LUThasChangingSignalOutput(LEcoord loc)
{
  // this shouldn't be called if there is no signal change matrix used
  // if it is, just return whether LUT passes inputs in some manner
  if (signalChangeMatrix==null) {
    return (getState(loc)>1); // LUT passes input
  }

  // otherwise return whether or not there was any change in output signal
  int r=loc.row-minrow;
  int c=loc.col-mincol;
  return (signalChangeMatrix[(r*2)+loc.LE][(c*2)+loc.slice]>0);
}


// Note we don't know if using synchronous-only or mixed signal circuits
// so need to test both registere dand unregistered outputs, but not
// slice control/carry outputs (YB and XB)
int[] connectedOUTbuses(LEcoord loc)
{
  if (!SHAREDOUTS) {			// slim model
    return connectedUnsharedOUTbuses(loc);
  }

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

  if (VERBOSE) { System.err.print(loc); }
  if (ocnt>0) {
    if (VERBOSE) { System.err.print(" -> "); }
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
  } else {
    if (VERBOSE) { System.err.println(" no connections to OUT bus!"); }
  }

  return outs;
}


// this is for the slim model, where LEs have dedicated output bus lines
// Note that we check both registered and unregisted LE outputs
// even though in slim tests we have only been using registered outputs;
// this allows us to use a 'slim'-like model, with dedicated out bus lines
// with mixed signal circuits, if desired

int[] connectedUnsharedOUTbuses(LEcoord loc)
{
  int row=loc.row;
  int col=loc.col;
  int[] val=null;
  int o1=-1, o2=-1;
  int[] outs=null;

  if (loc.slice==0 && loc.LE==0) {      // S0_Y[Q] -> OUT0,OUT1
    val=jbitsres.getbits(row,col,"OUT0.OUT0");
    if (jbitsres.isEquivVal(row,col,val,"OUT0.S0_YQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT0.S0_Y")) { o1=0;}
    val=jbitsres.getbits(row,col,"OUT1.OUT1");
    if (jbitsres.isEquivVal(row,col,val,"OUT1.S0_YQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT1.S0_Y")) { o2=1;}
  } else if (loc.slice==0 && loc.LE==1) { // S0_X[Q] -> OUT2,OUT5
    val=jbitsres.getbits(row,col,"OUT2.OUT2");
    if (jbitsres.isEquivVal(row,col,val,"OUT2.S0_XQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT2.S0_X")) { o1=2;}
    val=jbitsres.getbits(row,col,"OUT5.OUT5");
    if (jbitsres.isEquivVal(row,col,val,"OUT5.S0_XQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT5.S0_X")) { o2=5;}
  } else if (loc.slice==1 && loc.LE==0) { // S1_Y[Q] -> OUT4,OUT6
    val=jbitsres.getbits(row,col,"OUT4.OUT4");
    if (jbitsres.isEquivVal(row,col,val,"OUT4.S1_YQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT4.S1_Y")) { o1=4;}
    val=jbitsres.getbits(row,col,"OUT6.OUT6");
    if (jbitsres.isEquivVal(row,col,val,"OUT6.S1_YQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT6.S1_Y")) { o2=6;}
  } else { // (loc.slice==1 && loc.LE==1) // S1_X[Q] -> OUT3,OUT7
    val=jbitsres.getbits(row,col,"OUT3.OUT3");
    if (jbitsres.isEquivVal(row,col,val,"OUT3.S1_XQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT3.S1_X")) { o1=3;}
    val=jbitsres.getbits(row,col,"OUT7.OUT7");
    if (jbitsres.isEquivVal(row,col,val,"OUT7.S1_XQ") ||
	jbitsres.isEquivVal(row,col,val,"OUT7.S1_X")) { o2=7;}
  }

  if (VERBOSE) { System.err.print(loc); }

  if (o1>-1) {
    if (VERBOSE) { System.err.print("OUT"+o1+" "); }
    if (VERBOSE) { System.err.print(" -> "); }
    if (o2>-1) {
      outs=new int[2];
      outs[1]=o2;
      if (VERBOSE) { System.err.print("OUT"+o2+" "); }
    } else {
      outs=new int[1];
    }
    outs[0]=o1; 
  } else if (o2>-1) { // but o1<0
    if (VERBOSE) { System.err.print(" -> "); }
    outs=new int[1];
    outs[0]=o2;
    if (VERBOSE) { System.err.print("OUT"+o2+" "); }
  } else {
    if (VERBOSE) { System.err.println(" no connections to OUT bus!"); }
  }

  return outs;
}


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
    if (USEDIRECTOUT) {
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
    if (USEDIRECTOUT) {
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
    if (USEDIRECTOUT) {
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
    }

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
    if (USEDIRECTOUT) {
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


// this is used to check whether the LE that corresponds to an output cell
// is connected to the input of the neighbouring routing (to output IOB) CLB
// note that this assumes that the LE's output is connected to at least
// one OUT bus line in the CLB
// Note that we check both registered and unregisted LE outputs
boolean isOutlineConnectedToOutputBus(LEcoord loc)
{
  int row=loc.row;
  int col=loc.col;
  int slice=loc.slice;
  int le=loc.LE;
  String lestr;
  String lerstr;
  int[] val=null;
  boolean connected=false;
  LEcoord[] nbours=null;
  int o;

//System.err.print("isOutlineConnectedToOutputBus: ");
  if (!SHAREDOUTS) {			// slim model
//System.err.print("(slim): ");
    if (slice==0 && le==0) { // S0_Y[Q] uses out bus lines 0 & 1
      val=jbitsres.getbits(row,col,"OUT0.OUT0");
      if (jbitsres.isEquivVal(row,col,val,"OUT0.S0_YQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT0.S0_Y")) {
	o=0;
//System.err.print("out0 ");
      } else {
	o=1;
//System.err.print("out1 ");
      }
      nbours=OUTtoSingleConnectedLE(loc,o);
      if (hasDirNbour(loc,direction,nbours)) {
        connected=true;
//System.err.print("connected!");
      }
    } else if (slice==0 && le==1) { // S0_X[Q] uses out bus lines 2 & 5
      val=jbitsres.getbits(row,col,"OUT2.OUT2");
      if (jbitsres.isEquivVal(row,col,val,"OUT2.S0_XQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT2.S0_X")) {
	o=2;
//System.err.print("out2 ");
      } else {
	o=5;
//System.err.print("out5 ");
      }
      nbours=OUTtoSingleConnectedLE(loc,o);
      if (hasDirNbour(loc,direction,nbours)) {
        connected=true;
//System.err.print("connected!");
      }
    } else if (slice==1 && le==0) { // S1_Y[Q] uses out bus lines 4 & 6
      val=jbitsres.getbits(row,col,"OUT4.OUT4");
      if (jbitsres.isEquivVal(row,col,val,"OUT4.S1_YQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT4.S1_Y")) {
	o=4;
//System.err.print("out4 ");
      } else {
	o=6;
//System.err.print("out6 ");
      }
      nbours=OUTtoSingleConnectedLE(loc,o);
      if (hasDirNbour(loc,direction,nbours)) {
        connected=true;
//System.err.print("connected!");
      }
    } else { // slice==1 && le==1   // S1_X[Q] uses out bus lines 3 & 7
      val=jbitsres.getbits(row,col,"OUT3.OUT3");
      if (jbitsres.isEquivVal(row,col,val,"OUT3.S1_XQ") ||
	  jbitsres.isEquivVal(row,col,val,"OUT3.S1_X")) {
	o=3;
//System.err.print("out3 ");
      } else {
	o=7;
      }
      nbours=OUTtoSingleConnectedLE(loc,o);
      if (hasDirNbour(loc,direction,nbours)) {
        connected=true;
//System.err.print("connected!");
      }
    }
//System.err.println();
  } else { // out bus lines are shared, so need to check each
    lerstr=new String("S"+slice+"_"+((le==0)?"Y":"X")+"Q");
    lestr=new String("S"+slice+"_"+((le==0)?"Y":"X"));
    for (int i=0; i<8; i++) {
      val=jbitsres.getbits(row,col,"OUT"+i+".OUT"+i);
      // check if OUT line is connected to the LE output
      if (jbitsres.isEquivVal(row,col,val,"OUT"+i+"."+lerstr) ||
	  jbitsres.isEquivVal(row,col,val,"OUT"+i+"."+lestr)) {
//System.err.print("out"+i+" ");
        nbours=OUTtoSingleConnectedLE(loc,i);
        // check if OUT line connects to a single in the right direction
        if (hasDirNbour(loc,direction,nbours)) {
          connected=true;
//System.err.print("connected!");
	  break;
        }
      }
    }
//System.err.println();
  }

  return connected;
}


// we check for a neighbour (1 is enough) that is in the desired direction
boolean hasDirNbour(LEcoord loc, int dir, LEcoord[] nbours)
{
  int row=loc.row;
  int col=loc.col;
  int nrow,ncol;
  boolean connected=false;

  for (int i=0; i<nbours.length; i++) {
    nrow=nbours[i].row;
    ncol=nbours[i].col;
//System.err.print("["+nrow+","+ncol+"]");
    if ((dir==LtoR && ncol==col+1) ||
        (dir==RtoL && ncol==col-1) ||
        (dir==TtoB && nrow==row-1) ||
        (dir==BtoT && nrow==row+1)) {
      connected=true;
      break;
    }
  }

  return connected;
}


boolean isLUTlineConnectedToInputBus(LEcoord loc, int lutline)
{
  boolean connected=false;
  int row,col,slice,le;
  int[] val;
  row=loc.row;
  col=loc.col;
  slice=loc.slice;
  le=loc.LE;

  if (direction==LtoR) {		// inputs from WEST
//System.err.print("isLUTlineConnectedToInputBus: dir==LtoR; ");
    if (slice==0 && le==0) {		// S0G1-4
//System.err.print("S0G");
      if (lutline==1) {
//System.err.print("1");
        val=jbitsres.getbits(row,col,"S0G1.S0G1");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0G1.OUT_WEST1");
        }
        connected=connected ||
        	  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_WEST5") ||
	          jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_WEST17") ||
	          jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_WEST18");
      } else if (lutline==2) {
//System.err.print("2");
        val=jbitsres.getbits(row,col,"S0G2.S0G2");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0G2.OUT_WEST0");
        }
        connected=connected ||
	          jbitsres.isEquivVal(row,col,val,"S0G2.SINGLE_WEST2") ||
	          jbitsres.isEquivVal(row,col,val,"S0G2.SINGLE_WEST20");
      } else if (lutline==3) {
//System.err.print("3");
        val=jbitsres.getbits(row,col,"S0G3.S0G3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_WEST6") ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_WEST8") ||
	          jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_WEST15") ||
	          jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_WEST23");
      } else { // (lutline==4)
//System.err.print("4");
        val=jbitsres.getbits(row,col,"S0G4.S0G4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_WEST3") ||
	          jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_WEST11") ||
	          jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_WEST14");
      }
    } else if (slice==0 && le==1) {	// S0F1-4
//System.err.print("S0F");
      if (lutline==1) {
        val=jbitsres.getbits(row,col,"S0F1.S0F1");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0F1.OUT_WEST1");
        }
        connected=connected ||
        	  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_WEST5") ||
	          jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_WEST17") ||
	          jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_WEST18");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S0F2.S0F2");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0F2.OUT_WEST0");
        }
        connected=connected ||
	          jbitsres.isEquivVal(row,col,val,"S0F2.SINGLE_WEST2") ||
	          jbitsres.isEquivVal(row,col,val,"S0F2.SINGLE_WEST20");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S0F3.S0F3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_WEST6") ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_WEST8") ||
	          jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_WEST15") ||
	          jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_WEST23");
      } else { // (lutline==4)
        val=jbitsres.getbits(row,col,"S0F4.S0F4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_WEST3") ||
	          jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_WEST11") ||
	          jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_WEST14");
      }
    } else if (slice==1 && le==0) {	// S1G4-1
//System.err.print("S1G");
      if (lutline==4) {
        val=jbitsres.getbits(row,col,"S1G4.S1G4");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1G4.OUT_WEST1");
        }
        connected=connected ||
        	  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_WEST5") ||
	          jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_WEST17") ||
	          jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_WEST18");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S1G3.S1G3");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1G3.OUT_WEST0");
        }
        connected=connected ||
	          jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_WEST2") ||
	          jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_WEST20");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S1G2.S1G2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_WEST6") ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_WEST8") ||
	          jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_WEST15") ||
	          jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_WEST23");
      } else { // (lutline==1)
        val=jbitsres.getbits(row,col,"S1G1.S1G1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_WEST3") ||
	          jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_WEST11") ||
	          jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_WEST14");
      }
    } else { // (slice==1 && le==1)	// S1F4-1
//System.err.print("S1F");
      if (lutline==4) {
//System.err.print("4");
        val=jbitsres.getbits(row,col,"S1F4.S1F4");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1F4.OUT_WEST1");
        }
        connected=connected ||
        	  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_WEST5") ||
	          jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_WEST17") ||
	          jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_WEST18");
      } else if (lutline==3) {
//System.err.print("3");
        val=jbitsres.getbits(row,col,"S1F3.S1F3");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1F3.OUT_WEST0");
        }
        connected=connected ||
	          jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_WEST2") ||
	          jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_WEST20");
      } else if (lutline==2) {
//System.err.print("2");
        val=jbitsres.getbits(row,col,"S1F2.S1F2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_WEST6") ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_WEST8") ||
	          jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_WEST15") ||
	          jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_WEST23");
      } else { // (lutline==1)
//System.err.print("1");
        val=jbitsres.getbits(row,col,"S1F1.S1F1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_WEST3") ||
	          jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_WEST11") ||
	          jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_WEST14");
      }
    }
  } else if (direction==RtoL) {		// inputs from EAST
    if (slice==0 && le==0) {		// S0G1-4
      if (lutline==1) {
        //val=jbitsres.getbits(row,col,"S0G1.S0G1");
        //no connections from the east for S0G1
	connected=false;
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S0G2.S0G2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G2.SINGLE_EAST11") ||
		  jbitsres.isEquivVal(row,col,val,"S0G2.SINGLE_EAST23");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S0G3.S0G3");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0G3.OUT_EAST7");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_EAST5") ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_EAST7") ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_EAST9") ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_EAST10") ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_EAST21");
      } else { // (lutline==4)
        val=jbitsres.getbits(row,col,"S0G4.S0G4");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0G4.OUT_EAST6");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_EAST4") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_EAST16") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_EAST17") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_EAST19") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_EAST22");
      }
    } else if (slice==0 && le==1) {	// S0F1-4
      if (lutline==1) {
        //val=jbitsres.getbits(row,col,"S0F1.S0F1");
        //no connections from the east for S0F1
	connected=false;
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S0F2.S0F2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F2.SINGLE_EAST11") ||
		  jbitsres.isEquivVal(row,col,val,"S0F2.SINGLE_EAST23");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S0F3.S0F3");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0F3.OUT_EAST7");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_EAST5") ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_EAST7") ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_EAST9") ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_EAST10") ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_EAST21");
      } else { // (lutline==4)
        val=jbitsres.getbits(row,col,"S0F4.S0F4");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S0F4.OUT_EAST6");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_EAST4") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_EAST16") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_EAST17") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_EAST19");
      }
    } else if (slice==1 && le==0) {	// S1G4-1
      if (lutline==4) {
        //val=jbitsres.getbits(row,col,"S1G4.S1G4");
        //no connections from the east for S1G4
	connected=false;
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S1G3.S1G3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_EAST11") ||
		  jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_EAST23");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S1G2.S1G2");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1G2.OUT_EAST7");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_EAST5") ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_EAST7") ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_EAST9") ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_EAST10") ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_EAST21");
      } else { // (lutline==1)
        val=jbitsres.getbits(row,col,"S1G1.S1G1");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1G1.OUT_EAST6");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_EAST4") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_EAST16") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_EAST17") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_EAST19") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_EAST22");
      }
    } else { // (slice==1 && le==1)	// S1F4-1
      if (lutline==4) {
        //val=jbitsres.getbits(row,col,"S1F4.S1F4");
        //no connections from the east for S1F4
	connected=false;
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S1F3.S1F3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_EAST11") ||
		  jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_EAST23");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S1F2.S1F2");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1F2.OUT_EAST7");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_EAST5") ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_EAST7") ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_EAST9") ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_EAST10") ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_EAST21");
      } else { // (lutline==1)
        val=jbitsres.getbits(row,col,"S1F1.S1F1");
        if (USEDIRECTOUT) {
          connected=connected || jbitsres.isEquivVal(row,col,val,"S1F1.OUT_EAST6");
        }
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_EAST4") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_EAST16") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_EAST17") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_EAST19") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_EAST22");
      }
    }
  } else if (direction==TtoB) {		// inputs from NORTH
    if (slice==0 && le==0) {		// S0G1-4
      if (lutline==1) {
        val=jbitsres.getbits(row,col,"S0G1.S0G1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_NORTH15") ||
		  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_NORTH19") ||
		  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_NORTH22");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S0G2.S0G2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G2.SINGLE_NORTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S0G2.SINGLE_NORTH17");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S0G3.S0G3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_NORTH13");
      } else { // (lutline==4)
        val=jbitsres.getbits(row,col,"S0G4.S0G4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_NORTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_NORTH1") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_NORTH3") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_NORTH5") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_NORTH7") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_NORTH10");
      }
    } else if (slice==0 && le==1) {	// S0F1-4
      if (lutline==1) {
        val=jbitsres.getbits(row,col,"S0F1.S0F1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_NORTH15") ||
		  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_NORTH19") ||
		  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_NORTH22");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S0F2.S0F2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F2.SINGLE_NORTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S0F2.SINGLE_NORTH17");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S0F3.S0F3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_NORTH13");
      } else { // (lutline==4)
        val=jbitsres.getbits(row,col,"S0F4.S0F4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_NORTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_NORTH1") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_NORTH3") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_NORTH5") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_NORTH7") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_NORTH10");
      }
    } else if (slice==1 && le==0) {	// S1G4-1
      if (lutline==4) {
        val=jbitsres.getbits(row,col,"S1G4.S1G4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_NORTH15") ||
		  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_NORTH19") ||
		  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_NORTH22");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S1G3.S1G3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_NORTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_NORTH17");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S1G2.S1G2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_NORTH13");
      } else { // (lutline==1)
        val=jbitsres.getbits(row,col,"S1G1.S1G1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_NORTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_NORTH1") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_NORTH3") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_NORTH5") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_NORTH7") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_NORTH10");
      }
    } else { // (slice==1 && le==1)	// S1F4-1
      if (lutline==4) {
        val=jbitsres.getbits(row,col,"S1F4.S1F4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_NORTH15") ||
		  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_NORTH19") ||
		  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_NORTH22");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S1F3.S1F3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_NORTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_NORTH17");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S1F2.S1F2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_NORTH13");
      } else { // (lutline==1)
        val=jbitsres.getbits(row,col,"S1F1.S1F1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_NORTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_NORTH1") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_NORTH3") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_NORTH5") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_NORTH7") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_NORTH10");
      }
    }
  } else { // (direction==BtoT) 		// inputs from SOUTH
    if (slice==0 && le==0) {		// S0G1-4
      if (lutline==1) {
        val=jbitsres.getbits(row,col,"S0G1.S0G1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_SOUTH6") ||
		  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_SOUTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_SOUTH13") ||
		  jbitsres.isEquivVal(row,col,val,"S0G1.SINGLE_SOUTH21");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S0G2.S0G2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G2.SINGLE_SOUTH1");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S0G3.S0G3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_SOUTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_SOUTH14") ||
		  jbitsres.isEquivVal(row,col,val,"S0G3.SINGLE_SOUTH20");
      } else { // (lutline==4)
        val=jbitsres.getbits(row,col,"S0G4.S0G4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_SOUTH2") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_SOUTH8") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_SOUTH9") ||
		  jbitsres.isEquivVal(row,col,val,"S0G4.SINGLE_SOUTH18");
      }
    } else if (slice==0 && le==1) {	// S0F1-4
      if (lutline==1) {
        val=jbitsres.getbits(row,col,"S0F1.S0F1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_SOUTH6") ||
		  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_SOUTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_SOUTH13") ||
		  jbitsres.isEquivVal(row,col,val,"S0F1.SINGLE_SOUTH21");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S0F2.S0F2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F2.SINGLE_SOUTH1");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S0F3.S0F3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_SOUTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_SOUTH14") ||
		  jbitsres.isEquivVal(row,col,val,"S0F3.SINGLE_SOUTH20");
      } else { // (lutline==4)
        val=jbitsres.getbits(row,col,"S0F4.S0F4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_SOUTH2") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_SOUTH8") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_SOUTH9") ||
		  jbitsres.isEquivVal(row,col,val,"S0F4.SINGLE_SOUTH18");
      }
    } else if (slice==1 && le==0) {	// S1G4-1
      if (lutline==4) {
        val=jbitsres.getbits(row,col,"S1G4.S1G4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_SOUTH6") ||
		  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_SOUTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_SOUTH13") ||
		  jbitsres.isEquivVal(row,col,val,"S1G4.SINGLE_SOUTH21");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S1G3.S1G3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G3.SINGLE_SOUTH1");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S1G2.S1G2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_SOUTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_SOUTH14") ||
		  jbitsres.isEquivVal(row,col,val,"S1G2.SINGLE_SOUTH20");
      } else { // (lutline==1)
        val=jbitsres.getbits(row,col,"S1G1.S1G1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_SOUTH2") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_SOUTH8") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_SOUTH9") ||
		  jbitsres.isEquivVal(row,col,val,"S1G1.SINGLE_SOUTH18");
      }
    } else { // (slice==1 && le==1)	// S1F4-1
      if (lutline==4) {
        val=jbitsres.getbits(row,col,"S1F4.S1F4");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_SOUTH6") ||
		  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_SOUTH12") ||
		  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_SOUTH13") ||
		  jbitsres.isEquivVal(row,col,val,"S1F4.SINGLE_SOUTH21");
      } else if (lutline==3) {
        val=jbitsres.getbits(row,col,"S1F3.S1F3");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F3.SINGLE_SOUTH1");
      } else if (lutline==2) {
        val=jbitsres.getbits(row,col,"S1F2.S1F2");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_SOUTH0") ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_SOUTH14") ||
		  jbitsres.isEquivVal(row,col,val,"S1F2.SINGLE_SOUTH20");
      } else { // (lutline==1)
        val=jbitsres.getbits(row,col,"S1F1.S1F1");
        connected=connected ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_SOUTH2") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_SOUTH8") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_SOUTH9") ||
		  jbitsres.isEquivVal(row,col,val,"S1F1.SINGLE_SOUTH18");
      }
    }
  }

//System.err.println();
  return connected;
}



// return the current fitness value
public double getFitness() { return fitness; }


// calculate the fitness based on how many layers have been connected, and
// how many elements (up to the rounded up average of in and out bus sizes)
// in the layer are connected, and the progress of each LE at connecting
// from previous layer to the next layer 
double calcFitness()
{
  double fstep=100/(double)numlayers;
  int avginout=(inlocs.length+outlocs.length+1)/2; // rounded up avg
  int nmlayers=(numlayers>2) ? numlayers-2 : 0; // number non-i/o layers
  double fitness=0;
  double lfit=0;
  int ilsum=0;					   // sum of input layer
  int mlsum=0;					   // sum of middle layers
  int lsum=0;
  int lspread=0;
  int lestate=0;
  int outspread=outlocs.length;

  // if we use signal changes in connectivity analysis there will be
  // 5 possible states, otherwise there will be 4
  int cstates=(signalChangeMatrix==null) ? 4 : 5;

//System.err.println("fstep="+fstep+", avginout="+avginout);

  for (int i=0; i<numlayers; i++) {
    lsum=0;
    // at input and output layer we only use the connection values for the
    // input/output locations, for the other layers we use all locations
    if (i==0) { // input layer
      for (int j=0; j<inlocs.length; j++) {
	lestate=getBestInState(inlocs[j],j);
//System.err.print("{in["+j+"] state="+lestate+" statesum="+getLEConnectStateSum(lestate)+"} ");
	lsum=lsum+getLEConnectStateSum(lestate);
      }
      ilsum=lsum;
    } else if (i<numlayers-1) { // middle layers
      lsum=getLayerConnectSum(i);
      if (lsum>avginout*cstates) {
	 lsum=avginout*cstates; // don't care if more
      }
      lspread=getLayerConnectSpread(i);
//System.err.println("layer "+i+" spread="+lspread);
      // we want to encourage spread to help evolution find connections
      // to all outputs, so divide layers into #outputs regions, and count
      // the number of regions that have connected cells, which we then
      // scale the layer's fitness by
      if (i-1<(numlayers-2)/2) {	// first half of middle layers
	// penalise layer if less than 1/2 regions don't have connected cell
	if (lspread<outspread/2) {
//System.err.println("scaling first-half layer="+i+" by 1/2 from "+lsum+" to "+(lsum/2));
	  lsum=lsum/2;
	}
      } else if (i<numlayers-2) {	// second half of middle layers
	// penalise layer if less than 3/4 regions don't have connected cell
	// and if less than 1/2 then penalise further
	if (lspread<outspread/2) {
//System.err.println("scaling latter-half layer="+i+" by 1/2 from "+lsum+" to "+(lsum/2));
	  lsum=lsum/2;
	} else if (lspread<(outspread*3)/4) {
//System.err.println("scaling latter-half layer="+i+" by 3/4 from "+lsum+" to "+((lsum*3)/4));
	  lsum=(lsum*3)/4;
	}
      } else {				// last layer of "middle"
	if (lspread<outspread) {
//System.err.println("scaling last middle layer="+i+" by "+lspread+"/"+outspread+" from "+lsum+" to "+((int)(lsum*((float)lspread/(float)outspread))));
	  lsum=(int)(lsum*((float)lspread/(float)outspread));
	}
      }
      mlsum+=lsum;			// keep track of middle layers' sums
    }
    // don't use else here, as if only a single layer, inputs and outputs will
    // be in the same layer
    if (i>=numlayers-1) { // output layer, only look at output cells
      for (int j=0; j<outlocs.length; j++) {
	lestate=getBestOutState(inlocs[j],j);
//System.err.print("{out["+j+"] state="+lestate+" statesum="+getLEConnectStateSum(lestate)+"} ");
	lsum=lsum+getLEConnectStateSum(lestate);
      }
    }
//System.err.print("(layer["+i+"] sum="+lsum+") ");
    if (lsum>0) {
      // get the fitness metric of this layer (independent of other layers)
      // use average of in/out bus widths, except at output layer, where
      // where use number of outputs, or at input layer, where use # inputs
      // multiply by cstates as each LE needs this # things to connect layers
      if  (i==0 && numlayers==1) {	// input and output layer
        lfit=fstep*((double)lsum/(double)((inlocs.length+outlocs.length)*cstates));
      } else if (i==0) {		  // input layer
        lfit=fstep*((double)lsum/(double)(inlocs.length*cstates));
      } else if (i<numlayers-1) { // "middle" of evolvable region
        lfit=fstep*((double)lsum/(double)(avginout*cstates));
      } else {			  // at output layer
	// if all outputs are connected and (optionally ALL) inputs connected,
	// (and extra optionally) all middle layers have sufficient spread,
	// then fitness is 100%
	// to avoid rounding errors, we assign the value directly in this case
	if (lsum==(outlocs.length*cstates) &&
	    (ilsum==(inlocs.length*cstates) || NEEDSALLINPUTLES==false) &&
	    (mlsum==((avginout*cstates)*nmlayers) || NEEDSMIDLAYERSPREAD==false)) {
//System.err.println("fully connected inputs/outputs + sufficient middle connectivity! fitness=100.0");
	  return 100.0;			// so this is a complete solution
	} else {
          lfit=fstep*((double)lsum/(double)(outlocs.length*cstates));
	}
      }
//System.err.println("(layer["+i+"] fitness="+lfit+") ");
      // add this layer's fitness to the total fitness
      fitness+=lfit;
    } else {
      break;	// once reach an unconnected layer, the rest will be too
    }
  }

//System.err.println();

  return fitness;
}


// get the number of 'sections' in the layer that have connected elements
int getLayerConnectSpread(int l)
{
  int lspread=0;
  int[] lstates=getLayer(stateMatrix,l);
  int clbs=lstates.length/4;			// 4 LEs per CLB
  int[] sections=new int[outlocs.length];
  float step=(float)lstates.length/(float)sections.length;
  float sectionend=step;
//System.err.print("layer "+l+", clbs="+clbs+", step="+step+".. ");
  int s=0;
  int i=0;
  while (i<lstates.length) {
//System.err.print("i="+i+" ");
    if (i>=sectionend) {
      sectionend+=step; s++;
//System.err.print("s="+s+" ");
    }
    // note that in lstates the 4 LEs that comprise the CLB are stored in seq
    if (sections[s]==0 && 
	(lstates[i]>0 || lstates[i+1]>0 || lstates[i+2]>0 || lstates[i+3]>0)) {
      sections[s]=1; lspread++;
//System.err.print("section "+s+" connected .. ");
    }
    i+=4;	// the 4 LE states are stored contiguously
  }

//System.err.println("spread="+lspread);
  return lspread;
}


// get the sum of element connection contributions (0-4) in the layer.
// layer 0 is the input layer, layer 'numlayers-1' is the output to the
// CLBs routed to the output bus IOBs
int getLayerConnectSum(int l)
{
  int[] lstates=getLayer(stateMatrix,l);
  int lsum=0;
  for (int i=0;i<lstates.length;i++) {
    lsum+=getLEConnectStateSum(lstates[i]);
  }

  return lsum;
}


// get the sum of element connection contributions (0-4) in the LE.
// (note: if utilising signal changes then the sum will be 0-5)
int getLEConnectStateSum(int s)
{
  int lesum=0;
  if (s>0) {	// input connected
    lesum++;
    if (s>1) {  // LUT fn uses its inputs in some manner to generate output
      lesum++;
      // if we utilise signal changes in connectivity test, we have some
      // extra feedback to utilise (5 connectivity states vs 4). so we can
      // add an extra test to determine if LUT input has any effect on LE
      // output due to the way other inputs are used in the LUT function
      // a state of 2 indicates LUT uses at least 1 of its connected input(s),
      // while a state of 3 also requires that the connected input(s) effect
      // the LE's output
      if (signalChangeMatrix!=null && s>2) { lesum++; }
      if (s>4) { // LE output connected to OUT bus line
        lesum++;
        if (s>8) { // outbus to single connected and to nbour's in
	  lesum++;
        }
      }
    }
  }

  return lesum;
}

// get a horizontal or vertical layer of the matrix, numbered from 0
// (at input layer) to N-1 (at output layer)
// noting that there are 2 row (LEs) or cols (slices) per layer
// ie there are 4 elements per CLB
int[] getLayer(int[][] matrix, int l)
{
  int rowidx1, rowidx2, colidx1, colidx2;
  int[] layer=new int[numentries*4];

  for (int i=0; i<numentries; i++) {
    if (direction==LtoR || direction==RtoL) {
      rowidx1=i*2;
      rowidx2=rowidx1+1;
      colidx1=(direction==LtoR) ? l*2 : (numlayers*2)-(l*2)-1;
      colidx2=(direction==LtoR) ? colidx1+1 : colidx1-1;
      layer[i*4]=matrix[rowidx1][colidx1];
      layer[(i*4)+1]=matrix[rowidx1][colidx2];
      layer[(i*4)+2]=matrix[rowidx2][colidx1];
      layer[(i*4)+3]=matrix[rowidx2][colidx2];
    } else {
      colidx1=i*2;
      colidx2=colidx1+1;
      rowidx1=(direction==BtoT) ?  l*2 : (numlayers*2)-(l*2)-1;
      rowidx2=(direction==BtoT) ? rowidx1+1 : rowidx1-1;
      layer[i*4]=matrix[rowidx1][colidx1];
      layer[(i*4)+1]=matrix[rowidx1][colidx2];
      layer[(i*4)+2]=matrix[rowidx2][colidx1];
      layer[(i*4)+3]=matrix[rowidx2][colidx2];
    }
  } 

  return layer;
}




} // end class




