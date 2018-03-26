

//			 MhwMatrix.java
//
// this class implements the EHW morphogenesis process across a matrix
// of cells
//
// 		written Dec 2003, J.A. Lee
//
// this class also requires the local classes:
// 	StringSplitter, ChromInfo, GeneInfo, GeneState, Polymerase,
// 	CellResourcesMatrix, MhwCell, CellOrdering, CellCoords


// for String's etc
import java.lang.*;
// for collection classes (eg Map) & Random class
import java.util.*;
// for IO
import java.io.*;
// for JBits object
import com.xilinx.JBits.Virtex.JBits;





class MhwMatrix {

  // the following is an informative comment to store with state
  public static String statecomment=
    "# the format for storing the state of a MhwMatrix object is:\n"+
    "#   *,*: MHW=randstatefile,bitstream,\n"+
    "#        deviceName,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol,\n"+
    "#        inCellrow0/inCellcol0 inCellrow1/inCellcol1 ..,\n"+
    "#        outCellrow0/outCellcol0 outCellrow1/outCellcol1 ..,\n"
   +"#        contention-file, chromfile, cytoplasmFile,\n"+
    "#        RP2perGene, RP2activation-Threshold, transcription-rate,\n"+
    "#        morpholifespan, tflifespan, tfageratefree, tfageratebound,\n"+
    "#        morphoPropDelay, vgran, glimit, debug-flag\n"+
    "# note: same order for fields instantiating a MhwMatrix instance,\n"+
    "#       but with randstatefile replacing the random seed\n\n";

        String randstatefile;		// name of file Random obj stored in
  	String classargs;		// stores args class instantiated w/
        boolean DEBUG;
	Random rand;			// random number generator

	double polyToGeneRatio;		// num polymerase per gene
	int numRP2;			// number of polymerase molecules
	double RP2activationThreshold;  // prob of NOT activating free poly
	int transcriptionRate;		// how many bases transcribed per step

	ChromInfo chromosome;		// the decoded chrom parsed from file
	CellResourcesMatrix resources;	// all the config/queryable resources
	MhwCell cellmatrix[][];		// the cells

	int numCellRows;		// number of rows of cells
	int numCellCols;		// number of columns of cells

	int minCLBrow;			// these are the borders of the FPGA
	int maxCLBrow;			// inside which morphogenesis will
	int minCLBcol;			// be run
	int maxCLBcol;

	CellCoords[] incoords;		// cells receiving external inputs
	CellCoords[] outcoords;		// cells providing external outputs

