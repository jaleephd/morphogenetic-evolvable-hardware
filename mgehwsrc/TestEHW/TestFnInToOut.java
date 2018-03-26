
//			 TestFnInToOut.java
//
// this class tests for (or attempts to create) a fully connected circuit
// that performs the desired function. Once outputs are connected, it
// iterates through each of the possible input signal combinations, and
// tests the outputs to see how closely they match the required output.
// if no function is specified the default function is to pass inputs to
// outputs with the output signals ignored in fitness evaluation - ie it
// bases fitness solely on connectivity rather than function.
//
// We base fitness on how much progress is made in connecting inputs and
// outputs, and how closely the outputs match that of the desired function.
// The circuit fitness is given by scaling the connectivity and functional
// fitnesses by 1/2 and then adding them together
//
// Circuit connectivity is measured by how many layers have changing signal
// values, and how many elements in the layer change (up to the rounded down
// average of in and out bus sizes), except in input and output layers, where
// only use the input and output LEs. connectivity can be tested using either
// TestInToOutRecurse's evaluation or using the simulator with probes at the
// logic element's registered outputs (YQ/XQ) with tests for signal changes
// at these probes.
//
// Function equivalence is measured according to how close the delayed output
// matches the truth table's output signals, based on the hamming distance
// between the outputs (that _change_, unchanging outputs are assumed to be
// unconnected to the inputs, and for each of these 1 is added to the hamming
// distance) and the specified outputs, for delays of M .. M+N, where N is the
// maximum additional delay on top of M, the minimum propogation delay from
// the first input to first output, and choosing the delay with the smallest
// Hamming distance to base the fitness on. Fitness is then based on the
// proportion of matching signals, calculated as fitness=(L-H)/L, where L is
// the length of the input signal, and H is the Hamming distance.
// Note that for combinatorial (stateless) functions (eg 1- and 2-bit adders),
// only unregistered slice outputs should be used, and a maxdelay of 0 should
// be used.
//
//
// 		written August 2004, J.A. Lee
//
// this class also requires the local classes:
// 			TestInToOutRecurse, MhwMatrix, BuildEhw,
// 			JBitsResources, StringSplitter, LEcoord

// for thread support (needed for SafeBitstreamLoader)
import java.lang.Thread;

// for String's etc
import java.lang.*;
// for collection classes (eg ArrayList)
import java.util.*;
// for IO
import java.io.*;
// for formatting decimal numbers for output (like printf)
import java.text.DecimalFormat;

// for JBits object
import com.xilinx.JBits.Virtex.JBits;

// for placing stimulus and probe pins
import com.xilinx.JBits.CoreTemplate.Pin;
import com.xilinx.JBits.CoreTemplate.CoreException;
// defines the IOB constants (ie IOB.LEFT) for use in the Pin constructor
import com.xilinx.JBits.Virtex.Bits.IOB;
// defines routable (and Pin connectable) resources
import com.xilinx.JRoute2.Virtex.ResourceDB.*;

// Board Connection
import com.xilinx.XHWIF.XHWIF;
import com.xilinx.JBits.CoreTemplate.Port;
import com.xilinx.JBits.Virtex.ConfigurationException;
import com.xilinx.JBits.Virtex.ReadbackCommand;

// Virtex simulator
import com.xilinx.DeviceSimulator.Virtex.SimulatorClient;
import com.xilinx.DeviceSimulator.Virtex.SimulationException;






public class TestFnInToOut {

  	boolean VERBOSE;

        // interleaved input and output sequences for the desired function
	// [0,1 are digital signals; -1 = Z (unconnected); 2=X (don't care)]
	// there would normally be 2^I x I elements in the input sequence
	// and 2^I x O elements in the output sequence. If there are inputs
	// that never occur, or if for some input sequences the output
	// doesn't matter, then for these no entries are needed, or an output
	// of 2 can be used; however, it is essential that for every provided
	// input combination there is an associated ouput entry. The length of
	// sequences, Si and So, when divided by the number of inputs and
	// outputs, I and O, should be equal; ie Si/I = So/O

	int[] functioninseq;
	int[] functionoutseq;

	JBits jbits;
	JBitsResources jbitsres;

	static String deviceName="XCV50";
	static String asynchboardName = "VirtexDS@GCLK=1.speed=-6:xcv50";
	static String synchboardName = "VirtexDS@GCLK=1:xcv50";
	XHWIF board;
	SimulatorClient client;

	boolean SYNCHONLY=false;	// set true if only allow synch signals

	public int direction;		// desired direction of cct growth
  	// direction constants:
  	public final int BtoT=0;	// bottom to top
  	public final int LtoR=1;	// left to right
  	public final int TtoB=2;	// top to bottom
  	public final int RtoL=3;	// right to left

	public int minrow;
	public int mincol;
	public int maxrow;
	public int maxcol;

	public LEcoord[] inlocs;
	public LEcoord[] outlocs;

	public Pin[] inbusPins;		// stimulus signals are placed here
	public Pin[] outbusPins;	// receives the signals from grown cct
	public int[] response;		// probed values of outbusPins
	public int[] responseChange;	// # signal changes in above

	// note matrix is rows*2 x cols*2 from minrow,mincol-maxrow,maxcol:
        // we assign slice granularity to cols, and LE granularity to rows
	public Pin[][] growthProbes;	// probe progress of cct growth
	public int[][] responseMatrix;	// stores probed values of above
	public int[][] changeMatrix;	// stores # signal changes in above

	// these give the number of slice (or LE) layers from in CLBs to out
	// and the number of LE (or slice) entries per layer (ie other axis)
	public int numlayers;
	public int numentries;

	// this gives the count of elements in each layer that have had
	// signal changes, allowing fitness & signal propogation to be calc
	public int[] layerchangecnt;

	// num layers that signal has propogated from the inputs
	public int propogationfromin;

	// this gives finer grained connectivity feedback than just probing
	// LE outputs using the simulator. the default is not to use it
	public TestInToOutRecurse connectivitytest=null;

        boolean ignorefunction=false; // is set if output function is ignored

	// this is used to indicate whether we can rely on all LUT fns
	// being valid, in the sense that they are always a function of active
	// input lines, as implemented using the LUTActiveFNs resource type
	// and the ActiveInputLEs class
	boolean ACTIVELUTFNSONLY=false;

	// 0 for of performing Hamming distance on all cct outputs,
	// 1 for consecutive signals on each output starting at any offset
	// 2 for consecutive signals on each output starting at first signal
	int HAMMINGTYPE=0;

