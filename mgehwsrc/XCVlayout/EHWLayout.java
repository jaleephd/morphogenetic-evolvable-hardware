
//			 EHWLayout.java
//
// this class is used for determining the layout for morphogenesis on
// a Virtex FPGA. It decides the boundaries of the CLB region to be used,
// allocates input and output CLBs, for which single lines are available
// for connecting to the external input and output busses. These busses
// are routed to the LEs next to these input/output CLBs, from which the
// single lines carrying IO signals are driven.
//
// 		written Jan 2004, J.A. Lee
//
// this class also requires the local classes:
// 			XCV50bg256Board, RaptorXCV1000Board, LEcoord
//




/* CoreTemplate Net's, Bus'es, Pin's and stuff */
import com.xilinx.JBits.CoreTemplate.Net;
import com.xilinx.JBits.CoreTemplate.Bus;
import com.xilinx.JBits.CoreTemplate.Pin;
import com.xilinx.JBits.CoreTemplate.CoreException;

// this is needed to get net's source and sink pins
import com.xilinx.JBits.CoreTemplate.NetPins;

// this is needed for makeLocalConnections(NetPins)
import com.xilinx.JBits.Virtex.RTPCore.ULPrimitives.Prims2JBits;

// this is needed for defining (virtual) board connections with UCF file
import com.xilinx.JBits.Virtex.RTPCore.Iob.Board;

// this is needed for IOB coordinates (but gives wrong side!!!)
import com.xilinx.JBits.CoreTemplate.Side;

/* JRoute classes */
import com.xilinx.JRoute2.Virtex.JRoute;
import com.xilinx.JRoute2.Virtex.ResourceDB.CenterWires;
import com.xilinx.JRoute2.Virtex.ResourceDB.IobWiresLeft;
import com.xilinx.JRoute2.Virtex.ResourceDB.IobWiresRight;
import com.xilinx.JRoute2.Virtex.ResourceDB.IobWiresTop;
import com.xilinx.JRoute2.Virtex.ResourceDB.IobWiresBottom;
import com.xilinx.JRoute2.Virtex.ResourceDB.BramWires;
import com.xilinx.JRoute2.Virtex.RouteException;

/* low level stuff */
import  com.xilinx.JBits.Virtex.ConfigurationException;
import  com.xilinx.JBits.Virtex.ReadbackCommand;
import  com.xilinx.JBits.Virtex.Devices;
import  com.xilinx.JBits.Virtex.JBits;
import  com.xilinx.JBits.Virtex.ConfigurationException;
import  com.xilinx.JBits.Virtex.Bits.*;
import  com.xilinx.JBits.Virtex.Expr;
import  com.xilinx.JBits.Virtex.Util;


import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.PrintStream;


class EHWLayout {


public static void main(String args[])
{
  EHWLayout layout;
  Board board=null;
  int flags=0;
  int verbose=0;
  int clk=-1;
  boolean debugcct=false;

  if (args.length>0) {
    if (args[0].equals("-v") || args[0].equals("-d")) {
      flags=verbose=1;
      // this is to create a cct with verifiable output, by connecting
      // 1st input to 1st output
      if (args[0].equals("-d")) { debugcct=true; }
    }
    if (args.length-flags>1 && args[0+flags].equals("-c")) {
      clk=Integer.parseInt(args[1+flags]);
      flags+=2;
    }
  }
  if (args.length-flags!=8 && args.length-flags!=10) {
    System.err.println("Usage: EHWLayout [-v|-d] [-c GCLK#] (XCV50 | XCV1000) rows cols ucfFile inbus inbuswidth \\\n\t\t outbus outbuswidth [inbitfile outbitfile]");
    System.exit(1);
  }

  String device=args[0+flags];
  try {
    if (device.equals("XCV1000")) {
      board=new RaptorXCV1000Board("raptor");
    } else if (device.equals("XCV50")) {
      board=new XCV50bg256Board("xcv50");
    } else {
      System.err.println("Invalid board.. must be one of XCV50, XCV1000");
      System.exit(1);
    }
  } catch (CoreException ce) {
    System.out.println("Unable to setup board: "+ce);
    System.exit(1);
  }

  int rows=Integer.parseInt(args[1+flags]);
  int cols=Integer.parseInt(args[2+flags]);
  String ucf=args[3+flags];
  String inbus=args[4+flags];
  int inwidth=Integer.parseInt(args[5+flags]);
  String outbus=args[6+flags];
  int outwidth=Integer.parseInt(args[7+flags]);
  String nullbitstream=null;
  String outbitstream=null;
  if (args.length-flags==10) {
    nullbitstream=args[8+flags];
    outbitstream=args[9+flags];
  }
 
  // create layout according to command line specifications
  if (verbose==1) {
    layout = new EHWLayout(device,rows,cols,board,ucf,inbus,inwidth,outbus,outwidth,true);
  } else {
    layout = new EHWLayout(device,rows,cols,board,ucf,inbus,inwidth,outbus,outwidth);
  }

  // output details
  System.out.println("device "+device+" has CLB area of "+layout.numClbRows()+"x"+layout.numClbCols());
  System.out.println("allocated evolvable region of device is "+layout.minRow()+","+layout.minCol()+" to "+layout.maxRow()+","+layout.maxCol());
  LEcoord[] inclbs=layout.inClbs();
  LEcoord[] outclbs=layout.outClbs();
  System.out.print("input CLB locations are on "+layout.inbusSide()+" side:\n ");
  for (int i=0; i<inclbs.length; i++) { System.out.print(" "+inclbs[i]); }
  System.out.print("\noutput CLB locations are on "+layout.outbusSide()+" side:\n ");
  for (int i=0; i<outclbs.length; i++) { System.out.print(" "+outclbs[i]); }

  Pin[] inIobs=layout.inIobs();
  Pin[] outIobs=layout.outIobs();

  System.out.print("\ninput IOB locations:\n ");
  for (int i=0; i<inIobs.length; i++) { System.out.print(" "+inIobs[i]); }
  System.out.print("\noutput IOB locations:\n ");
  for (int i=0; i<outIobs.length; i++) { System.out.print(" "+outIobs[i]); }

  System.out.println();

  if (nullbitstream!=null) {
    // connect IOBs to singles in/out of input/output CLBs
    System.out.println("\nconnecting IOBs to IO CLBs input/output singles..");
    if (debugcct) { System.out.println("and routing from 1st inCLB to 1st outCLB with pass though LUTs in these.."); }
    boolean connected=layout.connectBitstream(nullbitstream,debugcct);

    // if the GClk buffer is provided, then connect all the slice Clk's in
    // the evolvable region to this GLKK. this is needed when evolving
    // synchronous circuits, but not evolving the slice Clk connections
    if (clk>-1) {
      layout.connectSliceClks(clk);
    }

    // if successfully connected save connected bitstream
    if (connected) {
      System.out.println("saving connected bitstream to file "+outbitstream+"..");
      layout.writeBitstream(outbitstream);
    }
  }

} // end main()

