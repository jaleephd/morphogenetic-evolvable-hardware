//			RaptorXCV1000Board.java
//
// This defines an abstract board class for an XCV1000 in a BG560 package
// as used by the Virtex chip in the Raptor Board
//
//		written by J.A. Lee, September 2004


import com.xilinx.JBits.Virtex.RTPCore.Iob.*;
import com.xilinx.JBits.CoreTemplate.CoreException;


public class RaptorXCV1000Board extends Board
{


	private XCVPackage xcvPackage[] = { new xcv1000_bg560() };
	private static int GCLK = 2;
	// note: Raptor ucf file gives: L_CLK LOC=D17;
	//	 D17 corresponds to GCLKPAD2



public RaptorXCV1000Board(String name) throws CoreException
{
  super(name);

  setXcvPackage(xcvPackage);
  setGCLK(GCLK);
}


}


