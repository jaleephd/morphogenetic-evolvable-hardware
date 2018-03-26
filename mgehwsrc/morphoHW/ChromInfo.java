
//			 ChromInfo.java
//
// this class is used to parse a decoded chromosome (from file) and to use
// this information to create an array of genes' details, stored as GeneInfo
// objects, for the morphogenesis system to utilise.
//
// The decoded chromosome file is in the format of lines of:
//	geneno,gstart: feat,+/-offset,length: details
//
// this class also requires local classes: StringSplitter, GeneInfo
//
// 		written December 2003, J.A. Lee


// for String's etc
import java.lang.*;
// for collection classes (eg List)
import java.util.*;
// for IO
import java.io.*;


class ChromInfo {

public static void main (String[] args)
{
  ChromInfo ci=null;

  if (args.length<1) {
    System.err.println("need to specify decoded chromosome file!");
    System.exit(1);
  }

  ci=new ChromInfo(args[0]);
  System.out.println("loaded decoded chromosome with "+ci.numGenes()+" genes...");
  System.out.println("printing contents...");
  GeneInfo gene;
  for (int i=0;i<ci.numGenes();i++) {
    gene=ci.getGene(i);
    System.out.println("gene "+i+" ...");
    System.out.println(gene);
  }
  System.out.println("finished...");

}




	List genes;			// all the genes in the chromosome
	long firstGeneBsiteStart;
	long lastGeneCodeRegionStop;

// the following are for processing chromosome as read in from file