		// this is dodgy, but it seems that getSide()
		// gets the sides jumbled around
		final int	Side_LEFT=Side.BOTTOM;
		final int	Side_RIGHT=Side.TOP;
		final int	Side_TOP=Side.LEFT;
		final int	Side_BOTTOM=Side.RIGHT;

		boolean		VERBOSE;
		JBits		jbits;
		JRoute		jroute;
  		PrintStream	ps;
		String		UCFfile;
		int		deviceType;
		int		clbRows;
		int		clbCols;
		Board		board;
		Bus		inbus;
		Bus		outbus;
		Pin[]		inIOBs;
		Pin[]		outIOBs;
		int		inside;
		int		outside;

		int		minrow;
		int		mincol;
		int		maxrow;
		int		maxcol;

	// these are public so can manually override chosen sites if necessary
	public	LEcoord[]	inCLBs;
	public	LEcoord[]	outCLBs;


// public access methods

public int numClbRows() { return clbRows; }
public int numClbCols() { return clbCols; }
public int minRow() { return minrow; }
public int minCol() { return mincol; }
public int maxRow() { return maxrow; }
public int maxCol() { return maxcol; }
public String inbusSide() { return sideStr(inside); }
public String outbusSide() { return sideStr(outside); }
public LEcoord[] inClbs() { return inCLBs; }
public LEcoord[] outClbs() { return outCLBs; }
public Pin[] inIobs() { return inIOBs; }
public Pin[] outIobs() { return outIOBs; }


public EHWLayout(String devname, int numrows, int numcols,
        Board aboard, String ucfFilename,
    	String inBusName, int inwidth, String outBusName, int outwidth)
{
  this(devname,numrows,numcols,aboard,ucfFilename,inBusName,inwidth,outBusName,outwidth,false);
}

public EHWLayout(String devname, int numrows, int numcols,
        Board aboard, String ucfFilename,
    	String inBusName, int inwidth, String outBusName, int outwidth,
	boolean vrb)
{
  VERBOSE=vrb;
  deviceType=Devices.getDeviceType(devname);
  if (deviceType==Devices.UNKNOWN_DEVICE) {
    System.out.println("unrecognised device type ("+devname+")!");
    System.exit(1);
  } else if (!Devices.isSupported(deviceType)) {
    System.out.println("unsupported device type ("+devname+")!");
    System.exit(1);
  }

  jbits = new JBits(deviceType);


  if (VERBOSE) {
    // Set up a print stream object.
    ps=System.out;
    // create JRoute object that prints jbits.set() calls to System.out
    jroute = new JRoute(jbits,ps);
  } else {
    ps=null;
    jroute = new JRoute(jbits);
  }

  UCFfile=ucfFilename;
  board=aboard;

  // add the input and output busses to the board
  inbus=addBus(inBusName,inwidth,"in");
  outbus=addBus(outBusName,outwidth,"out");

  // get the IOBs that these connect to
  inIOBs=getBusIobs(inbus,"in");
  outIOBs=getBusIobs(outbus,"out");

  if (VERBOSE) {
    System.out.println("mapping package pins to IOBs...");
    for (int i=0; i<inwidth; i++) {
      System.out.println(inBusName+i+" => "+inIOBs[i]);
    }
    for (int i=0; i<outwidth; i++) {
      System.out.println(outBusName+i+" => "+outIOBs[i]);
    }
    System.out.println();
  }

  // find device's CLB matrix size
  clbRows = jbits.getClbRows();
  clbCols = jbits.getClbColumns();

  if (numrows>clbRows-2) {
    System.out.println("number of rows exceeds devices "+clbRows+" rows minus 2 for IO routing buffers!");
    System.exit(1);
  } else if (numcols>clbCols-2) {
    System.out.println("number of cols exceeds devices "+clbCols+" cols minus 2 for IO routing buffers!");
    System.exit(1);
  } else if (numrows<1 || numcols<1) {
    System.out.println("illegal negative number of rows or cols!");
    System.exit(1);
  }

  // define evolvable region to lie in the middle of the CLB matrix
  minrow=(clbRows-numrows)/2;
  mincol=(clbCols-numcols)/2;
  maxrow=minrow+numrows-1;
  maxcol=mincol+numcols-1;

  // choose sides to connect IOBs to, according to the location of
  // the majority of each busses IOBs
  inside=getIobsSide(inIOBs,"in");
  outside=getIobsSide(outIOBs,"out");


  // if both busses assigned same side, try to find next best side for
  // one of them
  if (inside==outside) {
    int in2side=getIobsSide(inIOBs,"in",true);
    int out2side=getIobsSide(outIOBs,"out",true);
    if (in2side<0 && out2side<0) { // no 2nd choice
      System.out.println("Input and Output busses are on same IOBs side!");
      System.exit(1);
    }
    // give in bus preference
    if (out2side<0) { inside=in2side; }
    else { outside=out2side; }
  }

  if (VERBOSE) {
    System.out.println(sideStr(inside)+" Clbs assigned to in bus, "+sideStr(outside)+" Clbs assigned to out bus");
  }
  // now assign the CLB/Slice/LEs that the IOBs will connect to 
  inCLBs=assignInCLBs(inside,inwidth);
  outCLBs=assignOutCLBs(outside,outwidth);

}


// return string representation of IOB side
String sideStr(int side)
{
  String str="Undefined";

  if (side==Side_LEFT) { str="LEFT"; }
  else if (side==Side_RIGHT) { str="RIGHT"; }
  else if (side==Side_BOTTOM) { str="BOTTOM"; }
  else if (side==Side_TOP) { str="TOP"; }

  return str;
}


// place input CLBs (LEs actually) next to each other, in center of edge
// with inputs being to the G-LUT if slice 0, F-LUT if slice 1
// this is to ensure it can work with 'slim' model where
// S1G1 has no West single line inputs
LEcoord[] assignInCLBs(int side, int buswidth)
{
  int row=0,col=0;
  LEcoord[] inCLBs=new LEcoord[buswidth];

  // note width is number of CLBs*2 = 2 slices/LEs per CLB
  int edgewidth=(side==Side_TOP || side==Side_BOTTOM) ?
  	(maxcol-mincol+1) * 2 : (maxrow-minrow+1) * 2 ;

  // edgeoffset is about the center of the edge
  int edgeoffset=(edgewidth-buswidth)/2;

  // get row/col coord of fixed row/col
  if (side==Side_TOP) { row=maxrow; }
  else if (side==Side_BOTTOM) { row=minrow; }
  else if (side==Side_LEFT) { col=mincol; }
  else { col=maxcol; } // side==Side_RIGHT

  // get lower index of slices, and initial slice #
//System.out.println("Input edgewidth="+edgewidth+", edgeoffset="+edgeoffset);
  // create array of LE coords for Bus, with each point being placed
  // at the next slice/LE, around the center of the edge
  for (int i=0; i<buswidth; i++) {
    if (side==Side_TOP || side==Side_BOTTOM) {
      col=mincol+((edgeoffset+i)/2);
    } else {
      row=minrow+((edgeoffset+i)/2);
    }
//System.out.println("i="+i+": LEcoord at "+row+","+col+","+((edgeoffset+i)%2)+","+((edgeoffset+i)%2));
     // limits in CLBs to use either S0G-Y or S1F-X
     inCLBs[i]=new LEcoord(row,col,(edgeoffset+i)%2,(edgeoffset+i)%2);
  }

  return inCLBs;
}


// spread output CLBs evenly across edge
// but with outputs being to the S0_Y/YQ, or S1_X/XQ
// this is to ensure it can work with 'slim' model where
// S0_X/XQ has no West single outputs, and  S1_Y/YQ has no South
LEcoord[] assignOutCLBs(int side, int buswidth)
{
  int sliceoffset;
// int LEoffset;
  int row=0,col=0;
  LEcoord[] outCLBs=new LEcoord[buswidth];

  // // note width is number of CLBs*4 = 2 LEs per slice
  // int edgewidth=(side==Side_TOP || side==Side_BOTTOM) ?
  //     (maxcol-mincol) * 4 : (maxrow-minrow) * 4 ;

  // note width is number of CLBs*2 = 2 slices per CLB
  int edgewidth=(side==Side_TOP || side==Side_BOTTOM) ?
  	(maxcol-mincol+1) * 2 : (maxrow-minrow+1) * 2 ;

  // get row/col coord of fixed row/col
  if (side==Side_TOP) { row=maxrow; }
  else if (side==Side_BOTTOM) { row=minrow; }
  else if (side==Side_LEFT) { col=mincol; }
  else { col=maxcol; } // side==Side_RIGHT

  // note we need to find the number of _intervals_, except when there
  // is only 1 line, where we just choose the middle of the edge
  float step=(buswidth==1) ? 0 : (float)(edgewidth-1)/(float)((buswidth-1));
//System.out.println("step="+step);
  // create array of LE coords for Bus, with each point being evenly
  // spaced across the edge of the CLB region
  for (int i=0; i<buswidth; i++) {
    sliceoffset=(buswidth==1) ? (edgewidth/2) : (int)(i*step);
    //LEoffset=(buswidth==1) ? (edgewidth/2) : (int)(i*step);
//System.out.println("i="+i+", sliceoffset="+sliceoffset);
    if (side==Side_TOP || side==Side_BOTTOM) {
      col=mincol+(sliceoffset/2);
      // col=mincol+(LEoffset/4);
    } else {
      row=minrow+(sliceoffset/2);
      // row=minrow+(LEoffset/4);
    }
    // outCLBs[i]=new LEcoord(row,col, (LEoffset%4)/2, LEoffset%2);
    if (sliceoffset%2==0) {
      outCLBs[i]=new LEcoord(row,col,0,0);
    } else {
      outCLBs[i]=new LEcoord(row,col,1,1);
    }
  }

  return outCLBs;
}



Bus addBus(String busname, int width, String dir)
{
  int dutout=0,dutin=0;

  if (width<1) {
    System.out.println("Invalid bus width: " + width);
    System.exit(1);
  }

  // bus name must match the resolved UCF net names - with index appended
  Bus dabus = new Bus(busname, null, width);

  try {
    // add the desired bus/net (defined in the ucf file)
    if (dir.equals("out")) {
      dutout=board.addOutput(busname, dabus);
      // Invert the TriState line (enables output) 
      board.setOutputInvertT(dutout, true);
      // Don't invert the actual output 
      board.setOutputInvertO(dutout, false);
    } else {
      dutin=board.addInput(busname, dabus);
      // possibly may need to use Vref, depending on I/O standard used:
      // 	Net vnet=new Net(vrefname,null);
      //	int vr=board.addInput(vrefname, vnet);
      // 	setInputVref(vr,true);
    }
    // configure the IOB's based upon the UCF file specifications
    board.implement(0, UCFfile); 
  } catch (CoreException ce) {
    System.out.println("Unable to setup board according to "+UCFfile+" specs..");
    System.out.println(ce);
    System.exit(1);
  }

  return dabus;
}



// get the sites (IOBs) associated with the Bus
Pin[] getBusIobs(Bus dabus, String dir)
{
  Net dabusnet = null;
  Pin[] iobs=new Pin[dabus.getWidth()];
  for (int i=0; i<dabus.getWidth(); i++) {
    dabusnet=dabus.getNet(i);
    iobs[i]=getNetSite(dabusnet,dir);
  }
  return iobs;
}



// get the side of the FPGA that the majority of the IOBs in the bus
// connect to or if this is the same side as the other bus, then get
// the side that the next most connected IOBs are on

int getIobsSide(Pin[] iobs, String dir)
{
  return getIobsSide(iobs,dir,false);
}

int getIobsSide(Pin[] iobs, String dir, boolean second)
{
  int[] sides = new int[] { Side_TOP, Side_BOTTOM, Side_LEFT, Side_RIGHT };
  int width=iobs.length;
  int[] cnt= new int[4];
  for (int i=0; i<4; i++) { cnt[i]=0; }
//for (int i=0; i<4; i++) { System.out.println("side["+i+"]="+sides[i]+" == "+sideStr(sides[i])); }

  // count the occurences of each IOB side
  int side;
  for (int i=0; i<width; i++) {
    side=iobs[i].getSide();
//System.out.println("*** i="+i+", side="+side+"="+sideStr(side)+"; "+iobs[i]);
    if (side==Side_TOP) { cnt[0]++; }
    if (side==Side_BOTTOM) { cnt[1]++; }
    if (side==Side_LEFT) { cnt[2]++; }
    if (side==Side_RIGHT) { cnt[3]++; }
  }

  // find the side with the highest count
  int hicnt=-1;
  int hicnti=-1;
  for (int i=0; i<4; i++) {
//System.out.println("*** side cnt["+i+"]="+cnt[i]);
    if (cnt[i]>hicnt) { hicnt=cnt[i]; hicnti=i; }
//System.out.println("*** hicnt="+hicnt+", hcnti="+i);
  }

  // now we find the second highest count if necessary
  int nxtcnt=-1;
  int nxtcnti=-1;

  if (second) {
    for (int i=0; i<4; i++) {
      if (i!=hicnti && cnt[i]>nxtcnt) { nxtcnt=cnt[i]; nxtcnti=i; }
    }
  }

  return (second) ? sides[nxtcnti] : sides[hicnti];
}



/*
   can't directly query the location of a Net - need Pins defined, which are
   usually assigned to a specific physical location, however, we don't know
   the location (of the net sink), so to do this we use NetPins, as follows:
   (from Javadoc for com.xilinx.JBits.CoreTemplate.NetPins)

   1. a NetPins object is created by calling
  		NetPins netPins = net.getNetPins();
      which stores the primitive source and sink ports that are reachable
      from net. Generally, net should be the segment defined at the highest
      level in the hierarchy.
   2. call
   		Prims2JBits.makeLocalConnections(netPins)
      which implements multiplexer-based connections within slices or
      hardwired connections between adjacent slices. The ignoreForRouting
      attribute will also be set for the source and sink ports of these
      local connections.
   3. if netPins.isLocal() returns false then netPins.enumeratePins() is
      called, which assigns pins to ports defining non-local connections.
      The sets of source and sink pins are also recorded in netPins.
   4. client methods such as JRoute.route(netPins) or XDL.connect(netPins)
      are called. 
*/


Pin getNetSite(Net n, String dir)
{
  Pin p=null;
  NetPins nps = null;

  // create NetPins, etc (steps 1-3 from Coretemplate.Netpins javadoc above)
  //System.out.println("creating NetPins from Net...");

  try {
    nps = n.getNetPins();
    Prims2JBits.makeLocalConnections(nps);
    if (!nps.isLocal()) { nps.enumeratePins(); }
    // if IOB is for an output net, then we use the IOB's outputs
    // (the 'O' resource) this means that we will query the sink pin to
    // get IOB coords. for input nets we use IOB inputs (the 'I' resource)
    // and so we need to query the source pin
    if (dir.equals("out")) {
      p=nps.getSinkPin(0);
    } else {
      p=nps.getSourcePin(0);
    }
  } catch(CoreException ce) {
    System.out.println(ce);
    System.exit(1);
  }

  return p;
}



void loadBitstream(String bitfile)
{
  //System.out.println("reading bitstream from file "+bitfile+" ...");
  try {
    jbits.read(bitfile);
  } catch (FileNotFoundException fe) {
    System.out.println("File "+bitfile+" not found!");
    System.out.println(fe);
    System.exit(1);
  } catch (IOException io1) {
    System.out.println("Error reading the bitstream file");
    System.out.println(io1);
    System.exit(1);
   } catch (ConfigurationException ce1) {
    System.out.println("Internal error detected in the bitstream");
    System.out.println(ce1);
    System.exit(1);
   }
}



public void writeBitstream(String outfilename)
{
  //System.out.println("writing bitstream to file "+outfilename+" ...");
  try {
    jbits.write(outfilename);
  } catch (IOException ioe) {
    System.out.println("Error writing file "+ outfilename + ". Exiting");
    System.out.println(ioe);
    System.exit(1);
  }
}



// connect input & output bus IOBs to LEs neighbouring the in/out CLBs
// and then connect these to single lines in the direction of the in/out CLBs
// but don't explicitly connect these to the in CLB LUTs, or to the OUT bus
// of out CLBs, as in/out CLBs are no different from other CLBs in the
// evolvable region of the FPGA, and hence can have their configurations
// changed at any time by the morphogenesis process
public boolean connectBitstream(String nullfile)
{
  return connectBitstream(nullfile,false);
}

public boolean connectBitstream(String nullfile, boolean connectIn0ToOut0)
{
  // load initial configuration
  loadBitstream(nullfile);

  // get the neighbouring CLBs to the input/output CLBs
  LEcoord[] inIOBclbs=getNeighbourCLBs(inCLBs,inside);
  LEcoord[] outIOBclbs=getNeighbourCLBs(outCLBs,outside);

  // route from IOBs to these connecting CLBs
  try {
    if (VERBOSE) {
      System.out.println("routing from IOBs to inIOBclbs:");
      for (int i=0; i<inIOBclbs.length; i++) { System.out.println("\t"+inIOBs[i]+" => "+inIOBclbs[i]+"   "); }
      System.out.println();
    }
    routeIOBs(inIOBs,inIOBclbs,"in");

    if (VERBOSE) {
      System.out.println("routing from outIOBclb to IOBs:");
      for (int i=0; i<outIOBclbs.length; i++) { System.out.println("\t"+outIOBclbs[i]+" => "+outIOBs[i]); }
      System.out.println();
    }
    routeIOBs(outIOBs,outIOBclbs,"out");
  } catch (RouteException re) {
    System.err.println("Error occurred in routing..");
    System.err.println(re);
    return false;		// routing not successful
  }

  // setup connecting CLBs to pass signals to input/output CLBs
  try {
    setupIobToCLBs(inIOBclbs);
    setupCLBsToIob(outIOBclbs);
  } catch (ConfigurationException ce) {
    System.err.println("Error configuring the Bitstream..");
    System.err.println(ce);
    return false;
  }

  // produce a connected cct from 1st input to 1st output - this is purely
  // for being able to verify that the cct does something when connected
  if (connectIn0ToOut0) {
    try {
      System.out.println("routing from inIOBclbs[0] to inCLBs[0]:");
      Pin inio=new Pin(Pin.CLB,inIOBclbs[0].row,inIOBclbs[0].col,getCLBtoIOBpinResource(inIOBclbs[0],"out"));
      Pin intoclb1=new Pin(Pin.CLB,inCLBs[0].row,inCLBs[0].col,getCLBtoIOBpinResource(inCLBs[0],"in"));
      jroute.route(inio,intoclb1);
      System.out.println("setting up inCLBs[0] LUT to pass input to output:");
      setupPassThroughLUT(inCLBs[0]);
      System.out.println("routing from inCLBs[0] to outCLBs[0]:");
      Pin outclb1=new Pin(Pin.CLB,inCLBs[0].row,inCLBs[0].col,getCLBtoIOBpinResource(inCLBs[0],"out"));
      Pin intoclb2=new Pin(Pin.CLB,outCLBs[0].row,outCLBs[0].col,getCLBtoIOBpinResource(outCLBs[0],"in"));
      jroute.route(outclb1,intoclb2);
      System.out.println("setting up outCLBs[0] LUT to pass input to output:");
      setupPassThroughLUT(outCLBs[0]);
      System.out.println("routing from outCLBs[0] to outIOBclbs[0]:");
      Pin outclb2=new Pin(Pin.CLB,outCLBs[0].row,outCLBs[0].col,getCLBtoIOBpinResource(outCLBs[0],"out"));
      Pin outio=new Pin(Pin.CLB,outIOBclbs[0].row,outIOBclbs[0].col,getCLBtoIOBpinResource(outIOBclbs[0],"in"));
      jroute.route(outclb2,outio);
    } catch (RouteException re) {
      System.err.println("Error occurred in routing..");
      System.err.println(re);
      return false;
    } catch (ConfigurationException ce) {
      System.err.println("Error occurred in setting up pass through LUTs..");
      System.err.println(ce);
      return false;
    }
  }

  return true;
}


public void connectSliceClks(int clk)
{
  if (VERBOSE) { System.out.print("connecting slice Clk's in evolvable region to GCLK"+clk+".. "); }
  for (int i=minrow; i<=maxrow; i++) {
    for (int j=mincol; j<=maxcol; j++) {
      setSliceClk(i,j,0,clk);
      setSliceClk(i,j,1,clk);
    }
  }
 if (VERBOSE) { System.out.println("done."); }
}


void setSliceClk(int row, int col, int slice, int clk)
{
  try {
    if (clk<0 || clk>3) {
      throw new ConfigurationException("illegal GCLK ("+clk+"), must be 0..3!");
    }
    if (slice==0) {
      if (clk==0) {
        jbits.set(row,col,S0Clk.S0Clk,S0Clk.GCLK0);
      } else if (clk==1) {
        jbits.set(row,col,S0Clk.S0Clk,S0Clk.GCLK1);
      } else if (clk==2) {
        jbits.set(row,col,S0Clk.S0Clk,S0Clk.GCLK2);
      } else { // (clk==3)
        jbits.set(row,col,S0Clk.S0Clk,S0Clk.GCLK3);
      }
    } else {
      if (clk==0) {
        jbits.set(row,col,S1Clk.S1Clk,S1Clk.GCLK0);
      } else if (clk==1) {
        jbits.set(row,col,S1Clk.S1Clk,S1Clk.GCLK1);
      } else if (clk==2) {
        jbits.set(row,col,S1Clk.S1Clk,S1Clk.GCLK2);
      } else { // (clk==3)
        jbits.set(row,col,S1Clk.S1Clk,S1Clk.GCLK3);
      }
    }
  } catch (ConfigurationException ce) {
    System.err.println(ce);
    ce.printStackTrace();
    System.exit(1);
  }
}



// get neighbouring CLBs going inwards perpendicular to edge
LEcoord[] getNeighbourCLBs(LEcoord[] clbs, int side)
{
  LEcoord[] nclbs=new LEcoord[clbs.length];
  int row=0, col=0;

  // note that rows start at 0 at the top, and cols at 0 on the left
  if (side==Side_TOP) { row=clbs[0].row+1; }
  else if (side==Side_BOTTOM) { row=clbs[0].row-1; }
  else if (side==Side_LEFT) { col=clbs[0].col-1; }
  else if (side==Side_RIGHT) { col=clbs[0].col+1; }

  for (int i=0; i<clbs.length; i++) {
    if (side==Side_TOP || side==Side_BOTTOM) { col=clbs[i].col; }
    else if (side==Side_LEFT || side==Side_RIGHT) { row=clbs[i].row; }
    nclbs[i]=new LEcoord(row,col,clbs[i].slice,clbs[i].LE);
  }

  return nclbs;
}



// setup the LEs in the CLBs that the IOBs are routed to, to do following:
// 	  	- set LUT FN to pass input (F1/G1) to output
//	  	- connect Y/X output to 2 outbus lines
//	  	- connect outbus lines to singles in direction of
//	  	  neighbouring input CLB
void setupIobToCLBs(LEcoord[] clbs) throws ConfigurationException
{
  for (int i=0; i<clbs.length; i++) {
    if (VERBOSE) { System.out.println("setting up inIOBclb["+i+"]..."); }
    setupPassThroughLUT(clbs[i]);	// LUT passes single input to output
    connectToOut(clbs[i]);	// connect X/Y output to singles via OUT bus
    if (VERBOSE) { System.out.println(); }
  }
}


// this is used to pass input signals to CLBs on input border of evolvable area
// because we only use the one logic element per slice with input signals,
// we can pass each slice's output to 4 outbus lines, and then connects these
// to singles in the direction of neighbouring input CLB
// this uses an arbitrary ordering for connecting of slices to OUT bus
void connectToOut(LEcoord clb) throws ConfigurationException
{
  int row=clb.row;
  int col=clb.col;

  if (VERBOSE) { System.out.println("connecting "+clb+" to 4 OUT bus lines..."); }
  if (clb.slice==0) {
    if (clb.LE==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT0.OUT0, OUT0.S0_Y);"); }
      jbits.set(clb.row, clb.col,OUT0.OUT0,OUT0.S0_Y);
      connectOutToSingles(clb.row,clb.col,0);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT1.OUT1, OUT1.S0_Y);"); }
      jbits.set(clb.row, clb.col,OUT1.OUT1,OUT1.S0_Y);
      connectOutToSingles(clb.row,clb.col,1);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT2.OUT2, OUT2.S0_Y);"); }
      jbits.set(clb.row, clb.col,OUT2.OUT2,OUT2.S0_Y);
      connectOutToSingles(clb.row,clb.col,2);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT3.OUT3, OUT3.S0_Y);"); }
      jbits.set(clb.row, clb.col,OUT3.OUT3,OUT3.S0_Y);
      connectOutToSingles(clb.row,clb.col,3);
    } else {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT0.OUT0, OUT0.S0_X);"); }
      jbits.set(clb.row, clb.col,OUT0.OUT0,OUT0.S0_X);
      connectOutToSingles(clb.row,clb.col,0);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT1.OUT1, OUT1.S0_X);"); }
      jbits.set(clb.row, clb.col,OUT1.OUT1,OUT1.S0_X);
      connectOutToSingles(clb.row,clb.col,1);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT2.OUT2, OUT2.S0_X);"); }
      jbits.set(clb.row, clb.col,OUT2.OUT2,OUT2.S0_X);
      connectOutToSingles(clb.row,clb.col,2);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT3.OUT3, OUT3.S0_X);"); }
      jbits.set(clb.row, clb.col,OUT3.OUT3,OUT3.S0_X);
      connectOutToSingles(clb.row,clb.col,3);
    }
  } else {
    if (clb.LE==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT4.OUT4, OUT4.S1_Y);"); }
      jbits.set(clb.row, clb.col,OUT4.OUT4,OUT4.S1_Y);
      connectOutToSingles(clb.row,clb.col,4);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT5.OUT5, OUT5.S1_Y);"); }
      jbits.set(clb.row, clb.col,OUT5.OUT5,OUT5.S1_Y);
      connectOutToSingles(clb.row,clb.col,5);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT6.OUT6, OUT6.S1_Y);"); }
      jbits.set(clb.row, clb.col,OUT6.OUT6,OUT6.S1_Y);
      connectOutToSingles(clb.row,clb.col,6);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT7.OUT7, OUT7.S1_Y);"); }
      jbits.set(clb.row, clb.col,OUT7.OUT7,OUT7.S1_Y);
      connectOutToSingles(clb.row,clb.col,7);
    } else {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT4.OUT4, OUT4.S1_X);"); }
      jbits.set(clb.row, clb.col,OUT4.OUT4,OUT4.S1_X);
      connectOutToSingles(clb.row,clb.col,4);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT5.OUT5, OUT5.S1_X);"); }
      jbits.set(clb.row, clb.col,OUT5.OUT5,OUT5.S1_X);
      connectOutToSingles(clb.row,clb.col,5);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT6.OUT6, OUT6.S1_X);"); }
      jbits.set(clb.row, clb.col,OUT6.OUT6,OUT6.S1_X);
      connectOutToSingles(clb.row,clb.col,6);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OUT7.OUT7, OUT7.S1_X);"); }
      jbits.set(clb.row, clb.col,OUT7.OUT7,OUT7.S1_X);
      connectOutToSingles(clb.row,clb.col,7);
    }
  }
}


