
//			 GeneInfo.java
//
// this class stores the information pertaining to a gene, including the
// gene products produced at various points in the transcription region,
// the enhancer and repressor bind sites and what resources bind to them,
// the promoter location and sequence, etc.
//
// instances of this class are created by ChromInfo, which stores the
// information obtained from a decoded chromosome in these, for use with
// the morphogenesis system
//
// note that need to know promoter location before can add bind sites
// hence the requirement for the promoter's details in the constructor
//
// this class also requires local classes: StringSplitter
//
// 		written December 2003, J.A. Lee


// for String's etc
import java.lang.*;
// for collection classes (eg List)
import java.util.*;
// for IO
import java.io.*;



class GeneInfo {
  
	public long plocus;
	public String promseq;

	public long glocus;
	public long gendloc;

	// transcript maps endloc (locus+coding len) => resource settings
	public Map transcript;

	// enhb is a list of HashMaps, each of which is a bindsite
	public List enhb;
	// enhSiteDistMetric is a list of Integers giving dist from promoter
	public List enhSiteDistMetric;

	// repb is a list of HashMaps, each of which is a bindsite
	public List repb;
	// repSiteDistMetric is a list of Integers giving dist from promoter
	public List repSiteDistMetric;

	// enhb & repb HashMap's: resource settings => locus



// this is the minimum amount of information needed to create a gene entry
GeneInfo(long gloc, int codelen, String codingregion, int poffset, String pseq)
{
  glocus=gloc;
  gendloc=glocus+codelen;
  plocus=gloc+poffset;
  promseq=pseq;			// this also gives promoter length
  enhb=new ArrayList();
  enhSiteDistMetric=new ArrayList();
  repb=new ArrayList();
  repSiteDistMetric=new ArrayList();
  transcript=setRegion("gene",glocus,codelen,codingregion);
}


// transcript maps endloc (locus+coding len) => resource settings
public String getTranscribed(long endloc)
{
  return (String)transcript.get(new Long(endloc));
}

public void addBindSite(String type, int featoffset, int featlen, String details)
{
  if (type.equals("enhb")) {
    addEnhancerBindSite(featoffset,featlen,details);
  } else if (type.equals("repb")) {
    addRepressorBindSite(featoffset,featlen,details);
  } else {
    System.err.println("Invalid bind site type ("+type+")! type must be enhb or repb");
    System.exit(1);
  }
}

public void addEnhancerBindSite(int featoffset, int featlen, String details)
{
  long locus=glocus+featoffset;
  // repressors lie before promoter, hence closest to promoter start
  int dist2prom=(int)(plocus-(locus+featlen)+1);
  enhSiteDistMetric.add(new Integer(dist2prom));
  Map bsite=setRegion("enhb",locus,featlen,details);
  enhb.add(bsite);
}


public void addRepressorBindSite(int featoffset, int featlen, String details)
{
  long locus=glocus+featoffset;
  // repressors lie after promoter, hence closest to promoter end
  int dist2prom=(int)(locus-(plocus+promseq.length())+1);
  repSiteDistMetric.add(new Integer(dist2prom));
  Map bsite=setRegion("repb",locus,featlen,details);
  repb.add(bsite);
}



Map setRegion(String type, long featloc, int featlen, String details)
{
  Map region=new HashMap();

  int eoff, elen;
  String edets;
  Long loc;
  Long endloc;
  String[] fields;

  // details is a list of semi-colon delimited elements, with each element
  // itself being comma-delimited in the form: offset,len,elemdets
  // elemdets is further subdividable, but not for use here, so leave alone

  // get all the elements in this region
  String[] elems=StringSplitter.trimsplit(details,";");

  // process each element
  for (int i=0; i<elems.length; i++) {
    // element is in format: offset,length,details
    fields=StringSplitter.trimsplit(elems[i],",",2); // details has commas in it
    if (fields.length<3) {
      System.err.println("invalid "+type+" region element ("+elems[i]+") in gene at locus "+featloc);
      System.exit(1);
    }
    eoff=Integer.parseInt(fields[0]);
    elen=Integer.parseInt(fields[1]);
    edets=fields[2];
    if (type.equals("gene")) {
      // gene transcript maps endloc (locus+coding length) => resource settings
      endloc=new Long(featloc+eoff+elen);
      region.put(endloc,edets);
    } else { // its a bind site
      // bind sites map resource settings => locus
      loc=new Long(featloc+eoff);
      region.put(edets,loc);
    }
  }

  return region;
}


public String toString()
{
  String str=new String("plocus="+plocus+", promseq="+promseq+", glocus="+
      			 glocus+",gendloc="+gendloc);
  str+="\ntranscript="+transcript;
  str+="\nenhb="+enhb;
  str+="\nenhSiteDistMetric="+enhSiteDistMetric;
  str+="\nrepb="+repb;
  str+="\nrepSiteDistMetric="+repSiteDistMetric;

  return str;
}


}


