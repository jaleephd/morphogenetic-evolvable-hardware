# runEHW.pl by J.A. Lee, Feb 2004
#
#  Synopsis:
#  [perl] runEHW.pl [-v] [-f] [-s ngen] [gen+]numgens reprate[+] xrate mrate irate [idrate]
# 
#  Args:
#  		-v		Verbose mode - generate info to STDERR
# 		-f		save fitness with evolved chromosomes
#		-s ngen		generate fitness, length, and homology stats
#			  	every 'ngen' generations (0 for off - used to
# 				turn of stats when using -v switch).
#  		[gen+]numgens	the number of generations to evolve popn
#  				if preceded by gen (an integer) '+' then it
#  				will look for POPBASENAME_gen.txt and evolve
#  				it for a further numgens generations to give
#  				a resulting population in the file
#  				POPBASENAME_(gen+ngen).txt 
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
# 			.. the following parameter should only supplied if
# 			   using variable length chromosomes (not standard GA)
# 		idrate		rate of base insert/delete in offspring
# 				(0.0 - 1.0)
#
#  Inputs:
#  	   EHW_config.txt	holds the parameters used by the system
#
#  Outputs:
#  	   (STDERR)		  informative messages
#  	   POPBASENAME_#gens.txt  updated population of chromosomes, eg if
#  	   			  ngens=100, pop_100.txt
#  	    "  " _#gens.parm.txt  parameters used to evolve population
#	    "  " _#gens_stats.txt popn statistics (if -v or -s ngen > 0)
#
#  	   ... the following are produced if SAVEBEST=true in EHW_config.txt
#
#	   BESTCHROM		 chromosome that produced fittest circuit
#	   BESTBIT		 bitstream of fittest circuit
#	   FITTESTDETS		 for the fittest circuit:
#	   			   fitness [@ growth step : seed ; numgenes]
#	   			   # fittest occurred at generation GEN
#
#  Description:
#		performs evolution of a population of chromosomes, each of
#		which configures a region of the CLB matrix on a Virtex FPGA
#		using JBits. The parameters to this script are solely for
#		controlling evolution; EHW_config.txt (which is generated
#		by setupEHW.pl, and must reside in the current directory)
#		defines the parameters required for converting genotype
#		(chromosome) to phenotype (circuit), and for evaluating the
# 		fitness of the phenotype. Note that POPBASENAME, BESTCHROM,
#		BESTBIT, and FITTESTDETS have their actual names defined in
#		EHW_config.txt


### DEFINES

$CONFIGFILE="EHW_config.txt";
$GASCRIPT="genpopn.pl";
$FITSCRIPT="EHWfitness.pl";

### END DEFINES

$VERBOSE=0;
$SAVEFITNESS="";
$STATSGEN="";

$runparams=join(" ", @ARGV);

while ($#ARGV>-1 && $ARGV[0]=~/^-[vfs]/) {
  if ($ARGV[0] eq "-v") {
    shift;
    $VERBOSE=1;
    $FITSCRIPT=$FITSCRIPT . " -v";
    $GASCRIPT=$GASCRIPT . " -v";
  } elsif ($ARGV[0] eq "-f") {
    shift;
    $SAVEFITNESS="-f";
  } else { 			# $ARGV[0] eq "-s"
    $STATSGEN=join(" ",$ARGV[0],$ARGV[1]);
    shift;
    shift;
  }
}

# this is needed to pass to genpopn.pl so that can get gene stats with -sg
# if morphogenesis approach is being used
if ($VERBOSE==1 && $STATSGEN eq "") {
  $STATSGEN="-s 1";
}

$argc=$#ARGV+1;

if ($argc<5) { printUsage(); exit(1); }


$ngen=shift;
$reprate=shift;
$xrate=shift;
$mrate=shift;
$irate=shift;
if ($argc>5) { $idrate=shift; } else { $idrate=0; }

# check that config file exits - if not then exit
# setupEHW needs to be run to generate this
if (!-e $CONFIGFILE) {
  die("missing $CONFIGFILE! generate with setupEHW.pl and place in current directory\n");
}

# read EHW_config file (CONFIGFILE) and define the following (GLOBAL)
# constants as given in the file:
#	DATADIR POPBASENAME GENECODE DIRECTCHROMDECODE DEVNAME CONTENTIONFILE
#	TEST VERTGRAN CONF2BITS FITTESTDETS LAYOUTBIT TESTBIT BESTBIT BESTCHROM
#	SAVEBEST EXITONSUCCESS EXITONSTAGNATEGENS EXITONCCTFAIL MINCLB MAXCLB
#	INIOBS OUTIOBS INCLBLES OUTCLBLES NUMGROWTHSTEPS NUMTESTITER
#	TRANSCRIBERATE POLYTOGENE POLYTHRESHOLD USETFBINDS CYTOFILE
#	ANTIGENEBLOAT GENECEILING MGSEED
# (depreceated) #	SPLITCHROMALIGN SPLITCHROMMARKER



