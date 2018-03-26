
# genpopn.pl by J.A. Lee, March 2003
# updated Feb 2004 to give option of saving fitness with chromosomes, and
# to read initial chromosome file with fitnesses, and also to store stats
# to file
# updated March 2004 to give option of doing standard GA operations on a
# binary-digit fixed-length chromosome
#
# Synopsis: [perl] genpopn.pl [-v] [[-s|-sg] ngens] [-f] [-ga] (-g popsize (len | minlen maxlen) | popfile) outfile fitscript [gen+]numgens [reprate[+] xrate mrate irate [idrate]]
#
# Args:
# 	-v		- verbose output to STDERR, operator results,
# 			  information on removed population members, and
# 			  fitness, and length if '-ga' switch not given, stats
# 			  every generation
#	-s ngens	- generate fitness stats, and if not a standard GA,
#			  length and homology stats every 'ngens' generations
#			  (0 for off - used to turn of stats with verbose mode)
# 	-sg ngens	- as above, but also generate, gene count stats
# 	-f		- save fitness with evolved chromosomes
# 	-ga		- use standard GA on standard binary fixed length chrom
# 			  this means that -g takes 'len' not minlen and maxlen,
# 			  and the 'idrate' parameter should not be given
# 	-g popsize ( len | minlen maxlen)
# 			- generate a populate of size 'psize' randomly
# 			  chromosomes with lengths between min and max
# 			  or all of length 'len' if -ga switch was used
# 	popfile		- to use a pre-existing population
# 	outfile		- for storing resulting population
# 	fitscript	- fitness evaluator that returns lines of:
# 			  fitness '>' chromosome
# 			  If command line switches are needed for the
# 			  fitness script, script and switches should be
# 			  enclosed in single or double quotes.
# 	[gen+]numgens	- number of generations to evolve population. gen
# 			  indicates what generation to start at (default 0)
# 			  if numgens=0, the following arguments are optional
#  	reprate[+]	- number of members to replace with new (maximum of
# 			  1/4 the population size). If followed by a '+',
#			  then both offspring of the 'reprate' selected
#			  parents are introduced into the population (which
#			  requires 'reprate' to be an even number), otherwise
#			  only the fitest of the 2 x 'reprate' are introduced.
# 	xrate		- rate of crossover in offspring (0.0 - 1.0)
# 	mrate		- rate of mutation of bases in offspring (0.0 - 1.0)
# 	irate		- rate of inversion in offspring (0.0 - 1.0)
# 	idrate		- rate of base insert/delete (0.0 - 1.0) in offspring
#
# Inputs: (popfile)
#  			- lines in the form: [fitness '>'] chromosome, in the
#  		  	  optional population file (popfile)
#
# Returns:
# 	  (stdout)	- generation number
# 	  		- statistics (if -s option, verbose stats if -v)
# 	  		  - population fitness stats
# 	  		  - population chromosome length stats (if not -ga)
# 	  		  - population chromosome homology stats (if not -ga)
# 	  (stderr)	- various messages tracking progress (if -v)
# 	  		- operator results (if -v)
# 	  (outfile)	- lines of chromosomes in base 4, with or without
# 	  		  fitness in form: [fitness '>'] chromosome
# 	  (OUTFILEBASENAME_stats.txt)
# 	  		- (optional) stores statistics generated during run
#
# Dscription:
# 	Generate a population of chromosomes of size 'popsize' (or use
# 	existing population in 'popfile'), and evolve these over 'numgens'
# 	generations, with 'reprate' unfit individuals replaced at each
# 	generation with offspring of fit individuals, produced by genetic
# 	operators used according to their assigned probabilities (0.0 - 1.0),
#	to produce a population of chromosomes that are fit according to
# 	'fitscript'.
# 
# Note: genpopn.pl calls several external perl scripts, one of which
# 	(homxover) also calls the C executable lcsstr (longest common
# 	substring), and as such the path, name (and extension) for these
# 	need to be set correctly
#
# NB:	It seems that running (DOS/Win32) Perl on top of cygwin creates
# 	some issues: cygwin environment variables aren't available;
# 	directories are specified in the DOS convention '\'; and
# 	requires DOS "nul" to be used instead of unix "/dev/null".


### set the environment

$PID=$$;

#$STDERR="nul";	# coz Active Perl runs from win
$STDERR=(-e "/dev/null") ? "/dev/null" : "nul"; # assume DOS if not unix
$STDERR_REDIRECT="2>$STDERR";

$CAT="perl concat.pl";

