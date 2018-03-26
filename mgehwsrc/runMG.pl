
# runMG.pl by J.A. Lee, August 2004
#          May 2005, jl: added fixedlut and fixedmux options to adder1*
#          Sep 2005, jl: added fixed6200 options to adder*
#
#  Synopsis:
#  [perl] runMG.pl [-v | -t] testname basedir maxgens [[startrun#]numruns]
# 
#  Args:
#  	 	-v		Verbose mode - generate info to STDIO
#  	 	-t		Test mode - outputs commands used to run sys
#		testname	the EHW problem for which a solution is to
#				be found; can be one of:
#					slim5x5
#					slim5x5GA
#					slim5x5tfs
#					slim8x8
#					slim8x8GA
#					slim8x8tfs
#					adder1[(fixed|preset)(mux|6200)]
#					adder1bit[GA]([fixed|preset)(mux|6200|lut)]
#					adder1incr[(fixed|preset)(mux|6200|lut)]
#					adder1active[(fixed|preset)(mux|6200|lut)]
#					adder2[bit|incr|active]
#					slim:ROWSxCOLS[GA|tfs]:IN-OUT
#					inout:ROWSxCOLS:IN-OUT
#					inoutao:ROWSxCOLS:IN-OUT
#					fnseq:ROWSxCOLS:IN-OUT:INSEQ:OUTSEQ
#					fnseqao:ROWSxCOLS:IN-OUT:INSEQ:OUTSEQ
#				ROWS and COLS give the size of the CLB matrix
#				IN and OUT give the no. of inputs and outputs
#				INSEQ and OUTSEQ give the function truthtable
#				in little-endian (input/output) order comma
#				delimited binary (there will be 2^I x I input
#				signals and 2^I x O output signals).
#				signals are: 0,1 = digital signals; also on
#				outputs: -1 = Z (unconnected), 2=X (don't care)
#				eg. for inout; inout:5x5:1-1
#				eg. for a 1bit adder with fnseq;
#				fnseq:3x3:3-2:
#				0,0,0,1,0,0,0,1,0,1,1,0,0,0,1,1,0,1,0,1,1,1,1,1
#				:0,0,1,0,1,0,0,1,1,0,0,1,0,1,1,1
#		basedir		the base directory for storing populations,
#				etc. if numruns is specified, then each run
#				will be stored in subdirectory (run#) under
#				the basedir, otherwise basedir will be used.
#  	 	maxgens		the maximum number of generations to evolve
#  	 			the population
#  	 	startrun	initial run number (default is 1)
#		numruns		runs this numruns times (default is 1)
#
#  Inputs: (none)
#
#  Outputs:
#  	   (STDERR)		  informative messages
#  	   basedir/run#		  all the generated files:
#  	     pop_#gens.txt	  evolved population of chromosomes, eg if
#  	   			  completed 100 gens, pop_100.txt
#  	     pop_#gens.parm.txt   parameters used to evolve population
#	     pop_#gens_stats.txt  popn statistics (if -v or -s ngen > 0)
#  	     fittest_details.txt  details of fittest population members:
#  	     				fitness @iter : rand_seed
#  	     				fittest gene count
#  	     				generation fittest occured
#  	     				prevbest details ..
#  	     bestchrom.txt	  chromosome of fittest population member
#  	     bestchrom.(fitness).txt   "	prev fittest popn member
#  	     pop.txt		  initial generated population after pre-
#  	     			  evolution for 'viable' chromosomes, ie
#  	     			  containing genes
#  	     pop.parm.txt	  parameters used to evolve initial population
#  	     pop_stats.txt	  stats (incl viability fitness) for init popn
#  	     EHW_config.txt	  EHW configuration file that is placed in
#  	     			  EHW bin directory when running
#  	     layout.bit		  EHW initial layout bitstream
#  	     cytobits.txt	  cytoplasms (empty if no TFs used)
#
#  Description:
#		this is a front end to setupEHW.pl and runEHW.pl, it enables
#		easy setup (without needing to remember a complex set of
#		parameters) and multiple runs of runEHW.pl, each generating
#		a new population file, etc. in its own directory.
#		Note that the evolutionary and morphogenetic params are
#		defined as constants within this script
#		Note for "fixedlut" and "presetlut" option for ActiveLUTFns
#		adder2x2fixedActiveLUTsettings.txt must be in the current dir
#
#  See also:	setupEHW.pl, runEHW.pl, and TestEHW classes for more details


