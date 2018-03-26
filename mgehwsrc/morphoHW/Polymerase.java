//			Polymerase.java
//
// this class stores the state of a RNA polymerase II molecule
//
// transloc gives the current location on the chromosome
// geneidx gives the gene's index (Nth gene on chromosome) that
//	   is currently being transcribed by this molecule, or
//	   -1 if not bound to a gene
//
// 		written December 2003, J.A. Lee


class Polymerase {

  public long transloc;
  public int geneidx;

  Polymerase() { transloc=-1; geneidx=-1; }
  public boolean isBound() { return (geneidx>=0); }

} // end class


