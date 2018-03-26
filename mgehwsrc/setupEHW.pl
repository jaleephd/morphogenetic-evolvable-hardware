# setupEHW.pl by J.A. Lee, Feb 2004
#
#  Synopsis:
#  [perl] setupEHW.pl [-v] [-s] [-agb] [-notfs] [-code confile]              \
#  				[-exitonsuccess] [-exitonstagnate sg]	     \
#  		      		[-noexitoncctfail]                           \
#  		       		device rows cols inbuswidth outbuswidth      \
#  				testclass popsize [chromsize] datadir [      \
#  				numVgens reprate[+] xrate mrate irate idrate \
#  				numTestIter numGrowthSteps [ transcribeRate  \
#  				polyToGene polyThreshold ] [mgseed] ]
#
#  Args:
#  		-v		Verbose mode - generate info to STDIO during
#  				setup
#  		-s		save best bitstream option - during evolution
#  				the bitsream of the highest fitness is kept
#  		-agb		anti-gene-bloat for use with morphogenesis,
#  				factors in ratio of chromosome's gene count
#  				to fittest (to-date) chrom's gene count when
#  				evaluating fitness. If chrom has more genes,
#  				and is not fitter, then fitness is scaled down
#  				by the gene count ratio.
#	        -notfs		don't use TFs, incl. morphogens and cytoplasms
#  		-code confile	overides the default genetic code file
#  		-exitonsuccess	when get a 100% solution, finish generation
#  				and then exits. checkpoint population file is
#			        the successful generation and can be examined.
#	    -exitonstagnate sg  if maximum fitness (in population) has not
#				increased in 'sg' generations, then exit. The
#				checkpoint population file can be examined or
#				have evolution continued from.
#	      -noexitoncctfail	don't abort if a circuit evaluation fails,
#	      			just give genotype a fitness of 0 and continue
#   		device		FPGA device & resource subset (model). Must be
#   				one of the following, ordered with increasing
#   				resource limitations:
#		      XCV[1000|50] - slice granularity almost full model, use
#		      		  XCV50 with VirtexDS, and XCV1000 with Raptor
#		      XCV50sync - slice gran, lacks only asynch & Ctrl outputs
#		      XCV50nFF  - slice gran lacks unregistered & Ctrl outputs
#		      XCV50LE	- LE gran lacks SliceRAM & Ctrl inputs/outputs
#		      XCV50LEsync - LE gran also lacks unregistered outputs
#		      XCV50GAsync - as above but standard GA and LUTBitFNs
#		      XCV50LEnFF - same as sync but lacks registered outputs
#		      XCV50GAnFF - as above but standard GA and LUTBitFNs
#		      XCV50-	- LE gran uses 'trim' model with 2 outbus lines
#		      		  allocated per LE, synch slice out, LUTBitFNs
#		      XCV50GA-	- standard GA with 'trim' model
#		      XCV50--	- LE gran 'slim' model (1 LUT input, 4 LUT fn,
#		      		  2 OUT bus lines, 3-4 singles per LE)
#		      XCV50GA--	- standard GA with 'slim' model
#		      XCV50slim	- slice gran, 'slim' model
#  				note: a 'GA' suffix indicates that a standard
#				GA using a binary fixed length chromosome with
#  				a direct mapping to the FPGA is used.
#  		rows		number of CLB rows in evolvable region
#  		cols		number of CLB cols in evolvable region
#  		inbuswidth	width of input (DUT_in) bus
#  		outbuswidth	width of output (DUT_out) bus
#  		testclass	the name of the java classfile to run to (grow
#  				and) test evolved circuit. The output of this
#  				must be: fitness [@ iteration [; numgenes]]
#  		popsize		size of population to be generated
#  		chromsize	median size of chromosomes to generate, with
#  				sizes ranging from 0.5 - 1.5 x chromsize
#  				Note this parameter should be ommitted if
#  				XCV50GA device is specified
#  		datadir		where all the data files (eg population,
#  				bitstream) will be kept
#
#			... the following are not used if using standard GA
#
#  		numVgens	the number of generations to evolve initial
#  				population, to generate chromosomes containing
#  				genes for morphogenesis (including promoters,
#  				regulatory and transcription regions)
# (depreceated) or multiple chromosome sections for starting circuit construction at each of the IO cells
#  		reprate[+]	number of members to replace with new (maximum
#  				of 1/4 the population size). If followed by a
#  				'+', then both offspring of the 'reprate'
#  				selected parents are introduced into the
#  				population (which requires 'reprate' to be an
#  				even number), otherwise only the fitest of the
#  				2 x 'reprate' are introduced.
# 		xrate		crossover rate in offspring (0.0 - 1.0)
# 		mrate		rate of mutation of bases (0.0 - 1.0)
# 		irate		rate of inversion in offspring (0.0 - 1.0)
# 		idrate		rate of base insert/delete in offspring
# 				(0.0 - 1.0)
#
# (NDEHW depreceated so must be supplied) ... the following are only for morphogenetic approach and if supplied, will indicate that this approach will be used
#
#
#  		numTestIter	number of times to continue growth and test
#  				fitness when running morphogenesis
#  		numGrowthSteps	number of growth steps per fitness test
#  				when running morphogenesis
#
#		transcribeRate	number of bases transcribed each growth step
#				(default is 4)
#		polyToGene	RNA polymerase II molecules per gene, this
#				limits the number of genes that can be
#				active at any time. use a value < 1.0 to
#				indicate a ratio, or >1 to set a fixed number
#				(default is 0.3)
#		polyThreshold	theshold to be exceeded for a free polymerase
#				molecule to bind to a gene (default is 0.8
#				which means binding occurs 20% of the time)
#
#  		mgseed		optional random seed, to ensure the steps
#  				taken by morphogenesis process is reproducible
#
#  Inputs: (none)
#  Outputs:
#  	   (STDERR)		informative messages
#  	   POPBASENAME.txt	generated population of chromosomes
#  	   POPBASENAME.parm.txt parameters used to generate population
#  	   LAYOUTBIT		bitstream connected to GCLK, with input and
#  	   			output buses routed to border of evolvable
#  	   			region, a region of null CLBs
#  	   CYTOBITS.txt		generated file of cytoplasmic determinants
#  	   CONFIGFILE		configuration file for EHW system
#
#  Description:
#		generates EHW layout for the given device, an initial
#		population, and if performing morphogenesis a cytoplasmic
#		determinants placement file, and generates a configuration
#		file used by the EHW system



### set the environment

$PID=$$;
$CAT="perl concat.pl";


### FILENAME DEFINITIONS - saves passing as parameters

$CONFIGFILE="EHW_config.txt";

$TMPSPLGENFILE="TMPSPLGEN_$PID";

# config files

$CONTENTIONFILE="contentionInfo.txt";

# 	.. these will need the data dir prepended
$FITTESTDETS="fittest_details.txt";	# contains fitness [and @ growth iter]
$LAYOUTBIT="layout.bit";
$TESTBIT="test.bit";		# the generated bitfile
$BESTBIT="best.bit";
$BESTCHROM="bestchrom.txt";
$POPBASENAME="pop";
$CYTOFILE="_cyto.txt";		# only used till converted to JBits format
$CYTOBITS="cytobits";

### CONSTANT DEFINITIONS

$NULLXCV50="null50GCLK1.bit";
$NULLXCV1000="null1000GCLK2.bit";

$LESLIMCODELEN=12; # 12 bits to encode a slim-down LE: 4in+2fn,2bus,4out
#$old_LETRIMCODELEN=20; # 20 bits to encode an old 'trim' LE: 6in+4fn,2bus,8out
$LETRIMCODELEN=47;  # require 47 bits to encode a LE: 17in+16fn,2bus,12out
$LESYNCHCODELEN=53;  # require 53 bits to encode a LE: 17in+16fn,8bus,12out
$LEASYNCHCODELEN=53;  # require 53 bits to encode a LE: 17in+16fn,8bus,12out


