
//			 MhwCell.java
//
// this class implements the EHW morphogenesis process on a single cell
//
// 		written Dec 2003, J.A. Lee
//
// this class also requires the local classes:
// 	CellResourcesMatrix, ChromInfo, GeneInfo, GeneState, Polymerase


// for String's etc
import java.lang.*;
// for collection classes (eg Map) & Random class
import java.util.*;
// for IO
import java.io.*;





class MhwCell {

	// the following is an informative comment to store with state
	public static String statecomment=
    	"# the format for storing the state of a MhwCell object is:\n"+
    	"# 	cellrow,cellcol: totalFreeActivation: details1:details2:..\n"+
	"# where details is one of TF-state,polymerase-state,gene-state\n"+
	"# 	TF=free/bound/morphogen;TF0;TF1;..\n"+
	"#	   with free TFs in form: distfromsrc+sequence,locus,ttl\n"+
	"#	   bound TFs: distfromsrc+sequence@locus,ttl\n"+
	"#	   unreleased morphogens: ttr,distfromsrc+sequence,locus,ttl\n"+
	"#	poly=transloc,geneidx\n"+
	"#	gene=activation$esb0,esb1,esb2, .. $rsb0,rsb1,rsb2, ..\n\n";


	int numRP2;			// number of polymerase molecules
	Polymerase[] RNApoly2;		// these are used for transcription

	int numGenes;
	GeneState[] genes;		// cell's genes

  	CellResourcesMatrix resources;	// all config/queryable resources
	int row,col;			// cell location

	int transcriptionRate;		// how many bases transcribed per step
	double RP2activationThreshold;	// prob of NOT activating free poly
	double totalFreeActivation;	// sum activation of unbound genes