$GENCHROMS="genchroms.pl";
$GENPOPFSTATS="popfstats.pl";
$GENPOPLSTATS="poplstats.pl";
$GENPOPHSTATS="pophstats.pl";
$GENPOPGSTATS="popgstats.pl";
$UPDATEPOPN="updatepopn.pl";
#$FITTEST="fitest.pl -f";

$TMPPOPNFILE="TMPpop$PID";
$TMPPOPNINFO=$TMPPOPNFILE . "_info.txt";

###### end ENVironment declarations


# the following are all modifiable parameters that are unlikely to be
# changed much from one run to the next, so put here instead of needing
# to supply as command line parameters

$MINCS=4;				# minimum length of homology for xover
$IMIN=4;				# minimum inversion length
$IMAX=0.25;				# max inversion 25% of chromosome
$DBIAS=0.5;				# 50% delete bias for base ins/del op


###### end modifiable params to genpopn

$STANDARDGA=0;
$STARTGEN=0;

($VERBOSE,$genstats,$FITOUT,$outfile,$fitscript,$popfile,$popsize,$minlen,$maxlen,$reprate,$numgen,$xrate,$mrate,$irate,$idrate)=scanArgs();


# now have all necessary params - setup strings for executing external scripts
# note that genstats is <=0 for no stats, >0 for generating stats every
# 'genstats' generations - 0 is used to switch off stats when using -v

if ($VERBOSE) {
  $UPDATEPOPN .= " -a";
  $GENPOPFSTATS .= " -v";
  $GENPOPLSTATS .= " -v";
  $GENPOPHSTATS .= " -v";
  $GENPOPGSTATS .= " -v";
}

if ($STANDARDGA) {
  $UPDATEPOPN .= " -ga";
}

# when create file to store stats in, give it the same filename + "_stats"
# and a ".txt" extension
if ($genstats>0) {
  if (($ri=rindex($outfile,"."))>-1) {
    $popstatsfile=substr($outfile,0,$ri) . "_stats.txt";	
  } else {
    $popstatsfile=$outfile . "_stats.txt";
  }
}


if ($STANDARDGA==0) { # use base 4 encoding
  $genpopstr="perl $GENCHROMS $popsize 4 $minlen $maxlen > $TMPPOPNFILE.tmp";
  $updatepopnstr="perl $UPDATEPOPN -gen GENERATION $TMPPOPNFILE.gwf \"$fitscript\" $reprate $MINCS $IMIN $IMAX $xrate $mrate $irate $idrate $DBIAS";
} else { # use base 2 encoding
  $genpopstr="perl $GENCHROMS $popsize 2 $minlen $maxlen > $TMPPOPNFILE.tmp";
  $updatepopnstr="perl $UPDATEPOPN -gen GENERATION $TMPPOPNFILE.gwf \"$fitscript\" $reprate $IMIN $IMAX $xrate $mrate $irate";
#print STDERR "*** GA *** reprate=$reprate IMIN=$IMIN IMAX=$IMAX xrate=$xrate mrate=$mrate irate=$irate\n";
}

if ($popfile ne "") { $genpopstr=""; }
$calcpopfstr="$CAT $TMPPOPNFILE.tmp | perl $fitscript -gen $STARTGEN >> $TMPPOPNFILE.gwf";

$GENPOPFSTATSTR="perl $GENPOPFSTATS $TMPPOPNFILE.gwf"; 
$GENPOPLSTATSTR="perl $GENPOPLSTATS $TMPPOPNFILE.gwf";
$GENPOPHSTATSTR="perl $GENPOPHSTATS $TMPPOPNFILE.gwf";
$GENPOPGSTATSTR="perl $GENPOPGSTATS $TMPPOPNFILE.gwf";


if ($VERBOSE) {
  print STDERR "genpopn using the following script calls:\n";
  print STDERR "genpopstr='$genpopstr'\n";
  print STDERR "calcpopfstr='$calcpopfstr'\n";
  if ($numgens>0) { print STDERR "updatepopnstr='$updatepopnstr'\n"; }
  print STDERR "genpopfstatstr='$GENPOPFSTATSTR'\n";
  if ($STANDARDGA==0) {
    print STDERR "genpoplstatstr='$GENPOPLSTATSTR'\n";
    print STDERR "genpophstatstr='$GENPOPHSTATSTR'\n";
  }
  if ($GENGSTATS==1) {
    print STDERR "genpopgstatstr='$GENPOPGSTATSTR'\n";
  }
  print STDERR "\n";
}


# if using an existing population, copy it to a temporary file, and count
# the population size. the population members with fitness already
# evaluated get put in a separate file, so that they don't unnecessarily
# get re-evaluated