// connect an outbus line to the singles in dir of input CLB
void connectOutToSingles(int row, int col, int line) throws ConfigurationException
{
  if (VERBOSE) { System.out.println("connecting CLB ("+row+","+col+") outbus "+line+" to singles..."); }
  if (inside==Side_TOP) {		// pass outputs to the south
    if (line==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH1);"); }
      jbits.set(row,col,OutMuxToSingle.OUT0_TO_SINGLE_SOUTH1,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH3);"); }
      jbits.set(row,col,OutMuxToSingle.OUT0_TO_SINGLE_SOUTH3,OutMuxToSingle.ON);
    } else if (line==1) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH0);"); }
      jbits.set(row,col,OutMuxToSingle.OUT1_TO_SINGLE_SOUTH0,OutMuxToSingle.ON);
    } else if (line==2) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH5);"); }
      jbits.set(row,col,OutMuxToSingle.OUT2_TO_SINGLE_SOUTH5,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH7);"); }
      jbits.set(row,col,OutMuxToSingle.OUT2_TO_SINGLE_SOUTH7,OutMuxToSingle.ON);
    } else if (line==3) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH10);"); }
      jbits.set(row,col,OutMuxToSingle.OUT3_TO_SINGLE_SOUTH10,OutMuxToSingle.ON);
    } else if (line==4) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH13);"); }
      jbits.set(row,col,OutMuxToSingle.OUT4_TO_SINGLE_SOUTH13,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH15);"); }
      jbits.set(row,col,OutMuxToSingle.OUT4_TO_SINGLE_SOUTH15,OutMuxToSingle.ON);
    } else if (line==5) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH12);"); }
      jbits.set(row,col,OutMuxToSingle.OUT5_TO_SINGLE_SOUTH12,OutMuxToSingle.ON);
    } else if (line==6) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH17);"); }
      jbits.set(row,col,OutMuxToSingle.OUT6_TO_SINGLE_SOUTH17,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH19);"); }
      jbits.set(row,col,OutMuxToSingle.OUT6_TO_SINGLE_SOUTH19,OutMuxToSingle.ON);
    } else if (line==7) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_SOUTH22);"); }
      jbits.set(row,col,OutMuxToSingle.OUT7_TO_SINGLE_SOUTH22,OutMuxToSingle.ON);
    }
  } else if (inside==Side_BOTTOM) {	// pass outputs to the north
    if (line==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH0);"); }
      jbits.set(row,col,OutMuxToSingle.OUT0_TO_SINGLE_NORTH0,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH1);"); }
      jbits.set(row,col,OutMuxToSingle.OUT0_TO_SINGLE_NORTH1,OutMuxToSingle.ON);
    } else if (line==1) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH2);"); }
      jbits.set(row,col,OutMuxToSingle.OUT1_TO_SINGLE_NORTH2,OutMuxToSingle.ON);
    } else if (line==2) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH6);"); }
      jbits.set(row,col,OutMuxToSingle.OUT2_TO_SINGLE_NORTH6,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH8);"); }
      jbits.set(row,col,OutMuxToSingle.OUT2_TO_SINGLE_NORTH8,OutMuxToSingle.ON);
    } else if (line==3) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH9);"); }
      jbits.set(row,col,OutMuxToSingle.OUT3_TO_SINGLE_NORTH9,OutMuxToSingle.ON);
    } else if (line==4) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH12);"); }
      jbits.set(row,col,OutMuxToSingle.OUT4_TO_SINGLE_NORTH12,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH13);"); }
      jbits.set(row,col,OutMuxToSingle.OUT4_TO_SINGLE_NORTH13,OutMuxToSingle.ON);
    } else if (line==5) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH14);"); }
      jbits.set(row,col,OutMuxToSingle.OUT5_TO_SINGLE_NORTH14,OutMuxToSingle.ON);
    } else if (line==6) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH18);"); }
      jbits.set(row,col,OutMuxToSingle.OUT6_TO_SINGLE_NORTH18,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH20);"); }
      jbits.set(row,col,OutMuxToSingle.OUT6_TO_SINGLE_NORTH20,OutMuxToSingle.ON);
    } else if (line==7) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_NORTH21);"); }
      jbits.set(row,col,OutMuxToSingle.OUT7_TO_SINGLE_NORTH21,OutMuxToSingle.ON);
    }
  } else if (inside==Side_LEFT) {	// pass outputs to the east
    if (line==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST2);"); }
      jbits.set(row,col,OutMuxToSingle.OUT0_TO_SINGLE_EAST2,OutMuxToSingle.ON);
    } else if (line==1) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST3);"); }
      jbits.set(row,col,OutMuxToSingle.OUT1_TO_SINGLE_EAST3,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST5);"); }
      jbits.set(row,col,OutMuxToSingle.OUT1_TO_SINGLE_EAST5,OutMuxToSingle.ON);
    } else if (line==2) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST6);"); }
      jbits.set(row,col,OutMuxToSingle.OUT2_TO_SINGLE_EAST6,OutMuxToSingle.ON);
    } else if (line==3) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST8);"); }
      jbits.set(row,col,OutMuxToSingle.OUT3_TO_SINGLE_EAST8,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST11);"); }
      jbits.set(row,col,OutMuxToSingle.OUT3_TO_SINGLE_EAST11,OutMuxToSingle.ON);
    } else if (line==4) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST14);"); }
      jbits.set(row,col,OutMuxToSingle.OUT4_TO_SINGLE_EAST14,OutMuxToSingle.ON);
    } else if (line==5) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST15);"); }
      jbits.set(row,col,OutMuxToSingle.OUT5_TO_SINGLE_EAST15,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST17);"); }
      jbits.set(row,col,OutMuxToSingle.OUT5_TO_SINGLE_EAST17,OutMuxToSingle.ON);
    } else if (line==6) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST18);"); }
      jbits.set(row,col,OutMuxToSingle.OUT6_TO_SINGLE_EAST18,OutMuxToSingle.ON);
    } else if (line==7) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST20);"); }
      jbits.set(row,col,OutMuxToSingle.OUT7_TO_SINGLE_EAST20,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_EAST23);"); }
      jbits.set(row,col,OutMuxToSingle.OUT7_TO_SINGLE_EAST23,OutMuxToSingle.ON);
    }
  } else { // inside==Side_RIGHT	// pass outputs to the west
    if (line==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST7);"); }
      jbits.set(row,col,OutMuxToSingle.OUT0_TO_SINGLE_WEST7,OutMuxToSingle.ON);
    } else if (line==1) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST4);"); }
      jbits.set(row,col,OutMuxToSingle.OUT1_TO_SINGLE_WEST4,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST5);"); }
      jbits.set(row,col,OutMuxToSingle.OUT1_TO_SINGLE_WEST5,OutMuxToSingle.ON);
    } else if (line==2) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST9);"); }
      jbits.set(row,col,OutMuxToSingle.OUT2_TO_SINGLE_WEST9,OutMuxToSingle.ON);
    } else if (line==3) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST10);"); }
      jbits.set(row,col,OutMuxToSingle.OUT3_TO_SINGLE_WEST10,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST11);"); }
      jbits.set(row,col,OutMuxToSingle.OUT3_TO_SINGLE_WEST11,OutMuxToSingle.ON);
    } else if (line==4) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST19);"); }
      jbits.set(row,col,OutMuxToSingle.OUT4_TO_SINGLE_WEST19,OutMuxToSingle.ON);
    } else if (line==5) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST16);"); }
      jbits.set(row,col,OutMuxToSingle.OUT5_TO_SINGLE_WEST16,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST17);"); }
      jbits.set(row,col,OutMuxToSingle.OUT5_TO_SINGLE_WEST17,OutMuxToSingle.ON);
    } else if (line==6) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST21);"); }
      jbits.set(row,col,OutMuxToSingle.OUT6_TO_SINGLE_WEST21,OutMuxToSingle.ON);
    } else if (line==7) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST22);"); }
      jbits.set(row,col,OutMuxToSingle.OUT7_TO_SINGLE_WEST22,OutMuxToSingle.ON);
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", OutMuxToSingle.OUT"+line+"_TO_SINGLE_WEST23);"); }
      jbits.set(row,col,OutMuxToSingle.OUT7_TO_SINGLE_WEST23,OutMuxToSingle.ON);
    }
  }
}



