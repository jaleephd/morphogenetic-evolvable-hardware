
//			 DummyTest.java
//
// this class just outputs a random fitness, iteration and number of genes
// for testing other parts of the morphogenetic EHW system
//
// 		written September 2004, J.A. Lee


// for String's etc
import java.lang.*;
// for Random class
import java.util.*;
// for IO
import java.io.*;
// for formatting decimal numbers for output (like printf)
import java.text.DecimalFormat;


public class DummyMGTest {


public static void main (String[] args)
{
  // for limiting number of decimal places in output float/doubles
  DecimalFormat df = new DecimalFormat("0.0##");

  long seed=System.currentTimeMillis();
  Random rand=new Random(seed);

  double maxfitness=100*rand.nextDouble();
  if (maxfitness>99.9) { maxfitness=100.0; }
  int maxfititer=rand.nextInt(80)+1;
  int numgenes=rand.nextInt(16)+1;

  System.out.println(df.format(maxfitness)+" @ "+maxfititer+" : "+seed+" ; "+numgenes); 

} // end main

} // end DummyMGTest


