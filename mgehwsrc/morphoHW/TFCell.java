
//			 TFCell.java
//
// this class stores the state of TFs in a cell, and provides methods for
// creating and aging TFs, binding TFs to a locus on a chromosome, for
// querying a bound or unbound TF, and for saving and restoring the state
// of TFs in a cell.
//
// 		written Dec 2003, J.A. Lee
//


// for String's etc
import java.lang.*;
// for collection classes (eg Map) & Random class
import java.util.*;
// for IO
import java.io.*;



class TFCell {

	Map freetfs;		// freetfs is a collection of unbound TFs
				//   key:    dist '+' seq
				//   value:  (TreeMap) ordered collection of
				//           TFs with this dist+seq:
				// 		key:	locus
				// 		value:	(ArrayList) of ttl's
				// 			for TFs @ this locus


	Map boundtfs;		// boundtfs is a collection of TFs bound to
				// regulator sites on the chromosome
				// 	key:	dist '+' seq '@' bindloc
				// 	value:	ttl


	List releaseList;	// releaseList is a list of morphogen TFs
				// indexed by their release time. each release
				// time contains an ArrayList of Strings (TFs)
				// in format: dist,seq,locus,ttl

	long TFlifespan;	// life span of a TF
	long morpholifespan;	// default life span of a morphogen
	int TFagerate_free;	// aging rate for free TFs
	int TFagerate_bound;	// aging rate for bound Fs

	public boolean DEBUG=false;

