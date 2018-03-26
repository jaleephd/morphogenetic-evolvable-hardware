//			 GeneState.java
//
// this class stores a gene's state, that being whether or not it is
// being transcribed (and the current location of he bound polymerase
// molecule), and its activation according to the binding of resources
// to its bind sites. It also provides methods for performing a
// transcription step, and for accessing the enhancer and repressor bind
// sites hash map's of binding-resource-settings => locus. Direct access
// is allowed to the bind count/flag arrays for the enhancer/repressor
// bind sites. Methods are also provided for saving, loading, and clearing
// the gene's state.
//
// this class also requires local classes: Polymerase, GeneInfo
//
// 		written December 2003, J.A. Lee


// for collection classes (eg List and Map)
import java.util.*;
// for Integer and Math class functions
import java.lang.*;



class GeneState {

  	public GeneInfo ginfo;
	public double activation;
	public Polymerase poly;		// null indicates gene is free

	public int numenhb;
	public int numrepb;

	// these are directly manipulated by the morphogenesis system
	public int[] enhbSiteBound;
	public int[] repbSiteBound;


// this is for testing purposes only
public static void main(String[] args)
{

  if (args.length<1) {
    System.err.println("need to specify decoded chromosome file!");
    System.exit(1);
  }

  // load chromosome from decoded chromosome file
  ChromInfo ci=new ChromInfo(args[0]);
  System.out.println("loaded decoded chromosome with "+ci.numGenes()+" genes...");
  System.out.println("printing contents of gene 0...");
  GeneInfo gi=ci.getGene(0);
  System.out.println(gi);

  // init genestate with a geneinfo entry
  System.out.println("initialising gene state...");
  GeneState gs=new GeneState(gi);

  // bind a polymerase to gene
  System.out.println("binding polymerase...");
  Polymerase poly=new Polymerase();
  poly.geneidx=0;
  gs.bindPolymerase(poly);

  // perform a transcription step on gene
  System.out.println("performing transcription...");
  String gp;
  while ((gp=gs.transcribeStep())==null) {
    System.out.println(poly.transloc+" .. ");
  }
  System.out.println("produced: "+gp+" at locus: "+poly.transloc);

  // get the regulator sites and set regulator Site bounds
  System.out.println("enhancer bindsites...");
  String ebs="";
  for (int i=0; i<gs.numenhb; i++) {
    gs.enhbSiteBound[i]=i%2;
    ebs+="["+i+"]="+gs.enhbSiteBound[i]+";";
    System.out.println("enhb["+i+"]: "+gs.getEnhb(i));
  }

  System.out.println("repressor bindsites...");
  String rbs="";
  for (int i=0; i<gs.numrepb; i++) {
    gs.repbSiteBound[i]=(i+1)%2;
    rbs+="["+i+"]="+gs.repbSiteBound[i]+";";
    System.out.println("repb["+i+"]: "+gs.getRepb(i));
  }

  System.out.println("enhancer site bounds: "+ebs);
  System.out.println("repressor site bounds: "+rbs);

  System.out.println("updating gene activation...");
  gs.updateActivation();
  System.out.println("gene activation is:"+gs.activation);

  // save state
  System.out.println("gene state is:"+gs.getState());
  System.out.println("saving state...");
  String state=gs.getState();

  // clear state
  System.out.println("clearing state...");
  gs.clear();
  System.out.println("gene state is: "+gs.getState());

  // reload state
  System.out.println("restoring state...");
  gs.setState(state);
  System.out.println("gene state is: "+gs.getState());

  gs.updateActivation();
  System.out.println("gene activation is: "+gs.activation);

  System.out.println("restoring polymerase at "+poly.transloc+"..");
  gs.poly=poly;

  // perform transcription till reach end of gene
  System.out.println("performing transcription...");
  while (!gs.isFree()) {
    if ((gp=gs.transcribeStep())==null) {
      System.out.println(poly.transloc+" .. ");
    } else {
      System.out.println("produced: "+gp+" at locus: "+poly.transloc);
    }
  }

  System.out.println("released polymerase at..."+poly.transloc);
}


GeneState(GeneInfo info)
{
  ginfo=info;
  numenhb=ginfo.enhb.size();
  numrepb=ginfo.repb.size();
  enhbSiteBound=new int[numenhb];	// 0 is unbound, >=1 indicates bound
  repbSiteBound=new int[numrepb];
}


// perform a transcription step on the gene,
// if reach end of gene, releases the polymerase molecule
public String transcribeStep()
{
  String product=null;
  if (poly!=null) {
    product=ginfo.getTranscribed(poly.transloc);
    poly.transloc++;
    if (poly.transloc>ginfo.gendloc) {	// reached end of gene
      releasePolymerase();
    }
  }
  return product;
}

// is gene bound by polymerase molecule for transcription
public boolean isFree() { return poly==null; }


// bind polymerase molecule to start of gene coding region,
// and set polymerase's geneidx
public void bindPolymerase(Polymerase p, int gidx) 
{
  bindPolymerase(p);
  poly.geneidx=gidx;
}

// bind polymerase molecule to start of gene coding region
// note: still need to manually set the polymerase geneidx or will appear
//       to be unbound
public void bindPolymerase(Polymerase p) {poly=p; poly.transloc=ginfo.glocus;}


// releases polymerase from gene
public void releasePolymerase() { poly.geneidx=-1; poly=null; }

// get this bind site's HashMap of binding-resource-settings => locus
public Map getEnhb(int i) { return (Map)ginfo.enhb.get(i); }

// get this bind site's HashMap of binding-resource-settings => locus
public Map getRepb(int i) { return (Map)ginfo.repb.get(i); }


// update activation levels of promoter according to binding
// of resources and TFs to its regulators' bind sites
double updateActivation()
{
  int ecnt=0, rcnt=0;
  int edst=0, rdst=0;
  double emetric=0, rmetric=0;

  activation=0;

  // regulator metric = count(bound sites)/sqrt(sum(bind dist-from-promoter))
  for (int i=0; i<numenhb; i++) {
    if (enhbSiteBound[i]>0) {
      // the following allows for a count of binds on a bind site
      ecnt+=enhbSiteBound[i];
      edst+=((Integer)ginfo.enhSiteDistMetric.get(i)).intValue();
    }
  }
  if (edst>0) { emetric=ecnt/(Math.sqrt((double)edst)); }

  for (int i=0; i<numrepb; i++) {
    if (repbSiteBound[i]>0) {
      // the following allows for a count of binds on a bind site
      rcnt+=repbSiteBound[i];
      rdst+=((Integer)ginfo.repSiteDistMetric.get(i)).intValue();
    }
  }
  if (rdst>0) { rmetric=rcnt/(Math.sqrt((double)rdst)); }

  // promoter activation = (E-R)/(E+R)
  if (emetric+rmetric>0) { activation=(emetric-rmetric)/(emetric+rmetric); }

  // System.out.println("activation="+activation+": ecnt="+ecnt+", edst="+edst+", emetric="+emetric+" rcnt="+rcnt+", rdst="+rdst+", rmetric="+rmetric+" ...");

  return activation;
}



public void clear() { clearState(); }

// reset the state of the gene to free, 0 activation, and no sites bound
public void clearState()
{
  poly=null;
  activation=0;
  for (int i=0; i<enhbSiteBound.length; i++) { enhbSiteBound[i]=0; }
  for (int i=0; i<repbSiteBound.length; i++) { repbSiteBound[i]=0; }
}

// gene state is in the form of:
// 	activation$esb0,esb1,esb2, .. $rsb0,rsb1,rsb2, ..
public String getState()
{
  String state=activation+"$"+getEbstate()+"$"+getRbstate();
  return state;
}

String getEbstate()
{
  String state="";
  for (int i=0; i<enhbSiteBound.length; i++) {
    state=state+enhbSiteBound[i]+",";
  }
  if (state.length()>0) {
    state=state.substring(0,state.length()-1); // get rid of trailing comma
  }
  return state;
}

String getRbstate()
{
  String state="";
  for (int i=0; i<repbSiteBound.length; i++) {
    state=state+repbSiteBound[i]+",";
  }
  if (state.length()>0) {
    state=state.substring(0,state.length()-1); // get rid of trailing comma
  }
  return state;
}


// gene state is in the form of:
// 	activation$esb0,esb1,esb2, .. $rsb0,rsb1,rsb2, ..
public void setState(String statestr)
{
  String[] fields=StringSplitter.trimsplit(statestr,"$");
  if (fields.length<3) {
    System.err.println("Error in parsing GeneState settings (requires activation$esb0,esb1,esb2, .. $rsb0,rsb1,rsb2, ..) in " + statestr + "\n");
    System.exit(1);
  }
  activation=Double.parseDouble(fields[0]);
  setEbstate(fields[1]);
  setRbstate(fields[2]);
}

// state is a comma-delimited list of ints representing the values in the
// enhbSiteBound array
void setEbstate(String statestr)
{
  String[] vals=StringSplitter.trimsplit(statestr,",");
  for (int i=0; i<vals.length; i++) {
    enhbSiteBound[i]=Integer.parseInt(vals[i]);
  }
}

// state is a comma-delimited list of ints representing the values in the
// repbSiteBound array
void setRbstate(String statestr)
{
  String[] vals=StringSplitter.trimsplit(statestr,",");
  for (int i=0; i<vals.length; i++) {
    repbSiteBound[i]=Integer.parseInt(vals[i]);
  }
}



} // end class