### DEFINES

# Evolutionary parameters

$POPSIZE=100;
$CHROMSIZE=800;		# mean starting chromosome length - an arbitrary value

$REPRATE=10;		# chosen as 10% of a population of 100
			# can be up to 25% of population

$XRATE=0.8;		# probability of using Xover in offspring = 80%
$IRATE=0.05;		# prob of applying inversion in offspring = 5%

$MRATE=0.02;		# prob of any given base being mutated = 2%
			# which is approx 1 codon out of each 16
$IDRATE=0.001;		# prob of adding or deleting a base at any base = 0.1%
			# which is one per 1000 (or 333 codons)

$NUMVGENS=25;		# number of generations of pre-evolution to seed popn
			# with 'viable' chromosomes, ie containing genes

# Morphogenesis parameters

$NUMGROWTHSTEPS=1;	# how many growth steps per cct fitness eval
$NUMTESTITER=30;	# minimum number of fitness evals
$TRANSCRIBERATE=4;	# transcribe 4 bases of a gene each growth step
$POLYTOGENE=0.3;	# number of polymerase enzymes per gene
$POLYTHRESHOLD=0.8;	# probability of polymerase binding (1-0.8=0.2=20%)

# other parameters

$STATSGENS=5;		# generate population stats every 5 generations
$ANTIGENEBLOAT=1;	# -abg option, set to 0 to allow unbounded chrom growth
$NOTFS=1;		# -notfs option, set to 0 if want to use TFs
$NOEXITONCCTFAIL=1;	# -noexitoncctfail, set to 0 if debugging
$EXITONSUCCESS=1;	# -exitonsuccess option. set to 0 if want to continue
$EXITONSTAGNATEGENS=0;  # -exitonstagnategens GENS; set to GENS if wanted

# configuration files

$GENETICCODE="";	# this is set here or to the default in setupEHW.pl

### END DEFINES

$VERBOSE=0;
$NORUN=0;

#  [perl] runMG.pl [-v | -t] testname basedir maxgens [[startrun#]numruns]

if ($#ARGV>-1 && $ARGV[0] eq "-v") {
  shift;
  $VERBOSE=1;
} elsif ($#ARGV>-1 && $ARGV[0] eq "-t") {
  shift;
  $VERBOSE=1;
  $NORUN=1;
}

$argc=$#ARGV+1;
if ($argc<3 || $argc>4) { printUsage(); exit(1); }

$test=shift;
$basedir=shift;
$maxgens=shift;

# create base directory if necessary
if (!-e $basedir && !$NORUN) {
  if ($VERBOSE) { print STDERR "creating directory $basedir .. "; }
  mkdir($basedir,0700) || die("Unable to create directory $basedir");
  if ($VERBOSE) { print STDERR "done.\n"; }
} elsif (!-d $basedir) {
  die("$basedir already exists but is not a directory");
} 

$startrun=1;