	// this is used for morphogens, where there is no predetermined locus
	final long locusIgnored=-1;



// this is for testing purposes only
public static void main(String[] args)
{
  int lifespan=8;
  int ageratef=3;
  int agerateb=5;

  TFCell tfs=new TFCell(lifespan,ageratef,agerateb);
  tfs.DEBUG=true;

  String[] tflist= { "Local,,332132", 
  		     "Local,,2030322003100",
		     "Local,,01132",
		     "Local,,21223",
		     "morphogen,110,21103,3",
		     "morphogen,010,10103,3",
		     "morphogen,10,01301,2",
		     "morphogen,1,11103,1",
		     "morphogen,0,20123,1",
		     "morphogen,,20103,0",
		     "cytoplasm,,21223"
                   };

  String seq,type,dist;
  String[] fields;
  long locus=1000;
  int ttr;

  for (int i=0; i<tflist.length; i++) {
    fields=StringSplitter.split(tflist[i],",");
    seq=fields[2];
    type=fields[0];
    dist=fields[1];
    locus+=i*10;
    if (fields.length<4) {
      tfs.createTF(seq,type,dist,locus);
    } else {
      ttr=Integer.parseInt(fields[3]);
      tfs.addMorphogen(ttr,seq,dist,locus);
    }
  }

  tfs.printTFs();

  fields=StringSplitter.split(tflist[0],",");
  dist=fields[1]; seq=fields[2];

  String TF0=tfs.getFreeTF(dist,seq,locus);
  String TFnull=tfs.getFreeTF("011","32",locus);

  System.out.println("getFreeTF("+dist+","+seq+","+locus+"): "+TF0);
  System.out.println("getFreeTF(011,32,"+locus+"): "+TFnull);

  String bTF0=tfs.bindTF(dist,seq,locus-500);
  System.out.println("bindTF("+dist+"+"+seq+","+(locus-500)+"): "+bTF0);

  String gbTF0=tfs.getBoundTF(dist,seq,locus-500);
  TFnull=tfs.getBoundTF("011","32",locus);

  System.out.println("getBoundTF("+dist+"+"+seq+","+(locus-500)+"): "+gbTF0);
  System.out.println("getBoundTF(011,32,"+locus+"): "+TFnull);

  fields=StringSplitter.split(tflist[4],",");
  dist=fields[1]; seq=fields[2];

  String bTF1=tfs.bindTF(dist,seq,locus-300);
  System.out.println("bindTF("+dist+"+"+seq+","+(locus-300)+"): "+bTF1);

  System.out.println("\n\n");
  tfs.printTFs();

  System.out.println("\n\nbeginning updates...\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  // create duplicate
  tfs.createTF("01132","Local","",(long)1030);
  tfs.printTFs();

  // save state at this point
  System.out.println("saving state now...");
  String statestr=tfs.getState();
  System.out.println("\nsaved TF state is..\n"+statestr);

  System.out.println("\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  System.out.println("\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  System.out.println("\n\n");

  tfs.updateTFs();
  tfs.printTFs();

  System.out.println("\nclearing TFs..");
  tfs.clear();
  tfs.printTFs();

  System.out.println("\nsaved TF state was..\n"+statestr);
  System.out.println("restoring state...");
  tfs.setState(statestr);
  System.out.println("\nrestored TF state..\n");
  tfs.printTFs();

}



TFCell(long lifespan, int ageratef, int agerateb)
{
  this(lifespan,lifespan,ageratef,agerateb);
}

TFCell(long mglifespan, long tlifespan, int ageratef, int agerateb)
{
  freetfs=new HashMap();
  boundtfs=new HashMap();
  releaseList=new ArrayList();
  morpholifespan=mglifespan;
  TFlifespan=tlifespan;
  TFagerate_free=ageratef;
  TFagerate_bound=agerateb;
}



// create an unbound TF
// note that morphogen/local types are treated the same here, as TFs are
// stored local to a cell, however cytoplasmic determinants (type
// starts with a 'C' or 'c') are treated specially, as they don't age.
// Kludge alert: using Long.MIN_VALUE for ttl to indicate that TF is of
// cytoplasmic type, and so doesn't age.

public void createTF(String seq, String type, String distfromsrc)
{
  createTF(seq,type,distfromsrc,locusIgnored);
}

public void createTF(String seq, String type, String distfromsrc, long locus)
{
  long lifespan;

  // if it's a cytoplasm type, then it doesn't age - we use Long.MIN_VALUE
  // to indicate don't age or remove - yes, it's a KLUDGE!
  if (type.charAt(0)=='c' || type.charAt(0)=='C') {
    lifespan=Long.MIN_VALUE;
  } else {
    lifespan=TFlifespan;
  }

  createTF(seq,type,distfromsrc,locus,lifespan);
}

public void createTF(String seq, String type, String distfromsrc, long locus, long lifespan) {
  String key=new String(distfromsrc+"+"+seq);
  
  if (DEBUG) { 
    if (lifespan==Long.MIN_VALUE) {
      System.out.println("creating unbound TF ("+key+") with eternal lifespan ...");
    } else {
      System.out.println("creating unbound TF ("+key+") with lifespan "+lifespan+"...");
    }
  }

  addFreeTF(key,locus,lifespan);
}

// creates a new free TF
void addFreeTF(String distNseq, long locus, long ttl)
{
  Long loco=new Long(locus);
  Long ttlo=new Long(ttl);

  Map m=(TreeMap)freetfs.remove(distNseq);	// remove so can update
  if (m==null) {
    m=new TreeMap();
  }

  List l=(ArrayList)m.remove(loco);		// remove so can update
  if (l==null) {
    l=new ArrayList();
  }

if (DEBUG) { System.out.println("addFreeTF: matching ("+distNseq+") @ locus="+m+", with matching locus ("+locus+") have ttl="+l+"..."); }
  // now update the collections
  l.add(ttlo);				// TF's life span
  m.put(loco,l);
  freetfs.put(distNseq,m);
if (DEBUG) { System.out.println("updated freetfs="+freetfs+"..."); }
}


// create a new bound TF
void addBoundTF(String distNseqATloc, long ttl)
{
  Long ttlo=new Long(ttl);
  boundtfs.put(distNseqATloc,ttlo);
}



// looks for a free TF with matching dist & seq, in format: dist "+" seq
// return ANY matching free TF as String of dist+seq,locus, or NULL if none
// we don't care which TF, close or far, or what its ttl is - as this
// string won't be used except to indicate such a free TF exists

public String getFreeTF(String distNseq, long locus)
{
  if (((Map)freetfs.get(distNseq))!=null) {	// get matching dist+seq
    return new String(distNseq + "," + locus);
  } else {
    return null;
  }
}

public String getFreeTF(String dist, String seq, long locus)
{
  return getFreeTF(dist+"+"+seq,locus);
}


// return matching TF in format dist+seq,locus,ttl
// or null if not bound here

public String getBoundTF(String dist, String seq, long locus)
{
  return getBoundTF(dist+"+"+seq,locus);
}

public String getBoundTF(String distNseq, long locus)
{
  Object t;
  Long ttlo;
  String key=new String(distNseq+"@"+locus);

  if ((ttlo=(Long)boundtfs.get(key))!=null) {
    return new String(distNseq + "," + locus + "," + ttlo);
  } else {
    return null;
  }
}


// remove TF with matching distance & sequence from freetfs & add to boundtfs
// and return bound TF (if any) in format: dist+seq,locus,ttl
// note:
// - it just grabs the first matching TF, ignoring distance from locus,
//   and ttl - the first TF will probably be the oldest in list however
//   ie its ttl will be the least

public String bindTF(String dist, String seq, long locus)
{
  return bindTF(dist+"+"+seq,locus);
}

public String bindTF(String distNseq, long locus)
{
if (DEBUG) { System.out.println("binding TF ("+distNseq+") to "+locus+"..."); }
  String bnd=null;
  Long loco;
  long k, closest, lessthan, greaterthaneq;
  Map locmap;
  List ttlist;
  Long ttlo;
  String key;

if (DEBUG) {
System.out.println("free TFs: "+freetfs);
locmap=(Map)freetfs.get(distNseq); System.out.println("matched "+locmap);
}
  // note the use of remove in the following, as need to update entries
  // extract matching dist+sequence TFs - a Map of (locus, List of ttls))
  if ((locmap=(Map)freetfs.remove(distNseq))!=null) { 
if (DEBUG) { System.out.println("matched TF ("+distNseq+") in freetfs..."); }

    // from the matching TFs find the closest TF locus
    closest=lessthan=greaterthaneq=-1;
    Iterator iter = locmap.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry e = (Map.Entry)iter.next();
      k=((Long)e.getKey()).longValue();
      if (k>=locus) {
	greaterthaneq=k;
	if (lessthan<0) { lessthan=greaterthaneq; }
	break;
      }
      lessthan=greaterthaneq=k;
    }
    
    if (lessthan<0) {	 	// in case empty Map for this sequence
if (DEBUG) { System.out.println("failed bind: empty locus->ttl-list Map for sequence, or bind locus<0..."); }
      if (locmap.size()>0) {
        freetfs.put(distNseq,locmap);	// put it back
      }
      return bnd; 
    }
    closest = (locus-lessthan<=greaterthaneq-locus)? lessthan : greaterthaneq;
    loco=new Long(closest);
if (DEBUG) { System.out.println("found closest ("+distNseq+") @ "+closest+" in freetfs..."); }

    // and from this entry extract the ttl's List with the matching locus
    if ((ttlist=(List)locmap.remove(loco))!=null) {

      // and extract the first (oldest) ttl, to give a TF to bind
      if ((ttlo=(Long)ttlist.remove(0))!=null) {
if (DEBUG) { System.out.println("extracted oldest ("+distNseq+") @ "+closest+" with ttl="+ttlo.longValue()+" in freetfs..."); }
	// add TF with dist+seq @ locus with this ttl to the bound TFs
	//	bountfs: key = dist '+' seq '@' bindloc; value = ttl
        // the following assumes that no TF already bound at this locus
        key=new String(distNseq+"@"+locus);
        boundtfs.put(key,ttlo);
	// set return value (for info purposes) to this TF
	bnd=new String(distNseq+","+locus+","+ttlo.longValue());
      }

      // if any ttls left then put back updated ttl list for this locus
      if (ttlist.size()>0) {
if (DEBUG) { System.out.println("ttls left at this locus.."); }
        locmap.put(loco,ttlist);
      }
    }

    // if any TFs @ this locus, put back updated (locus) entry
    if (locmap.size()>0) {
if (DEBUG) { System.out.println("TFs left at this locus in freetfs.."); }
      freetfs.put(distNseq,locmap);
    }
  }

  return bnd; // return bound TF, if any
}



// age TFs, and remove dead TFs (those with ttl<=0) from population
// note that bound and free TFs are stored slightly differently
// (as only 1 TF molecule can bind at a specific (start) locus

public void updateTFs()
{
  if (DEBUG) { System.out.println("updating TF TTL's..."); }
  ageBoundTFs();
  ageFreeTFs();
  if (DEBUG) { System.out.println("releasing available TF morphogens..."); }
  releaseAvailableMorphogens();
  if (DEBUG) { System.out.println("updating morphogen TF TTR's..."); }
  updateMorphogenReleaseTime();
}



void ageFreeTFs()
{
  Map locmap;
  List ttlist;

  long ttl;
  Long ttlo;

  if (DEBUG) { System.out.println("aging free TFs..."); }
  // for each TF with a different dist+seq
  Iterator iter1 = freetfs.entrySet().iterator();
  while (iter1.hasNext()) {
    Map.Entry e1 = (Map.Entry)iter1.next();
    locmap=(TreeMap)e1.getValue();

if (DEBUG) { System.out.println("getting "+locmap.size()+" TF locii with dist+seq="+(String)e1.getKey()+"..."); }
    // for each different locus
    Iterator iter2 = locmap.entrySet().iterator();
//if (DEBUG) { System.out.println("got iter2..."); }
    while (iter2.hasNext()) {
//if (DEBUG) { System.out.println("iter2 hasNext()..."); }
      Map.Entry e2 = (Map.Entry)iter2.next();
//if (DEBUG) { System.out.println("got e2 "+e2.getValue()+"..."); }
      ttlist=(ArrayList)e2.getValue();
if (DEBUG) { System.out.println(".. "+ttlist.size()+" TFs @ locus="+e2.getKey()+"..."); }

      // for each ttl
      ListIterator iter3 = ttlist.listIterator();
      while (iter3.hasNext()) {
	ttlo=(Long)iter3.next();
	if (ttlo.longValue()!=Long.MIN_VALUE) { // don't age cytoplasms
	  ttl=ttlo.longValue()-TFagerate_free;
          if (DEBUG) { System.out.println("aged free TF ("+e1.getKey()+") @ "+e2.getKey()+" ttl="+ttl+"..."); }
          if (ttl<=0) {
            iter3.remove();		// its dead now, so remove it
            if (DEBUG) { System.out.println("removed TF..."); }
          } else {
	    ttlo=new Long(ttl);
            iter3.set(ttlo);		// update ttl
            if (DEBUG) { System.out.println("updated TF..."); }
          }
	} // end age non-cytoplasm
else if (DEBUG) { System.out.println("TF is cytoplasm.. don't age..."); }
      } // end ttl while

      // check if array of ttls now empty (after remove <=0 )
      if (ttlist.size()==0) {
        iter2.remove();			// remove this locus from dist+seq TFs
      } else {
        e2.setValue(ttlist);		// update ttl's at this locus
      }
    }

    // check if any TFs left at this locus
    // and 
    if (locmap.size()==0) {
      iter1.remove();			// remove this dist+seq from TFs
    } else {
      e1.setValue(locmap);		// update TFs with this dist+seq
    }
  }

}


void ageBoundTFs()
{
  long ttl;
  Long ttlo;

  if (DEBUG) { System.out.println("aging bound TFs..."); }
  Iterator iter = boundtfs.entrySet().iterator();
  while (iter.hasNext()) {
    Map.Entry e = (Map.Entry)iter.next();
    ttlo=(Long)e.getValue();
    if (ttlo.longValue()!=Long.MIN_VALUE) { // don't age cytoplasms
      ttl=ttlo.longValue()-TFagerate_bound;
      if (DEBUG) { System.out.println("aged bound TF ("+(String)e.getKey()+") ttl="+ttl+"..."); }
      if (ttl<=0) {
        iter.remove();
        if (DEBUG) { System.out.println("removed TF..."); }
      } else {
        ttlo=new Long(ttl);
        e.setValue(ttlo);
        if (DEBUG) { System.out.println("updated TF..."); }
      }
    } // end age non-cyto TF
else if (DEBUG) { System.out.println("TF is cytoplasm.. don't age..."); }
  }
}

public void addMorphogen(int ttr, String seq, String distfromsrc)
{
  addMorphogen(ttr,seq,distfromsrc,locusIgnored);
}

public void addMorphogen(int ttr, String seq, String distfromsrc, long locus)
{
  addMorphogen(ttr,seq,distfromsrc,locusIgnored,morpholifespan);
}

// morphogen sill be stored as a string in format: dist,seq,locus,ttl
// in an array of morphogens to be released at a given time
public void addMorphogen(int ttr, String seq, String distfromsrc, long locus, long ttl)
{
  if (DEBUG) { System.out.println("adding morphogen ("+distfromsrc+"+"+seq+"@"+locus+") to be released in t+"+ttr+" updates, and has ttl="+ttl+" ..."); }
  // increase size of list if necessary, so that there is an entry for each
  // time up to the time of release. ttr of 0 = now
  if (releaseList.size()-1<ttr) {
    for (int i=releaseList.size()-1; i<ttr; i++) {
       releaseList.add(new ArrayList());
    }
  }
  // this should probably be done with (releaseList.get(ttr)).add( .. )
  List rlist=(List)releaseList.get(ttr);
  rlist.add(new String(distfromsrc+","+seq+","+locus+","+ttl));
  releaseList.set(ttr,rlist);
}


// just shift array 1 to left
void updateMorphogenReleaseTime()
{
  if (DEBUG) { System.out.print("updating morphogen release times..."); }
  if (!releaseList.isEmpty()) { releaseList.remove(0); }
  if (DEBUG) {
    System.out.println(" done.\n");
    if (releaseList.size()>0) {
      System.out.println("further morphogens to be released up to t+"+(releaseList.size()-1)+" update steps..");
    } else {
      System.out.println("no further morphogens to be released..");
    }
  }
}


// the morphogens at index 0 are ready for release
// they are stored in an ArrayList of Strings
void releaseAvailableMorphogens()
{
  String morphogen=null;
  List available=null;

  if (!releaseList.isEmpty()) {
    available=(List)releaseList.get(0);
  }

  if (available!=null) {
    ListIterator iter = available.listIterator();
    while (iter.hasNext()) {
      morphogen=(String)iter.next();
      releaseMorphogen(morphogen);
    }
  }
}


// a ready-to-be-released morphogen is in format: dist,seq,locus,ttl
// it is released by creating a corresponding new free TF
void releaseMorphogen(String morphogen)
{
  if (DEBUG) { System.out.println("releasing morphogen ("+morphogen+") ..."); }
  String[] fields=StringSplitter.trimsplit(morphogen,",");
  createTF(fields[1],"morphogen",fields[0],Long.parseLong(fields[2]),Long.parseLong(fields[3]));
}


// print the state of the TF collections for debugging purposes
public void printTFs()
{
  System.out.println("free TFs: "+freetfs);
  System.out.println("bound TFs: "+boundtfs);
  System.out.println("not yet released morphogen TFs: "+releaseList);
}


// clear all the TFs out of the cell
public void clear()
{
  freetfs=new HashMap();
  boundtfs=new HashMap();
  releaseList=new ArrayList();
}


// TFs state is in the form: boundTFstate:freeTFstate:morphogenreleasestate
public void setState(String statestr)
{
  try {
    String[] states=StringSplitter.trimsplit(statestr,":");
    if (states.length<3) {
      throw new Exception("Error in TFCell.setState("+statestr+") - requires boundTFstate:freeTFstate:morphogenreleasestate\n");
    }
    setBoundState(states[0]);
    setFreeState(states[1]);
    setMorphogenReleaseState(states[2]);
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}


// boundstr is in format: "TF=bound;" boundTF0 ";" boundTF1 ";" boundTF2 ";" ..
// each boundTF consists of:
// 	distfromsrc+sequence@locus,ttl
public void setBoundState(String boundstr)
{
  try {
    boundtfs=new HashMap(); // clear the TFs
    String[] tfslist=StringSplitter.trimsplit(boundstr,";");
    if (!tfslist[0].equals("TF=bound")) {
      throw new Exception("Error in TFCell.setBoundState("+boundstr+") - requires format: TF=bound;boundTFlist\n");
    }
    String[] tfdets;
    for (int i=1; i<tfslist.length; i++) {
      tfdets=StringSplitter.trimsplit(tfslist[i],",");
      if (tfdets.length<2) {
        throw new Exception("Error in TFCell.setBoundState("+boundstr+") - TF state requires distfromsrc+sequence@locus,ttl\n");
      }
      addBoundTF(tfdets[0],Long.parseLong(tfdets[1]));
    }
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}

// freestr is in format: "TF=free;" freeTF0 ";" freeTF1 ";" freeTF2 ";" ..
// each freeTF consists of:
// 	distfromsrc+sequence,locus,ttl
public void setFreeState(String freestr)
{
  try {
    freetfs=new HashMap(); // clear the TFs
    String[] tfslist=StringSplitter.trimsplit(freestr,";");
    if (!tfslist[0].equals("TF=free")) {
      throw new Exception("Error in TFCell.setFreeState("+freestr+") - requires format: TF=free;freeTFlist\n");
    }
    String[] tfdets;
    for (int i=1; i<tfslist.length; i++) {
      tfdets=StringSplitter.trimsplit(tfslist[i],",");
      if (tfdets.length<3) {
        throw new Exception("Error in TFCell.setFreeState("+freestr+") - TF state requires distfromsrc+sequence,locus,ttl\n");
      }
      addFreeTF(tfdets[0],Long.parseLong(tfdets[1]),Long.parseLong(tfdets[2]));
    }
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}


// releasestr is in format: "TF=morphogen;" mTF0 ";" mTF1 ";" mTF2 ";" ..
// each mTF consists of:
//	ttr "," distfromsrc "," sequence "," locus "," ttl
public void setMorphogenReleaseState(String releasestr)
{
  try {
    releaseList=new ArrayList();	// clear the morphogens
    String[] mlist=StringSplitter.trimsplit(releasestr,";");
    if (!mlist[0].equals("TF=morphogen")) {
      throw new Exception("Error in TFCell.setMorphogenReleaseState("+releasestr+") - requires format: TF=morphogen;morphogenTFReleaselist\n");
    }

    String[] mdets;
    for (int i=1; i<mlist.length; i++) {
      mdets=StringSplitter.trimsplit(mlist[i],",");
      if (mdets.length<5) {
        throw new Exception("Error in TFCell.setMorphogenReleaseState("+releasestr+") - morphogen state requires ttr,distfromsrc,sequence,locus,ttl\n");
      }
      addMorphogen(Integer.parseInt(mdets[0]),mdets[1],mdets[2],Long.parseLong(mdets[3]),Long.parseLong(mdets[4]));
    }
  } catch (Exception e) {
    System.out.println(e);
    e.printStackTrace();
    System.exit(1);
  }
}



// returns the state of the free & bound TFs & unyet released morphogens
// in a cell, in semicolon delimited format:
// 	TF=bound;distfromsrc+sequence@locus,ttl
// or
// 	TF=free;distfromsrc+sequence,locus,ttl
// or
// 	TF=morphogen;ttr,distfromsrc+sequence,locus,ttl
public String getState()
{
  String state="";
  state+=getBoundstate()+":"+getFreestate()+":"+getMorphogenReleasestate();
  return state;
}


// get the state of the bound TFs in semicolon delimited form:
// 	TF=bound; TF0; TF1; ...
// where each TF is in form:
// 		distfromsrc "+" sequence "@" locus "," ttl
public String getBoundstate()
{
  String state="TF=bound;";
  Iterator iter = boundtfs.entrySet().iterator();
  while (iter.hasNext()) {
    Map.Entry e = (Map.Entry)iter.next();
    // bound TFs are stored as:
    //  	key:	dist '+' seq '@' bindloc
    //  	value:	ttl
    state+=e.getKey()+","+e.getValue()+";";
  }

  return state;
}


// get the state of the free TFs in semicolon delimited form:
// 	TF=free; TF0; TF1; ...
// where each TF is in form:
// 	distfromsrc "+" sequence "," locus "," ttl
public String getFreestate()
{
  Map locmap;
  List ttlist;
  Long ttlo;
  
  String state="TF=free;";

  // for each TF with a different dist+seq
  //   key:    dist '+' seq
  //   value:	TreeMap of these at different locus
  Iterator iter1 = freetfs.entrySet().iterator();
  while (iter1.hasNext()) {
    Map.Entry e1 = (Map.Entry)iter1.next();
    locmap=(TreeMap)e1.getValue();

    // for each different locus
    // 	key:	locus
    // 	value:	ArrayList of ttl's at this locus
    Iterator iter2 = locmap.entrySet().iterator();
    while (iter2.hasNext()) {
      Map.Entry e2 = (Map.Entry)iter2.next();
      ttlist=(ArrayList)e2.getValue();
      ttlist=(ArrayList)e2.getValue();

      // for each ttl
      ListIterator iter3 = ttlist.listIterator();
      while (iter3.hasNext()) {
	ttlo=(Long)iter3.next();
	state+=e1.getKey()+","+e2.getKey()+","+ttlo+";";
      }
    }
  }

  return state;
}

// get the state of the unyet released morphogens in semicolon delimited form:
// 	TF=morphogen; TF0; TF1; ...
// where each TF is in form:
// 	ttr "," distfromsrc "," sequence "," locus "," ttl
public String getMorphogenReleasestate()
{
  String state="TF=morphogen;";
  List rlist;

  // for each set of morphgens with a different time to release
  for (int i=0; i<releaseList.size(); i++) {
    rlist=(List)releaseList.get(i);
    if (rlist.size()>0) {
      // for each morphogen to be released at that time
      ListIterator iter2 = rlist.listIterator();
      while (iter2.hasNext()) {
	// add it to the state
	state+=i+","+((String)iter2.next())+";";
      }
    }
  }

  return state;
}


} // end class


// helper classes




