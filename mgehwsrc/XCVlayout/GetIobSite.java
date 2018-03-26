

/*
 * 			GetIobSite.Java
 *
 * translates Net names in a UCF file to IOB site and coordinates
 * for a XCV1000 in a bg560 package (on a Raptor2000 board).
 *
 * 		written by J.A. Lee, 29/8/2003
 *
 * requires RaptorXCV1000Board class in same directory, and
 * to be defined, as follows:

	import com.xilinx.JBits.Virtex.RTPCore.Iob.*;
	import com.xilinx.JBits.CoreTemplate.CoreException;

	public class RaptorXCV1000Board extends Board
	{

	public RaptorXCV1000Board(String name) throws CoreException
	{
	  super(name);
	  setXcvPackage(xcvPackage);
	  setGCLK(GCLK);
	}

	private XCVPackage xcvPackage[] = { new xcv1000_bg560() };
	private static int GCLK = 2;
	// Raptor ucf file gives: L_CLK LOC=D17; D17 corresponds to GCLKPAD2

	};

 */



/* CoreTemplate Net's, Bus'es, Pin's and stuff */
import com.xilinx.JBits.CoreTemplate.Net;
import com.xilinx.JBits.CoreTemplate.Bus;
import com.xilinx.JBits.CoreTemplate.Pin;
import com.xilinx.JBits.CoreTemplate.CoreException;

// this is needed to get net's source and sink pins
import com.xilinx.JBits.CoreTemplate.NetPins;

// this is needed for makeLocalConnections(NetPins)
import com.xilinx.JBits.Virtex.RTPCore.ULPrimitives.Prims2JBits;


public class GetIobSite {

  static String usage = "Usage: GetIobSite UCF-file Bus-NetName [BusWidth]";



public static void main(String args[])
{

  if (args.length < 2 || args.length>3) {
    System.out.println(usage);
    System.exit(-1);
  }

  String ucfFile = args[0];
  String busNetName = args[1];
  int busWidth=-1;
  Bus dabus = null;
  Net danet = null;

  if (args.length==3) { // ie if its a Bus
    busWidth=Integer.parseInt(args[2],10);
    if (busWidth<1) {
      System.out.println("Invalid bus width: " + args[2]);
      System.exit(-1);
    }
    // bus name must match the resolved UCF net names - with index appended
    dabus = new Bus(busNetName, null, busWidth);
  } else {
    danet = new Net(busNetName, null);
  }


  try {
    // create a board based on the device & package being used
    RaptorXCV1000Board board = new RaptorXCV1000Board("testBoard");

    // connect to the desired bus/net (defined in the ucf file)
    // Note that all IOB resources on a pin map to the same IOB coord,
    // so we will just use IOB outputs (the IOB's 'O' resource)
    if (busWidth>0) {
      board.addOutput(busNetName, dabus); 
    } else {
      board.addOutput(busNetName, danet); 
    }
    // configure the IOB's based upon the UCF file specifications
    board.implement(0, ucfFile); 
  } catch (CoreException ce1) {
    System.out.println("Unable to setup board");
  }

  Net dabusnet = null;
  if (busWidth>0) {
    // convert Bus to its constituent Nets, and then print the Net's info
    for (int i=0; i<dabus.getWidth(); i++) {
      dabusnet=dabus.getNet(i);
      printNetSite(dabusnet);
    }
  } else {
    printNetSite(danet);	// just print this Net's info
  }

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


public static void printNetSite(Net n)
{
  NetPins nps = null;
  nps = n.getNetPins();
  try {
    Prims2JBits.makeLocalConnections(nps);
    if (!nps.isLocal()) { nps.enumeratePins(); }
    // because we used the IOB's outputs (the 'O' resource)
    // this means that we will query the sink pin to get IOB coords.
    // if we used inputs then we would need to query the source pin
    System.out.println("IOB coords="+nps.getSinkPin(0));
  }
  catch(CoreException ce) {
    System.out.println(ce);
  }
}


}