if ($argc==4) {
  $ONCEONLY=0;
  $numruns=shift;
  if ($numruns=~/\#/) { ($startrun,$numruns)=split(/\#/,$numruns); }
} else {
  $ONCEONLY=1;
  $numruns=1;
}


if ($test=~/slim5x5/) {
  $DEVICE=($test ne "slim5x5GA") ? "XCV50--" : "XCV50GA--";
  $testclass="TestInToOutRecurse -ndo";
  $nrows=$ncols=5;
  $numin=$numout=1;
  $EXITONSTAGNATEGENS=1000;
  if ($test eq "slim5x5tfs") { $NOTFS=0; }
} elsif ($test=~/slim8x8/) {
  $DEVICE=($test ne "slim8x8GA") ? "XCV50--" : "XCV50GA--";
  $testclass="TestInToOutRecurse -ndo";
  $nrows=$ncols=8;
  $numin=$numout=4;
  $EXITONSTAGNATEGENS=1500;
  if ($test eq "slim8x8tfs") { $NOTFS=0; }
} elsif ($test=~/adder1/) {
  if ($test=~/GA/) {
    $DEVICE="XCV50GAnFF"; # combinatorial circuit, no state = no FFs
  } else {
    $DEVICE="XCV50LEnFF"; # combinatorial circuit, no state = no FFs
  }
  $testclass="TestFnInToOut -onebitadder";
  $EXITONSTAGNATEGENS=2000;

  if ($test=~/adder1bit/) {
    $GENETICCODE="VirtexGeneticCodeBitLUTs.conf"; # only LUTBitFNs
  } elsif ($test=~/adder1incr/) {
    $GENETICCODE="VirtexGeneticCodeIncrLUTs.conf"; # only LUTIncrFNs
  } elsif ($test=~/adder1active/) {
    $GENETICCODE="VirtexGeneticCodeActiveLUTs.conf"; # only LUTActiveFNs
  } else {
    if ($test=~/fixedlut$/ || $test=~/presetlut$/) {
      print STDERR "fixedlut and presetlut options can't be used with non-specified LUT type!\n";
      exit(1);
    }
    $GENETICCODE="VirtexGeneticCodeLUTsmore.conf"; # all LUT config types
  }

  if ($GENETICCODE eq "VirtexGeneticCodeActiveLUTs.conf") {
    $testclass.=" -activelutfnsonly";
  }

  # to test evolving 1-bit full adder function and routing separately:
  # testclass is modified to call an intervening perl script that will strip
  # parts of the decoded chromosome to stop modifications to the fixed parts
  # of the bitsream/adder design. it will then call the appended $testclass

  if ($test=~/fixedlut$/) {
    $testclass="Adder2x2Fixed.pl -lut " .  $testclass;
  } elsif ($test=~/presetlut$/) {
    $testclass="Adder2x2Fixed.pl -lut -presetonly " .  $testclass;
  } elsif ($test=~/fixed6200$/) {
    $testclass="Adder2x2Fixed.pl -6200 " . $testclass;
  } elsif ($test=~/preset6200$/) {
    $testclass="Adder2x2Fixed.pl -6200 -presetonly " . $testclass;
  } elsif ($test=~/fixedmux$/) {
    $testclass="Adder2x2Fixed.pl -mux " . $testclass;
  } elsif ($test=~/presetmux$/) {
    $testclass="Adder2x2Fixed.pl -mux -presetonly " . $testclass;
  }

  # for pre-setting the active LUTs
  if (($test=~/fixedlut$/ || $test=~/presetlut$/) &&
      $GENETICCODE=~/ActiveLUTs/) {
    # this file must be located in the current directory, and have the
    # settings required to configure the active LUTs in the LUTs that
    # perform some function
    $testclass .= " -load adder2x2fixedActiveLUTsettings.txt";
  }


  $nrows=$ncols=2; # this was chosen to see affect of both routing and fns
  $numin=3;  # x, y, cin
  $numout=2; # sum, cout
} elsif ($test=~/adder2/) {
  $DEVICE="XCV50LEnFF"; # combinatorial circuit, no state = no FFs
  $testclass="TestFnInToOut -twobitadder";

  if ($test eq "adder2bit") {
    $GENETICCODE="VirtexGeneticCodeBitLUTs.conf"; # only LUTBitFNs
  } elsif ($test eq "adder2incr") {
    $GENETICCODE="VirtexGeneticCodeIncrLUTs.conf"; # only LUTIncrFNs
  } elsif ($test eq "adder2active") {
    $GENETICCODE="VirtexGeneticCodeActiveLUTs.conf"; # only LUTActiveFNs
  } else {
    $GENETICCODE="VirtexGeneticCodeLUTsmore.conf"; # all LUT config types
  }

  if ($GENETICCODE eq "VirtexGeneticCodeActiveLUTs.conf") {
    $testclass.=" -activelutfnsonly";
  }

  $nrows=$ncols=3;			# this is arbitrary & could be changed
  $numin=5;
  $numout=3;
} else {
  #	slim:rowsxcols[GA|tfs]:in-out
  #	inout[ao]:rowsxcols:in-out
  #	fn[ao]:rowsxcols:in-out:inseq:outseq
  ($testname,$size,$inout,$inseq,$outseq)=split(/:/,$test);
  ($nrows,$ncols)=split(/x/,$size);
  ($numin,$numout)=split(/-/,$inout);
  if ($testname eq "slim") { 
    if ($size=~/GA$/) {
      $DEVICE="XCV50GA--";
      $ncols=substr($ncols,0,-2);
    } elsif ($size=~/tfs$/) {
      $NOTFS=0;
      $DEVICE="XCV50--";
      $ncols=substr($ncols,0,-3);
    } else {
      $DEVICE="XCV50--";
    }
    $testclass="TestInToOutRecurse -ndo";
  } else {
    $DEVICE="XCV50LE"; # use "XCV50LEsync" if want synchronous only ccts
    if ($testname=~/ao$/) {
      $GENETICCODE="VirtexGeneticCodeActiveLUTs.conf"; # only LUTActiveFNs
      $testclass="TestFnInToOut -$testname -activelutfnsonly";
      chop($testname); chop($testname); # lose the trailing "ao"
    } else {
      $GENETICCODE="VirtexGeneticCodeLUTsmore.conf";
      $testclass="TestFnInToOut -$testname";
    }
    if ($testname eq "fnseq") {
      if ($inseq ne "" && $outseq ne "") {
        $testclass .= " -fnseq $inseq $outseq";
      } else {
	print STDERR "fnseq needs binary input and output sequences! exiting...\n"; 
        exit(1);
      }
    }
  }
}


$setup="perl setupEHW.pl";
$setup .= " -v" if ($VERBOSE==1);
$setup .= " -agb" if ($ANTIGENEBLOAT==1);
$setup .= " -notfs" if ($NOTFS==1);
$setup .= " -exitonsuccess" if ($EXITONSUCCESS==1);
$setup .= " -exitonstagnate $EXITONSTAGNATEGENS" if ($EXITONSTAGNATEGENS);
$setup .= " -noexitoncctfail" if ($NOEXITONCCTFAIL==1);
$setup .= " -code $GENETICCODE" if ($GENETICCODE ne "");
$setup .= " $DEVICE $nrows $ncols $numin $numout \"$testclass\" $POPSIZE";

# for debugging - may also want to change vals for POPSIZE,REPRATE,NUMVGENS
#$setup .= " $DEVICE $nrows $ncols $numin $numout \"DummyTest $testclass\" $POPSIZE";

# if using a direct encoding/GA, then chromosome is fixed length and
# calculated according to the size of the evolvable region, so dont specify
$setup .= " $CHROMSIZE" unless ($DEVICE=~/GA/);

# the next parameter to setupEHW.pl is the datadir, but this will change
# from one run to the next, so we leave this for now

$evparams="$REPRATE $XRATE $MRATE $IRATE $IDRATE";

# these only apply to morphogenetic approaches, not GA
unless ($DEVICE=~/GA/) {
  $mgparams = "$NUMVGENS $evparams $NUMTESTITER $NUMGROWTHSTEPS ";
  $mgparams .="$TRANSCRIBERATE $POLYTOGENE $POLYTHRESHOLD";
} else {
  $mgparams="";
  # antigenebloat isn't relevant for direct encoding
  $ANTIGENEBLOAT=0;
}


$runcmd="perl runEHW.pl";
$runcmd .= " -v" if ($VERBOSE==1);
$runcmd .= " -s $STATSGENS" if ($VERBOSE==1);

# this allows us to stop and start evolution by storing fitness with chroms
$runcmd .= " -f";

# always start evaluate the initial population before evolving";
$run0cmd=$runcmd . " 0 $evparams";

$runcmd=$runcmd . " 0+$maxgens $evparams";


for ($run=$startrun; $run<=$startrun+$numruns-1; $run++) {

  $datadir=($ONCEONLY==1) ? $basedir : "$basedir/$run";
  $setupcmd="$setup $datadir";
  $setupcmd .= " $mgparams" if ($mgparams);

  if ($VERBOSE && !$ONCEONLY) { print STDERR "entering run number $run ...\n"; }
  if ($VERBOSE) { print STDERR "executing $setupcmd ...\n"; }
  unless ($NORUN) {
    system($setupcmd) && die("failed while attempting to setup EHW run $run");
    if ($VERBOSE) { print STDERR "\ncompleted EHW setup.\n"; }
  }
  if ($VERBOSE) { print STDERR "evaluating initial population with $run0cmd ...\n"; }
  unless ($NORUN) {
    system($run0cmd); # this could fail, if we find a 100% soln in popn
  }

  if ($VERBOSE) { print STDERR "\nevolving population with $runcmd ...\n"; }
  unless ($NORUN) {
    system($runcmd); # this could fail, if we find a 100% soln or stagnate
    if ($VERBOSE) { print STDERR "\nfinished evolution run $run ...\n"; }
  }

  # cleanup the temp files
  # NOTE: cleanupEHW.pl requires perl libraries installed to run
  $cleancmd="perl cleanupEHW.pl";
  $cleancmd.=" -v" if ($VERBOSE);
  $cleancmd.=" $maxgens";
  if ($VERBOSE) { print STDERR "cleaning up after evolution run $run with $cleancmd ...\n"; }
  unless ($NORUN) {
    system($cleancmd);
  }
}

unless ($NORUN) {
  if ($VERBOSE) { print STDERR "\ncompleted all runs...\n"; }
}



############# end main()


sub printUsage {

  print STDERR <<EOH
   Synopsis:
  [perl] runMG.pl [-v | -t] testname basedir maxgens [[startrun#]numruns]
 
  Args:
  	 	-v		Verbose mode - generate info to STDIO
  	 	-t		Test mode - outputs commands used to run sys
		testname	the EHW problem for which a solution is to
				be found; can be one of:
					slim5x5
					slim5x5GA
					slim5x5tfs
					slim8x8
					slim8x8GA
					slim8x8tfs
					adder1[(fixed|preset)(mux|6200)]
					adder1bit[GA]([fixed|preset)(mux|6200|lut)]
					adder1incr[(fixed|preset)(mux|6200|lut)]
					adder1active[(fixed|preset)(mux|6200|lut)]
					adder2[bit|incr|active]
					slim:ROWSxCOLS[GA|tfs]:IN-OUT
					inout:ROWSxCOLS:IN-OUT
					inoutao:ROWSxCOLS:IN-OUT
					fnseq:ROWSxCOLS:IN-OUT:INSEQ:OUTSEQ
					fnseqao:ROWSxCOLS:IN-OUT:INSEQ:OUTSEQ
				ROWS and COLS give the size of the CLB matrix
				IN and OUT give the no. of inputs and outputs
				INSEQ and OUTSEQ give the function truthtable
				in little-endian (input/output) order comma
				delimited binary (there will be 2^I x I input
				signals and 2^I x O output signals).
				signals are: 0,1 = digital signals; also on
				outputs: -1 = Z (unconnected), 2=X (don't care)
				eg. for inout; inout:5x5:1-1
				eg. for a 1bit adder with fnseq;
				fnseq:3x3:3-2:
				0,0,0,1,0,0,0,1,0,1,1,0,0,0,1,1,0,1,0,1,1,1,1,1
				:0,0,1,0,1,0,0,1,1,0,0,1,0,1,1,1
		basedir		the base directory for storing populations,
				etc. if numruns is specified, then each run
				will be stored in subdirectory (run#) under
				the basedir, otherwise basedir will be used.
  	 	maxgens		the maximum number of generations to evolve
  	 			the population
  	 	startrun	initial run number (default is 1)
		numruns		runs this numruns times (default is 1)

  Inputs: (none)

  Outputs:
  	   (STDERR)		  informative messages
  	   basedir/run#		  all the generated files:
  	     pop_#gens.txt	  evolved population of chromosomes, eg if
  	   			  completed 100 gens, pop_100.txt
  	     pop_#gens.parm.txt   parameters used to evolve population
	     pop_#gens_stats.txt  popn statistics (if -v or -s ngen > 0)
  	     fittest_details.txt  details of fittest population members:
  	     				fitness \@iter : rand_seed
  	     				fittest gene count
  	     				generation fittest occured
  	     				prevbest details ..
  	     bestchrom.txt	  chromosome of fittest population member
  	     bestchrom.(fitness).txt   "	prev fittest popn member
  	     pop.txt		  initial generated population after pre-
  	     			  evolution for 'viable' chromosomes, ie
  	     			  containing genes
  	     pop.parm.txt	  parameters used to evolve initial population
  	     pop_stats.txt	  stats (incl viability fitness) for init popn
  	     EHW_config.txt	  EHW configuration file that is placed in
  	     			  EHW bin directory when running
  	     layout.bit		  EHW initial layout bitstream
  	     cytobits.txt	  cytoplasms (empty if no TFs used)

  Description:
		this is a front end to setupEHW.pl and runEHW.pl, it enables
		easy setup (without needing to remember a complex set of
		parameters) and multiple runs of runEHW.pl, each generating
		a new population file, etc. in its own directory.
		Note that the evolutionary and morphogenetic params are
		defined as constants within this script
		Note for "fixedlut" and "presetlut" option for ActiveLUTFns
		adder2x2fixedActiveLUTsettings.txt must be in the current dir

  See also:	setupEHW.pl, runEHW.pl, and TestEHW classes for more details

EOH
}