// setup the LEs in the CLBs that are routed to the IOBs, to do following:
// 	  	- set LUT FN to pass input (S0F4|S1F1/S0G4|S1G1) to output
// 	  	- connect LUT input to single from direction of
// 	  	  neighbouring output CLB
void setupCLBsToIob(LEcoord[] clbs) throws ConfigurationException
{
  for (int i=0; i<clbs.length; i++) {
    if (VERBOSE) { System.out.println("setting up outIOBclbs["+i+"]..."); }
    setupPassThroughLUT(clbs[i]);	// LUT passes single input to output
    connectLUTinputs(clbs[i]);		// connect singles from dir of IO CLB
    if (VERBOSE) { System.out.println(); }
  }

}



// connect single from direction of output CLB in evolvable region to
// pass-through LUT in CLB that is routed to output IOB.
// these single line inputs have been allocated, so as to work with the 'slim'
// model (and where lines missing in slim model, then 'trim' model limes have
// been used) and with the expectation that generally output cells will be
// S0G-Y or S1F-X, but with allowances made if otherwise.
void connectLUTinputs(LEcoord clb) throws ConfigurationException
{
  int row=clb.row;
  int col=clb.col;

  if (VERBOSE) { System.out.print("connecting LUT input for "+clb+" from single "); }
  if (outside==Side_TOP) {		// take inputs from the south
    if (clb.slice==0) {
      if (clb.LE==0) {	// S0G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0G4.S0G4, S0G4.SINGLE_SOUTH2);"); }
        jbits.set(clb.row, clb.col,S0G4.S0G4,S0G4.SINGLE_SOUTH2);
      } else {		// S0F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0F4.S0F4, S0F4.SINGLE_SOUTH9);"); }
        jbits.set(clb.row, clb.col,S0F4.S0F4,S0F4.SINGLE_SOUTH9);
      }
    } else {
      if (clb.LE==0) {	// S1G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1G1.S1G1, S1G1.SINGLE_SOUTH18);"); }
        jbits.set(clb.row, clb.col,S1G1.S1G1,S1G1.SINGLE_SOUTH18);
      } else {		// S1F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1F1.S1F1, S1F1.SINGLE_SOUTH8);"); }
        jbits.set(clb.row, clb.col,S1F1.S1F1,S1F1.SINGLE_SOUTH8);
      }
    }
  } else if (outside==Side_BOTTOM) {	// take inputs from the north
    if (clb.slice==0) {
      if (clb.LE==0) {	// S0G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0G4.S0G4, S0G4.SINGLE_NORTH3);"); }
        jbits.set(clb.row, clb.col,S0G4.S0G4,S0G4.SINGLE_NORTH3);
      } else {		// S0F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0F4.S0F4, S0F4.SINGLE_NORTH10);"); }
        jbits.set(clb.row, clb.col,S0F4.S0F4,S0F4.SINGLE_NORTH10);
      }
    } else {
      if (clb.LE==0) {	// S1G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1G1.S1G1, S1G1.SINGLE_NORTH5);"); }
        jbits.set(clb.row, clb.col,S1G1.S1G1,S1G1.SINGLE_NORTH5);
      } else {		// S1F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1F1.S1F1, S1F1.SINGLE_NORTH7);"); }
        jbits.set(clb.row, clb.col,S1F1.S1F1,S1F1.SINGLE_NORTH7);
      }
    }
  } else if (outside==Side_LEFT) {	// take inputs from the east
    if (clb.slice==0) {
      if (clb.LE==0) {	// S0G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0G4.S0G4, S0G4.SINGLE_EAST4);"); }
        jbits.set(clb.row, clb.col,S0G4.S0G4,S0G4.SINGLE_EAST4);
      } else {		// S0F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0F4.S0F4, S0F4.SINGLE_EAST22);"); }
        jbits.set(clb.row, clb.col,S0F4.S0F4,S0F4.SINGLE_EAST22);
      }
    } else {
      if (clb.LE==0) {	// S1G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1G1.S1G1, S1G1.SINGLE_EAST19);"); }
        jbits.set(clb.row, clb.col,S1G1.S1G1,S1G1.SINGLE_EAST19);
      } else {		// S1F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1F1.S1F1, S1F1.SINGLE_EAST16);"); }
        jbits.set(clb.row, clb.col,S1F1.S1F1,S1F1.SINGLE_EAST16);
      }
    }
  } else { // outside==Side_RIGHT	// take inputs from the west
    if (clb.slice==0) {
      if (clb.LE==0) {	// S0G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0G4.S0G4, S0G4.SINGLE_WEST3);"); }
        jbits.set(clb.row, clb.col,S0G4.S0G4,S0G4.SINGLE_WEST3);
      } else {		// S0F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S0F4.S0F4, S0F4.SINGLE_WEST14);"); }
        jbits.set(clb.row, clb.col,S0F4.S0F4,S0F4.SINGLE_WEST14);
      }
    } else {
      if (clb.LE==0) {	// S1G LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1G1.S1G1, S1G1.SINGLE_WEST14);"); }
        jbits.set(clb.row, clb.col,S1G1.S1G1,S1G1.SINGLE_WEST14);
      } else {		// S1F LUT
	if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", S1F1.S1F1, S1F1.SINGLE_WEST11);"); }
        jbits.set(clb.row, clb.col,S1F1.S1F1,S1F1.SINGLE_WEST11);
      }
    }
  }
}