# genetic and morphogenetic constants..
# these are all arbitrary values currently

$TRANSRATE=4;			# 4 bases transcribed each step
$POLY2GENE=0.3;			# number of polymerase molecules = genes * 0.3
$POLYTHRESH=0.8;		# prob of poly binding is 1-0.8=0.2 => 20%

# this determines what the ceiling is on the number of genes to be generated
# using viable fitness, any more than this will not contribute to fitness
# it is also used in anti-gene-bloat, any chromosomes with more genes than
# this will be penalised (unless they are the fittest so far)
# 16 is for full Virtex CLB resources, for slimmed down models we set to less
$CEILINGGENES=16;

# the following act as chemical markers for the input and output cells
# and if using LE granularity, each LE in CLB gets a different marker
# or if slice granularity, each slice in CLB gets a different marker
$CYTOI="022";			# this is arbitrary - it's a stop codon
$CYTOI0="022";			# this is arbitrary - it's a stop codon
$CYTOI1="023";			# this is arbitrary - it's a stop codon
$CYTOO="203";			# this is arbitrary - it's a start codon
$CYTOO0="032";			# this is arbitrary - it's a stop codon
$CYTOO1="203";			# this is arbitrary - it's a start codon
$CYTOLEG0="022023";		# this is arbitrary - it's 2 stop codons
$CYTOLEF0="023022";		# this is arbitrary - it's 2 stop codons
$CYTOLEG1="203032";		# this is arbitrary - it's a start & stop codon
$CYTOLEF1="023032";		# this is arbitrary - it's 2 stop codons
$CYTOS0="203032";		# this is arbitrary - it's a start & stop codon
$CYTOS1="023022";		# this is arbitrary - it's 2 stop codons
$CYTOIOSPREADROWS=0;
$CYTOIOSPREADCOLS=0;
$CYTOIOSPREADROWDIFFUSION=1;
$CYTOIOSPREADCOLDIFFUSION=1;


# the following act as chemical gradients around the input and output layers
# and help differentiate the axis through the distfromsrc fields
$CYTOGRADIN="000";		# this is arbitrary - it's a TF res codon
$CYTOGRADOUT="001";		# this is arbitrary - it's a TF res codon
$CYTOGRADINROWDIFFUSION=1;	# move 1 CLB before update distance
$CYTOGRADINCOLDIFFUSION=1;
$CYTOGRADOUTROWDIFFUSION=1;
$CYTOGRADOUTCOLDIFFUSION=1;


# depreceated
# these are for splitting the chromosome into sections (use one for each
# input/output starting location) for the non-developmental approach:
# codon aligned, or a multiple of it, will prevent base shifts corrupting
# entire chromosome sections. only (combos of) non-coding codons should 
# be used, to prevent interference with valid resource settings, noting that
# non-coding (and non-virtex) codons are also used as STOP markers for
# resource-setting sequences

#$SPLITCHROMMARKER="023203";	# STOP+START codons
#$SPLITCHROMALIGN=6;		# default to double codon aligned


###### END constants

$VERBOSE=0;
$usetfs="true";
$USETFBINDS="true";
$ANTIGENEBLOAT="false";
$exitonsuccess="false"; 
$exitonstagnate=""; 
$exitoncctfail="true";
$save="false";
$ngStep="";
$ntIter="";
$mgseed=""; 
$VERTGRAN="2";

$setupparams=join(" ", @ARGV);
$setuptime=localtime();

while ($#ARGV>-1 && $ARGV[0]=~/^-[vsaenc]/) {
  if ($ARGV[0] eq "-v") { $VERBOSE=1; shift; }
  elsif ($ARGV[0] eq "-s") { $save="true"; shift; }
  elsif ($ARGV[0] eq "-agb") { $ANTIGENEBLOAT="true"; shift; }
  elsif ($ARGV[0] eq "-notfs") { $usetfs="false"; shift; }
  elsif ($ARGV[0] eq "-exitonsuccess") { $exitonsuccess="true"; shift; }
  elsif ($ARGV[0] eq "-exitonstagnate") { shift; $exitonstagnate=shift; }
  elsif ($ARGV[0] eq "-noexitoncctfail") { $exitoncctfail="false"; shift; }
  elsif ($ARGV[0] eq "-code") { shift; $GENECODE=shift; }
  else {
    die ("unknown switch '".$ARGV[0]."', must be one of -v -s -agb -notfs -noexitoncctfail -exiton[success|stagnate]\n");
  }
}

if ($GENECODE eq "") {
  if ($usetfs eq "false") {
    $GENECODE="VirtexGeneticCodeNoTFs.conf";	# no TF gene products
  } else {
    $GENECODE="VirtexGeneticCode.conf";
  }
}

$argc=$#ARGV+1;

if ($argc<8) { printUsage(); exit(1); }

$device=shift;
$nrows=shift;
$ncols=shift;
$inbuswidth=shift;
$outbuswidth=shift;
$testclass=shift;

# Adder2x2Fixed.pl (-mux|-6200|-lut) is a test for evolving 1-bit full adder
# function (fixed mux), input and function (fixed 6200), and routing
# (fixed lut) separately:
#
# testclass is actually a perl script, that will strip parts of the decoded
# chromosome to stop modifications to the fixed parts of the bitsream/adder
# design. it will then call the actual testclass (TestFnInToOut.class), which
# has its classname and arguments appended to the perl scripts arguments.
#
# KLUDGE ALERT:
# Adder2x2Fixed.pl in the testclass passed to setupEHW.pl from runMG.pl is
# also used to indicate preset bitstream, in which case Adder2x2Fixed.pl is
# removed from the testclass calling string. this just saves passing more
# parameters
#
# Note testclass should be in the form:
# 	Adder2x2Fixed.pl -(mux|6200|lut) [-presetonly] javatestclass params

if ($testclass=~/^Adder2x2Fixed.pl/) {
  if ($testclass=~/^Adder2x2Fixed.pl -mux/) {
    # layout with routing lines and inputs pre-configured to a given solution
    $SPECIALLAYOUT="mux";
  } elsif ($testclass=~/^Adder2x2Fixed.pl -6200/) {
    # use a layout with output routing lines pre-configured to a given solution
    $SPECIALLAYOUT="6200";
  } elsif ($testclass=~/^Adder2x2Fixed.pl -lut/) {
    # use a layout with LUTs pre-configured to one particular solution
    $SPECIALLAYOUT="lut";
  } else {
    die("Unknown Fixed configuration option for 2x2 Adder: $testclass");
  }
  if ($testclass=~/-presetonly/) {
    $SPECIALLAYOUT=$SPECIALLAYOUT . " -presetonly";
  }
  # strip this off the testclass for now, it will be prepended later
  $testclass=substr($testclass,length("Adder2x2Fixed.pl -" . $SPECIALLAYOUT));
} else {
  # the usual case
  $SPECIALLAYOUT="";
}

$TEST="java " . $testclass;

$popsize=shift;

