//			XCV50bg256Board.java
//
// This defines an abstract board class for an XCV50 in a BG256 package
//
//		written by J.A. Lee, January 2004


import com.xilinx.JBits.Virtex.RTPCore.Iob.*;
import com.xilinx.JBits.CoreTemplate.CoreException;


public class XCV50bg256Board extends Board
{

  private XCVPackage xcvPackage[] = { new xcv50_bg256() };
  private static int GCLK = 1;
  // as used in demo bitstreams and default for VirtexDS



public XCV50bg256Board(String name) throws CoreException
{
  super(name);

  setXcvPackage(xcvPackage);
  setGCLK(GCLK);
}


}