	Random rand;			// random number generator
        boolean DEBUG;


// this is for testing purposes only
public static void main(String[] args)
{


  if (args.length<5) {
    System.err.println("synopsis: MhwCell chromosomefile devname bitfile contentionfile cytoplasmfile");
    System.err.println("eg: MhwCell dchrom.txt XCV50 null50GCLK1.bit contentionInfo.txt cytobits.txt");
    System.exit(1);
  }

  // load chromosome from decoded chromosome file
  ChromInfo chrom=new ChromInfo(args[0]);
  System.out.println("loaded decoded chromosome of length "+chrom.length()+" with "+chrom.numGenes()+" genes..."); 

  String devname=args[1];			// eg "XCV50";
  String bitfile=args[2];			// eg null50GCLK1.bit
  String contentious=args[3];
  String cytofile=args[4];
  int lifespan=8;
  int ageratef=3;
  int agerateb=5;
  int clbrowmin=5;
  int clbcolmin=5;
  int clbrowmax=6;
  int clbcolmax=6;

  int row=0;
  int col=1;
  int numpoly=5;
  double polythreshold=0.5;
  int transcriptionrate=2;

  long seed=1113;
  Random rand=new Random(seed);
  boolean debug=true;

  // create the (interface to the) resources to manipulate and query
  CellResourcesMatrix cells=new CellResourcesMatrix(devname,
      clbrowmin,clbcolmin,clbrowmax,clbcolmax,
      contentious,bitfile,cytofile,lifespan,ageratef,agerateb,rand);

  // create the cell
  MhwCell cell=new MhwCell(row,col,numpoly,polythreshold,transcriptionrate,chrom,cells,rand,debug);

  // print initial state
  System.out.println("initial state is:");
  cell.printState();


  String state=null;
  // step and print state a few times
  for (int i=0; i<10; i++) {
    System.out.println("\nentering iteration "+i+"..");
    if (i==5) {
      System.out.println("*** saving state...");
      state=cell.getState();
      System.out.println("save state: "+state+"\n\n");
    }
    cell.step();		// coz its debug it will printState() too
    System.out.println("\n");
  }

  // the following is needed so as to restore the state of the random object
  // and cellresources when we restore the cell state. so create dummy cell
  // and iterate through the same # times to put the random object and cell
  // resources in same state as when cell state was saved - this just saves
  // us saving serialised random object to file and restoring cell
  // resources
  rand=new Random(seed);
  cells=new CellResourcesMatrix(devname,clbrowmin,clbcolmin,clbrowmax,
      clbcolmax,contentious,bitfile,cytofile,lifespan,lifespan,ageratef,agerateb,1,rand);
  MhwCell dummycell=new MhwCell(row,col,numpoly,polythreshold,transcriptionrate,chrom,cells,rand);
  for (int i=0; i<5; i++) { dummycell.step(); }
  

  System.out.println("\nrestoring state...");
  cell.setState(state,rand);
 
  // print restored state
  System.out.println("restored state is:");
  cell.printState();

  // and check all ok
  System.out.println("iterating 1 step...");
  cell.step();

}




public MhwCell(int r, int c, int nrp2, double pa, int trate, 
		    ChromInfo chrom, CellResourcesMatrix res, Random rnd)
{
  this(r,c,nrp2,pa,trate,chrom,res,rnd,false);
}


public MhwCell(int r, int c, int nrp2, double pa, int trate, 
		    ChromInfo chrom, CellResourcesMatrix res,
		    Random rnd, boolean dbg)
{
  DEBUG=dbg;

  row=r;
  col=c;

  RP2activationThreshold=pa;
  transcriptionRate=trate;

  resources=res;
  rand=rnd;

  // instantiate the polymerase molecules
  // evenly spreading them over the length of the chromosome
  numRP2=nrp2;
  RNApoly2=new Polymerase[numRP2];
  for (int i=0; i<numRP2; i++) {
    RNApoly2[i]=new Polymerase();
    // note need to ensure that random # from nextLong() is +ve
    RNApoly2[i].transloc=Math.abs(rand.nextLong()) % chrom.length();
  }

  // instantiate the genes using information from the decoded chromosome
  numGenes=chrom.numGenes();
  genes=new GeneState[numGenes];
  for (int i=0; i<numGenes; i++) {
    genes[i]=new GeneState(chrom.getGene(i));
  }

  totalFreeActivation=0;
}



public void step()
{
  updateBinding();		// update binding of resources to regulators
  updateActivations();		// update activation levels of promoters
  updatePolymeraseBinding();	// randomly bind some RNApoly2 to promoter
  resources.updateTFs(row,col);	// age, remove old TFs, and update morphogens
  for (int i=0;i<transcriptionRate;i++) {
    updateTranscribe();		// transcribe for each active RNApoly2
  }
  // note that updateTFs() was originally done here, after transcription.
  // unfortunately this means that TFs were aged prematurely,
  // which meant that their time to live parameters were misleading
  if (DEBUG) { printState(); }
}


void updateTranscribe()
{
  if (DEBUG) { System.out.println("updating transcription in cell "+row+","+col+"..."); }
  int gidx;
  // for each bound polymerase molecule, perform a transcription step
  for (int i=0; i<numRP2; ++i) {
    if (!RNApoly2[i].isBound()) continue; // skip if molecule not bound
    gidx=RNApoly2[i].geneidx;
    if (DEBUG) { System.out.println("updating transcription of gene "+gidx+" by polymerase "+i);}
    transcribe(genes[gidx]);			// transcribe
    if (!RNApoly2[i].isBound()) {		// if reached end of gene
      if (DEBUG) { System.out.println("released polymerase "+i+" from gene "+gidx+" at locus "+RNApoly2[i].transloc+"..."); }
    }
  }
}



// perform a transcription step on the gene,
// if reach end of gene, releases the polymerase molecule
void transcribe(GeneState g)
{
  // transcribe the product at current location (if any)
  String product=g.transcribeStep();
  // if there was a product then set the appropriate resource
  if (product!=null) {
    if (DEBUG) { System.out.println("produced product "+product+" at "+g.poly.transloc); }
    resources.set(row,col,product);
  }
}


// update the binding of resources to bind sites on gene's regulatory regions
void updateBinding()
{
  if (DEBUG) { System.out.println("updating binding in cell "+row+","+col+"..."); }
  int gidx;
  // for each gene that is not being transcribed
  for (int i=0; i<numGenes; ++i) {
    if (!genes[i].isFree()) continue;	// ignore transcribing genes
    bindRegulators(genes[i]);		// update binding on regulator sites
  }
}



// set gene's bindsite states according to if any resources bind to it
void bindRegulators(GeneState g)
{
  for (int i=0; i<g.numenhb; i++) {
    g.enhbSiteBound[i]=queryBinds(g.getEnhb(i));
  }

  for (int i=0; i<g.numrepb; i++) {
    g.repbSiteBound[i]=queryBinds(g.getRepb(i));
  }
}


// bsite is a HashMap of resource settings => locus
int queryBinds(Map bsite)
{
  int binds=0;
  Long loc;
  String res;

  Iterator iter = bsite.entrySet().iterator();
  while (iter.hasNext()) {
    Map.Entry e = (Map.Entry)iter.next();
    // System.out.println(e.getKey() + " => " + e.getValue());
    res=(String)e.getKey();
    loc=(Long)e.getValue();
    if (DEBUG) { System.out.println("checking binding of "+res+" at "+loc); }
    // check if bound, and in case of TFs, if can bind a matching free TF
    if (resources.isBound(row,col,res,loc.longValue())) {
      binds++;
      break;	// because we only need to know if ANY bind for this version
    }
  }

  return binds;
}



// after binding of resources to regulator bind sites
// can determine activation levels of the genes

void updateActivations()
{
  double activation;
  totalFreeActivation=0;
  for (int i=0; i<numGenes; i++) {
    if (genes[i].isFree()) {
      if (DEBUG) { System.out.println("updating gene "+i+"'s activation..."); }
      activation=genes[i].updateActivation();
      if (DEBUG) { System.out.println("gene "+i+"'s activation="+activation); }
      // total activation is only of genes that aren't suppressed
      if (activation>=0.0) {
        totalFreeActivation+=activation;
      }
    } //else {
      //if (DEBUG) { System.out.println("gene "+i+" not free - being transcribed..."); }
    //}
  }
}



// randomly bind free polymerase molecules to a strongly activated promoter
// (as determined by binding of cell resources to regulators)

void updatePolymeraseBinding()
{

  if (DEBUG) { System.out.println("updating binding of polymerase molecules..."); }


  int gidx=-1;
  for (int i=0; i<numRP2; ++i) {
    //if (DEBUG) { System.out.println("checking polymerase "+i+"'s bind status..."); }
    if (!RNApoly2[i].isBound()) {	// if molecule is unbound
      //if (DEBUG) { System.out.println("polymerase "+i+" is unbound..."); }

      // adding a random factor here so that binding of Rpoly2 to
      // promoters doesn't happen immediately, and there is a variable
      // number of genes being transcribed at any one time
      if (rand.nextDouble()>RP2activationThreshold) {
        gidx=chooseGene();

        // if found a free gene then set it's state to bound, and remove its
        // activation from the total, and then bind the polymerase molecule to
        // the start of the gene coding region (other possibility would be to
        // use the promoter's locus, but this may take lot of time before it
        // reaches the gene coding region) its termination point will be the
        // last base of the associated gene coding region
        if (gidx>=0) {
          genes[gidx].bindPolymerase(RNApoly2[i],gidx);
          totalFreeActivation-=genes[gidx].activation;
          if (DEBUG) { System.out.println("binding polymerase "+i+" to gene "+gidx+" with activation: "+genes[gidx].activation+" @ "+RNApoly2[i].transloc+"..."); }
        }
      }
    }
  }
}



// choose gene (or none) weighted by activation level
// randomly choose one of the non-repressed genes with probability being
// determined by
// sum non -ve activations traversed so far / freePromoters activation total

int chooseGene()
{
  if (DEBUG) { System.out.println("choosing a gene to bind polymerase molecule..."); }
  double threshold=rand.nextDouble()*totalFreeActivation;
  double sum=0;
  int gidx=-1;
  int i=0;

  while (sum<threshold && i<numGenes) {
    if (genes[i].isFree() && genes[i].activation>=0.0) {
if (DEBUG) { System.out.println("gene "+i+" is available ..."); }
      sum+=genes[i].activation;
      gidx=i;
    }
    i++;
  }

  // gidx should be the index of the free gene that exceeded the threshold
  // if gidx<0 then there are no free promoters

  return gidx;
}


// get the number of currently active genes in the cell
// ie genes bound to by polymerase
public int getActiveGeneCount() 
{
  int cnt=0;
  for (int i=0; i<numRP2; ++i) {
    if (RNApoly2[i].isBound()==true) { cnt++; }
  }

  return cnt;
}


// get a list of the indexes of currently active genes in the cell
public int[] getActiveGeneList() 
{
  List glist=new ArrayList();
  for (int i=0; i<numRP2; ++i) {
    if (RNApoly2[i].geneidx>-1) {
      glist.add(new Integer(RNApoly2[i].geneidx));
    }
  }

  int[] activelist=new int[glist.size()];
  for (int i=0; i<glist.size(); i++) {
    activelist[i]=((Integer)glist.get(i)).intValue();
  }

  return activelist;
}



// print the state of various parts of the system for debugging purposes
void printState()
{
  String bs;
  // print status of Polymerase molecules
  String s=new String("");
  for (int i=0; i<numRP2; ++i) {
    if (RNApoly2[i].isBound()==true) {
      s+="("+RNApoly2[i].geneidx+":"+RNApoly2[i].transloc+"->"+genes[RNApoly2[i].geneidx].ginfo.gendloc+")";
    } else {
      s+="("+i+":unbound@"+RNApoly2[i].transloc+")";
      //s+="("+i+":unbound)";
    }
  }
  System.out.println("polymerase bind states: "+s);

  // print status of genes
  s=new String("");
  for (int i=0; i<numGenes; i++) {
    s+="["+i+":";
    if (genes[i].isFree()) {
      s+=genes[i].activation;
      // print bind site states
      bs=new String("<enhb states: ");
      for (int j=0; j<genes[i].numenhb; j++) {
	bs+="{"+j+":"+genes[i].enhbSiteBound[j]+"}";
      }
      bs+="><repb states: ";
      for (int j=0; j<genes[i].numrepb; j++) {
	bs+="{"+j+":"+genes[i].repbSiteBound[j]+"}";
      }
      bs+=">";
      s+=bs;
    } else {
      s+="transcribing";
    }
    s+="]";
  }
  System.out.println("gene activation states: "+s);
  System.out.println("total free gene activation: "+totalFreeActivation);

  // print the state of bound & unbound TFs, etc
  // resources.printState();

  System.out.println("...");
}



// clear all TF resources, and reset all polymerase and gene states
void clearCellState()
{
  // remove all TF's
  resources.clear(row,col);

  // reset all polymerase states
  for (int i=0; i<numRP2; i++) {
    RNApoly2[i].geneidx=-1;
    RNApoly2[i].transloc=-1;
  }

  // reset the state of all the genes
  for (int i=0; i<numGenes; i++) {
    genes[i].clear();
  }
}


// returns the cell's state (excluding FPGA state) in format:
// 	cellrow,cellcol: totalFreeActivation: polydetails: genedetails: 
// 		freeTFdetails: boundTFdetails: morphogenReleasedetails
public String getState()
{
  String state=row+","+col+":"+totalFreeActivation+":";
  state+=getPolystate()+":";
  state+=getGenestate()+":";
  state+=resources.getTFstate(row,col);
  return state;
}


// returns a semicolon delimited list of:
//	transloc,geneidx
String getPolystate()
{
  String state="";
  for (int i=0;i<RNApoly2.length;i++) {
    state+=RNApoly2[i].transloc+","+RNApoly2[i].geneidx+";";
  }
  if (state.charAt(state.length()-1)==';') {
    state=state.substring(0,state.length()-1); // chop off trailing ";"
  }

  return state;
}


// returns a semicolon delimited list of:
// 	activation$esb0,esb1,esb2, .. $rsb0,rsb1,rsb2, ..
String getGenestate()
{
  String state="";
  for (int i=0;i<genes.length;i++) {
    state+=genes[i].getState()+";";
  }
  if (state.charAt(state.length()-1)==';') {
    state=state.substring(0,state.length()-1); // chop off trailing ";"
  }

  return state;
}


// set's the cell's state (excluding FPGA state) from string in format:
// 	cellrow,cellcol: totalFreeActivation: polydetails: genedetails: 
// 		freeTFdetails: boundTFdetails: morphogenReleasedetails
// note that we need to set random object's state too, otherwise cell
// behaviour will diverge


public void setState(String statestr, Random rnd)
{
  // restore random number generator to previous state
  rand=rnd;

  // reset all TF resources, polymerase and gene states before setting state
  clearCellState();

  if (DEBUG) { System.out.println("clearing state before setting ..."); }
  if (DEBUG) { printState(); }
  if (DEBUG) { System.out.println("setting state to: "+statestr); }
  
  String[] fields=StringSplitter.trimsplit(statestr,":");
  if (fields.length<7) {
    System.err.println("Error in parsing MhwCell settings (requires coords:totalFreeActivation:polydetails:genedetails:freeTFdetails:boundTFdetails:morphogenReleasedetails) in: " + statestr + "\n");
    System.exit(1);
  }

  totalFreeActivation=Double.parseDouble(fields[1]);
  setGenestate(fields[3]); // setup genes b4 bind polymerase to them
  setPolystate(fields[2]);
  setTFstate(fields[4],fields[5],fields[6]);

}

// free & bound TF states are semicolon-delimited lists of the following:
// 	free state:	distfromsrc+sequence,locus,ttl
// 	bound state:	distfromsrc+sequence@locus,ttl
// 	morphogenRelease state: ttr,distfromsrc+sequence,locus,ttl
void setTFstate(String freestate, String boundstate, String morphostate)
{
  if (DEBUG) { System.out.println("setting free TF state to: "+freestate); }
  if (DEBUG) { System.out.println("setting bound TF state to: "+boundstate); }
  if (DEBUG) { System.out.println("setting unreleased morphogen state to: "+morphostate); }
  resources.setTFstate(row,col,freestate+":"+boundstate+":"+morphostate);
}

// state is a semicolon delimited list of: transloc,geneidx
void setPolystate(String statestr)
{
  String[] fields;
  String[] states=StringSplitter.trimsplit(statestr,";");
  for (int i=0;i<states.length;i++) {
    if (DEBUG) { System.out.println("setting polymerase "+i+" state to: "+states[i]); }
    fields=StringSplitter.trimsplit(states[i],",");
    if (fields.length<2) {
      System.err.println("Error in parsing MhwCell Polymerase settings (requires transloc,geneidx) in " + statestr + "\n");
      System.exit(1);
    }
    // because of possibly -ve numbers, need to use StringSplitter fns to parse
    RNApoly2[i].transloc=StringSplitter.parselong(fields[0]);
    RNApoly2[i].geneidx=StringSplitter.parseint(fields[1]);
    // also need to re-bind this polymerase to its associated gene
    if (RNApoly2[i].geneidx>=0) {
      genes[RNApoly2[i].geneidx].poly=RNApoly2[i];
    }
  }
}

// state is a semicolon delimited list of:
// 		activation$esb0,esb1,esb2, .. $rsb0,rsb1,rsb2, ..
void setGenestate(String statestr)
{
  String[] fields=StringSplitter.trimsplit(statestr,";");
  for (int i=0;i<fields.length;i++) {
    if (DEBUG) { System.out.println("setting gene "+i+" state to: "+fields[i]); }
    genes[i].setState(fields[i]);
  }
}


} // end class




