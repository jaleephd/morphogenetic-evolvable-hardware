//			 ContentionEntry.java
//
// this class is simply a structure for storing information on a Virtex
// resource that will cause contention problems with another resource.
// this will typically be used to store the information on competing
// resources in a List for another resource to check.
//
// - bitString is the name of the JBits 'bits' constant that is contentious,
//   for eg OutMuxToSingle.OUT1_TO_SINGLE_WEST5
// - valString is the name of the JBits 'value' constant that will cause
//   contention problems, for eg OutMuxToSingle.ON.
// - rowOffset and colOffset are the CLB row and column offset from
//   another resource, that will have contention problems if this JBits
//   resource is set with the above settings. For eg the row & col offset
//   for the above example would be 0, +1
//
//
// 		written December 2003, J.A. Lee

// for String's etc
import java.lang.*;

class ContentionEntry {

  public String bitString;
  public String valString;
  public int rowOffset;
  public int colOffset;


public ContentionEntry(String bs, String vs, int ro, int co)
{
  bitString=new String(bs);
  valString=new String(vs);
  rowOffset=ro;
  colOffset=co;
}


// convert state to a String
public String toString()
{

  String str="("+bitString+","+valString+","+rowOffset+","+colOffset+")";
  return str;
}


}