if ($popfile ne "") {
  open(PFILE,"<$popfile") || die "Unable to open population file ($popfile)!\n";
  open(OFILE,">$TMPPOPNFILE.tmp") || die "Unable to create temp popn file ($TMPPOPNFILE.tmp)!\n";
  open(FFILE,">$TMPPOPNFILE.gwf") || die "Unable to create temp popn with fitness file ($TMPPOPNFILE.gwf)!\n";
  while (<PFILE>) {
    chop;
    ($fit,$gene)=split(/>/);
    if ($gene eq "" && $fit ne "") {		# in case no fitness
      $gene=$fit; $fit="";			# just grab gene
      $numchroms++;
      print OFILE $gene, "\n";
    } elsif ($gene ne "" && $fit ne "") {	# got fitness and gene
      $numchroms++;
      print FFILE join(">",$fit,$gene) . "\n";
    }
    # ignore lines w/out genes
  }
  close(FFILE);
  close(OFILE);
  close(PFILE);
}



# if not using a pre-existing population, then generate a population of
# chromosomes (in base 4) of size popsize each with random length between
# minlen & maxlen 

if ($popfile eq "") {
  system($genpopstr) && die "Unable to generate initial population. Exiting..\n";
}



# now calculate initial population's fitnesses

system($calcpopfstr) && die "Unable to calulate fitness of population. Exiting..\n";
unlink("$TMPPOPNFILE.tmp") || die "Unable to remove $TMPPOPNFILE.tmp  Exiting..\n";


if ($VERBOSE) { print STDERR "generated initial population and calculated fitness\n"; }
if ($genstats>0) {

  # delete it, so that only stats from this run
  if (-e $popstatsfile) { unlink ($popstatsfile); }
  if ($VERBOSE) { print STDERR "generated stats will also be stored in $popstatsfile\n"; }
  if ($VERBOSE) { print STDERR "generating stats for initial population...\n\n"; }

  getPopStats($STARTGEN,$popstatsfile);
}


# now evolve the population for the given number of generations

for ($i=$STARTGEN; $i<$STARTGEN+$numgens; ++$i) {
  $ii=$i+1;
if ($VERBOSE) { print STDERR "\nEntering generation $ii at " . localtime() . "..\n"; }
  updateInfoFile($i);
  # substitute the current generation into the updatepopn param
  $updatepopnstrwgen=$updatepopnstr;
  $updatepopnstrwgen=~s/-gen GENERATION/-gen $ii/;
if ($VERBOSE) { print STDERR "updating population with: $updatepopnstrwgen\n"; }
  system($updatepopnstrwgen) && die "Unable to update population at gen $ii. Exiting..\n";
if ($VERBOSE) { print STDERR "\n"; }
  # every genstats gens generate stats: 1 = every, 0 = never
  if ($genstats>0 && !($ii % $genstats)) {
    getPopStats($ii,$popstatsfile);
  }
}

# in case didn't generate stats for this the last generation, do it now
if ($genstats>0 && $numgens>0 && ($ii % $genstats)) {
  getPopStats($ii,$popstatsfile);
}


# output evolved population (with or without fitness) to output file

if ($VERBOSE) { print STDERR "\nevolution complete at " . localtime() . ".. outputting resulting popn to $outfile..\n"; }

open(PFILE,"<$TMPPOPNFILE.gwf") || die "Unable to open $TMPPOPNFILE.gwf\n";
open(OFILE,">$outfile") || die "Unable to create output (resulting population) file ($outfile)!\n";
while (<PFILE>) {
  if ($FITOUT) { print OFILE $_; next; }	# keep fitness values
  chop;
  ($fit,$gene)=split(/>/);
  if ($gene) {					# ignore lines w/out fit>gene
    print OFILE "$gene\n";
  }
}
close(OFILE);
close(PFILE);

if ($VERBOSE) { print STDERR "cleaning up temporary (with fitness) population file..\n"; }
if (-e $TMPPOPNINFO) { unlink($TMPPOPNINFO) || die "Unable to remove $TMPPOPNINFO\n"; }
unlink("$TMPPOPNFILE.gwf") || die "Unable to remove $TMPPOPNFILE.gwf\n";
if ($VERBOSE) { print STDERR "done!!!\n"; }



####################### end main()


sub updateInfoFile {
  my ($lastgen) = @_;

  open(PIFILE,">$TMPPOPNINFO") || die "Unable to create popn checkpoint info file ($TMPPOPNINFO)!\n";
  print PIFILE "evolution checkpoint information:\n";
  print PIFILE "last successfully completed generation was $lastgen\n";
  print PIFILE "$TMPPOPNFILE.gwf is the evaluated & updated population file\n";
  print PIFILE "evolution can safely be continued using this population file..\n";
  close PIFILE;
}