if ($device =~/XCV50GA/) {	# using a standard GA
  $STANDARDGA=1;
  $GENECODE="";			# these aren't used with standard GA
  $USETFBINDS="";
  $ANTIGENEBLOAT="";
  if ($device eq "XCV50GA--") {
    # there are 4 LEs per CLB, each requires LESLIMCODELEN bits to encode it 
    $chromsize=$nrows*$ncols*4*$LESLIMCODELEN;
    $device="XCV50--";
  } elsif ($device eq "XCV50GA-") {
    # there are 4 LEs per CLB, each requires LETRIMCODELEN bits to encode it 
    $chromsize=$nrows*$ncols*4*$LETRIMCODELEN;
    $device="XCV50-";
  } elsif ($device eq "XCV50GAnFF") {
    # there are 4 LEs per CLB, each requires LEASYNCHCODELEN bits to encode it 
    # note that LUTBitFNs are used to encode the LUT
    $chromsize=$nrows*$ncols*4*$LEASYNCHCODELEN;
    $device="XCV50LEnFF";
  } elsif ($device eq "XCV50GAsync") {
    # there are 4 LEs per CLB, each requires LESYNCHCODELEN bits to encode it 
    # note that LUTBitFNs are used to encode the LUT
    $chromsize=$nrows*$ncols*4*$LESYNCHCODELEN;
    $device="XCV50LEsync";
  } else {
    print STDERR "direct encoding for '$device' unavailable, only XCV50GA-, XCV50GA--, XCV50GAnFF, XCV50GAFF standard GA encodings currently supported.\n";
    exit(1);
  }
  $minchrom=$maxchrom=$chromsize;
} else {
  $STANDARDGA=0;
  $chromsize=shift;
  if ($usetfs eq "false") {
    $USETFBINDS="false";			# no TF binding
  }
}

$datadir=shift;

$MG=0;				# MG=1 indicates morphogenetic approach 

$argc=$#ARGV+1;
if ($argc) {					# bioGA
  if ($argc<6) { printUsage(); exit(1); }
  $nvgen=shift;
  $reprate=shift;
  $xrate=shift;
  $mrate=shift;
  $irate=shift;
  $idrate=shift;

  $minchrom=$chromsize*0.5;
  $maxchrom=$chromsize*1.5;

  $argc=$#ARGV+1;
  # NDEHW depreceated - so must be Morphogenetic - so these params necessary
  # if ($argc>0) {
  if ($argc<2) { printUsage(); exit(1); }
  $MG=1;
  $ntIter=shift; $argc--;
  $ngSteps=shift; $argc--;
  if ($argc==2) { printUsage(); exit(1); }
  if ($argc>1) {
    $gtrate=shift; $argc--;
    $ptog=shift; $argc--;
    $pthresh=shift; $argc--;
  } else { # used defaults
    $gtrate=$TRANSRATE;
    $ptog=$POLY2GENE;
    $pthresh=$POLYTHRESH;
  }
  if ($argc>0) { $mgseed=shift; }
  #}
} elsif (!$STANDARDGA) {			# standard GA
  printUsage(); exit(1);
}



# these files will be located in the data directory
$LAYOUTBIT="$datadir/$LAYOUTBIT";
$TESTBIT="$datadir/$TESTBIT";
$BESTBIT="$datadir/$BESTBIT";
$BESTCHROM="$datadir/$BESTCHROM";
$FITTESTDETS="$datadir/$FITTESTDETS";
$POPBASENAME="$datadir/$POPBASENAME";
$POPPARAM="$POPBASENAME.parm.txt";
$CYTOFILE="$datadir/$CYTOFILE";
$CYTOBITS="$datadir/$CYTOBITS";
$cytofile=($MG) ? "$CYTOBITS.txt" : "";  

# create data directory
if (!-e $datadir) {
if ($VERBOSE) { print STDERR "creating directory $datadir .. "; }
  mkdir($datadir,0700) || die("Unable to create directory $datadir");
if ($VERBOSE) { print STDERR "done.\n"; }
} elsif (!-d $datadir) {
  die("$datadir already exists but is not a directory");
} else {
  # data dir already exists, so cleanup stuff from previous runs
if ($VERBOSE) { print STDERR "cleaning up files in $datadir directory.. "; }
  unlink ($FITTESTDETS);
  unlink ($LAYOUTBIT);
  unlink ($BESTBIT);
  unlink ($BESTCHROM);
  eval "unlink <$TESTBIT*>";
  eval "unlink <$POPBASENAME*>";
  unlink ("$CYTOBITS.txt");
if ($VERBOSE) { print STDERR "done.\n"; }
}

$DIRECTCHROMDECODE="";
if ($device =~/XCV50/) {
  if ($device=~/LE/ || $device=~/-/) {
    $VERTGRAN=1;			# logic element granularity
    #depreceated
    #$SPLITCHROMALIGN=3;			# codon aligned
  }

  # on non-slim models we use the simulator, and want to avoid crashes
  if (!($device=~/slim/ || $device=~/--/)) { 
    # increase stack and heap memory, maybe this will stop VirtexDS from crash
    $TEST="java -mx256m -ms256m $testclass";
  }

  if ($device eq "XCV50") {		# run on the VirtexDS simulator, GCLK=1
    $CONF2BITS="convToJBits.conf"; 	# slice gran resources 
    $CEILINGGENES=14;
  } elsif ($device eq "XCV50sync") {	# run on the VirtexDS simulator, but
    $CONF2BITS="VirtexDSconvToJBits.conf";  # with only synchronous resources 
    $TEST.=" -synchonly";
    $CEILINGGENES=14;
  } elsif ($device eq "XCV50nFF") {	# run on the VirtexDS simulator, but
    $CONF2BITS="NFFconvToJBits.conf";   # without registered outputs
    $TEST.=" -maxdelay 0";
    $CEILINGGENES=14;
  } elsif ($device eq "XCV50LE") {	# LE gran lacks SliceRAM & Ctrl inputs
    $CONF2BITS="legranconvToJBits.conf";
    # note that this provides direct OUT line connections, but if don't want
    # them used with TestInToOutRecurse the -ndo flag needs to be specified
    $CEILINGGENES=12;
  } elsif ($device eq "XCV50LEsync") {	# and with only registered slice outs
    $CONF2BITS="legranDSconvToJBits.conf";
    if ($STANDARDGA) { $DIRECTCHROMDECODE="directdecodelegransynch.pl"; }
    # note that this provides direct OUT line connections, but if don't want
    # them used with TestInToOutRecurse the -ndo flag needs to be specified
    $TEST.=" -synchonly";
    $CEILINGGENES=12;
  } elsif ($device eq "XCV50LEnFF") {	# with only unregistered slice outs
    $CONF2BITS="legranNFFconvToJBits.conf";
    if ($STANDARDGA) { $DIRECTCHROMDECODE="directdecodelegranasynch.pl"; }
    $TEST.=" -maxdelay 0";
    $CEILINGGENES=12;
  } elsif ($device eq "XCV50-") {	     # trim LE: LUTBitFNs & sync outs
    $CONF2BITS="legrantrimconvToJBits.conf";
    if ($STANDARDGA) { $DIRECTCHROMDECODE="directdecodelegrantrim.pl"; }
    $CEILINGGENES=10;
  } elsif ($device eq "XCV50--") {	     # 'slim' model: 1 LUT input only
    $CONF2BITS="legranslimconvToJBits.conf"; # at LE gran
    if ($STANDARDGA) { $DIRECTCHROMDECODE="directdecodelegranslim.pl"; }
    $CEILINGGENES=8;
  } elsif ($device eq "XCV50slim") {	     # 'slim' model at slice gran
    $CONF2BITS="slimconvToJBits.conf";
    $CEILINGGENES=10;
  } else {
    print STDERR "uknown suffix option to XCV50: '$device'\n";
    exit(1);
  }

  # KLUDGE Alert!!!
  # for slim routing problem only (uses bitstream only, not VirtexDS or board)
  # if not enough room for EHW matrix on XCV50 (16x24 minus routing CLBs), use
  # XCV1000 which gives 64x96 CLBs (minus routing CLBs)
  if ($device eq "XCV50--" && ($nrows>14 || $ncols>22)) {
    $device="XCV1000";
    $UCF="RaptorXCV1000.ucf";
    $NULLBIT=$NULLXCV1000;
    $CONF2BITS="legranslimconvToJBits.conf"; # at LE gran
  } else {
    $device="XCV50";
    $UCF="XCV50bg256.ucf";
    $NULLBIT=$NULLXCV50;
  }
} elsif ($device eq "XCV1000") {
  $UCF="RaptorXCV1000.ucf";
  $NULLBIT=$NULLXCV1000;
  $CONF2BITS="convToJBits.conf";
} else {
  die("Currently only support XCV50 or XCV1000...");
}