	// for limiting number of decimal places in output float/doubles
	DecimalFormat df;


public static void main (String[] args)
{
  int save=0;
  int vgran=2;		// CLB granularity in vertical axis
  boolean verbose=false;
  boolean debug=false;	// this is used for more info than verbose
  boolean recurse=true; // whether to TestInToOutRecurse to test connectivity
  boolean activefnsonly=false; // LUTs only use active lines in their function
  boolean synchsigsonly=false; // if true only registered slice outputs allowed
  boolean strictiterlimit=false; // allow further iter if fitness increasing
  int maxdelay=-1;	// how many clock cycles more than min propogate delay
  			// -1 indicates that we use minprop as maxdelay
  //int siglen=8;	// length of signal sequence to be tested against
  int hammingtype=0;    // 0 is standard, 1 for consecutive, 2 for incremental 

  int transcriptionrate=4; // defaults
  double npoly2gene=0.3;
  double polythreshold=0.8;

  String devname=TestFnInToOut.deviceName;
  String outbit=null;
  String recordfile=null;	// this is for recording set() instructions

  String clbSettingsRecord=null;// this is for loading recorded set() instrs
  				// mostly for pre-setting ActiveLUTs

  int[] inputseq=null;
  int[] outputseq=null;

  boolean inoutfn=false;		// for in-out fn

  String usage = "Usage: TestFnInToOut [-v | -d] [-le] [-sil] [-norecurse] [-activelutfnsonly] [-synchonly] [-maxdelay D] [-incrhamming | -consechamming] [ -inout | -onebitadder | -twobitadder | -fnseq iii.. ooo.. ] [ -s outbit | -sr outbit ] [-load clbSettingsRecord.txt] XCV50CLK1.bit inIOB outIOB inCLBslice outCLBslice minCLB maxCLB contentionFile [ dchromfilelist | dchromfile cytofile numTestIter nGrowthStepsPerTest [ transcriptionRate npoly2gene polyThreshold ] [ seed ] ]";

  // Check the arguments
  int argc=args.length;
  int flags=0;

  while (argc>0 && args[0+flags].charAt(0)=='-') {
    if (args[0+flags].equals("-d")) {
      debug=verbose=true;		// extra level of debugging info
      flags++; argc--;
    } else if (args[0+flags].equals("-v")) {
      verbose=true;
      flags++; argc--;
    } else if (args[0+flags].equals("-le")) {
      vgran=1;		// logic element granularity in vertical axis
      flags++; argc--;
    } else if (args[0+flags].equals("-sil")) {
      strictiterlimit=true;	// always stop at given test iter
      flags++; argc--;
    } else if (args[0+flags].equals("-activelutfnsonly")) {
      activefnsonly=true;
      flags++; argc--;
    } else if (args[0+flags].equals("-synchonly")) {
      synchsigsonly=true;
      flags++; argc--;
    } else if (args[0+flags].equals("-norecurse")) {
      recurse=false;
      flags++; argc--;
    } else if (argc>1 && args[0+flags].equals("-maxdelay")) {
      maxdelay=Integer.parseInt(args[1+flags]);
      flags+=2; argc-=2;
    } else if (args[0+flags].equals("-consechamming")) {
      hammingtype=1;
      flags++; argc--;
    } else if (args[0+flags].equals("-incrhamming")) {
      hammingtype=2;
      flags++; argc--;
    } else if (args[0+flags].equals("-inout") && inputseq==null) {
      inputseq=new int[0]; // dummy entry while parsing cmd args
      inoutfn=true;
      flags++; argc--;
    } else if (args[0+flags].equals("-onebitadder") && inputseq==null) {
      // create interleaved full adder input and output sequences
      // there will 2^I x I input signals and 2^I x O output signals
      // note that order of inputs is x,y,Cin and of outputs is Sum,Cout
      // NB: IO orders are little-endian!!!
      inputseq=new int[] {
	0,0,0, 1,0,0, 0,1,0, 1,1,0,
	0,0,1, 1,0,1, 0,1,1, 1,1,1
      };
      outputseq=new int[] {
	0,0,   1,0,   1,0,   0,1,
	1,0,   0,1,   0,1,   1,1
      };
      flags++; argc--;
    } else if (args[0+flags].equals("-twobitadder") && inputseq==null) {
      // two bit full adder, specified in little-endian format
      // order of inputs is X1 X2 Y1 Y2 Cin; outputs is S1 S2 Cout
      // Cin/X1/Y1 are least significant bits, Cout is the most significant bit
      inputseq=new int[] {
	0,0,0,0,0, 1,0,0,0,0, 0,1,0,0,0, 1,1,0,0,0,
	0,0,1,0,0, 1,0,1,0,0, 0,1,1,0,0, 1,1,1,0,0,
	0,0,0,1,0, 1,0,0,1,0, 0,1,0,1,0, 1,1,0,1,0,
	0,0,1,1,0, 1,0,1,1,0, 0,1,1,1,0, 1,1,1,1,0,
	0,0,0,0,1, 1,0,0,0,1, 0,1,0,0,1, 1,1,0,0,1,
	0,0,1,0,1, 1,0,1,0,1, 0,1,1,0,1, 1,1,1,0,1,
	0,0,0,1,1, 1,0,0,1,1, 0,1,0,1,1, 1,1,0,1,1,
	0,0,1,1,1, 1,0,1,1,1, 0,1,1,1,1, 1,1,1,1,1
      };
      outputseq=new int[] {
	0,0,0,     1,0,0,     0,1,0,     1,1,0,
	1,0,0,     0,1,0,     1,1,0,     0,0,1,
	0,1,0,     1,1,0,     0,0,1,     1,0,1,
	1,1,0,     0,0,1,     1,0,1,     0,1,1,
	1,0,0,     0,1,0,     1,1,0,     0,0,1,
	0,1,0,     1,1,0,     0,0,1,     1,0,1,
	1,1,0,     0,0,1,     1,0,1,     0,1,1,
	0,0,1,     1,0,1,     0,1,1,     1,1,1
      };
      flags++; argc--;
    } else if (argc>2 && args[0+flags].equals("-fnseq") && inputseq==null) {
      // get user-specified function (note that IO orders are little-endian)
      // input seq in form (eg 4 in): IA0,IB0,IC0,ID0,IA1,IB1,IC1,..,ID(2^n)-1
      inputseq=StringSplitter.str2ints(args[0+flags+1],",") ;
      // output seq in form (eg 3 out): OA0,OB0,OC0,OA1,OB1,OC1,..,OC(2^n)-1
      outputseq=StringSplitter.str2ints(args[0+flags+2],",") ;
      flags+=3; argc-=3;
    } else if (argc>1 && (args[0+flags].equals("-s") || args[0+flags].equals("-sr"))) {
      outbit=args[1+flags];
      if (args[0+flags].equals("-sr")) {
        recordfile=new String(outbit+".record.txt");
      }
      save=1; flags+=2; argc-=2;
    } else if (argc>1 && args[0+flags].equals("-load")) {
      clbSettingsRecord=args[1+flags];
      flags+=2; argc-=2;
    } else {
      System.err.println("\nunknown switch ("+args[0+flags]+")!\n"+usage);
      System.exit(1);
    }
  }

  // don't need dummy entry any more
  if (inoutfn) { inputseq=null; }

  if (argc<8 || argc==10 || argc==14) {
    System.err.println("\nmissing arguments!\n"+usage);
    System.exit(1);
  } else if (argc>16) {
    System.err.println("\ntoo many arguments ("+args.length+")!\n"+usage);
    System.exit(1);
  }

  String bitfile=args[0+flags];

  String[] inIOBs=StringSplitter.split(args[1+flags],";");
  String[] outIOBs=StringSplitter.split(args[2+flags],";");
  String[] inCLBslices=StringSplitter.split(args[3+flags],";");
  String[] outCLBslices=StringSplitter.split(args[4+flags],";");

  int inputwidth=inIOBs.length;
  int outputwidth=outIOBs.length;

  // if defined a function on cmd line, then check it has correct number
  // of table entries, according to the number of inputs and outputs,
  // otherwise create the appropriate function tables
  // note that there is 2^I x I input signals and 2^I x O output signals
  // also note that IO orders in tables are little-endian!!!
 
  if (inputseq!=null) {
    if (inputseq.length!=twoToTheN(inputwidth)*inputwidth) {
      System.err.println("\nsupplied function input table has wrong number of entries ("+inputseq+"). should be "+(twoToTheN(inputwidth)*inputwidth)+"!\n");
      System.exit(1);
    }
  } else {
    // now we need to create function truth tables where not already created
    inputseq=new int[twoToTheN(inputwidth)*inputwidth];
    for (int i=0; i<twoToTheN(inputwidth); i++) { // for each set of inputs
      int ii=i;
      for (int j=inputwidth-1; j>=0; j--) {
	inputseq[(i*inputwidth)+j]=ii/twoToTheN(j);
	ii=ii-((ii/twoToTheN(j))*twoToTheN(j));
      }
    }
  }

  if (outputseq!=null) {
    if (outputseq.length!=twoToTheN(inputwidth)*outputwidth) {
      System.err.println("\nsupplied function output table has wrong number of entries ("+outputseq.length+"). should be "+(twoToTheN(inputwidth)*outputwidth)+"!\n");
      System.exit(1);
    }
  } else {
    outputseq=new int[twoToTheN(inputwidth)*outputwidth];
    // if no function is specified (and for unmatched outputs), use default
    // function of pass inputs to outputs with outputs ignored
    for (int i=0; i<outputseq.length; i++) {
      outputseq[i]=2;	// default to ignore output
    }
    if (inoutfn) {
      // match each output signal to an input signal, those that don't
      // correspond are ignored. ie there can be a different number of
      // inputs and outputs (more or less of either)
      for (int i=0; i<twoToTheN(outputwidth); i++) { // each set of outputs
	for (int j=0; j<inputwidth && j<outputwidth; j++) { // for each input
          outputseq[i]=inputseq[((twoToTheN(i)-1)*inputwidth)+j]; // copy it
	}
      }
      
    }
  }

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
  LEcoord[] inoutlocs=new LEcoord[inCLBslices.length+outCLBslices.length];

  // need to ensure that all in/out CLB coords are in int,int,int,int format
  LEcoord le=null;
  for (int i=0; i<inCLBslices.length; i++) {
    le=new LEcoord(inCLBslices[i]);		// parse and store coords
    inCLBslices[i]=le.toIntString();		// store as string of ints
    inoutlocs[i]=le;
  }
  for (int i=0; i<outCLBslices.length; i++) {
    le=new LEcoord(outCLBslices[i]);		// parse and store coords
    outCLBslices[i]=le.toIntString();		// store as string of ints
    inoutlocs[i+inCLBslices.length]=le;
  }

  int[] mins=StringSplitter.str2ints(minCLB,",");
  int[] maxs=StringSplitter.str2ints(maxCLB,",");

  MhwMatrix cells=null;
  JBits jbits=null;
  JBitsResources jbr=null;			// for recording set()'s
  ActiveInputLEs activeLEs=null;

  if (argc==8) {				// just use supplied bitstream
    // create's jbits object and loads bitstream
    jbr=new JBitsResources(devname,contentious,bitfile);
    // optionally load settings from a (recorded) file
    if (clbSettingsRecord!=null) {
      if (verbose) { System.err.print("loading CLB settings record from "+clbSettingsRecord+" .. "); }
      jbr.loadRecordFile(clbSettingsRecord);
      if (verbose) { System.err.println("Done."); }
    }
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

    jbits=ndehw.getJBits();
    jbr=ndehw.getJBitsResources();

    // optionally load settings from a (recorded) file
    if (clbSettingsRecord!=null) {
      if (verbose) { System.err.print("loading CLB settings record from "+clbSettingsRecord+" .. "); }
      jbr.loadRecordFile(clbSettingsRecord);
      if (verbose) { System.err.println("Done."); }
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
          inCLBslices,outCLBslices,contentious,dchromfile,cytofile, 
	  npoly2gene,polythreshold,transcriptionrate,mglifespan,tflifespan,
	  ageratefree,ageratebound,morphogenPropDelay,vgran,debug);

    numgenes=cells.numGenes();

    // to keep track of how many genes are currently active in each cell
    // if slice granularity, then still create as many rows as for LE gran
    // so that can use same matrix display routines as for LE states
    // but here we just double up on the activations
    geneActivationMatrix=new int[cells.getCellRows()*vgran][cells.getCellCols()];
    if (verbose) { System.err.println("chromosome contains "+numgenes+" genes...\n"); }

    // so we can share/access the same JBitsResources instance wherever needed
    jbr=cells.getJBitsResources();

    // optionally load settings from a (recorded) file
    if (clbSettingsRecord!=null) {
      if (verbose) { System.err.print("loading CLB settings record from "+clbSettingsRecord+" .. "); }
      jbr.loadRecordFile(clbSettingsRecord);
      if (verbose) { System.err.println("Done."); }
    }

    // if we are using the save option, then we save initial cct, even though
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
  TestFnInToOut sim = new TestFnInToOut(jbr,minCLB,maxCLB,inIOBs,outIOBs,inCLBslices,outCLBslices,inputseq,outputseq,recurse,activefnsonly,synchsigsonly,hammingtype,verbose);

  // this provides a means of preventing inactive inputs from dominating
  // LUT functions, by only using inputs from active LEs in boolean FNs
  // active LEs are determined by starting from the input routing CLBs
  // and tracing from these into the evolvable region.

  LEcoord[] activeinlocs=new LEcoord[inCLBslices.length];
  for (int i=0; i<inCLBslices.length; i++) {
    activeinlocs[i]=new LEcoord(inCLBslices[i]);
    // we actually need the input routing LE, as this is active
    if (sim.direction==sim.LtoR) {
      activeinlocs[i].col--;
    } else if (sim.direction==sim.RtoL) {
      activeinlocs[i].col++;
    } else if (sim.direction==sim.BtoT) {
      activeinlocs[i].row++;
    } else { // if (sim.direction==sim.TtoB)
      activeinlocs[i].row--;
    }
    // and we need the specific input on this LE that is active..
    // IOBs are routed to inputs:  S0_F4, S1_F1, S0_G4, S1_G1
    activeinlocs[i].line=(activeinlocs[i].slice==0) ? 4 : 1;
  }

  // need to include the input routing CLBs (outside evolvable region) in the
  // matrix used by ActiveInputLEs class, but all other CLBs in that row/col
  // should be ommitted, and for printing we need to remove them, so record
  // the direction of shift needed to remove them
  LEcoord[] inactivelocs=null;
  List inactivelist=new ArrayList();
  LEcoord mncoord=new LEcoord(minCLB+",0,0");
  LEcoord mxcoord=new LEcoord(maxCLB+",0,0");
  int mrshift=0;
  int mcshift=0;
  if (sim.direction==sim.LtoR || sim.direction==sim.RtoL) {
    mcshift=(sim.direction==sim.LtoR) ? 1 : -1;
    mncoord.col=activeinlocs[0].col;
    // inactivate the rest of the LEs in the 'in' column
    for (int r=mncoord.row; r<=mxcoord.row; r++) {
      inactivelist.add(new LEcoord(r,mncoord.col,0,0));
      inactivelist.add(new LEcoord(r,mncoord.col,0,1));
      inactivelist.add(new LEcoord(r,mncoord.col,1,0));
      inactivelist.add(new LEcoord(r,mncoord.col,1,1));
    }
  } else {
    mrshift=(sim.direction==sim.BtoT) ? 1 : -1;
    mncoord.row=activeinlocs[0].row;
    // inactivate the rest of the LEs in the 'in' row
    for (int c=mncoord.col; c<=mxcoord.col; c++) {
      inactivelist.add(new LEcoord(mncoord.row,c,0,0));
      inactivelist.add(new LEcoord(mncoord.row,c,0,1));
      inactivelist.add(new LEcoord(mncoord.row,c,1,0));
      inactivelist.add(new LEcoord(mncoord.row,c,1,1));
    }
  }

  // now we need to remove the (active) input locations from the list
  // of inactive elements
  for (int i=0; i<activeinlocs.length; i++) {
    for (int j=0; j<inactivelist.size(); j++) {
      if (activeinlocs[i].equals((LEcoord)inactivelist.get(j))) {
        inactivelist.remove(j);
	break;
      }
    }
  }

  // note use of empty array param to the ArrayList.toArray() method
  inactivelocs=(LEcoord[])inactivelist.toArray(new LEcoord[0]);

  activeLEs=new ActiveInputLEs(jbr,mncoord,mxcoord,activeinlocs,inactivelocs,debug);

  if (verbose) {
    System.err.println("initial active LUT input counts...\n");
    System.err.println(sim.getMatrix(activeLEs.getActiveCntMatrix(mrshift,mcshift),false));
  }

//System.exit(0); // just to test active LE connectivity

  DecimalFormat df = new DecimalFormat("0.0##");
  //Random rand=new Random(seed+101); // for creating signal sequence 

  if (verbose) { System.err.println("initial probed signals are...\n"); }
  if (verbose) { System.err.println(sim.strResponse(false)); }
  if (verbose) { System.err.println(sim.getProbeMatrix()); }

  // test this initial cct..
  double fitness=sim.testCCT(maxdelay);
  if (verbose) { System.err.println("initial fitness="+df.format(fitness)); }

  // show the count of active genes in each cell
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
    System.err.println(sim.getMatrix(geneActivationMatrix,false));
  }

  long iterstarttime=0; // for profiling info (set to 0 to avoid compiler cr@p)
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
    if (verbose) { iterstarttime=System.currentTimeMillis(); } // for profiling info
    prevfit=fitness;
    giter=((di-1)*ngrowthsteps)+1;
    if (verbose) { System.err.println("\n\nentering growth phase "+giter+"..."); }
    cells.step(ngrowthsteps);	// perform growth steps

    // this needs to be called after each morphogenesis growth step, to update
    // the LUTs in the circuit to only use the currently active lines
    activeLEs.update();

    giter+=ngrowthsteps-1;
    if (verbose) { System.err.println("completed growth phase "+giter+"..."); }
    if (verbose) { 
      System.err.println("active LUT input counts...");
      System.err.println(sim.getMatrix(activeLEs.getActiveCntMatrix(mrshift,mcshift),false));
    }

    fitness=sim.testCCT(maxdelay); // and now test the circuit
    if (verbose) { System.err.println("fitness="+df.format(fitness)+"        \t highest was "+df.format(maxfitness)+" at "+maxfititer); }

  // show the count of active genes in each cell
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
      System.err.println(sim.getMatrix(geneActivationMatrix,false));
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
    if (verbose) { System.err.println("iter time taken="+(System.currentTimeMillis()-iterstarttime)+" ms ...\n"); } // profiling
  }