sub getPopStats {
  my ($gen,$statsfile) = @_;
  my ($pfstats,$plstats,$phstats,$pgstats);
  my $currentime=localtime();

  print "\n\nGeneration $gen statistics (generated at $currentime):\n\n";
  $pfstats=`$GENPOPFSTATSTR` || die "Unable to generate population fitness stats. Exiting..\n";
  print "$pfstats\n";
  if ($STANDARDGA==0) {
    $plstats=`$GENPOPLSTATSTR` || die "Unable to generate population length stats. Exiting..\n";
    print "$plstats\n";
    $phstats=`$GENPOPHSTATSTR` || die "Unable to generate population homology stats. Exiting..\n";
    print "$phstats\n";
  }
  if ($GENGSTATS==1) {
    $pgstats=`$GENPOPGSTATSTR` || die "Unable to generate population gene count stats. Exiting..\n";
    print "$pgstats\n";
  }

  if ($statsfile ne "") { 
    open PSFILE,">>$statsfile";
    print PSFILE "\n\nGeneration $gen (completed at $currentime):\n";
    print PSFILE "$pfstats\n";
    if ($STANDARDGA==0) {
      print PSFILE "$plstats\n";
      print PSFILE "$phstats\n";
    }
    if ($GENGSTATS==1) {
      print PSFILE "$pgstats\n";
    }
    close PSFILE;
  }
}