# now that all the java interpreter options have been added
# we can prepend the perl script for the Adder2x2Fixed option
# which will call the java script after 'fix'ing the decoded chromosome
# but first check if its just a preset layout only

if ($SPECIALLAYOUT=~/ -presetonly/) {
  $SPECIALLAYOUT=~s/ -presetonly//;
} elsif ($SPECIALLAYOUT eq "mux") {
  $TEST="perl Adder2x2Fixed.pl -mux $TEST";
} elsif ($SPECIALLAYOUT eq "6200") {
  $TEST="perl Adder2x2Fixed.pl -6200 $TEST";
} elsif ($SPECIALLAYOUT eq "lut") {
  $TEST="perl Adder2x2Fixed.pl -lut $TEST";
}


# in the layout we'll also preconfigure the slice Clk's to connect to GCLK1
# when we use XCV50, as we have limited slice outputs to only be synchronous
# due to problems with asynchronous circuits in the simulator
if ($device eq "XCV50") {
  $layoutcmd="java EHWLayout -c 1 $device $nrows $ncols $UCF DUT_in $inbuswidth DUT_out $outbuswidth $NULLBIT $LAYOUTBIT";
} else {
  $layoutcmd="java EHWLayout $device $nrows $ncols $UCF DUT_in $inbuswidth DUT_out $outbuswidth $NULLBIT $LAYOUTBIT";
}

if ($VERBOSE) { print STDERR "performing EHW layout and routing..\n"; }
$layoutstr=`$layoutcmd`;
if ($VERBOSE) { print STDERR $layoutstr . "done.\n\n"; }

(@lines)=split(/\n\s*/,$layoutstr);	# and get rid of leading spaces

# example output, for 'java EHWLayout XCV50 8 8 XCV50bg256.ucf DUT_in 4 DUT_out 4 datafiles/null50GCLK1.bit test.bit', would be:
# device XCV50 has CLB area of 16x24
# allocated evolvable region of device is 4,8 to 11,15
# input CLB locations are on LEFT side:
#   (7,8,S0G-Y) (7,8,S1F-X) (8,8,S0G-Y) (8,8,S1F-X)
# output CLB locations are on RIGHT side:
#   (4,15,S0G-Y) (6,15,S1F-X) (9,15,S0G-Y) (11,15,S1F-X)
# input IOB locations:
#   IOB(LEFT,11,I[1]) IOB(LEFT,11,I[2]) IOB(LEFT,10,I[2]) IOB(LEFT,10,I[3])
# output IOB locations:
#   IOB(RIGHT,6,O[1]) IOB(RIGHT,5,O[1]) IOB(RIGHT,4,O[1]) IOB(RIGHT,4,O[2])
#
# connecting IOBs to IO CLBs input/output singles..
# saving connected bitstream to file test.bit..


# if there was some kind of error
if ($#lines<0 || $lines[$#lines]=~/Exception/ ||
		!($lines[0]=~/^device $device has CLB area/)) {
  die($layoutstr);
}

# 2nd line is for eg: allocated evolvable region of device is 4,8 to 12,16
(@words)=split(/\s+/,$lines[1]);
$minclb=$words[6];
$maxclb=$words[8];

# 3rd line gives us input side, from this we can determine if growth is
# along horizontal or vertical axis
$GROWAXIS=($lines[2]=~/LEFT/ || $lines[2]=~/RIGHT/) ? "horiz" : "vert";

# in/out CLBs are on 4th & 6th lines
(@inputs)=split(/\s+/,$lines[3]);
(@outputs)=split(/\s+/,$lines[5]);

$inLEstr=join(";",@inputs);
$outLEstr=join(";",@outputs);
$inLEstr=~s/[()]//g;		# strip parenthesis
$outLEstr=~s/[()]//g;

# depreceated NDEHW..
#$STARTCLBSLICE=$inputs[0];
# convert from eg (8,8,S0G-Y) to 8,8,0
#$STARTCLBSLICE=~s/[()\-SGYFX]//g;

#in/out IOBs are on 8th and 10th lines
(@inIOBs)=split(/\s+/,$lines[7]);
(@outIOBs)=split(/\s+/,$lines[9]);