  if (verbose) {
    System.err.println();
    if (numIter>0) { System.err.println("finished growth...\n"); }
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

  if (verbose) { System.err.println("\nDone."); }

  sim.disconnect();
  if (verbose) { System.err.println("exiting."); }

} // end main



public static int twoToTheN (int n) { return 1 << n; }




// in/out IOBsites are comma delimited Strings in format: side,index,siteindex

TestFnInToOut(JBitsResources jbr, String mincoord, String maxcoord, String[] inIOBsites, String[] outIOBsites, String[] inClbLEs, String[] outClbLEs, int[] inseq, int[] outseq, boolean recurse, boolean onlyactivefns, boolean synchsigsonly, int hammingtype)
{
  this(jbr,mincoord,maxcoord,inIOBsites,outIOBsites,inClbLEs,outClbLEs,inseq,outseq,recurse,onlyactivefns,synchsigsonly,hammingtype,false);
}

TestFnInToOut(JBitsResources jbr, String mincoord, String maxcoord, String[] inIOBsites, String[] outIOBsites, String[] inClbLEs, String[] outClbLEs, int[] inseq, int[] outseq, boolean recurse, boolean onlyactivefns, boolean synchsigsonly, int hammingtype, boolean vb)
{
  VERBOSE=vb;

  ACTIVELUTFNSONLY=onlyactivefns;
  SYNCHONLY=synchsigsonly;
  HAMMINGTYPE=hammingtype;

  // to format outputs to 3 decimal places max
  df = new DecimalFormat("0.0##");

  // copy the interleaved input and output sequences for the desired function
  // note that inseq/#inputs should equal outseq/#outputs
  if (inseq.length/inClbLEs.length!=outseq.length/outClbLEs.length) {
    System.err.println("Function Input and Output signal combos don't match..");
    System.err.print((inseq.length/inClbLEs.length)+"input combos !=");
    System.err.println((outseq.length/outClbLEs.length)+"output combos");
    System.exit(1);
  }
  functioninseq=new int[inseq.length];
  for (int i=0; i<inseq.length; i++) { functioninseq[i]=inseq[i]; }

  int ignoredouts=0;
  functionoutseq=new int[outseq.length];
  for (int i=0; i<outseq.length; i++) {
    functionoutseq[i]=outseq[i];
    if (outseq[i]==2) { ignoredouts++; }
  }
  // if all output signals are ignored, then we set a flag to indicate this
  if (ignoredouts==outseq.length) { ignorefunction=true; }

  jbitsres=jbr;
  jbits=jbr.getJBits();
 
  LEcoord coord=new LEcoord(mincoord+",0,0");
  minrow=coord.row;
  mincol=coord.col;

  coord=new LEcoord(maxcoord+",0,0");
  maxrow=coord.row;
  maxcol=coord.col;

  // convert to LEcoord types and store locations of IO CLBs
  inlocs=new LEcoord[inClbLEs.length];
  for (int i=0; i<inClbLEs.length; i++) { inlocs[i]=new LEcoord(inClbLEs[i]); }
  outlocs=new LEcoord[outClbLEs.length];
  for (int i=0;i<outClbLEs.length;i++){ outlocs[i]=new LEcoord(outClbLEs[i]); }

  // put stimulus pins on input bus IOB sites
  inbusPins=new Pin[inIOBsites.length];
  for (int i=0; i<inIOBsites.length; i++) {
    inbusPins[i]=makeIOBpin(inIOBsites[i],"in");
  }

  // put probe pins on ouput bus IOB sites
  outbusPins=new Pin[outIOBsites.length];
  for (int i=0; i<outIOBsites.length; i++) {
    outbusPins[i]=makeIOBpin(outIOBsites[i],"out");
// for debug only: use out CLB's, on cmdline subst IOB coords for CLB's
//outbusPins[i]=makeCLBpin(outIOBsites[i]);
//System.err.println("substituted IOB pin for CLB pin at "+outbusPins[i]);
  }

  // create response and response change arrays to store outputs and changes
  // that occur at the outbus
  response=new int[outIOBsites.length];
  responseChange=new int[outIOBsites.length];

  // put probe pins on (unregistered) output of each LE in evolvable region
  // here we assign 2 slices to each col, and 2 LEs to each row
  int numrows=maxrow-minrow+1;
  int numcols=maxcol-mincol+1;
  growthProbes=new Pin[numrows*2][numcols*2];
  responseMatrix=new int[numrows*2][numcols*2];
  changeMatrix=new int[numrows*2][numcols*2];
  for (int i=0; i<numrows*2; i++) {
    for (int j=0; j<numcols*2; j++) {
      growthProbes[i][j]=makeCLBpin(minrow+(i/2),mincol+(j/2),j%2,i%2);
    }
  }

  // now determine what the direction of growth should be ...
  direction=-1;

  // if region is 1x1, then determine direction with a heuristic -
  // use the IOB edges to determine direction
  if (minrow==maxrow && mincol==maxcol) {
    // IOB sites are of form: SIDE,INDEX,SITE
    if (inIOBsites[0].charAt(0)=='L') { 
      direction=LtoR; // left to right
    } else if (inIOBsites[0].charAt(0)=='R') { 
      direction=RtoL; // right to left
    } else if (inIOBsites[0].charAt(0)=='T') { 
      direction=TtoB; // top to bottom
    } else { // if (inIOBsites[0].charAt(0)=='B') { 
      direction=BtoT; // bottom to top
    }

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

  // the # elems that have signal changes in a layer
  // we add 1 extra layer to represent the output IOBs
  layerchangecnt=new int[numlayers+1];
  propogationfromin=0;

  startUpVirtexDS();

  // initialise the probe values
  updateProbes();

  resetChangeCounters();

  // now, if we use the recursive connectivity test, then initialise it
  if (recurse) {
    // note shareouts and usedirectout are implicitly true,
    // as the simulator will use them
    connectivitytest=new TestInToOutRecurse(jbr,mincoord,maxcoord,inClbLEs,outClbLEs,true,true,false);
    // if we want to use signal propogation information (slow!) create a
    // reference to the changeMatrix in the connectivity test.
    // if we only use LUTActiveFN's then don't need this, as LUT functions
    // will only use their active inputs
    if (!ACTIVELUTFNSONLY) {
      connectivitytest.signalChangeMatrix=changeMatrix;
    }
  }

}


// for next round of stimulus signals to be applied
public void resetChangeCounters()
{
  int numrows=maxrow-minrow+1;
  int numcols=maxcol-mincol+1;

  // ensure change counters are set to 0
  for (int i=0; i<responseChange.length; i++) { responseChange[i]=0; }
  for (int i=0; i<layerchangecnt.length; i++) { layerchangecnt[i]=0; }

  for (int i=0; i<numrows*2; i++) {
    for (int j=0; j<numcols*2; j++) {
      changeMatrix[i][j]=0;
    }
  }
}


// update the count of elements in a layer that have had signal changes
// this is used to calculate fitness; and determine how far signals
// have propogated from inputs
void updateLayerChangeCounts()
{
  for (int i=0; i<numlayers+1; i++) {
    layerchangecnt[i]=getLayerChangeCnt(i);
  }
  propogationfromin=0;
  for (int i=0; i<numlayers+1; i++) {
    if (layerchangecnt[i]>0) { propogationfromin=i+1; }
    else { break; }
  }
}


// get the count of elements in the layer that have changing signals
// layer 0 is the input layer, layer 'numlayers' is the output response
// at the output bus IOBs
int getLayerChangeCnt(int l)
{
//System.err.print("getting layer "+l+" change cnt .. [");

  int[] lchange=(l==numlayers)?responseChange:getLayer(changeMatrix,l);
  int lcnt=cntNonZero(lchange);		    // count all the changing elems

//for (int ii=0;ii<lchange.length;ii++) { System.err.print(lchange[ii]+" "); }
//System.err.print("] lcnt="+lcnt+"->");
//System.err.print(lcnt+" .. ");

  return lcnt;
}


int cntNonZero(int[] x)
{
  int cnt=0;
  for (int i=0; i<x.length; i++) { cnt+=(x[i]!=0) ? 1 : 0; }
  return cnt;
}


public void disconnect()
{
  board.disconnect();
  client.close();
}


// apply stimulus signals (starting at offset) to input bus pins -
// one stimulus signal per pin

public void stimulate(int[] stimulus, int offset)
{
  int numPins=inbusPins.length;
//long stimulatetimestart=System.currentTimeMillis(); // for profiling info
//System.err.print("applying stimulus.. ");
  // apply the stimulus to the inbus
  if (SYNCHONLY) {
    for (int i=0; i<numPins; i++) {
      client.setPinValue(inbusPins[i],stimulus[offset+i]);
    }
  } else {
    // possible probs with client hanging unable to reconcile signals
    try {
      SafeStimulate stim=new SafeStimulate(client,inbusPins,stimulus,offset);
      Thread t=new Thread(stim);
      t.start();
      // wait upto 5 sec x input bus width
      for (int i=0; i<numPins*5*100 && !stim.finished; i++) {
        try {
          Thread.sleep(10); // wait 10 msec between checks
        } catch (InterruptedException e) { // if thread was interrupted
          // do nothing probably *shrug*
        }
      }
      if (!stim.finished) {
        throw new SimulationException("applying stimulus caused simulator to hang");
      }
    } catch (SimulationException e) { // simulator has hung
      // unblocking approach may not work either, so don't try to use it
      // may give an error such as: Invalid probe value from server
      // and then hang!
//System.err.println("simulator hung on applying stimulus...");
//System.err.print("applying stimulus to probes (unblocking).. ");
//for (int i=0; i<inbusPins.length; i++) {
//client.setPinValue(inbusPins[i],stimulus[offset+i],true); // unblocking
//}
//System.err.println("done.");
      System.err.println(e);
      e.printStackTrace();
      System.exit(1);
    }
  }

//System.err.println("done.");
//System.err.print("{S="+(System.currentTimeMillis()-stimulatetimestart)+"}"); // profiling
//long UPtimestart=System.currentTimeMillis(); // for profiling info

  // NOTE: the ordering of calls to updateProbes() followed by signalStep()
  // means that the probed values are from the previous clock cycle
  // (the cycle in which the signals were applied)

//System.err.print("updating probes.. ");
  // update the values of probed locations
  updateProbes();
//System.err.println("done.");
//System.err.print("+{UP="+(System.currentTimeMillis()-UPtimestart)+"}"); // profiling
//long CStimestart=System.currentTimeMillis(); // for profiling info

//System.err.print("stepping clock.. ");
  // and step the Virtex simulator clock
  try {
    signalStep();
  } catch (ConfigurationException e) { // simulator has hung
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
//System.err.println("done.");
//System.err.print("+{CS="+(System.currentTimeMillis()-CStimestart)+"}"); // profiling
//System.err.println("->{total="+(System.currentTimeMillis()-stimulatetimestart)+"}"); // profiling

}



// update the values of probed locations (outbus, and evolvable region)
public void updateProbes()
{
  probeOutbusPins();
  probeGrowthPins();
}


// step the virtex simulator by 1 clock step
// note that it is possible that VirtexDS may hang with some asynchronous
// configurations, so we use a 'safe' board clock stepper in another thread,
// that we can kill if it hangs.

public void signalStep() throws ConfigurationException
{
  if (SYNCHONLY) {
    board.clockStep(1);		// step the (simulator) board clock
  } else {
    // VirtexDS can hang on some asynchronous configurations, so we use
    // a 'safe' clock stepper in another thread, that we can kill if
    // it hangs.
    SafeClockStep stepper = new SafeClockStep(board,1);
    Thread t=new Thread(stepper);
    t.start();
    for (int i=0; i<3000 && !stepper.finished; i++) { // wait up to 30 secs
      try {
        Thread.sleep(10); // wait .025 seconds between checks
      } catch (InterruptedException e) { // if thread was interrupted
        // do nothing probably *shrug*
      }
    }
    if (!stepper.finished) {
      throw new ConfigurationException("stepping board caused simulator to hang");
    }
  }
}


// get the circuit response at the output bus to applied signals at input bus
public int[] getResponse() { return response; }

// gives a string representation for outputing a sequence of stimulus signals
// and the cct response.
// step is the current iteration, use step=-1 to print top heading and axis
// stimulus[] holds the array of input values, with offset indicating where
// in the array this iteration took inputs from
// response[] holds the values probed at the outputs
public String stimulusResponse(int step, int[] stimulus, int offset, int[] responses)
{
  int[] in=new int[inbusPins.length];
  for (int i=0; i<inbusPins.length; i++) {
    in[i]=stimulus[(i+offset)%stimulus.length]; // allow wrap-around
  }

  return stimulusResponse(step,in,responses);
}

public String stimulusResponse(int step, int[] in, int[] out)
{
  String line;
  if (step<0) { // indicates do heading and top axis
    // top axis will look like the following (eg of 2 in 2 out):
    //
    //  STEP |IN 02 01 |OUT 02 01 
    // ------|---------|----------
    line=" STEP |IN ";
    for (int i=in.length-1; i>=0; i--) {
      if (i<10) { line=line+"0"; }
      line=line+i+" ";
    }
    line=line+"|OUT ";
    for (int i=out.length-1; i>=0; i--) {
      if (i<10) { line=line+"0"; }
      line=line+i+" ";
    }
    line=line+"\n------|---";
    for (int i=0;i<in.length;i++) { line=line+"---"; }
    line=line+"|----";
    for (int i=0;i<out.length;i++) { line=line+"---"; }
  } else {
    line=" ";
    if (step<1000) { line=line+" "; }
    if (step<100) { line=line+" "; }
    if (step<10) { line=line+" "; }
    line=line+step+" |   ";
    for (int i=in.length-1; i>=0; i--) { line=line+" "+in[i]+" "; }
    line=line+"|    ";
    for (int i=out.length-1; i>=0; i--) { line=line+" "+out[i]+" "; }
    line=line+"|";
  }

  return line;
}



// get a string representation of cct response or cct response changes
public String strResponse() { return strResponse(false); }

public String strResponse(boolean change)
{
  String str="outbus";
  if (change) { str+= "changes"; }
  str+="[0-"+(response.length-1)+"]=";
  for (int i=0; i<response.length; i++) {
    str+= (change) ? responseChange[i] : response[i];
  }
  return str;
}



// this function tests the circuit for both connectivity and function
// connectivity can be tested using either TestInToOutRecurse's evaluation
// or using the simulator with probes at logic element outputs (YQ/XQ)
// with tests for signal changes at these probes. connectivity is measured
// based on how far signals propogate from inputs to outputs.
// functionality is tested by comparing how closely the connected circuit
// outputs match the desired function
// the two tests are combined by scaling their individual fitnesses by 1/2
// and then adding them together

double testCCT(int maxdelay)
{
  int numins=inlocs.length;
  int numouts=outlocs.length;
  int connectedins=0;
  int connectedouts=0;
  double propogatefitness=0;
  double functionfitness=0;

  // if we don't use the recursive connectivity test, or we use not just
  // active LUT functions (which guarantee that each LE's output will be a
  // function only of its inputs that are receiving changing signal values,
  // which will usually be (indirectly) from the circuit inputs) then need
  // to pass signals through the circuit to accurately determine connectivity
  // by using probe points on each LUT's registered output to show if that
  // LE is effected by the input signals
  if (connectivitytest==null || !ACTIVELUTFNSONLY) {
    functionfitness=testCCTresponse(maxdelay);

    if (VERBOSE) {
      System.err.println("\nsignal change matrix...");
      System.err.println(getChangeMatrix());
    }
  }


  if (connectivitytest==null) {
    // fitness based on signal propogation
    propogatefitness=calcSignalPropogationFitness();
    connectedouts=cntNonZero(responseChange);
    if (VERBOSE) { System.err.println(); }
  } else {
    // use the recursive circuit connectivity test to determine propogation
    propogatefitness=connectivitytest.evalCCT();
    if (VERBOSE) {
      System.err.println("connection states are...");
      System.err.println(connectivitytest.getStateMatrix());
    }
    for (int i=0; i<numins; i++) {
      if (connectivitytest.getBestInState(inlocs[i],i)>0) {
	connectedins++;
      }
    }
    for (int i=0; i<numouts; i++) {
      if (connectivitytest.getBestOutState(outlocs[i],i)==0xf) {
	connectedouts++;
      }
    }
    if (VERBOSE) {
      System.err.println("\nthere are "+connectedins+" connected inputs...");
      System.err.println("there are "+connectedouts+" connected outputs...\n"); 
    }

    // if only using LUTActiveFNs, then only need to pass signals through
    // circuit to determine circuit's functional behaviour, which only need
    // be done when there is at least one output connected to inputs
    if (ACTIVELUTFNSONLY && connectedouts>0) {
      functionfitness=testCCTresponse(maxdelay);
    }
  }

  if (VERBOSE) {
    System.err.print("connectivity fitness="+df.format(propogatefitness)+", ");
    System.err.println("weighting="+((ignorefunction)?"100%":"50%"));
    System.err.print("function fitness="+df.format(functionfitness)+", ");
    System.err.println("weighting="+((ignorefunction)?"0%":"50%"));
    System.err.println();
  }

  // if all outputs ignored, then fitness is based solely on signal propogation
  if (ignorefunction) { return propogatefitness; }

  return (functionfitness*0.5)+(propogatefitness*0.5);
}


// test the circuit by applying stimulus and calculating fitness based on
// the how close the cct's response matches the sum of inputs and how far
// signals are able to propogate from inputs to outputs, with signal
// delay being anything from minprop delay, to minprop + maxdelay

double testCCTresponse(int maxdelay)
{

  // before we can test the circuit, we need to upload the reconfigured
  // bitstream to the (simulator) board.

//long reconftimestart=System.currentTimeMillis(); // for profiling info
  try {
    reconfig(); // load bitstream to board
  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }
//System.err.println("reconfig time="+(System.currentTimeMillis()-reconftimestart)+"..."); // profiling

  // get minimum propogation delay for a synchronous signal
  int minsynchprop=getMinProp();
  // min propogation from inputs to outputs
  int minprop=(SYNCHONLY) ? minsynchprop : 0;

  // if no maxdelay provided, we default to allowing 2*minprop for total delay
  // if using synchronous signals only (eg S0_YQ). ie the minimum propogation
  // delay (unsampled), plus another minprop delay to give some leeway.
  // However, if using asynchronous signals (eg S0_Y) as well, then min prop
  // will be 0, but still need to allow delays for the synchronous signals in
  // the cicuit, so set maxdelay to double that of synchronous only circuits -
  // this is because the min delay of synch signals will still be minsynchprop,
  // and the max delay of synch signals will still be minsynchprop+maxdelay,
  // while the min delay for asynch signals will be 0.
  if (maxdelay<0) {
    maxdelay=(SYNCHONLY) ? minsynchprop : minsynchprop*2;
  }

  if (VERBOSE) {
    System.err.println("min propogation delay="+minprop);
    System.err.println("max propogation delay="+(minprop+maxdelay));
  }

  // get the (interleaved output) circuit response to the intputs
  int[] response=getDelayedResponse(functioninseq,minprop,maxdelay);

  // change matrix will have been updated when signals applied, so now
  // update the array of signal changes per layer (only need do once per
  // set of signals applied, at the end)
  updateLayerChangeCounts();

  // note that for disconnected circuits, some output signals will match
  // the desired output (eg unconnected LUTs output a 1 signal), but this
  // will skew the fitness test if taken into account (eg an uncconected
  // circuit could have a fitness of 50%, whereas a connected circuit that
  // produces the wrong function could get less, when obviously the latter
  // should actually be fitter, or at least no less fit. For this reason
  // we use the following to prevent unconnected outputs from contributing
  // to the fitness
  int[] connectedresponse=maskUnconnectedOutputSignals(response);
  if (VERBOSE) {
    System.err.println();
    System.err.print("actual response =");
    for (int i=0; i<response.length; i++) {System.err.print(" "+response[i]);}
    System.err.println();
    System.err.print("masked response =");
    for (int i=0; i<connectedresponse.length; i++) {
      if (connectedresponse[i]>=0) {
        System.err.print(" "+connectedresponse[i]);
      } else {
        System.err.print(" -"); // unconnected
      }
    }
    System.err.println();
  }

  // if all outputs are ignored, then function fitness is automatically 100%
  if (ignorefunction) {
    if (VERBOSE) { System.err.println("ignoring circuit outputs in fitness eval!"); }
    return 100;
  }

  return calcFnFitness(functionoutseq,connectedresponse,maxdelay);
}



// get num layers and add orthogonal distance to find the minimum
// propogation delay for a synchronous signal
int getMinProp()
{
  return numlayers + ((direction==LtoR || direction==RtoL) ?
                      (Math.abs(inlocs[0].row-outlocs[0].row)) :
                      (Math.abs(inlocs[0].col-outlocs[0].col)));
}


// (note that examples here are for a 1-bit full adder)
// this method applies the supplied sequence of signals (sigseq) in interleaved
// format (eg x1 y1 cin1 x2 y2 cin2 ..) to the inputs, with zeroes for the
// following maxdelay signals, and zero padding after for the last 'minprop'
// signals. for the maxdelay signals, anything will do, so long as it is
// unrelated to the last few signals, which could confuse the calculation of
// the hamming distance between offset circuit response and the truth table
//
// returns the delayed circuit response at each of the outputs in
// interleaved format in the response array (eg [sum0 cout0 sum1 cout1 .. ])
// the response is provided beginning at the minimum signal propogation delay
// to min prop + maxdelay plus the length of the sequence
//
// note that although all inputs and outputs are used for passing signals
// only the first input and output have the minimum propogation delay
// calculated properly

int[] getDelayedResponse(int[] sigseq, int minprop, int maxdelay)
{
  int inputwidth=inbusPins.length;
  int outputwidth=outbusPins.length;
  int siglen=sigseq.length/inputwidth; // # input sets (eg interleaved x,y,cin)

  // create stimulus that will be passed from inputs to outputs.
  // The total length of stimulus will be: siglen + maxdelay + MinProp
  // the last maxdelay signals will or wont be received depending on cct delay
  // the last MinProp signals will never be received at the outputs, as they
  // are just filler used to "push" the desired sequence to the outputs

  if (VERBOSE) { System.err.println("signal sequence len="+siglen); }
  int[] stimulus = new int[(siglen+maxdelay+minprop)*inputwidth];

  if (VERBOSE) { System.err.print("sequence is: "); }
  for (int i=0; i<sigseq.length; i++) {
    stimulus[i]=sigseq[i];
    if (VERBOSE) { System.err.print(" "+stimulus[i]); }
  }

  if (VERBOSE) { System.err.print(" + optfill: "); }
  for (int i=sigseq.length; i<sigseq.length+(maxdelay*inputwidth); i++) {
    stimulus[i]=0;
    if (VERBOSE) { System.err.print(" "+stimulus[i]); }
  }

  // these signals are just filler while the above sequence travels to outputs
  if (VERBOSE) { System.err.print(" + filler: "); }
  for (int i=sigseq.length+(maxdelay*inputwidth); i<stimulus.length; i++) {
    stimulus[i]=0;
    if (VERBOSE) { System.err.print(" "+stimulus[i]); }
  }

  if (VERBOSE) { System.err.println(); }

//long cleartimetaken=System.currentTimeMillis(); // for profiling info
  // this is for clearing out the signals from above, b4 testing cct
  // Note: we do this as we don't have dedicated lines routed to reset FFs
  int[] clearStimulus = new int[maxdelay*inputwidth]; // was siglen*inputwidth
  for (int i=0; i<clearStimulus.length; i++) { clearStimulus[i]=0; }

  if (VERBOSE) { System.err.println("clearing signals..."); }
  for (int i=0; i<clearStimulus.length/inputwidth; i++) {
    stimulate(clearStimulus,i*inputwidth);
  }
//cleartimetaken=System.currentTimeMillis()-cleartimetaken; //profiling
//System.err.println("clear time taken="+cleartimetaken+" ms"); // profiling

  // need to clear count of signal changes b4 applying a new set of signals
  resetChangeCounters();

  if (VERBOSE) { System.err.println("\npre-stimulus probe states are...\n"); }
  if (VERBOSE) { System.err.println(strResponse(false)); }
  if (VERBOSE) { System.err.println(getProbeMatrix()); }

//if (VERBOSE) { System.err.println("\nsignal change matrix...\n"); }
//if (VERBOSE) { System.err.println(getChangeMatrix()); }

  if (VERBOSE) { System.err.println("ready! applying stimulus to cct...\n"); }
  if (VERBOSE) {
    System.err.print(stimulusResponse(-1,stimulus,0,getResponse()));
    System.err.println("|  sampled");
  }

//long mproptimetaken=System.currentTimeMillis(); // for profiling info
  // the first MinProp (min propogation delay) responses are ignored
  for (int i=0; i<minprop; i++) {
    stimulate(stimulus,i*inputwidth);
    if (VERBOSE) {
      System.err.println(stimulusResponse(i,stimulus,i*inputwidth,getResponse()));
    }
  }
//mproptimetaken=System.currentTimeMillis()-mproptimetaken; //profiling
//System.err.println("minprop time taken="+mproptimetaken+" ms"); // profiling
 
  // the next siglen + maxdelay responses are sampled
  // these will be compared against the desired output signal
  int[] responseseq=new int[(siglen+maxdelay)*outputwidth];
  int[] sigresponse;
//long stimulatetimetaken=System.currentTimeMillis(); // for profiling info

  for (int i=0; i<siglen+maxdelay; i++) {
    stimulate(stimulus,(i+minprop)*inputwidth);
    sigresponse=getResponse();
    if (VERBOSE) {
      System.err.print(stimulusResponse(i+minprop,stimulus,(i+minprop)*inputwidth,response));
      System.err.println("     .");
    }
    for (int j=0; j<sigresponse.length; j++) {
      responseseq[(i*outputwidth)+j]=sigresponse[j];
    }
  }

//stimulatetimetaken=System.currentTimeMillis()-stimulatetimetaken; //profiling
//System.err.println("total stimulus sample response time taken="+stimulatetimetaken+" ms"); // profiling
  if (VERBOSE) { System.err.println("\nfinal probed signals are...\n"); }
  if (VERBOSE) { System.err.println(strResponse(false)); }
  if (VERBOSE) { System.err.println(getProbeMatrix()); }

  return responseseq;
}



// remove all output signals that don't change at all, indicating that the
// output is not connected to the inputs at all. output signals are
// effectively removed by giving them an impossible value (-1), which can
// never equal a desired output signal (assumes we do a comparison for
// equality)

int[] maskUnconnectedOutputSignals(int[] response)
{
  int[] cresponse = new int[response.length];
  for (int i=0; i<response.length; i++) { cresponse[i]=response[i]; }

  // we need to test whether the outputs are connected, by checking if
  // the signal changes at all - of course this assumes that the signal is
  // meant to change, which is the case for any outputs on a full adder

  int outputwidth=outbusPins.length;
  int outlen=response.length/outputwidth; // # output signals per output
  boolean connectedout;
//System.err.println("there are "+outputwidth+" interleaved outputs with "+outlen+" signals each");
  for (int i=0; i<outputwidth; i++) {	// which interleaved output
//System.err.println("checking out line "+i+" signals for changes..");
    connectedout=false;		// unconnected
    for (int j=0; j<outlen-1; j++) {
      // check if output signal changes at all
//System.err.print(" r["+((j*outputwidth)+i)+"],["+(((j+1)*outputwidth)+i)+"] ");
      if (response[(j*outputwidth)+i] != response[((j+1)*outputwidth)+i]) {
	// output changes so output is connected
        connectedout=true;
	break;
      }
    }
//System.err.println();
    // if output isn't connected, we don't want the output signal to
    // contribute at all to the fitness of the circuit, so we ensure that
    // it will never match the desired signal, by setting it to an impossible
    // signal value (-1). Note that signals < 0 indicate never match, while
    // signals > 1 indicate don't care
    if (!connectedout) {
      for (int j=0; j<outlen; j++) { cresponse[(j*outputwidth)+i]=-1; }
    }
  }

  return cresponse;
}


// We base fitness on how close the outputs match the required function, as
// specified by the truth table, and measured by the hamming distance, for
// delays minprop .. minprop+maxdelay, and choosing the one with the smallest
// Hamming distance to base the fitness on as a proportion of matching signals.
// Fitness is calculated as (L-H)/L, where L is 2^I x O; I is number of inputs,
// O is number of outputs, and H is the Hamming distance
//
// both sequence arrays are interleaved in the form [o1_1 o2_1 .. o1_2 o2_2 ..]
// truthtableseq is the proper interleaved responses
// responseseq is the actual (interleaved) responses, starting from the
//   minimum propogation delay and covering up to maxdelay plus the signal
//   sequence length
// maxdelay is the maximum delay on top of the propogation delay, to check

double calcFnFitness(int[] truthtableseq, int[] responseseq, int maxdelay)
{
  double fitness=0;
  int outputwidth=outbusPins.length;
  int siglen=truthtableseq.length; // interleaved desired output signals
  int minh=siglen; // no signals match
  int mini=-1; // if no signals match this will remain unchanged
  int h;

  if (VERBOSE) { System.err.println("\ncalculating Hamming distances of sampled signals to truthtable..\n"); }
  // find delay with smallest number of unmatching signals
  // where more than one with same number, we keep the closest to minprop
  // which is the first found, so don't need to check for this
  for (int i=0; i<=maxdelay; i++) {
    if (VERBOSE) {
      System.err.println("calculating for delay "+i+"..");
      System.err.print("sampled signals:  ");
      for (int j=0; j<siglen; j++) {
	if (responseseq[j+(i*outputwidth)]>=0) { // ie is it a connected output
          System.err.print(" "+responseseq[j+(i*outputwidth)]);
        } else {
          System.err.print(" -");
	}
      }
      System.err.println();
      System.err.print("truth table vals: ");
      for (int j=0; j<siglen; j++) {
        System.err.print(" "+truthtableseq[j]);
      }
      System.err.println();
    }
    if (HAMMINGTYPE==0) {
      h=calcHammingDistance(truthtableseq,responseseq,siglen,0,i*outputwidth);
      if (VERBOSE) { System.err.println("hamming dist="+h); }
    } else if (HAMMINGTYPE==1) {
      h=calcConsecHammingDistance(truthtableseq,responseseq,siglen/outputwidth,outputwidth,0,i*outputwidth);
      if (VERBOSE) { System.err.println("consecutive hamming dist="+h); }
    } else { // if (HAMMINGTYPE==2) {
      h=calcIncrHammingDistance(truthtableseq,responseseq,siglen/outputwidth,outputwidth,0,i*outputwidth);
      if (VERBOSE) { System.err.println("incremental hamming dist="+h); }
    }
    if (h<minh) { minh=h; mini=i; }
  }
  if (VERBOSE) { System.err.println("\nmin hamming dist="+minh+" at delay minprop+"+mini); }

  if (mini>=0) { // if some signals matched
    // base fitness on proportion of matching signals
    if (minh==0) {
      fitness=100; // just to avoid floating point errors
    } else {
      fitness=100*(double)(siglen-minh)/(double)siglen;
    }
  }

  if (VERBOSE) { System.err.println("percentage of matching signals="+df.format(fitness)); }
  if (VERBOSE) { System.err.println(); }

  return fitness;
}


// calculate hamming distance, _but_ ignore signals > 1 (usually 2),
// which are used to indicate X (don't care); and signals < 0 (usually -1)
// are used to indicate Z (unconnected - never match). note that don't care
// is given higher precedence than unnconnected
// both arrays are interleaved in the form [o1_1 o2_1 .. o1_2 o2_2 ..]
int calcHammingDistance(int[] a1, int[] a2, int len, int off1, int off2)
{
  int h=0;
  for (int i=0; i<len; i++) {
    if ((a1[off1+i]!=a2[off2+i]) && a1[off1+i]<2 && a2[off2+i]<2) { h++; }
  }
  return h;
}


// instead of using the hamming distance between the total set of
// output signals and the desired signals, we base the hamming distance
// on counting the max number of consecutive matching signals on each output
// note that ignore signals > 1 (usually 2), which are used to indicate X
// (don't care); while signals < 0 (usually -1) are used to indicate Z
// (unconnected) and will never match as they only ocur on outputs; also
// X is given higher precedence than Z.
// both arrays are interleaved in the form [o1_1 o2_1 .. o1_2 o2_2 ..]
int calcConsecHammingDistance(int[] a1, int[] a2, int siglen, int sigwidth, int off1, int off2)
{
  int h=siglen*sigwidth; 	   // start with no matches
  for (int i=0; i<sigwidth; i++) { // for each cct output
    int maxconsec=0;
    int consec=0;
    for (int j=0; j<siglen; j++) { // count the first N matching signals
      if ((a1[off1+i+(sigwidth*j)]==a2[off2+i+(sigwidth*j)]) ||
	   a1[off1+i+(sigwidth*j)]>1 || a2[off2+i+(sigwidth*j)]>1) {
	consec++;
	if (consec>maxconsec) { maxconsec=consec; }
      } else {
	consec=0;
      }
    }

    h=h-maxconsec; // decrease hamming dist by max consec matches on output
  }

  return h;
}


// same as calcConsecHammingDistance but here it is the number of consecutive
// matching signals at each output starting at the first signal
int calcIncrHammingDistance(int[] a1, int[] a2, int siglen, int sigwidth, int off1, int off2)
{
  int h=siglen*sigwidth; 	   // start with no matches
  for (int i=0; i<sigwidth; i++) { // for each cct output
    for (int j=0; j<siglen; j++) { // count the first N matching signals
      if ((a1[off1+i+(sigwidth*j)]==a2[off2+i+(sigwidth*j)]) ||
	   a1[off1+i+(sigwidth*j)]>1 || a2[off2+i+(sigwidth*j)]>1) {
	h--;
      } else {
	break;
      }
    }
  }
  return h;
}


// calculate the fitness based on how many layers have changing signal values,
// and how many elements in the layer change (up to the rounded down average
// of in and out bus sizes), except in input and output layers, where only use
// the input and output LEs. this is an attempt to measure the connectedness
// of the circuit from inputs to outputs

double calcSignalPropogationFitness()
{
  double fstep=(double)100/(double)numlayers;
  int avginout=(inbusPins.length+outbusPins.length)/2; // rounded down avg
  double fitness=0;
  double lfit=0;
  int lcnt=0;

  if (VERBOSE) { System.err.println(); }
  if (VERBOSE) { System.err.println("checking propogation of signals.."); }

  // evaluate layers
  for (int i=0; i<numlayers; i++) {
    lcnt=layerchangecnt[i];
    if (VERBOSE) { System.err.print("(layer["+i+"] cnt="+lcnt+") "); }
    if (lcnt>0) {
      // get the fitness metric of this layer (independent of other layers)
      // use average of in/out bus widths, except at output layer, where
      // where use number of outputs, or at input layer, where use # inputs
      if (i==0) {		  // input layer
        if (lcnt>inlocs.length) { lcnt=inlocs.length; }
        lfit=fstep*((double)lcnt/(double)inlocs.length);
      } else if (i<numlayers-1) { // "middle" of evolvable region
        if (lcnt>avginout) { lcnt=avginout; } // don't care if there are more
        lfit=fstep*((double)lcnt/(double)avginout);
      } else {			  // at output layer, or IOB outputs
        lcnt=cntNonZero(responseChange);
        lfit=fstep*((double)lcnt/(double)outlocs.length);
        if (VERBOSE) { System.err.println(cntNonZero(responseChange)+" output LEs connected"); }
      }
      // add this layer's fitness to the total fitness
      fitness+=lfit;
    }
  }
  if (VERBOSE && numlayers>2) { System.err.println(); }

  return fitness;
}


// this will show the signals at all the probe points in the evolvable area
// note each CLB takes up 2 cols (slices) and 2 rows (Y/X) in the probe matrix
// also note that this can't fit more than 15 cols of CLBs on a 80 char line,
// or more than 12 rows of CLBs (excluding horizontal axis) on screen with a
// 24 line terminal
public String getProbeMatrix() { return getMatrix(responseMatrix,false); }

// this does the same, except shows the number of signal changes at each
// probe, displaying "." if none, and if exceeds single digit, displays "*"
// this makes changes easier to spot, and stops confusion between probed
// values (0's and 1's) and counts of changes in signal
public String getChangeMatrix() { return getMatrix(changeMatrix,true); }


String getMatrix(int[][] matrix, boolean noZeros)
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
      if (r<10) {
	if (noZeros && r==0) {
	  row=row+".";			// easier to spot changes then
	} else {
	  row=row+r;
	}
      } else {
        row=row+"*";
      }
      row=row+" ";
    }
    row=row+rborder.charAt(i);
    row=row+" "+((i%2==0) ? "Y" : "X"); // right border shows if X or Y LE
    pmatrix=pmatrix+row;
  }