// setup the LUT to pass its input to output
void setupPassThroughLUT(LEcoord clb) throws ConfigurationException
{
  int row=clb.row;
  int col=clb.col;

  if (VERBOSE) { System.out.println("setting up "+clb+" LUT to pass single input to output..."); }
  // LUT FN = pass (sole) input to output (choose input with most lines avail)
  int  inout_F[] = (clb.slice==0) ? Expr.F_LUT("F4") : Expr.F_LUT("F1");
  int  inout_G[] = (clb.slice==0) ? Expr.G_LUT("G4") : Expr.G_LUT("G1");

  int[] inout=(clb.LE==0) ? inout_G : inout_F;
  int[] lut = Util.InvertIntArray(inout);

  if (clb.slice==0) {
    if (clb.LE==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", LUT.SLICE0_G, Expr.G_LUT(\"G4\"));"); }
      jbits.set(clb.row, clb.col, LUT.SLICE0_G, lut);
    } else {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", LUT.SLICE0_F, Expr.F_LUT(\"F4\"));"); }
      jbits.set(clb.row, clb.col, LUT.SLICE0_F, lut);
    }
  } else {
    if (clb.LE==0) {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", LUT.SLICE1_G, Expr.G_LUT(\"G1\"));"); }
      jbits.set(clb.row, clb.col, LUT.SLICE1_G, lut);
    } else {
      if (VERBOSE) { System.out.println("jbits.set("+row+", "+col+", LUT.SLICE1_F, Expr.F_LUT(\"F1\"));"); }
      jbits.set(clb.row, clb.col, LUT.SLICE1_F, lut);
    }
  }
}