	List updateorder;		// cell coords ordered for update



// this is for testing purposes only
public static void main(String[] args)
{


  if (args.length<5) {
    System.err.println("synopsis: MhwMatrix chromosomefile devname bitfile contentionfile cytoplasmfile");
    System.err.println("eg: MhwMatrix dchrom.txt XCV50 null50GCLK1.bit contentionInfo.txt cytobits.txt");
    System.exit(1);
  }


  String chromfile=args[0];
  String devname=args[1];			// eg "XCV50";
  String bitfile=args[2];			// eg null50GCLK1.bit
  String contentious=args[3];
  String cytofile=args[4];
  String minCLB="5,5";
  String maxCLB="8,6";
  String[] inCLBslices= { "5,5,0", "8,5,0" };
  String[] outCLBslices= { "7,6,1" };

  long lifespan=8;
  int ageratefree=3;
  int ageratebound=5;
  int morphoPropDelay=1;
  double npoly2gene=.7;
  double polythreshold=0.5;
  int transcriptionrate=2;
  long seed=1113;
  boolean debug=true;

  String savedbitFile="saved_"+devname+".bit";
  String savedstatefile="saved_mhw_state.txt";
  String savedrandfile="saved_randomstate.rnd";

  // create morphogenesis object
  MhwMatrix mhw=new MhwMatrix(seed,bitfile,devname,minCLB,maxCLB,
          inCLBslices,outCLBslices,contentious,chromfile,cytofile, 
	  npoly2gene,polythreshold,transcriptionrate,lifespan,lifespan,
	  ageratefree,ageratebound,morphoPropDelay,debug);

  // print initial state
  System.out.println("initial state is:"+mhw.getCellStates());

  // step through a few iterations
  for (int i=0; i<5; i++) {
    if (i==2) {
      System.out.println("\n----------------------- saving state -----------");
      mhw.saveState(savedstatefile,savedbitFile,savedrandfile);
      System.out.println("saved state is:"+mhw.getCellStates());
      System.out.println("----------------------- state saved -----------\n");
    }
    System.out.println("\nentering iteration "+i+"..");
    mhw.step();			// coz its debug it will printState() too
    System.out.println("\n************************************************\n");
  }

  // restore state
  System.out.println("\n----------------------- restoring state -----------");
  mhw=new MhwMatrix(savedstatefile);
 
  // print restored state
  System.out.println("restored state is:"+mhw.getCellStates());
  System.out.println("----------------------- state restored -----------\n");

  // and check all ok
  System.out.println("iterating 1 step...");
  mhw.step();
  System.out.println("final state (equiv iteration 3) is:"+mhw.getCellStates());
}


// instantiate class with state of a previously saved object
public MhwMatrix(String statefile)
{
  loadState(statefile);
}


public MhwMatrix(long seed, String bitFile, String deviceName,
          String minCLBcoord, String maxCLBcoord,
          String[] inputCLBslices, String[] outputCLBslices, 
          String contentionFile, String chromFile, String cytoplasmFile, 
	  double p2gratio, double pa, int trate, long mglifespan,
          long tflifespan, int tfageratefree, int tfageratebound,
	  int mgpropdelay)
{
  this(seed,bitFile,deviceName,minCLBcoord,maxCLBcoord,inputCLBslices,
       outputCLBslices,contentionFile,chromFile,cytoplasmFile,p2gratio,pa,
       trate,mglifespan,tflifespan,tfageratefree,tfageratebound,mgpropdelay,
       2,false);
}

public MhwMatrix(long seed, String bitFile, String deviceName,
          String minCLBcoord, String maxCLBcoord,
          String[] inputCLBslices, String[] outputCLBslices, 
          String contentionFile, String chromFile, String cytoplasmFile, 
	  double p2gratio, double pa, int trate, long mglifespan,
          long tflifespan, int tfageratefree, int tfageratebound,
	  int mgpropdelay, boolean dbg)
{
  this(seed,bitFile,deviceName,minCLBcoord,maxCLBcoord,inputCLBslices,
       outputCLBslices,contentionFile,chromFile,cytoplasmFile,p2gratio,pa,
       trate,mglifespan,tflifespan,tfageratefree,tfageratebound,mgpropdelay,
       2,dbg);
}

public MhwMatrix(long seed, String bitFile, String deviceName,
          String minCLBcoord, String maxCLBcoord,
          String[] inputCLBslices, String[] outputCLBslices, 
          String contentionFile, String chromFile, String cytoplasmFile, 
	  double p2gratio, double pa, int trate, long mglifespan,
          long tflifespan, int tfageratefree, int tfageratebound,
	  int mgpropdelay, int vgran, boolean dbg)
{
  this(seed,bitFile,deviceName,minCLBcoord,maxCLBcoord,inputCLBslices,
       outputCLBslices,contentionFile,chromFile,cytoplasmFile,p2gratio,pa,
       trate,mglifespan,tflifespan,tfageratefree,tfageratebound,mgpropdelay,
       vgran,0,dbg);
}

public MhwMatrix(long seed, String bitFile, String deviceName,
          String minCLBcoord, String maxCLBcoord,
          String[] inputCLBslices, String[] outputCLBslices, 
          String contentionFile, String chromFile, String cytoplasmFile, 
	  double p2gratio, double pa, int trate, long mglifespan,
          long tflifespan, int tfageratefree, int tfageratebound,
	  int mgpropdelay, int vgran, int glimit, boolean dbg)
{
  rand=new Random(seed); // initialise new random number generator
  init(bitFile,deviceName,minCLBcoord,maxCLBcoord,inputCLBslices,
       outputCLBslices,contentionFile,chromFile,cytoplasmFile,p2gratio,pa,
       trate,mglifespan,tflifespan,tfageratefree,tfageratebound,mgpropdelay,
       vgran,glimit,dbg);
}


// initialise the object, except for the random number generator, which
// is done elsewhere
void init(String bitFile, String deviceName, String mincoord, String maxcoord,
          String[] inputCLBslices, String[] outputCLBslices, 
          String contentionFile, String chromFile, String cytoplasmFile, 
	  double p2gratio, double pa, int trate, long mglifespan,
	  long tflifespan, int tfageratefree, int tfageratebound,
	  int mgpropdelay, int vgran, int glimit, boolean dbg)
{

  DEBUG=dbg;

  // cytoplasmFile may be null (or empty) if there isn't one
  if (cytoplasmFile==null) { cytoplasmFile=""; } // so can store in classargs

  // extract info from decoded chromosome
  chromosome=new ChromInfo(chromFile,glimit);
  polyToGeneRatio=p2gratio;
  // use values above 1.0 to explicitly set number of polymerase molecules
  // as values above 1.0 are meaningless, as there will always be enough
  // molecules for any gene
  if (polyToGeneRatio>1) {
    numRP2=(int)(polyToGeneRatio+0.5); // round to avoid precision errors
  } else {
    numRP2=(int)((chromosome.numGenes()*polyToGeneRatio)+0.5); // round up
     // ensure not 0, unless explicitly given with p2g of 0 or less
    if (numRP2<1 && chromosome.numGenes()>0 && polyToGeneRatio<=0) {
      numRP2=1;
    }
  }
  RP2activationThreshold=pa;
  transcriptionRate=trate;

  // get borders of the area of the FPGA that undergoes morphogenesis
  int[] mins=StringSplitter.str2ints(mincoord,",");
  if (mins.length!=2) {
    System.err.println("Invalid number of fields ("+mins.length+") for morphogenesis lower bound CLB coords");
    System.exit(1);
  }

  int[] maxs=StringSplitter.str2ints(maxcoord,",");
  if (maxs.length!=2) {
    System.err.println("Invalid number of fields ("+maxs.length+") for morphogenesis upper bound CLB coords");
    System.exit(1);
  }

  minCLBrow=mins[0];
  minCLBcol=mins[1];
  maxCLBrow=maxs[0];
  maxCLBcol=maxs[1];

  // cells with (horiz) slice granularity, (vert) CLB (2) or LE (1) gran
  resources=new CellResourcesMatrix(deviceName, mins[0], mins[1], maxs[0], maxs[1], contentionFile, bitFile, cytoplasmFile, mglifespan, tflifespan, tfageratefree, tfageratebound,mgpropdelay,1,vgran,rand);

  numCellRows=resources.getCellRows();
  numCellCols=resources.getCellCols();

  if (DEBUG) { System.out.println("created matrix of cells "+numCellRows+","+numCellCols+"..."); }
  if (DEBUG) { System.out.println("with horiz cell granularity of "+resources.getHGran()+" slice(s)"); }
  if (DEBUG) { System.out.println("vert cell granularity of "+resources.getVGran()+" logic element(s)"); }

  // convert IO coords from CLB-based strings to cell coords
  incoords=new CellCoords[inputCLBslices.length];
  for (int i=0; i<incoords.length; i++) {
    incoords[i]=CLBtoCellCoords(StringSplitter.str2ints(inputCLBslices[i],","));
    if (DEBUG) { System.out.println("incoords["+i+"]="+incoords[i]); }
  }

  outcoords=new CellCoords[outputCLBslices.length];
  for (int i=0; i<outcoords.length; i++) {
    outcoords[i]=CLBtoCellCoords(StringSplitter.str2ints(outputCLBslices[i],","));
    if (DEBUG) { System.out.println("outcoords["+i+"]="+outcoords[i]); }
  }

  // now create the matrix of cells
  cellmatrix=new MhwCell[numCellRows][numCellCols];
  for (int i=0; i<numCellRows; i++) {
    for (int j=0; j<numCellCols; j++) {
      cellmatrix[i][j]=new MhwCell(i,j,numRP2,pa,trate,chromosome,resources,rand,dbg);
    }
  }

  // initialise the cells initial state from cytoplasmic determinants file
  if (cytoplasmFile.length()>0) {
    resources.loadCytoFile(cytoplasmFile);
  }

  // now create a cell ordering for the updating of cells

  CellOrdering ordering=new CellOrdering(numCellRows,numCellCols,incoords,outcoords);
  updateorder=new ArrayList(ordering.order);
  if (DEBUG) { System.out.println("created cell ordering..."); }
  //if (DEBUG) { System.out.println(updateorder); }
  if (DEBUG) { ordering.printMatrix(); }

  // store the parameters the class was instantiated with so that can
  // save state if necessary
  classargs=deviceName+",";
  classargs+=minCLBrow+","+minCLBcol+","+maxCLBrow+","+maxCLBcol+",";
  for (int i=0; i<inputCLBslices.length;i++) {
    classargs+=inputCLBslices[i].replace(',','/');
    if (i<inputCLBslices.length-1) { classargs+=" "; }
    else { classargs+=","; }
  }
  for (int i=0; i<outputCLBslices.length;i++) {
    classargs+=outputCLBslices[i].replace(',','/');
    if (i<outputCLBslices.length-1) { classargs+=" "; }
    else { classargs+=","; }
  }
  classargs+=contentionFile+","+chromFile+","+cytoplasmFile+",";
  classargs+=polyToGeneRatio+","+RP2activationThreshold+",";
  classargs+=transcriptionRate+","+mglifespan+","+tflifespan+",";
  classargs+=tfageratefree+","+tfageratebound+","+mgpropdelay+",";
  classargs+=vgran+","+glimit+","+DEBUG;

  if (DEBUG) { System.out.println("\n\nstate comment="+statecomment); }
  if (DEBUG) { System.out.println("classargs="+classargs); }
}


// public access methods for boundaries
public int getMinCLBrow() { return minCLBrow; }
public int getMinCLBcol() { return minCLBcol; }
public int getMaxCLBrow() { return maxCLBrow; }
public int getMaxCLBcol() { return maxCLBcol; }

public int getCellRows() { return resources.getCellRows(); }
public int getCellCols() { return resources.getCellCols(); }

// update a specific cell
public void step(int row, int col) { cellmatrix[row][col].step(); }

// update all cells N times
public void step(int nsteps) { for (int i=0; i<nsteps; i++) { step(); } }

// update all cells
public void step()
{
  CellCoords coord;
  // this needs the cell ordering
  ListIterator iter = updateorder.listIterator();
  while (iter.hasNext()) {
    // get a cell coordinate
    coord=(CellCoords)iter.next();
    // update this cell
    (cellmatrix[coord.row][coord.col]).step();
  }

  if (DEBUG) { System.out.println("updated state: "+getCellStates()); }
}


// cell coords run from 0,0 at min CLB coords (slice 0 [S0-GY])
// to max row, max col at max CLB coords (slice 1 [S1-FX])

CellCoords CLBtoCellCoords(int[] coords)
{
// if (DEBUG) { for (int i=0; i<coords.length; i++) { System.out.print("coords["+i+"]="+coords[i]+" .. "); } System.out.println(); }
  int hgran=resources.getHGran();
  int vgran=resources.getVGran();
  if (coords.length==4 && hgran==1 && vgran==1) { // LE granularity
    return CLBtoCellCoords(coords[0],coords[1],coords[2],coords[3]);
  } else if (coords.length>=3 && vgran==2) {	// slice granularity
    return CLBtoCellCoords(coords[0],coords[1],coords[2]);
  } else {
    return CLBtoCellCoords(coords[0],coords[1]);
  }
}

// slice granularity horizontally, logic element gran horizontally
CellCoords CLBtoCellCoords(int r, int c, int s, int le)
{
//if (DEBUG) { System.out.println("converting CLB coord "+r+","+c+","+s+","+le+" to LE-gran cell"); }
  return CellCoords.CLBtoCellCoords(r,c,s,le,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
}

CellCoords CLBtoCellCoords(int r, int c, int s)
{
  int vgran=resources.getVGran();
  if (vgran==2) { // clb gran vert, slice gran horiz
//if (DEBUG) { System.out.println("converting CLB coord "+r+","+c+","+s+" to slice-gran cell"); }
    return CellCoords.CLBtoCellCoords(r,c,s,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
  } else { // (vgran==1) LE gran vert, slice gran horiz
//if (DEBUG) { System.out.println("slice -> LE gran"); }
    return CLBtoCellCoords(r,c,s,0);
  }
}

CellCoords CLBtoCellCoords(int r, int c)
{
  int hgran=resources.getHGran();
  int vgran=resources.getVGran();

  if (hgran==2 && vgran==2) { // clb gran both vert & horiz
//if (DEBUG) { System.out.println("converting CLB coord "+r+","+c+" to CLB-gran cell"); }
    return CellCoords.CLBtoCellCoords(r,c,minCLBrow,minCLBcol,maxCLBrow,maxCLBcol);
  } else { // (hgran==1) slice gran horiz (possibly LE gran vert)
    return CLBtoCellCoords(r,c,0);
  }
}


// get the number of currently active genes in a single cell
public int getActiveGeneCount(int row, int col) 
{
  return cellmatrix[row][col].getActiveGeneCount();
}

// get a list of the indexes of currently active genes in a cell
public int[] getActiveGeneList(int row, int col) 
{
  return cellmatrix[row][col].getActiveGeneList();
}


// get the state of a single cell
public String getCellState(int row, int col) 
{
  return cellmatrix[row][col].getState();
}

// get the state of the system, not including FPGA-state and Random state
public String getCellStates()
{
  String state="";

  // get state of each cell
  for (int i=0; i<numCellRows; i++) {
    for (int j=0; j<numCellCols; j++) {
      state+=cellmatrix[i][j].getState()+"\n";
    }
  }

  return state;
}

// this is to allow direct access to the FPGA resources, bypassing the
// morphogenesis system, and should be used with care. its main use
// would be to directly query FPGA signals for dynamic fitness tests
// run in parallel with morphogenesis
public JBits getJBits() { return resources.getJBits(); }
// this allows access via the JBitsResources class
public JBitsResources getJBitsResources() 
{ return resources.getJBitsResources(); }

// this is to get access to information about the chromosome, and its genes
public ChromInfo getChrom() { return chromosome; }
public int numGenes() { return chromosome.numGenes(); }


// save the current state of the system to file
// requires 3 files: s/w system state file, FPGA configuration bitstream,
// random object serialised state
public void saveState(String statefile, String bitFile, String randstatefile)
{
  // get system-wide state
  String state=MhwMatrix.statecomment+
  	       "*,*: MHW="+randstatefile+","+bitFile+","+classargs+"\n";
  state+=MhwCell.statecomment+"\n"+getCellStates();

  // save FPGA state
  writeBitstream(bitFile);
  // save state of psuedo-random number generator
  writeRandomState(randstatefile);

  // write state to file
  writeSWstate(state,statefile);
}



// write the FPGA's current configuration to file
public void writeBitstream(String outfile)
{
  resources.writeBitstream(outfile);
}


void writeRandomState(String randstatefile)
{
  try {
    FileOutputStream fos = new FileOutputStream(randstatefile);
    ObjectOutputStream oos = new ObjectOutputStream(fos);
    oos.writeObject(rand);
    oos.close();
  } catch (Exception e) {
    System.err.println("Error writing " + randstatefile + "\n" + e);
    System.exit(1);
  }
}


void writeSWstate(String state, String statefile)
{
  // create an array of lines, because line terminators for IO
  // may not be \n in system that running on
  String[] statelines=StringSplitter.split(state,"\n");
  
  File outputfile;
  FileWriter writer;
  BufferedWriter bufferedwriter;

  try {
    // create the file, a file writer, and buffered writer
    outputfile=new File(statefile);
    writer=new FileWriter(outputfile);
    bufferedwriter=new BufferedWriter(writer);

    // write the lines of state information to the file
    for (int i=0; i<statelines.length; i++) {
      bufferedwriter.write(statelines[i]);
      bufferedwriter.newLine();		// ensures platform independence
    }

    // close the file
    bufferedwriter.close();
  } catch (Exception e) {
    System.err.println("Error creating " + statefile + "\n" + e);
    System.exit(1);
  }
}



// this should only be called by the constructor, that's why its not public
void loadState(String statefile)
{
  List statelist=loadStateFromFile(statefile);

  // first line should be: *,*: MHW=randstatefile,bitstream, ...
  // ie all the variables for this class
  String mhwsettings=(String)statelist.get(0);
  // get rid of the *.*: MHW=
  mhwsettings=mhwsettings.substring(mhwsettings.indexOf('=')+1);
  // put each setting into a list
  String[] settings=StringSplitter.trimsplit(mhwsettings,",");
  if (settings.length<22) {
    System.err.println("Error in parsing MhwMatrix settings: too few settings ("+settings.length+") in " + mhwsettings + " from file "+ statefile +"\n");
    System.exit(1);
  }
  
  // settings 0-6 are: randstatefile,bitstream,deviceName,
  // 			minCLBrow,minCLBcol,maxCLBrow,maxCLBcol

  String randstatefile=settings[0];

  String bitFile=settings[1];
  String deviceName=settings[2];
  String minCLBcoords=settings[3]+","+settings[4];
  String maxCLBcoords=settings[5]+","+settings[6];
  
  // settings 7 is a space delimited list of inCLBcoords, with
  // row and col separated by a "/"
  settings[7]=settings[7].replace('/',',');
  String[] inputCLBcoords=StringSplitter.split(settings[7]," ");
  
  // settings 8 is a space delimited list of outCLBcoords, with
  // row and col separated by a "/"
  settings[8]=settings[8].replace('/',',');
  String[] outputCLBcoords=StringSplitter.split(settings[8]," ");

 // settings 9-22 are: contentionFile, chromFile, cytoplasmFile,
 //                    polyToGeneRatio, RP2activationThreshold,
 //                    transcriptionRate, mglifespan, tflifespan,
 //                    tfageratefree, tfageratebound, mgpropdelay,
 //                    vgran, glimit, debugflag

  String contentionFile=settings[9];
  String chromFile=settings[10];
  String cytoplasmFile=settings[11];
  double p2gratio=Double.parseDouble(settings[12]);
  double pa=Double.parseDouble(settings[13]);
  int trate=Integer.parseInt(settings[14]);
  long mglifespan=Long.parseLong(settings[15]);
  long tflifespan=Long.parseLong(settings[16]);
  int tfageratefree=Integer.parseInt(settings[17]);
  int tfageratebound=Integer.parseInt(settings[18]);
  int mgpropdelay=Integer.parseInt(settings[19]);
  int vgran=Integer.parseInt(settings[20]);
  int glimit=Integer.parseInt(settings[21]);
  boolean dbg=(settings[22].equals("true"));

  // create a dummy Random object here, that will be used when generating
  // the cells initial states, but since these states will be overwritten
  // by the saved state, we don't care what this temporary cell states are
  rand=new Random();

  // initialise Mhw object with saved state (cell states done after)
  init(bitFile, deviceName, minCLBcoords, maxCLBcoords,
            inputCLBcoords, outputCLBcoords, contentionFile, chromFile,
	    cytoplasmFile, p2gratio, pa, trate, mglifespan, tflifespan,
	    tfageratefree, tfageratebound, mgpropdelay, vgran, glimit, dbg);

  // we need the old Random object to be passed to all the cells
  rand=readRandomState(randstatefile);

if (DEBUG) { System.out.println("MHW state="+(String)statelist.get(0)); }
  int r,c;
  String statestr;
  String [] fields;
  String [] coords;
  // reload state for each cell by parsing coords
  // and then restoring state for that cell
  for (int i=1; i<statelist.size(); i++) {
    statestr=(String)statelist.get(i);
if (DEBUG) { System.out.println("cell state("+i+")="+statestr); }
    fields=StringSplitter.trimsplit(statestr,":");
    if (fields.length<2) {
      System.err.println("Error in parsing MhwCell settings (requires coords:settings) in" + statefile + "\n");
      System.exit(1);
    }
    coords=StringSplitter.trimsplit(fields[0],",");
    if (coords.length!=2) {
      System.err.println("Error in parsing MhwCell settings (requires row,col) in" + statefile + "\n");
      System.exit(1);
    }

    r=Integer.parseInt(coords[0]);
    c=Integer.parseInt(coords[1]);
    cellmatrix[r][c].setState(statestr,rand);
  }
}


Random readRandomState(String randstatefile)
{
  Random rnd=null;
  try {
    FileInputStream fis = new FileInputStream(randstatefile);
    ObjectInputStream ois = new ObjectInputStream(fis);
    rnd = (Random) ois.readObject();
    ois.close();
  } catch (Exception e) {
    System.err.println("Error reading " + randstatefile + "\n" + e);
    System.exit(1);
  }
  return rnd;
}



// load state information from file into a string of newline terminated lines,
// ignoring blank lines, and comment lines
List loadStateFromFile(String statefile)
{
  List statelist=new ArrayList();

  InputStream inputstream=null;
  InputStreamReader inputstreamreader=null;
  BufferedReader bufferedreader=null;

  String line;

  try {
    // open the file and create a buffered input stream reader
    inputstream = new FileInputStream(statefile);
    inputstreamreader=new InputStreamReader(inputstream);
    bufferedreader = new BufferedReader(inputstreamreader);
  } catch (Exception e) {
    System.err.println("Error opening " + statefile + "\n" + e);
    System.exit(1);
  }

  // read the file
  try {
    while ((line = bufferedreader.readLine()) != null) {
      line=line.trim();		// strip leading & trailing whitespace
//System.out.println(line);
      // ignore empty lines, and lines starting with a '#' indicating a comment
      if (line.length()>0 && line.charAt(0)!='#') {
	statelist.add(line);
      }
    }
  } catch (Exception e) {
    System.err.println("Error reading file!");
    System.err.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }


  try { 
    // finished with file, so close it
    inputstream.close();
  } catch (Exception e) {
    System.err.println("Error closing file! exiting...");
    System.exit(1);
  }


  return statelist;
}



} // end class





