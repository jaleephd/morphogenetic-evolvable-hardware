//			 JBitsLookup
//
// this class implements a lookup for JBits resources, to return the
// constants required for configuring and querying the Virtex, when
// supplied with the String representation of the resource, or to do
// a reverse lookup to return the name of the resource associated with
// a given bit pattern for a given JBits bits class
// 
// this class also requires local class: StringSplitter
//
// 		written December 2003, J.A. Lee



// for Strings, and reflection
import java.lang.*;
import java.lang.reflect.*;

// for JBits
import com.xilinx.JBits.Virtex.JBits;
import com.xilinx.JBits.Virtex.Bits.*;

// for test routine
import com.xilinx.JBits.Virtex.Util;


class JBitsLookup {

public static void main(String args[])
{

  if (args.length<2 || args.length>3) {
    System.err.println("Synopsis: java JBitsLookup class[.innerclass] (bitsconst valueconst | bitpattern)");
    // note though that class innerclass.bitsconst innerclass.valueconst
    // also works and is equivalent
    System.exit(1);
  }

  String classname=args[0];

  // String classname="S0F1";
  //     String bitstr="S0F1";
  //     String valuestr="SINGLE_EAST12";
  //   or
  //     String bitpattern="000001010"

  if (args.length==3) { // get the bits associated with the name
    String strbits=args[1];
    String strval=args[2];  

    int[][] bits=JBitsLookup.getBits(classname+"."+strbits);
    System.out.print("bits="); 
    String bitstr;
    for (int i=0;i<bits.length;i++) {
      bitstr=Util.IntArrayToString(bits[i]);
      System.out.print("["+bitstr+"]"); 
    }

    int[] value=JBitsLookup.getValue(classname+"."+strval);
    System.out.print("\nvalue="); 
    String valstr=Util.IntArrayToString(value);
    System.out.println(valstr); 
  } else if (args.length==2) { // get the name associated with the bitpattern
    String bitpattern=args[1];
    // ensure that it isn't a class field name
    if (bitpattern.charAt(0)=='0' || bitpattern.charAt(0)=='1') {
      String name=JBitsLookup.getName(classname,bitpattern);
      if (bitpattern!=null) {
        System.out.println(classname+"."+name); 
      } else {
        System.err.println(classname+" has no constant with bit pattern: "+bitpattern); 
        System.exit(1);
      }
    } else {
      System.err.println(bitpattern+" is not a valid bit pattern!"); 
      System.exit(1);
    }
  }

}


// takes a string of the form classname.[innerclassname.]constname
// for example S0F1.S0F1 or S1Control.YDin.YDin
// and returns the constant bits associated with that constname
public static int[][] getBits(String strbits)
{
  int[][] bits=null;

  try {
    String[] fields=StringSplitter.split(strbits,".");
    if (fields.length<2 || fields.length>3) {
      throw new Exception("Invalid bits field ("+strbits+"), must be of the form classname.[innerclassname.]constname");
    }

    String classname;
    String constname;

    if (fields.length==2) {
      classname=fields[0];
      constname=fields[1];
    } else { // an inner class eg SliceControl.Cin
      classname=fields[0]+"$"+fields[1]; // $ indicates an inner class
      constname=fields[2];
    }
    // get the class, eg S0F1 or S1Control
    Class c=Class.forName("com.xilinx.JBits.Virtex.Bits."+classname);
    // get the static constant, eg int[][] SOF1.S0F1 or S1Control.YDin.YDin
    Field f=c.getField(constname);
    bits=(int[][])f.get(null); // static field so object ignored
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }

  return bits;
}


// takes a string of the form classname.[innerclassname.]constname
// for example SOF1.SINGLE_EAST12 or S1Control.YDin.BY
// and returns the constant value associated with that constname
public static int[] getValue(String strval)
{

  int[] val=null;

  try {
    String[] fields=StringSplitter.split(strval,".");
    if (fields.length<2 || fields.length>3) {
      throw new Exception("Invalid value field ("+strval+"), must be of the form classname.[subclassname.]constname");
    }

    String classname;
    String constname;

    if (fields.length==2) {
      classname=fields[0];
      constname=fields[1];
    } else { // an inner class eg S1Control.YDin
      classname=fields[0]+"$"+fields[1]; // $ indicates an inner class
      constname=fields[2];
    }

    // get the class, eg S0F1
    Class c=Class.forName("com.xilinx.JBits.Virtex.Bits."+classname);
    // get the static constant, eg int[] SOF1.SINGLE_EAST12 or S1Control.YDin.BY
    Field f=c.getField(constname);
    val=(int[])f.get(null); // static field so object ignored
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }

  return val;
}


// takes a class name, and a bit pattern value
// returns the name of the constant in the class represented by the bit pattern
public static String getName(String classname, int[] bits)
{
  StringBuffer bitbuf=new StringBuffer(bits.length);
  for (int i=0; i<bits.length; i++) {
    bitbuf.append((bits[i]==0) ? '0' : '1');
  }
  return JBitsLookup.getName(classname,bitbuf.toString());
}

// takes a string indicating the classname (eg S0F1) and another representing
// the resource/setting's bit pattern (eg 000001010)
public static String getName(String classname, String bitpattern)
{
  String name=null;
  try {
    // get the class, eg S0F1
    Class c=Class.forName("com.xilinx.JBits.Virtex.Bits."+classname);

    // get the static String[][] Name constant, eg SOF1.Name
    // note this doesn't work for the OutMuxToSingle class as it doesn't
    // have a Name field, so do a work around for this class
    if (classname.equals("OutMuxToSingle")) {
      // the bit pattern can only be 0 or 1 corresponding to OFF or ON
      name=new String(((bitpattern.equals("0"))?"OFF":"ON"));
    } else {
      Field f=c.getField("Name");
      String[][] names=(String[][])f.get(null);// static field so object ignored
      // the 'Name' string array contains pairs of strings representing
      // the name of a given resource and its associated bit pattern
      for (int i=0; i<names.length; i++) {
        if (bitpattern.equals(names[i][1])) {
          name=new String(names[i][0]);
          break;
        }
      }
    }
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }

  return name;
}

} // end class