sub scanArgs {

  my ($verbose,$genstats,$fitout,$popfile,$outfile,$fitscript,$popsize,$minlen,$maxlen,$numgen,$reprate,$xrate,$mrate,$irate,$idrate);
  my $argc;
  my $childsurvival="";

  $fitout=0;
  $genstats=-1;
  $GENGSTATS=0;
  $numgens=-1;
  $popsize=0;
  $argc="".@ARGV;

  if ($argc>0 && $ARGV[0] eq "-v") {	# to dump extra info
    $verbose=1;
    $genstats=1;			# but may be overwritten by -s
    shift @ARGV;			# for processing the rest
    --$argc;
  }

  if ($argc>1 && $ARGV[0]=~/^-s/) {
    if ($ARGV[0] eq "-sg") { $GENGSTATS=1; }
    if ($ARGV[1]=~/^([0-9]+)$/) {
      $genstats=$1;
      shift @ARGV;
      shift @ARGV;
      $argc-=2;
    }
  }

  if ($argc>0 && $ARGV[0] eq "-f") {
    $fitout=1;
    shift @ARGV;
    --$argc;
  }

  if ($argc>0 && $ARGV[0] eq "-ga") {
    $STANDARDGA=1;
    $idrate=0;
    shift @ARGV;
    --$argc;
  }

  if ($argc+$STANDARDGA>3 && $ARGV[0] eq "-g") {
    if ($ARGV[1]=~/^([0-9]+)$/) {
      $popsize=$1;
      if ($ARGV[2]=~/^([0-9]+)$/) {
        $minlen=$maxlen=$1;
        if ($STANDARDGA==0 && $ARGV[3]=~/^([0-9]+)$/) {
          $maxlen=$1;
          shift @ARGV;
          --$argc;
	}
      }
    }
    shift @ARGV;
    shift @ARGV;
    shift @ARGV;
    $argc-=3;
  }
  elsif ($argc>1 && !($ARGV[0]=~/^-/)) {
    $popfile=$ARGV[0];
    shift @ARGV;
    $argc--;
  }

  if ($argc>=3 && !($ARGV[0]=~/^-/)) {
    $outfile=$ARGV[0];
    if ($ARGV[1]=~/^["'](.*)["']$/) {		# in case cmdline switches
      $fitscript=$1;				# to script
    } else {
      $fitscript=$ARGV[1];
    }
    if ($ARGV[2]=~/^(\d+)/) {				# an unsigned integer
      if ($ARGV[2]=~/^(\d+)[+](\d+)$/) {		# startgen + numgens
	$STARTGEN=$1;
        $numgens=$2;
      } else {
        $numgens=$1;
      }
      if ($argc>=7 && $ARGV[3]=~/^([0-9]+)([+]?)$/) {
        $reprate=$1;
        $childsurvival=$2;
        if ($ARGV[4]=~/^([0-9]*[.]?[0-9]+)$/) {	# a floating pt number
          $xrate=$1;
          if ($ARGV[5]=~/^([0-9]*[.]?[0-9]+)$/) {
    	    $mrate=$1;
            if ($ARGV[6]=~/^([0-9]*[.]?[0-9]+)$/) {
    	      $irate=$1;
              if ($argc==8 && $STANDARDGA==0
		           && $ARGV[7]=~/^([0-9]*[.]?[0-9]+)$/) {
    	        $idrate=$1;
	      }
	    }
	  }
	}
      }
    }
  }

  if ($numgens!=0 && $idrate eq "") {
    printUsage();
    exit(1);
  }

  if (!$popfile) {
    if ($popsize<4) {
      die("Invalid 'popsize' parameter ($popsize) - must be >= 4!\n");
    }

    if ($reprate>int($popsize/4)) {
      die("Invalid 'reprate', 'popsize' parameter combination ($reprate, $popsize)!\n'popsize' must be >= 4 * 'reprate' for tournament selection and replacement.\n");
    }

    if ($childsurvival eq "+") {
      if ($reprate % 2 != 0) {
        die("The reprate parameter must be an even number if the '+' option is selected\n");
      }
# we don't check reprate again, so restore it for passing to updatepopn.pl
      $reprate .= "+"; 
    }

  }

  if ($numgens>0 && ($xrate>1 || $mrate>1 || $irate>1 || $idrate>1)) {
    die("Operator rate parameters must lie between 0 - 1!\n");
  }


  ($verbose,$genstats,$fitout,$outfile,$fitscript,$popfile,$popsize,$minlen,$maxlen,$reprate,$numgen,$xrate,$mrate,$irate,$idrate);
}



sub printUsage {

  print STDERR "
  Synopsis: [perl] genpopn.pl [-v] [[-s|-sg] ngens] [-f] [-ga] (-g popsize (len | minlen maxlen) | popfile) outfile fitscript [gen+]numgens [reprate[+] xrate mrate irate [idrate]]
 
  Args:
  	-v		- verbose output to STDERR, operator results,
  			  information on removed population members, and
 			  fitness, and length if '-ga' switch not given, stats
 			  every generation
 	-s ngens	- generate fitness stats, and if not a standard GA,
			  length and homology stats every 'ngens' generations
 			  (0 for off - used to turn of stats with verbose mode)
 	-sg		- as above, but also generate, gene count stats
 	-f		- save fitness with evolved chromosomes
  	-ga		- use standard GA on standard binary fixed length chrom
  			  this means that -g takes 'len' not minlen and maxlen,
  			  and the 'idrate' parameter should not be given
  	-g popsize ( len | minlen maxlen)
  			- generate a populate of size 'psize' randomly
  			  chromosomes with lengths between min and max
  			  or all of length 'len' if -ga switch was used
  	popfile		- to use a pre-existing population
  	outfile		- for storing resulting population
  	fitscript	- fitness evaluator that returns lines of:
  			  fitness '>' chromosome
  			  If command line switches are needed for the
  			  fitness script, script and switches should be
  			  enclosed in single or double quotes.
  	[gen+]numgens	- number of generations to evolve population. gen
  			  indicates what generation to start at (default 0)
  			  if numgens=0, the following arguments are optional
  	reprate[+]	- number of members to replace with new (maximum of
  			  1/4 the population size). If followed by a '+',
			  then both offspring of the 'reprate' selected
			  parents are introduced into the population (which
			  requires 'reprate' to be an even number), otherwise
			  only the fitest of the 2 x 'reprate' are introduced.
  	xrate		- rate of crossover in offspring (0.0 - 1.0)
  	mrate		- rate of mutation of bases in offspring (0.0 - 1.0)
  	irate		- rate of inversion in offspring (0.0 - 1.0)
  	idrate		- rate of base insert/delete (0.0 - 1.0) in offspring
 
  Inputs: (popfile)
  			- lines in the form: [fitness '>'] chromosome, in the
  		  	  optional population file (popfile)
 
  Returns:
  	  (stdout)	- generation number
  	  		- statistics (if -s option, verbose stats if -v)
  	  		  - population fitness stats
  	  		  - population chromosome length stats
  	  		  - population chromosome homology stats
  	  (stderr)	- various messages tracking progress (if -v)
  	  		- operator results (if -v)
  	  (outfile)	- lines of chromosomes in base 4, without fitnesses
 	  (OUTFILEBASENAME_stats.txt)
 	  		- (optional) stores statistics generated during run
 
  Dscription:
  	Generate a population of chromosomes of size 'popsize' (or use
  	existing population in 'popfile'), and evolve these over 'numgens'
  	generations, with 'reprate' unfit individuals replaced at each
  	generation with offspring of fit individuals, produced by genetic
  	operators used according to their assigned probabilities (0.0 - 1.0),
	to produce a population of chromosomes that are fit according to
  	'fitscript'.

  ";
}