# put into form SIDE,index,siteidx
for ($i=0;$i<$#inIOBs+1;$i++) { $inIOBs[$i]=convertIOBloc($inIOBs[$i]); }
for ($i=0;$i<$#outIOBs+1;$i++) { $outIOBs[$i]=convertIOBloc($outIOBs[$i]); }

$inIOBstr=join(";",@inIOBs);
$outIOBstr=join(";",@outIOBs);


# this is for evolving a 1-bit full adder, with either function or routing
# preconfigured (and fixed). expects a layout-routed, but otherwise null
# bitstream (as doesn't configure resources to be off)
if ($SPECIALLAYOUT ne "") {
  # create temp file containing instructions for generating special layout
  open(SPLFILE,">$TMPSPLGENFILE") || die "Unable to create temp file ($TMPSPLGENFILE)!\n";

  # hand crafted circuit for 1-bit full adder on a 2x2 CLB matrix
  # this circuit is based on:
  #   sum = x XOR y XOR cin
  #   cout= x.y + x.cin + y.cin = x.y + ( (x+y).cin )
  #
  # with a layout of:
  # (Note X = unused LE)
  #
  #          0      1     0      1
  #        --------------------------
  #       |            ,-----,       |
  #       |  X   ,=AND'   X   '=OR---|-C X
  #       |     /  \           /     |
  #       |,___/    \         /      |
  #   8 c-|-OR,    X \    X  |   X   |   Y
  #       |   |      |       |       |
  #       |   |      |       /       |
  #     x-|,--/---=OR'  ,-OR'    X   |   X
  #       || /    /     /            |
  #       |\/    y,    /             |
  #   7 y-|=XOR, x=AND' ,-OR,    X ,-|-S Y
  #       |    |_______/     \____/  |
  #        --------------------------
  #             11           12

  # use layout with (output) routing lines preconfigured to a given solution
  # for fixed mux and fixed6200
  if ($SPECIALLAYOUT eq "mux" || $SPECIALLAYOUT eq "6200") {
    if ($VERBOSE) { print STDERR "generating adder2x2 routing..\n"; }
    print SPLFILE <<EOSPL1
set: 7,11: jbits=OUT3.OUT3,OUT3.S0_Y
set: 7,11: jbits=OutMuxToSingle.OUT3_TO_SINGLE_EAST11,OutMuxToSingle.ON

set: 7,11: jbits=OUT1.OUT1,OUT1.S1_Y
set: 7,11: jbits=OutMuxToSingle.OUT1_TO_SINGLE_EAST3,OutMuxToSingle.ON

set: 7,11: jbits=OUT0.OUT0,OUT0.S1_X
set: 7,11: jbits=OutMuxToSingle.OUT0_TO_SINGLE_NORTH1,OutMuxToSingle.ON

set: 7,12: jbits=OUT1.OUT1,OUT1.S0_Y
set: 7,12: jbits=OutMuxToSingle.OUT1_TO_SINGLE_EAST3,OutMuxToSingle.ON

set: 7,12: jbits=OUT0.OUT0,OUT0.S0_X
set: 7,12: jbits=OutMuxToSingle.OUT0_TO_SINGLE_NORTH1,OutMuxToSingle.ON

set: 8,11: jbits=OUT5.OUT5,OUT5.S0_Y
set: 8,11: jbits=OutMuxToSingle.OUT5_TO_SINGLE_SOUTH12,OutMuxToSingle.ON

set: 8,11: jbits=OUT6.OUT6,OUT6.S1_X
set: 8,11: jbits=OutMuxToSingle.OUT6_TO_SINGLE_EAST18,OutMuxToSingle.ON

set: 8,12: jbits=OUT3.OUT3,OUT3.S1_X
set: 8,12: jbits=OutMuxToSingle.OUT3_TO_SINGLE_EAST11,OutMuxToSingle.ON
EOSPL1
; # this is needed!
    # if fixed mux use layout with input muxes fixed too
    if ($SPECIALLAYOUT eq "mux") {
      print SPLFILE <<EOSPL2
set: 7,11: jbits=S0G4.S0G4,S0G4.SINGLE_WEST3
set: 7,11: jbits=S0G3.S0G3,S0G3.SINGLE_WEST15
set: 7,11: jbits=S0G2.S0G2,S0G2.SINGLE_NORTH12

set: 7,11: jbits=S1G4.S1G4,S1G4.SINGLE_WEST5
set: 7,11: jbits=S1G3.S1G3,S1G3.SINGLE_WEST20

set: 7,11: jbits=S1F4.S1F4,S1F4.SINGLE_WEST5
set: 7,11: jbits=S1F3.S1F3,S1F3.SINGLE_WEST20

set: 7,12: jbits=S0G4.S0G4,S0G4.SINGLE_WEST11

set: 7,12: jbits=S0F4.S0F4,S0F4.SINGLE_WEST3

set: 8,11: jbits=S0G4.S0G4,S0G4.SINGLE_WEST3

set: 8,11: jbits=S1F4.S1F4,S1F4.SINGLE_WEST5
set: 8,11: jbits=S1F3.S1F3,S1F3.SINGLE_SOUTH1

set: 8,12: jbits=S1F4.S1F4,S1F4.SINGLE_WEST18
set: 8,12: jbits=S1F3.S1F3,S1F3.SINGLE_SOUTH1
EOSPL2
    }
  }

  # use a layout with LUTs pre-configured to one particular solution
  if ($SPECIALLAYOUT eq "lut") {
    if ($VERBOSE) { print STDERR "generating adder2x2 LUT functions..\n"; }
    print SPLFILE <<EOSPL3
set: 7,11: LUTIncrFN=LUT.SLICE0_G,SET,XOR,111-
set: 7,11: LUTIncrFN=LUT.SLICE1_G,SET,AND,11--
set: 7,11: LUTIncrFN=LUT.SLICE1_F,SET,OR,11--
set: 7,12: LUTIncrFN=LUT.SLICE0_G,SET,OR,1---
set: 7,12: LUTIncrFN=LUT.SLICE0_F,SET,OR,1---
set: 8,11: LUTIncrFN=LUT.SLICE0_G,SET,OR,1---
set: 8,11: LUTIncrFN=LUT.SLICE1_F,SET,AND,11--
set: 8,12: LUTIncrFN=LUT.SLICE1_F,SET,OR,11--
EOSPL3
  }

  close(SPLFILE);

  # now generate configured bitstream using JBitsResources load
  # write bitstream back to the layout bitstream
  # note that $device should be an "XCV50"

  $splconfcmd="java JBitsResources $device $LAYOUTBIT $CONTENTIONFILE load $TMPSPLGENFILE $LAYOUTBIT";
  if ($VERBOSE) { print STDERR "modifying $LAYOUTBIT with partial adder2x2 config..\n"; }
  if (system($splconfcmd)) {
    unlink($TMPSPLGENFILE);
    die("Unable to create adder layout bitstream - failed executing: $splconfcmd.. ");
  }

  # clean up temp layout config file
  unlink($TMPSPLGENFILE);
}


if ($MG) {			# morphogenesis approach

if ($VERBOSE) { print STDERR "setting up EHW system for morphogenetic runs..\n"; }

  if ($usetfs eq "false") {
    # we don't use cytoplasmic determinants, but still need to generate a
    # dummy file to pass to the morphogenesis system
    open(CYTOFILE,">$CYTOBITS.txt") || die "Unable to create dummy cytoplasmic determinant placement file ($CYTOBITS.txt)\n";
    print CYTOFILE "\n";
    close(CYTOFILE);
  } else {

    # generate a new cytoplasmic determinant placement file ..
  if ($VERBOSE) { print STDERR "generating cytoplasmic determinant placement file $cytofile..\n"; }

    # delete existing b4 create new one
    if (-e $CYTOFILE) { unlink($CYTOFILE); }

    # have to convert from CLB row,col,Slice-LE to cell row & col
    # but we use different CYTO sequences depending on which LE (if slice gran)
    @incells=CLBtoCell($minclb,@inputs);
    @outcells=CLBtoCell($minclb,@outputs);
    #$inCellstr=$incells[2];
    #$outCellstr=$outcells[2];

#printA("inputs",@inputs);
#printA("incells",@incells);
#printA("outputs",@outputs);
#printA("outcells",@outcells);

    $maxrow=($nrows*2/$VERTGRAN)-1;# 2 LE's / CLB row, LE gran -> 1 LE per Row
    $maxcol=($ncols*2)-1;	# 1 slice per column (ie clb takes 2 cols)

    if ($VERTGRAN==2) { # slice granularity so don't use LE-specific cytoplasm
      if ($incells[2] ne "") {
        $gencytocmd="perl cytogen.pl $CYTOI \"$maxrow,$maxcol\" \"".$incells[2]."\" $CYTOIOSPREADROWS $CYTOIOSPREADCOLS $CYTOIOSPREADROWDIFFUSION $CYTOIOSPREADCOLDIFFUSION >> $CYTOFILE";
        if ($VERBOSE) { print STDERR "generating in cell cytoplasms with: $gencytocmd\n"; }
        system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      }

      if ($outcells[2] ne "") {
        $gencytocmd="perl cytogen.pl $CYTOO \"$maxrow,$maxcol\" \"".$outcells[2]."\" $CYTOIOSPREADROWS $CYTOIOSPREADCOLS $CYTOIOSPREADROWDIFFUSION $CYTOIOSPREADCOLDIFFUSION >> $CYTOFILE";
        if ($VERBOSE) { print STDERR "generating out cell cytoplasms with: $gencytocmd\n"; }
        system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      }

      # also need to place different cytoplasms in the different slices
      # as they have different line orderings at their inputs
      for ($ri=0; $ri<$nrows; $ri++) {
        for ($ci=0; $ci<$ncols*2; $ci++) {
  	  if ($ci%2==0) { push (@s0list,"$ri,$ci"); }
	  if ($ci%2==1) { push (@s1list,"$ri,$ci"); }
        }
      }

      $gencytocmd="perl cytogen.pl $CYTOS0 \"$maxrow,$maxcol\" \"" . join(";",@s0list) . "\" 0 0 1 1 >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "generating slice 0 cell differentiating cytoplasms with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");

      $gencytocmd="perl cytogen.pl $CYTOS1 \"$maxrow,$maxcol\" \"" . join(";",@s1list) . "\" 0 0 1 1 >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "generating slice 1 cell differentiating cytoplasms with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");

    } else { # LE granularity so use LE-specific cytoplasms at IO cells
      if ($incells[0] ne "") {
        $gencytocmd="perl cytogen.pl $CYTOI0 \"$maxrow,$maxcol\" \"".$incells[0]."\" $CYTOIOSPREADROWS $CYTOIOSPREADCOLS $CYTOIOSPREADROWDIFFUSION $CYTOIOSPREADCOLDIFFUSION >> $CYTOFILE";
        if ($VERBOSE) { print STDERR "generating in cell cytoplasms with: $gencytocmd\n"; }
        system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      }

      if ($incells[1] ne "") {
        $gencytocmd="perl cytogen.pl $CYTOI1 \"$maxrow,$maxcol\" \"".$incells[1]."\" $CYTOIOSPREADROWS $CYTOIOSPREADCOLS $CYTOIOSPREADROWDIFFUSION $CYTOIOSPREADCOLDIFFUSION >> $CYTOFILE";
        if ($VERBOSE) { print STDERR "generating in cell cytoplasms with: $gencytocmd\n"; }
        system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      }

      if ($outcells[0] ne "") {
        $gencytocmd="perl cytogen.pl $CYTOO0 \"$maxrow,$maxcol\" \"".$outcells[0]."\" $CYTOIOSPREADROWS $CYTOIOSPREADCOLS $CYTOIOSPREADROWDIFFUSION $CYTOIOSPREADCOLDIFFUSION >> $CYTOFILE";
        if ($VERBOSE) { print STDERR "generating out cell cytoplasms with: $gencytocmd\n"; }
        system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      }

      if ($outcells[1] ne "") {
        $gencytocmd="perl cytogen.pl $CYTOO1 \"$maxrow,$maxcol\" \"".$outcells[1]."\" $CYTOIOSPREADROWS $CYTOIOSPREADCOLS $CYTOIOSPREADROWDIFFUSION $CYTOIOSPREADCOLDIFFUSION >> $CYTOFILE";
        if ($VERBOSE) { print STDERR "generating out cell cytoplasms with: $gencytocmd\n"; }
        system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      }

      # also need to place different cytoplasms in the different LEs
      # as they have different resources available
      for ($ri=0; $ri<$nrows*2; $ri++) {
        for ($ci=0; $ci<$ncols*2; $ci++) {
	  if ($ri%2==0 && $ci%2==0) { push (@g0list,"$ri,$ci"); }
	  if ($ri%2==1 && $ci%2==0) { push (@f0list,"$ri,$ci"); }
	  if ($ri%2==0 && $ci%2==1) { push (@g1list,"$ri,$ci"); }
	  if ($ri%2==1 && $ci%2==1) { push (@f1list,"$ri,$ci"); }
        }
      }

      $gencytocmd="perl cytogen.pl $CYTOLEG0 \"$maxrow,$maxcol\" \"" . join(";",@g0list) . "\" 0 0 1 1 >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "generating S0G-Y LE cell differentiating cytoplasms with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");

      $gencytocmd="perl cytogen.pl $CYTOLEF0 \"$maxrow,$maxcol\" \"" . join(";",@f0list) . "\" 0 0 1 1 >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "generating S0F-X LE cell differentiating cytoplasms with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");

      $gencytocmd="perl cytogen.pl $CYTOLEG1 \"$maxrow,$maxcol\" \"" . join(";",@g1list) . "\" 0 0 1 1 >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "generating S1G-Y LE cell differentiating cytoplasms with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");

      $gencytocmd="perl cytogen.pl $CYTOLEF1 \"$maxrow,$maxcol\" \"" . join(";",@f1list) . "\" 0 0 1 1 >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "generating S1F-X LE cell differentiating cytoplasms with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");

    }

    # now place the cyto chemical gradients. pass granularity field to
    # cytogen.pl so that distance field and spread is done in terms of CLBs
    # not cells

    $maxrow=$nrows-1;		# CLB rows
    $maxcol=$ncols-1;		# CLB cols

    # note that we assume inputs and outputs are on opposite ends of one axis
    # we create a spread that traverse entire axis between inputs and outputs
    # but only want to cover all the orthogonal axis on the output end
    if ($GROWAXIS eq "horiz") {
      # create less vert spread from inputs till 1/2 way across horiz axis
      $CYTOGRADINSPREADROWS=int($nrows/4);
      $CYTOGRADINSPREADCOLS=int($ncols/2);
      # create more vert spread towards outputs from 1/2 way across horiz axis 
      $CYTOGRADOUTSPREADROWS=int(($nrows/$outbuswidth)+0.5);
      $CYTOGRADOUTSPREADCOLS=int($ncols/2);
    } else {
      # create less horiz spread from inputs till 1/2 way across vert axis
      $CYTOGRADINSPREADROWS=int($nrows/2);
      $CYTOGRADINSPREADCOLS=int($ncols/4);
      # create more horiz spread towards outputs from 1/2 way across vert axis 
      $CYTOGRADOUTSPREADROWS=int($nrows/2);
      $CYTOGRADOUTSPREADCOLS=int(($ncols/$outbuswidth)+0.5);
    }


    $CYTOGRAN=($VERTGRAN==1) ? "2,2" : "1,2"; # LE or slice gran
    $CYTOGRAN="\"$CYTOGRAN\"";

    # if there are any in cell locations then generate cyto chemical gradients
    # starting at those locations
    if ($incells[2] ne "") {
      # convert cell coords back to CLB coords for passing to cytogen
      @clblocs=();
      @locs=split(/;/,$incells[2]);
      foreach $loc (@locs) {
        ($r,$c)=split(/,/,$loc);
        if ($VERTGRAN==1) { $r=int($r/2); } # if LE gran convert back to CLB
        $c=int($c/2);			  # convert from slice gran to CLB
        push(@clblocs,join(",",$r,$c));
      }
      $clbinloclist=join(";",@clblocs);

      $gencytocmd="perl cytogen.pl -gran $CYTOGRAN $CYTOGRADIN \"$maxrow,$maxcol\" \"".$clbinloclist."\" $CYTOGRADINSPREADROWS $CYTOGRADINSPREADCOLS $CYTOGRADINROWDIFFUSION $CYTOGRADINCOLDIFFUSION >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "\ngenerating input layers cytoplasmsic gradients with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      if ($VERBOSE) {
        $gencytocmd="perl cytogen.pl -v -gran $CYTOGRAN $CYTOGRADIN \"$maxrow,$maxcol\" \"".$clbinloclist."\" $CYTOGRADINSPREADROWS $CYTOGRADINSPREADCOLS $CYTOGRADINROWDIFFUSION $CYTOGRADINCOLDIFFUSION";
        system($gencytocmd) && die("Unable to visualise cytoplasmic determinant placement");
        print STDERR "\n";
      }
    }

    # if there are any out cell locations then generate cyto chemical gradients
    # starting at those locations
    if ($outcells[2] ne "") {
      # convert cell coords back to CLB coords for passing to cytogen
      @clblocs=();
      @locs=split(/;/,$outcells[2]);
      foreach $loc (@locs) {
        ($r,$c)=split(/,/,$loc);
        if ($VERTGRAN==1) { $r=int($r/2); } # if LE gran convert back to CLB
        $c=int($c/2);			  # convert from slice gran to CLB
        push(@clblocs,join(",",$r,$c));
      }
      $clboutloclist=join(";",@clblocs);

      $gencytocmd="perl cytogen.pl -gran $CYTOGRAN $CYTOGRADOUT \"$maxrow,$maxcol\" \"".$clboutloclist."\" $CYTOGRADOUTSPREADROWS $CYTOGRADOUTSPREADCOLS $CYTOGRADOUTROWDIFFUSION $CYTOGRADOUTCOLDIFFUSION >> $CYTOFILE";
      if ($VERBOSE) { print STDERR "\ngenerating output layers cytoplasmsic gradients with: $gencytocmd\n"; }
      system($gencytocmd) && die("Unable to create cytoplasmic determinant placement file");
      if ($VERBOSE) {
        $gencytocmd="perl cytogen.pl -v -gran $CYTOGRAN $CYTOGRADOUT \"$maxrow,$maxcol\" \"".$clboutloclist."\" $CYTOGRADOUTSPREADROWS $CYTOGRADOUTSPREADCOLS $CYTOGRADOUTROWDIFFUSION $CYTOGRADOUTCOLDIFFUSION";
        system($gencytocmd) && die("Unable to visualise cytoplasmic determinant placement");
        print STDERR "\n\n";
      }
    }

    # now convert file to JBits compatible
    $convcytocmd="$CAT $CYTOFILE | perl resattrset2bitsval.pl -c $CONF2BITS $CYTOBITS txt";
    system($convcytocmd) && die("Unable to convert cytoplasmic determinant placement file to JBits format");

    # don't need this any more
    unlink($CYTOFILE);

    if ($VERBOSE) { print STDERR ".. generating cytoplasmic determinant placement file complete.\n\n"; }

  }

  # create the initial population, and evolve until population hopefully
  # contains lots of chromosomes that are full of genes
  if ($VERBOSE) {
    # calculate how often to display stats on evolving initial 'viable' popn
    if ($nvgen<20) { $vstats=int($nvgen/2); }
    elsif ($nvgen<100) { $vstats=10; }
    else { $vstats=int($nvgen/10); }

    print STDERR "generating popn of $popsize chroms of sizes $minchrom - $maxchrom ..\nevolving for $nvgen gens with upto $CEILINGGENES genes contributing to fitness..\n";
    $gencmd="perl genpopn.pl " . (($VERBOSE) ? "-v " : "") . "-sg $vstats -g $popsize $minchrom $maxchrom $POPBASENAME.txt \"viablefitness.pl -f $CEILINGGENES\" $nvgen $reprate $xrate $mrate $irate $idrate";
  } else {
    $gencmd="perl genpopn.pl -g $popsize $minchrom $maxchrom $POPBASENAME.txt \"viablefitness.pl -f $CEILINGGENES\" $nvgen $reprate $xrate $mrate $irate $idrate";
  }
  $gentime=localtime();
  system($gencmd) && die("Unable to create initial population with: $gencmd");

  if ($VERBOSE) { print STDERR "done.\n"; }

} elsif ($STANDARDGA) {		# using standard GA with direct encoding
  if ($VERBOSE) {
    print STDERR "setting up EHW system for standard GA with direct encoding runs..\n"; 
    print STDERR "generating popn of $popsize binary chroms of size $chromsize ..\n";
  }
  $gencmd="perl genpopn.pl " . (($VERBOSE) ? "-v -s 0 " : "") . "-ga -g $popsize $chromsize $POPBASENAME.txt \"dummyfitness.pl -f\" 0";

  $gentime=localtime();
  system($gencmd) && die("Unable to create initial population with: $gencmd");
  if ($VERBOSE) { print STDERR "done.\n"; }
}
# depreceated
# else {		# non-developmental bioGA approach
#if ($VERBOSE) { print STDERR "setting up EHW system for non-developmental runs..\n"; }
##if ($VERBOSE) { print STDERR "generating initial popn of $popsize chroms of sizes $minchrom - $maxchrom ... "; }
#  #$gencmd="perl genpopn.pl -g $popsize $minchrom $maxchrom $POPBASENAME.txt \"dummyFitness.pl -f\" 0";
#
#  # maximum useful sections is the number of input and output cells which
#  # is the sum of the width of the input & output busses
#  $maxsections=$inbuswidth+$outbuswidth;
#  # maximum useful length of a section is (we want to keep it small):
#  # 	1 move codon + in,lut,out,out2single resource setting lengths
#  # per LE. which is # LE's in evolvable region / number of sections
#  $numles=int(($nrows*$ncols*4)/$maxsections);# 4 LEs per CLB in section
#  $maxsulen=$numles*(1+3+5+4+2)*3;		# x3 as lengths in codons
#
#  # create the initial population, and evolve until population hopefully
#  # contains enough useful sections to construct starting from the IO cells
#  if ($VERBOSE) {
#    # calculate how often to display stats on evolving initial 'viable' popn
#    if ($nvgen<20) { $vstats=int($nvgen/2); }
#    elsif ($nvgen<100) { $vstats=10; }
#    else { $vstats=int($nvgen/10); }
#
#    print STDERR "generating popn of $popsize chroms of sizes $minchrom - $maxchrom ..\nevolving for $nvgen gens with upto $maxsections chromosome sections of max len $maxsulen\ncontributing to fitness..\n";
#    $gencmd="perl genpopn.pl -v  -s $vstats -g $popsize $minchrom $maxchrom $POPBASENAME.txt \"viableNDfitness.pl -f -a $SPLITCHROMALIGN $maxsections $maxsulen\" $nvgen $reprate $xrate $mrate $irate $idrate";
#  } else {
#    $gencmd="perl genpopn.pl -g $popsize $minchrom $maxchrom $POPBASENAME.txt \"viableNDfitness.pl -f -a $SPLITCHROMALIGN $maxsections $maxsulen\" $nvgen $reprate $xrate $mrate $irate $idrate";
#  }
#
#  $gentime=localtime();
#  system($gencmd) && die("Unable to create initial population with: $gencmd");
#  if ($VERBOSE) { print STDERR "done.\n"; }
#
#}



if ($VERBOSE) { print STDERR "creating population parameter file $POPPARAM .. "; }

open(PFILE,">$POPPARAM") || die "Unable to create population parameter file $POPPARAM";
print PFILE "# population was generated at: $gentime\n";
print PFILE "# using: $gencmd\n";
print PFILE "# run from: setupEHW.pl $setupparams\n";
close PFILE;

if ($VERBOSE) { print STDERR "done.\n"; }

if ($VERBOSE) { print STDERR "creating configuration file $CONFIGFILE.. "; }

# create the config file for running EHW system
# delete existing b4 create new one
if (-e $CONFIGFILE) { unlink($CONFIGFILE); }

open(CFILE,">$CONFIGFILE") || die "Unable to create EHW configuration file";
print CFILE "
# setup was generated at: $setuptime
# by: setupEHW.pl $setupparams

DATADIR=$datadir

# population files use this as the base name, with suffix added
POPBASENAME=$POPBASENAME

# this should be empty if using standard GA with direct encoding
GENECODE=$GENECODE

# this should be empty if using a genetic code with bioGA.
# it is also used to indicate that using a standard fixed-length binary chrom
DIRECTCHROMDECODE=$DIRECTCHROMDECODE

DEVNAME=$device
CONTENTIONFILE=$CONTENTIONFILE
TEST=$TEST
VERTGRAN=$VERTGRAN
CONF2BITS=$CONF2BITS

FITTESTDETS=$FITTESTDETS
LAYOUTBIT=$LAYOUTBIT
TESTBIT=$TESTBIT
BESTBIT=$BESTBIT
BESTCHROM=$BESTCHROM

SAVEBEST=$save
EXITONSUCCESS=$exitonsuccess
EXITONSTAGNATEGENS=$exitonstagnate
EXITONCCTFAIL=$exitoncctfail

MINCLB=$minclb
MAXCLB=$maxclb
INIOBS=$inIOBstr
OUTIOBS=$outIOBstr
INCLBLES=$inLEstr
OUTCLBLES=$outLEstr

# this indicates how many growth steps are performed per test iteration
# if it is 0 or empty, then it indicates that non-developmental approach used
NUMGROWTHSTEPS=$ngSteps

# these are only needed for running morphogenesis (MGSEED is optional)
NUMTESTITER=$ntIter
TRANSCRIBERATE=$gtrate
POLYTOGENE=$ptog
POLYTHRESHOLD=$pthresh

USETFBINDS=$USETFBINDS
CYTOFILE=$cytofile
ANTIGENEBLOAT=$ANTIGENEBLOAT
GENECEILING=$CEILINGGENES

MGSEED=$mgseed

";
# depreceated - removed from end of output (after MGSEED line)
# these are only needed for running non-developmental EHW
#SPLITCHROMMARKER=$SPLITCHROMMARKER
#SPLITCHROMALIGN=$SPLITCHROMALIGN


close(CFILE);

if ($VERBOSE) { print STDERR "done.\nEHW setup completed successfully\n"; }

############# end main()


# convert from CLB row,col,Slice-LE to cell row & col,
# by mapping min CLB to cell 0,0, and noting that there are 2 cols per CLB
# and 1 or 2 rows (depending on if using slice or logic element granularity)
# returns a 3 element array: 1st elem is LE G-Y, 2nd is LE F-X,
# 3rd is list of cells in which either LE was present
sub CLBtoCell {
  my ($minclb,@clbs) = @_;
  my ($minr,$minc)=split(/,/,$minclb);
  my ($clb,$r,$c,$sle);
  my (@cells0,@cells1,@cells2);
  my @cells=();

  foreach $clb (@clbs) {
    $clb=~s/[()]//g;			# get rid of surrounding parenthesis
    ($r,$c,$sle)=split(/,/,$clb);
    $s=substr($sle,1,1);		# slice/LE is in form: S(0|1)(G-Y|F-X)
    if ($sle=~/G-Y/) {
      push(@cells0,join(",",($r-$minr)*2/$VERTGRAN,(($c-$minc)*2)+$s));
    } else {
      push(@cells1,join(",",($r-$minr)*2/$VERTGRAN,(($c-$minc)*2)+$s));
    }
    push(@cells2,join(",",$r-$minr,(($c-$minc)*2)+$s));
  }

  $cells[0]=join(";",@cells0);
  $cells[1]=join(";",@cells1);
  $cells[2]=join(";",uniq(@cells0,@cells1));

  @cells;
}


# removes duplicates from a list, returning a list of unique elements
sub uniq {
  my (@maybedupslist) = @_;
  my %hashlist;
  my @uniqlist;

  # make list elements unique by converting to a hash
  @hashlist{@maybedupslist} = ();
  # convert back to an array
  @uniqlist = keys %hashlist;

  @uniqlist;
}


# convert format from IOB(SIDE,idx,i/o[siteidx]) to SIDE,idx,siteidx
sub convertIOBloc {
  my ($iob) = @_;
  my @fields=split(/,/,$iob);

  $fields[0]=substr($fields[0],4);		# get rid of leading "IOB("
  $fields[2]=substr($fields[2],2,1);		# only keep site index
  $iob=join(",",@fields);

  $iob;
}


sub printA {                            # for debugging to print arrays
  my ($str,@a) = @_;
  my $i;

  print STDERR "$str: ";
  for ($i=0; $i<$#a+1; ++$i) {
    print STDERR "($a[$i])";
  }
  print STDERR "\n";
}


sub printUsage {

  print STDERR <<EOH
   Synopsis:
   [perl] setupEHW.pl [-v] [-s] [-agb] [-notfs] [-code confile]              \
   				[-exitonsuccess] [-exitonstagnate sg]	     \
   		      		[-noexitoncctfail]                           \
		       		device rows cols inbuswidth outbuswidth      \
   				testclass popsize [chromsize] datadir [      \
   				numVgens reprate[+] xrate mrate irate idrate \
  				numTestIter numGrowthSteps [ transcribeRate  \
  				polyToGene polyThreshold ] [mgseed] ]

   Args:
   		-v		Verbose mode - generate info to STDIO during
   				setup
   		-s		save best bitstream option - during evolution
   				the bitsream of the highest fitness is kept
  		-agb		anti-gene-bloat for use with morphogenesis,
  				factors in ratio of chromosome's gene count
  				to fittest (to-date) chrom's gene count when
  				evaluating fitness. If chrom has more genes,
  				and is not fitter, then fitness is scaled down
  				by the gene count ratio.
 	        -notfs		don't use TFs, incl. morphogens and cytoplasms
   		-code confile	overides the default genetic code file
  		-exitonsuccess	when get a 100% solution, finish generation
  				and then exits. checkpoint population file is
			        the successful generation and can be examined.
	    -exitonstagnate sg  if maximum fitness (in population) has not
				increased in 'sg' generations, then exit. The
				checkpoint population file can be examined or
				have evolution continued from.
 	      -noexitoncctfail	don't abort if a circuit evaluation fails,
 	      			just give genotype a fitness of 0 and continue
   		device		FPGA device & resource subset (model). Must be
   				one of the following, ordered with increasing
   				resource limitations:
		      XCV[1000|50] - slice granularity almost full model, use
		      		  XCV50 with VirtexDS, and XCV1000 with Raptor
		      XCV50sync - slice gran, lacks only asynch & Ctrl outputs
		      XCV50nFF  - slice gran lacks unregistered & Ctrl outputs
		      XCV50LE	- LE gran lacks SliceRAM & Ctrl inputs/outputs
		      XCV50LEsync - LE gran also lacks unregistered outputs
		      XCV50GAsync - as above but standard GA and LUTBitFNs
		      XCV50LEnFF - same as sync but lacks registered outputs
		      XCV50GAnFF - as above but standard GA and LUTBitFNs
		      XCV50-	- LE gran uses 'trim' model with 2 outbus lines
		      		  allocated per LE, synch slice out, LUTBitFNs
		      XCV50GA-	- standard GA with 'trim' model
		      XCV50--	- LE gran 'slim' model (1 LUT input, 4 LUT fn,
		      		  2 OUT bus lines, 3-4 singles per LE)
		      XCV50GA--	- standard GA with 'slim' model
		      XCV50slim	- slice gran, 'slim' model
  				note: a 'GA' suffix indicates that a standard
				GA using a binary fixed length chromosome with
  				a direct mapping to the FPGA is used.
   		rows		number of CLB rows in evolvable region
   		cols		number of CLB cols in evolvable region
  		inbuswidth	width of input (DUT_in) bus
  		outbuswidth	width of output (DUT_out) bus
   		testclass	the name of the java classfile to run to (grow
   				and) test evolved circuit. The output of this
  				must be: fitness [@ iteration [; numgenes]]
   		popsize		size of population to be generated
   		chromsize	median size of chromosomes to generate, with
   				sizes ranging from 0.5 - 1.5 x chromsize
  				Note this parameter should be ommitted if
  				XCV50GA device is specified
   		datadir		where all the data files (eg population,
   				bitstream) will be kept

			... the following are not used if using standard GA

   		numVgens	the number of generations to evolve initial
   				population, to generate chromosomes containing
   				genes for morphogenesis (including promoters,
   				regulatory and transcription regions)
   		reprate[+]	number of members to replace with new (maximum
   				of 1/4 the population size). If followed by a
   				'+', then both offspring of the 'reprate'
   				selected parents are introduced into the
   				population (which requires 'reprate' to be an
   				even number), otherwise only the fitest of the
   				2 x 'reprate' are introduced.
  		xrate		crossover rate in offspring (0.0 - 1.0)
  		mrate		rate of mutation of bases (0.0 - 1.0)
  		irate		rate of inversion in offspring (0.0 - 1.0)
  		idrate		rate of base insert/delete in offspring
  				(0.0 - 1.0)
  		numTestIter	number of times to continue growth and test
  				fitness when running morphogenesis
  		numGrowthSteps	number of growth steps per fitness test
  				when running morphogenesis

		transcribeRate	number of bases transcribed each growth step
				(default is 4)
		polyToGene	RNA polymerase II molecules per gene, this
				limits the number of genes that can be
				active at any time. use a value < 1.0 to
				indicate a ratio, or >1 to set a fixed number
				(default is 0.3)
		polyThreshold	theshold to be exceeded for a free polymerase
				molecule to bind to a gene (default is 0.8
				which means binding occurs 20% of the time)

   		mgseed		optional random seed, to ensure the steps
   				taken by morphogenesis process is reproducible

   Inputs: (none)
   Outputs:
   	   (STDERR)		informative messages
   	   $POPBASENAME.txt	generated population of chromosomes
  	   $POPPARAM		parameters used to generate population
   	   $LAYOUTBIT		bitstream connected to GCLK, with input and
   	   			output buses routed to border of evolvable
   	   			region, a region of null CLBs
   	   $CYTOBITS.txt   	generated file of cytoplasmic determinants
   	   $CONFIGFILE		configuration file for EHW system

   Description:
 		generates EHW layout for the given device, an initial
 		population, and if performing morphogenesis a cytoplasmic
 		determinants placement file, and generates a configuration
 		file used by the EHW system

EOH
}