	int linenum;			// current line of chromosome file
	int geneidx;			// current index of gene being stored
	long geneloc=-1;		// current gene (coding region) locus
	SiteDetails genecode=null;	// current gene coding region details
	SiteDetails promoter=null;	// current gene promoter details
	List bindsites=null;		// current gene's bind sites
	int geneLimit;			// if > 0, indicates limit on # genes


public ChromInfo(String decodedChromFile)
{
  this(decodedChromFile,0);
}

public ChromInfo(String decodedChromFile, int glimit)
{
  firstGeneBsiteStart=lastGeneCodeRegionStop=-1;
  genes=new ArrayList();
  geneidx=-1;
  geneLimit=glimit;
  loadChromFromFile(decodedChromFile);
}


public GeneInfo[] getGenes()
{
  return (GeneInfo[])genes.toArray(new GeneInfo[0]);
}


public GeneInfo getGene(int i)
{
  return (GeneInfo)genes.get(i);
}


public int numGenes()
{
  return genes.size();
}


public long length()
{
  return lastGeneCodeRegionStop;
}


public long usedlength()
{
  return lastGeneCodeRegionStop-firstGeneBsiteStart+1;
}


void addGene()
{
  SiteDetails bsite=null;

  // only process genes that have a promoter (need to have to deal with
  // bind sites) and a coding region (which have to have otherwise no gene)
  // if there is a limit to the number of genes to be processed, then don't
  // process it
  if (genecode!=null && promoter!=null &&
                         !(geneLimit>0 && genes.size()>=geneLimit)) {
    // check if it is the most downstream feature in chromosome
    if (geneloc+genecode.length-1>lastGeneCodeRegionStop) {
      lastGeneCodeRegionStop=geneloc+genecode.length-1;
    }
    GeneInfo gene=new GeneInfo(geneloc,genecode.length,genecode.details,
				promoter.offset,promoter.details);
    // now can process bind sites
    ListIterator iter = bindsites.listIterator();
    while (iter.hasNext()) {
      // get a bind site
      bsite=(SiteDetails)iter.next();
      // check if it is the most upstream feature in chromosome
      if (firstGeneBsiteStart<0 || geneloc+bsite.offset<firstGeneBsiteStart) {
	firstGeneBsiteStart=geneloc+bsite.offset;
      }
      if ((bsite.type).equals("enhb")) {
        gene.addEnhancerBindSite(bsite.offset,bsite.length,bsite.details);
      } else {
        gene.addRepressorBindSite(bsite.offset,bsite.length,bsite.details);
      }
    }
    // now have complete gene, so can add it to chromosome's list of genes
    genes.add(gene);
  }
//else { System.out.println("discarding gene lacking a promoter or transcription region..."); }

  promoter=null;
  genecode=null;
  bindsites=new ArrayList();
}



void storeGeneInfo(int gidx, long gloc, String feat, int featoffset, int featlen, String details)
{
//System.out.println("geneidx="+geneidx+", gidx="+gidx);

  // if its a new gene then store details for prev gene
  if (gidx!=geneidx) {
    if (geneidx!=-1) {		// check in case its 1st gene
//System.out.println("adding gene...");
      addGene();
    }	
    geneidx=gidx;
    geneloc=gloc;
  }

  // feat should be one of: gene, prom, enhb, repb
  if (feat.equals("prom")) {
    promoter=new SiteDetails(feat,featoffset,featlen,details);
  } else if (feat.equals("gene")) {
    genecode=new SiteDetails(feat,featoffset,featlen,details);
  } else if (feat.equals("enhb") || feat.equals("repb")) {
    SiteDetails site=new SiteDetails(feat,featoffset,featlen,details);
    bindsites.add(site);
  } else {
    System.err.println("Invalid feature type ("+feat+") in gene "+gidx+" at line "+linenum+"!\nFeature type must be one of prom, gene, enhb, repb");
    System.exit(1);
  }

}



// read the decoded chromosome file in format:
//   geneno,gstart: feat,+/-offset,length: details
// note genes are expected to be in order

void loadChromFromFile(String decodedChromFile)
{
  InputStream inputstream=null;
  InputStreamReader inputstreamreader=null;
  BufferedReader bufferedreader=null;

  String line=null;
  String[] fields;
  int gidx=-1;
  long gloc;
  int featoffset,featlen;
  String featdets,gidxNloc,feat,details;
  char sign;

  try {
    // open the file and create a buffered input stream reader
    inputstream = new FileInputStream(decodedChromFile);
    inputstreamreader=new InputStreamReader(inputstream);
    bufferedreader = new BufferedReader(inputstreamreader);
  } catch (Exception e) {
    System.err.println("Error opening " + decodedChromFile + "\n" + e);
    System.exit(1);
  }

  bindsites=new ArrayList();		// prepare for first gene

  try {
    linenum=0;
    while ((line = bufferedreader.readLine()) != null) {
      linenum++;
      line=line.trim();		// strip leading & trailing whitespace
//System.out.println("linenum="+linenum);
//System.out.println(line);
      // ignore empty lines, and lines starting with a '#' indicating a comment
      if (line.length()>0 && line.charAt(0)!='#') {
	// get gene locus, feature & length+offset, and decoded details
        fields=StringSplitter.trimsplit(line,":");
	if (fields.length!=3) {
          System.err.println("Invalid format in chromosome at line "+linenum+"! Should be geneno,gstart: feat,+/-offset,length: details");
	  System.exit(1);
	}
	gidxNloc=fields[0];
	featdets=fields[1];
	details=fields[2];

	// get gene index and locus (locus is start of coding region)
        fields=StringSplitter.trimsplit(gidxNloc,",");
	if (fields.length!=2) {
          System.err.println("Invalid gene location format on line "+linenum+"! Should be geneno,genestart");
	  System.exit(1);
	}
	gidx=Integer.parseInt(fields[0]);
	gloc=Long.parseLong(fields[1]);
//System.out.println("gidx,gloc="+gidx+","+gloc);

	// get feature details field
        fields=StringSplitter.trimsplit(featdets,",");
	if (fields.length!=3) {
          System.err.println("Invalid feature details format on line "+linenum+"! Should be feature,+/-offset,length");
	  System.exit(1);
	}
	feat=fields[0];
	sign=fields[1].charAt(0);
	if (sign=='+' || sign=='-') {
	  // parseInt doesn't handle sign extensions
	  featoffset=Integer.parseInt(fields[1].substring(1));
          if (sign=='-') { featoffset=featoffset*-1; }
	} else {
	  featoffset=Integer.parseInt(fields[1]);
	}
	featlen=Integer.parseInt(fields[2]);
//System.out.println("feat,offset,len="+feat+","+featoffset+","+featlen);
//System.out.println("details="+details);

        // now process/store this info
        storeGeneInfo(gidx,gloc,feat,featoffset,featlen,details);
      }
    }
  } catch (Exception e) {
    System.err.println("Error reading file!");
    System.err.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }
 
  // finalise the last unprocessed gene
  if (promoter!=null) {
//System.out.println("adding gene...");
    addGene();
  }

  try { 
    // finished with file, so close it
    inputstream.close();
  } catch (Exception e) {
    System.err.println("Error closing file! exiting...");
    System.exit(1);
  }

}

  
}// end class

// helper classes


class SiteDetails {

	public String type;
	public int offset;
	public int length;
	public String details;

SiteDetails(String typ, int off, int len, String dets)
{
  type=typ;
  offset=off;
  length=len;
  details=dets;
}

}// end class