if ($VERBOSE) { print STDERR "reading in EHW configuration file...\n"; }
open(CFILE,"$CONFIGFILE") || die "Unable to read EHW configuration file";
while(<CFILE>) {
  next if (/^\s*#/ || /^\s*$/);	# skip empty and comment lines
  unless (/=/) { die("Invalid line in EHW configuration file: $_"); }
  chop;
  $val="";
  ($const,$val)=split(/=/);
  # the const's name isn't preceded by a '$' in the file, so add here
  # and as the assigned values are strings, enclose them in quotes
  eval "\$$const=\"$val\";";
  #if ($VERBOSE) { eval "print STDERR \" [$const=\$$const] \""; }
}
close(CFILE);
if ($VERBOSE) { print STDERR "\ndone\n"; }

$startgen=0;
if ($ngen=~/[+]/) {
  ($startgen,$gens)=split(/[+]/,$ngen);
  $initpop=$POPBASENAME . "_$startgen.txt";
  if (!-e $initpop) {
    die "generation $startgen population file ($initpop) can't be found!";
  }
  $finalpop=$POPBASENAME . "_" . ($startgen+$gens) . ".txt";
  $popparam=$POPBASENAME . "_" . ($startgen+$gens) . ".parm.txt";
} else {
  $initpop=$POPBASENAME . ".txt";
  $finalpop=$POPBASENAME . "_$ngen.txt";
  $popparam=$POPBASENAME . "_$ngen.parm.txt";
}

if ($DIRECTCHROMDECODE ne "") {
  $runcmd="perl $GASCRIPT $STATSGEN $SAVEFITNESS -ga $initpop $finalpop \"$FITSCRIPT -f\" $ngen $reprate $xrate $mrate $irate";
} else {
  if ($NUMGROWTHSTEPS) {	# if morphogenetic approach
    $STATSGEN=~s/^-s/-sg/;	# we also want gene count stats
  }
  $runcmd="perl $GASCRIPT $STATSGEN $SAVEFITNESS $initpop $finalpop \"$FITSCRIPT -f\" $ngen $reprate $xrate $mrate $irate $idrate";
}

# replace multiple spaces (from empty params) with a single space
$runcmd=~s/\s+/ /g;
$runtime=localtime();

if ($VERBOSE) { print STDERR "creating population parameter file $popparam .. "; }
open(PFILE,">$popparam") || die "Unable to create population parameter file $popparam";
print PFILE "# population was generated starting at $runtime\n";
print PFILE "# with command: $runcmd\n";
print PFILE "# run from runEHW.pl $runparams\n";
close PFILE;
if ($VERBOSE) { print STDERR "done.\n"; }

if ($VERBOSE) { print STDERR "running EHW system with command: $runcmd\n"; }

system($runcmd) && die("failed while attempting to run EHW system");
if ($VERBOSE) { print STDERR "done.\n"; }


############# end main()



sub printUsage {

  print STDERR <<EOH
   Synopsis:
   [perl] runEHW.pl [-v] [-f] [-s ngen] [gen+]numgens reprate[+] xrate mrate irate idrate
  
   Args:
   		-v		Verbose mode - generate info to STDERR
  		-f		save fitness with evolved chromosomes
 		-s ngen		generate fitness, length, and homology stats
 			  	every 'ngen' generations (0 for off - used to
  				turn of stats when using -v switch).
  		[gen+]numgens	the number of generations to evolve popn
  				if preceded by gen (an integer) '+' then it
  				will look for POPBASENAME_gen.txt and evolve
  				it for a further numgens generations to give
  				a resulting population in the file
  				POPBASENAME_(gen+ngen).txt 
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
 			.. the following parameter should only supplied if
 			   using variable length chromosomes (not standard GA)
  		idrate		rate of base insert/delete in offspring
  				(0.0 - 1.0)
 
   Inputs:
   	   EHW_config.txt	holds the parameters used by the system
 
   Outputs:
   	   (STDERR)		  informative messages
  	   POPBASENAME_#gens.txt  updated population of chromosomes, eg if
  	   			  ngens=100, pop_100.txt
  	    "  " _#gens.parm.txt  parameters used to evolve population
	    "  " _#gens_stats.txt popn statistics (if -v or -s ngen > 0)
 
   	   ... the following are produced if SAVEBEST=true in EHW_config.txt
 
	   BESTCHROM		 chromosome that produced fittest circuit
	   BESTBIT		 bitstream of fittest circuit
	   FITTESTDETS		 for the fittest circuit:
	   			   fitness [@ growth step : seed ; numgenes]
	   			   # fittest occurred at generation GEN
 
   Description:
 		performs evolution of a population of chromosomes, each of
 		which configures a region of the CLB matrix on a Virtex FPGA
 		using JBits. The parameters to this script are solely for
 		controlling evolution; EHW_config.txt (which is generated
 		by setupEHW.pl, and must reside in the current directory)
 		defines the parameters required for converting genotype
 		(chromosome) to phenotype (circuit), and for evaluating the
 		fitness of the phenotype. Note that POPBASENAME, BESTCHROM,
		BESTBIT, and FITTESTDETS have their actual names defined in
		EHW_config.txt

EOH
}