// route the IOBs to the CLBs neighbouring the input/output CLBs
void routeIOBs(Pin[] iobs, LEcoord[] clbs, String dir) throws RouteException
{
  // for each CLB assign the resources on CLB that IOB will route to/from,
  // create a pin there, and then route the IOB-CLB
  int pinres;
  Pin[] clbpins=new Pin[clbs.length];

  for (int i=0; i<clbs.length; i++) {
    if (VERBOSE) {
      if (dir.equals("in")) { System.out.println("routing inbus line "+i+" from IOB to CLB..."); }
      else { System.out.println("routing outbus line "+i+" from CLB to IOB..."); }
    }
    pinres=getCLBtoIOBpinResource(clbs[i],dir);
    clbpins[i]=new Pin(Pin.CLB,clbs[i].row,clbs[i].col,pinres);
    connectIOBtoCLB(iobs[i],clbpins[i],dir);
    if (VERBOSE) { System.out.println(); }
  }
}


// get the resource on CLB for Pin that IOB will connect to, these are:
// out: S0_Y, S1_Y, S0_X, S1_X
// in:  S0_F4, S1_F1, S0_G4, S1_G1
int getCLBtoIOBpinResource(LEcoord clb, String dir)
{
  int resource;

  if (dir.equals("in")) {
    if (clb.slice==0) {
      resource=(clb.LE==0) ? CenterWires.S0_G4 : CenterWires.S0_F4;
    } else {
      resource=(clb.LE==0) ? CenterWires.S1_G1 : CenterWires.S1_F1;
    }
  } else {
    if (clb.slice==0) {
      resource=(clb.LE==0) ? CenterWires.S0_Y : CenterWires.S0_X; 
    } else {
      resource=(clb.LE==0) ? CenterWires.S1_Y : CenterWires.S1_X;
    }
  }

  return resource;
}


void connectIOBtoCLB(Pin iobPin, Pin clbPin, String dir) throws RouteException
{
  // connect IOB and CLB pins
  try {
    if (dir.equals("in")) {
      if (VERBOSE) { System.out.println("Routing from "+iobPin+" to "+clbPin+"..."); }
      jroute.route(iobPin,clbPin);
    } else {
      if (VERBOSE) { System.out.println("Routing from "+clbPin+" to "+iobPin+"..."); }
      jroute.route(clbPin,iobPin);
    }
  } catch (RouteException re) {
    if (dir.equals("in")) {
      System.out.print("Error routing IOB - CLB... "); 
    } else {
      System.out.print("Error routing CLB - IOB... "); 
    }
    System.err.println(re);
    throw re;
  }
  //System.out.println("Done Routing...");
}



} // end class