  pmatrix=pmatrix+"\n"+bborder+"\n"+baxis+"\n";

  return pmatrix;
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


public int getProbeMatrixEntry(LEcoord loc)
{
  return getMatrixEntry(responseMatrix,loc);
}

public int getChangeMatrixEntry(LEcoord loc)
{
  return getMatrixEntry(changeMatrix,loc);
}

// get entry from the matrix corresponding to the CLB/slice/le coord provided
public int getMatrixEntry(int[][] matrix, LEcoord loc)
{
  // note there are 2 slices to each col, and 2 LEs to each row
  int r=((loc.row-minrow)*2)+loc.LE;
  int c=((loc.col-mincol)*2)+loc.slice;
  return matrix[r][c];
}


// probe cct outputs at outbus IOBs, and update response change counter
void probeOutbusPins()
{
  int r;
  for (int i=0; i<outbusPins.length; i++) {
    r=probePin(outbusPins[i]);
    if(r!=response[i]) { responseChange[i]++; }
    response[i]=r;
  }
}

// probe all the (unregistered) LE outputs in the evolvable region
// and update the change counters of these
void probeGrowthPins()
{
  int r;
  // probe pins on the registered output of each LE in evolvable region
  // note we assign 2 slices to each col, and 2 LEs to each row
  int numrows=maxrow-minrow+1;
  int numcols=maxcol-mincol+1;
  for (int i=0; i<numrows*2; i++) {
//System.err.print("row "+i+"[");
    for (int j=0; j<numcols*2; j++) {
      r=probePin(growthProbes[i][j]);
//System.err.print(" "+r+" ");
      if (r!=responseMatrix[i][j]) {
	changeMatrix[i][j]++;
//System.err.println("["+i+"]["+j+"] "+responseMatrix[i][j]+"=>"+r);
      }
      responseMatrix[i][j]=r;
    }
//System.err.println("]");
  }
}


int probePin(Pin probe)
{
  int value=client.probePinValue(probe); // this will return -1 on error!
  try {
    if (value<0) {
      throw new SimulationException("probing pin "+probe+" returned error!");
    }
  } catch(SimulationException e) { // probably didn't resolve cct signals
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

  return value;
}


// iob string is in form of: side,index,siteidx
// where side is one of TOP,BOTTOM,LEFT,RIGHT, and siteidx is an int from 0-3
// dir is "in" or "out", indicating if its an input or output
Pin makeIOBpin(String iob, String dir)
{
  String[] fields=StringSplitter.split(iob,",");
  if (fields.length<3) {
    System.err.println("TestDelayInToOut:makeIOBpin(iob,dir): iob requires 3 comma delimited fields!");
    System.exit(1);
  }

  int index=Integer.parseInt(fields[1]);
  int siteidx=Integer.parseInt(fields[2]);

  int side=-1;
  int res=-1;

  if (fields[0].equals("TOP")) {
    side=IOB.TOP;
    res=(dir.equals("in")) ? IobWiresTop.I[siteidx] : IobWiresTop.O[siteidx];
  } else if (fields[0].equals("BOTTOM")) {
    side=IOB.BOTTOM;
    res=(dir.equals("in")) ? IobWiresBottom.I[siteidx] : IobWiresBottom.O[siteidx];
  } else if (fields[0].equals("LEFT")) {
    side=IOB.LEFT;
    res=(dir.equals("in")) ? IobWiresLeft.I[siteidx] : IobWiresLeft.O[siteidx];
  } else if (fields[0].equals("RIGHT")) {
    side=IOB.RIGHT;
    res=(dir.equals("in")) ? IobWiresRight.I[siteidx] : IobWiresRight.O[siteidx];
  } else {
    System.err.println("TestDelayInToOut:makeIOBpin(iob,dir): Invalid iob side field!");
    System.exit(1);
  }

  Pin iopin=new Pin(Pin.IOB,side,index,res);
  return iopin;
}



// we usually won't know which (LUT) lines will be getting driven
// so even for input CLBs, we just check the (registered) slice output
Pin makeCLBpin(String clbstr)
{
  LEcoord clb=new LEcoord(clbstr);
  return makeCLBpin(clb);
}

Pin makeCLBpin(LEcoord clb)
{
  return makeCLBpin(clb.row,clb.col,clb.slice,clb.LE);
}

Pin makeCLBpin(int row, int col, int slice, int LE)
{
  Pin clbpin=null;

  // note that when using registered slice outputs,
  // the signal won't change till the next clock step
  if (slice==0 && LE==0) {
    clbpin=new Pin(Pin.CLB,row,col,CenterWires.S0_YQ);
  } else if (slice==0 && LE==1) {
    clbpin=new Pin(Pin.CLB,row,col,CenterWires.S0_XQ);
  } else if (slice==1 && LE==0) {
    clbpin=new Pin(Pin.CLB,row,col,CenterWires.S1_YQ);
  } else if (slice==1 && LE==1) {
    clbpin=new Pin(Pin.CLB,row,col,CenterWires.S1_XQ);
  } else {
    System.err.println("TestDelayInToOut:makeCLBpin(): Invalid clb slice or le field!");
    System.exit(1);
  }
//System.err.println("added pin "+clbpin);
  return clbpin;
}



void startUpVirtexDS()
{
  String boardName=(SYNCHONLY) ? synchboardName : asynchboardName;

  try {
    // Connect to the VirtexDS
    board = XHWIF.Get(boardName);
    if (board == null) {
      System.err.println("can't get "+boardName);
    }

    String serverName = XHWIF.GetRemoteHostName(boardName);
    int port = XHWIF.GetPort(boardName);
    int result = board.connect(serverName,port);

    if (result != 0) {
      System.err.println("could not connect to "+boardName);
      System.exit(1);
    }

    if (VERBOSE) { System.err.println("connected to "+boardName); }

    // Reset the board
    if (VERBOSE) { System.err.print("reseting board ... "); }
    resetBoard();
    if (VERBOSE) { System.err.println("done"); }

    board.clockOn();

    // resets the partial reconfiguration flag to force a full reconfiguration
    if (VERBOSE) { System.err.println("forcing full bitstream reconfig... "); }
    jbits.clearPartial();

    // load bitstream to board
    reconfig();

    if (VERBOSE) { System.err.print("creating simulator client ... "); }
    client = new SimulatorClient();
    if (VERBOSE) { System.err.println("done"); }

  } catch (Exception e) {
    System.err.println(e);
    e.printStackTrace();
    System.exit(1);
  }

}



void resetBoard() throws ConfigurationException
{
  if (SYNCHONLY) {
    board.reset();
  } else {
    // VirtexDS can hang sometimes (on asynchronous configurations?), so we use
    // a 'safe' board in another thread, that we can kill if it hangs.
    SafeBoardReset resetter = new SafeBoardReset(board);
    Thread t=new Thread(resetter);
    t.start();
    for (int i=0; i<3000 && !resetter.finished; i++) { // wait up to 30 secs
    if (VERBOSE && i%25==0) { System.err.print("*"); }
      try {
        Thread.sleep(10); // wait 10 msec between checks
      } catch (InterruptedException e) { // if thread was interrupted
        // do nothing probably *shrug*
      }
    }
    if (VERBOSE) { System.err.print(" "); }
    if (!resetter.finished) {
      throw new ConfigurationException("reseting board caused board to hang");
    }
  }
}



// upload updated bitstream to board
void reconfig() throws ConfigurationException
{
  // generate partial bitstream (doesnt support BRAM partial reconfig packets)
  byte[] configBitstream = jbits.getPartial();
  // if no changes were made to the bitstream, it will be empty, and would
  // cause a null pointer error if try to upload it
  if (configBitstream!=null) {
    if (VERBOSE) { System.err.print("loading bitstream to board ... "); }
    if (SYNCHONLY) {
      board.setConfiguration(0, configBitstream); //  and upload to board
    } else {
      // VirtexDS can hang on some asynchronous configurations, so we use
      // a 'safe' bitstream loader in another thread, that we can kill if
      // it hangs.
      SafeBitstreamLoader loader=
			new SafeBitstreamLoader(board,configBitstream);
      Thread t=new Thread(loader);
      t.start();
      for (int i=0; i<3000 && !loader.finished; i++) { // wait up to 30 seconds
        if (VERBOSE && i%25==0) { System.err.print("*"); }
        try {
          Thread.sleep(10); // wait 10 msec between checks
        } catch (InterruptedException e) { // if thread was interrupted
	  // do nothing probably *shrug*
        }
      }
      if (VERBOSE) { System.err.print(" "); }
      if (!loader.finished) {
        // board hung on download!
	// if wished to continue (morphogenesis)
	// 	reset the board which will be left in an unknown state
	// 	exit download thread (ie kill it), supposedly using
	//	t.interrupt(), but not sure if this will work on board hang
	// instead we throw an exception, and exit process
	throw new ConfigurationException("Downloading bitstream caused board to hang");
      }
    }
    if (VERBOSE) { System.err.println("done"); }
  } else {
    if (VERBOSE) { System.err.println("partial configuration bitstream is empty (no changes made). skipping..."); }
  }
}


} // end class





// this implements the downloading of the bitstream to the VirtexDS simulator
// board as a separate thread, if it hangs the calling thread can kill it
class SafeBitstreamLoader implements Runnable {
  boolean finished=false;
  byte[] configBitstream=null;
  XHWIF board;

  SafeBitstreamLoader(XHWIF theboard, byte[] bitstream)
  {
    board=theboard;
    configBitstream=bitstream;
  }

  public void run()
  {
    finished=false;
    try {
      board.setConfiguration(0, configBitstream);
    } catch (Exception e) {
      // if downloading bitstream to board fails, then we terminate the whole
      // (JVM) process, not just (exit) this thread!
      System.err.println("SafeBitstreamLoader: "+e+"\n");
      System.exit(1);
    }
    finished=true;
  }
}




// this implements the stepping of the VirtexDS simulator board clock
// as a separate thread, if it hangs the instantiating thread can kill it
class SafeClockStep implements Runnable {
  boolean finished=false;
  XHWIF board;
  int count;

  SafeClockStep(XHWIF theboard, int cnt)
  {
    board=theboard;
    count=cnt;
  }

  public void run()
  {
    finished=false;
    board.clockStep(count);		// step the (simulator) board clock
    finished=true;
  }
}




// this implements the resetting of the VirtexDS simulator board
// as a separate thread, if it hangs the instantiating thread can kill it
class SafeBoardReset implements Runnable {
  
  boolean finished=false;
  XHWIF board;

  SafeBoardReset(XHWIF theboard)
  {
    board=theboard;
  }

  public void run()
  {
    finished=false;
    board.reset();		// reset the (simulator) board
    finished=true;
  }
}



// this implements applying signals to the cct input pins on the simulator
// as a separate thread, if it hangs the instantiating thread can kill it
class SafeStimulate implements Runnable {
  
  boolean finished=false;
  SimulatorClient client;
  Pin[] inbusPins;		// stimulus signals are placed here
  int[] stimulus;
  int offset;

  SafeStimulate(SimulatorClient sim, Pin[] pins, int[] signals, int off)
  {
    client=sim;
    inbusPins=pins;
    stimulus=signals;
    offset=off;
  }

  // apply stimulus signals (starting at offset) to input bus pins -
  // one stimulus signal per pin
  public void run()
  {
    finished=false;
    for (int i=0; i<inbusPins.length; i++) {
      client.setPinValue(inbusPins[i],stimulus[offset+i]);
    }
    finished=true;
  }
}



