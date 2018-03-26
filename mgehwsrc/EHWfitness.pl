
# EHWfitness.pl by J.A. Lee, Feb 2004
#
#   Synopsis:
# 	[perl] EHWfitness.pl [-v] [ -l | -f ] [-gen gen#]
#
# Args:
# 	-v	- output (to STDERR) some extra information
# 	-l	- output (to STDERR) chromosomes's line number
# 	-f	- output (to STDOUT) fitness and chromosome together
#	-gen gen# the current generation, so that this can be stored with
#		  the fittest's details
#
# Inputs: (stdin)
# 		- lines of chromosomes in base 4, or base 2 if standard GA
# 	  (EHW_config.txt)
# 	  	- holds the parameters used by the system
#
# Returns:
#  		lines of output
# 	    	- (stdout) chromosomes' fitness ( '>' chromosome )
# 		- (stderr) ( line count ': ' ) ( extra info '; ' )
#
#		(FITTESTDETS) fitness [@ growth step : seed ; numgenes] 
#		              of best cct
#		(BESTCHROM) chromosome of fittest circuit
#
#  		optional (if SAVEBEST=true in EHW_config.txt)
#		- (BESTBIT) bitstream of fittest circuit
#
# Description:
#	EHWfitness takes chromosomes (from stdin), comprised of bases
#	specified as [0-3] (equivalent to DNA [TCAG]), or [01] (binary) if
#	a standard GA, and calculates their fitness, by constructing and
#	testing the encoded circuit.  Optionally, the bitstream of the
#	fittest circuit may be kept.

######### GLOBALS

$GEPP="perl decodechrom.pl";
$CONV2JBITS="perl resattrset2bitsval.pl";
$NDBUILD="java BuildEhw";
$CONFIGFILE="EHW_config.txt";
# depreceated
#$NDECODE="perl ndecode.pl";
#$ADDSTOPS="TF";

$PID=$$;

######### end GLOBALS


$VERBOSE=0;
$FULLOUT=0;
$LINECNT=0;
$BESTGEN="";
$GEN="";

#printA("ARGV",@ARGV);

while ($#ARGV>-1 && $ARGV[0]=~/^-[vlfg]/) {
  if ($ARGV[0] eq "-v") { $VERBOSE=1; shift; }
  elsif ($ARGV[0] eq "-l") { $LINECNT=1; $lineno=0; shift; }
  elsif ($ARGV[0] eq "-f") { $FULLOUT=1; shift; } 
  else { # ($ARGV[0] eq "-gen")
    shift;
    if ($#ARGV+1>0 && $ARGV[0]=~/^\d+/) {
      $GEN=shift;
    } else {
      die("empty or non-numeric generation # '" . $ARGV[0] . "' supplied with -g switch");
    }
  }
}

#print STDERR "current generation is $GEN\n";

if ($#ARGV>-1) { printUsage(); exit(1); }


# read in EHW_config file - which should contain the following
# constant definitions:
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
  if ($VERBOSE) { eval "print STDERR \" [$const=\$$const] \""; }
}
close(CFILE);
if ($VERBOSE) { print STDERR "\ndone\n"; }

if ($USETFBINDS eq "false") { $GEPP .= " -notfbind"; }

# this is so can use filename with fitness extension for prev best chrom
if ($BESTCHROM=~/(.*)[.][^.]*/) {
  $BESTCHROMFN=$1;			# get rid of file extension
}

# if NUMGROWTHSTEPS is defined and is >0 then indicates morphogenetic approach
if ($NUMGROWTHSTEPS) {
  $MG=1;
# $ND=0; # depreceated
} else {
  $MG=0;
# $ND=1; # depreceated
}

# if DIRECTCHROMDECODE defined then indicates direct encoding on binary chrom
if ($DIRECTCHROMDECODE ne "") {
  $STANDARDGA=1;
} else {
  $STANDARDGA=0;
}

if ($VERBOSE) { $TEST = $TEST . " -v"; }

# depreceated
# need to know how many IO locs there are if use non-developmental approach
#$IOLOCSCNT=0;
#if ($INCLBLES ne "") {
#  (@iolocs)=split(/;/,$INCLBLES);
#  $IOLOCSCNT=$IOLOCSCNT+$#iolocs+1;
#}
#if ($OUTCLBLES ne "") {
#  (@iolocs)=split(/;/,$OUTCLBLES);
#  $IOLOCSCNT=$IOLOCSCNT+$#iolocs+1;
#}

# specify the vertical granularity (CLB/Slice or logic element)
# if necessary.
if ($VERTGRAN==1) {
  $TEST .= " -le";
}

# if we aren't using a 'slim' resource subset, but are using the test class
# 'TestInToOutRecurse' then we need to specify that out bus lines are shared
# by all logic elements within a CLB, as this class defaults to 'slim' model
if ($TEST=~/TestInToOutRecurse/ && !($CONF2BITS=~/slim/)) {
  $TEST .= " -sharedouts";
}

# read chromosomes in, calculate fitness, and print fitness out
if ($VERBOSE) { print STDERR "reading in chromosome(s)...\n"; }

while (<STDIN>) {
  if ($LINECNT) {
    ++$lineno;
    print STDERR "$lineno: ";
  }
  chop;
  if ($_) {
    $chrom=$_;
    # output fitness (and '>' chromosome)
    print calcFitness($chrom);
    if ($FULLOUT) {
      print ">$chrom";
    }
    print "\n";
  }
  print STDERR "\n" if ($LINECNT || $VERBOSE);
}

if ($VERBOSE) { print STDERR "finished...\n"; }

### end main()




sub calcFitness {

  my ($chromosome) = @_;
  my $fitness;
  my ($tchromfile,$dchromfile,$cdchromfile,$buildcmd,$testcmd,$dcodecmd,$delcnt);
  my ($splitcmd,$chr,$nchr,$i);
  my ($fitres,$saveoption,$itsd,$atIter,$seed,$maxfitness,$maxfititer);
  my ($meanbestceilgcnt);
  my (@fitreslines,@chroms);
  my ($c);

  # check the records of the maximum fitness attained so far
  ($maxfitness,$maxfititer)=getMaxFitness();

  # if reached 100% and we want to exit on success,
  # and reached next generation so that successful generation is saved
  # then exit
  if ($maxfitness>=100 && $EXITONSUCCESS eq "true" && $GEN>$BESTGEN) {
    print STDERR "\n=============================================================================\n\n";
    print STDERR "\tExiting on SUCCESS at " . localtime() . "\n";
    print STDERR "\t100% solution found in completed generation $BESTGEN.\n";
    print STDERR "\tExamine $FITTESTDETS and $BESTCHROM for details.\n"; 
    print STDERR "\tLast completed generation is stored in checkpoint popn file...\n"; 
    print STDERR "\n=============================================================================\n\n";
    exit(100);
  }

  if ($EXITONSTAGNATEGENS ne "" && $EXITONSTAGNATEGENS>0 &&
      $GEN>$BESTGEN+$EXITONSTAGNATEGENS) {
    print STDERR "\n-----------------------------------------------------------------------------\n\n";
    print STDERR "\tExiting on STAGNATION at " . localtime() . "\n";
    print STDERR "\t$maxfitness% solution occurred in generation $BESTGEN.\n";
    print STDERR "\tExamine $FITTESTDETS for details.\n"; 
    print STDERR "\tLast completed generation is stored in checkpoint popn file...\n"; 
    print STDERR "\n-----------------------------------------------------------------------------\n\n";
    exit(2);
  }


  $CHROMSECTIONCNT="";		# used to pass info to store in fittest dets

  # save current chromosome to a temp file
  # this is just to help debugging when the system fails, so that the
  # chromosome that the system failed on is available
  $tchromfile="$DATADIR/$PID" . "_tmp_chrom.txt";
  open(TCFILE,">$tchromfile") || die "Unable to create $tchromfile";
  print TCFILE $chromosome . "\n";
  close(TCFILE);

  # decode and convert the chromosome to JBits format
  $dchromfile="$DATADIR/$PID" . "_dc_chrom";
  if ($MG) {
    $dcodecmd="$GEPP $GENECODE $CYTOFILE | $CONV2JBITS $CONF2BITS $dchromfile txt";
    push(@chroms,$chromosome);
  } elsif ($STANDARDGA) {	# direct encoding on fixed len binary chrom
    $dcodecmd="perl $DIRECTCHROMDECODE $dchromfile txt";
    push(@chroms,$chromosome);
  }
  # the following is depreceated
#  else {	# nondevelopmental var len codon based encoding
#    @chroms=alignsplit($chromosome,$SPLITCHROMMARKER,$SPLITCHROMALIGN);
#    # get rid of empty entries as ndecode won't use them, and then the
#    # expected decoded chrom file won't exist, causing an error here
#    @chroms=removeEmpty(@chroms);
#    $CHROMSECTIONCNT=$#chroms+1;
#    if ($VERBOSE) { print STDERR "split chromosome of length ". length($chromosome) ." into " . $CHROMSECTIONCNT . " sections\n"; }
#    if ($#chroms+1>$IOLOCSCNT) {	 # can only use N chrom sections
#      if ($VERBOSE) { print STDERR "discarding extra " . ($#chroms+1-$IOLOCSCNT) . " sections\n"; }
#      @chroms=@chroms[0 .. ($IOLOCSCNT-1)]; # use slice to keep 1st N sections
#    }
#    if ($VERBOSE) { 
#      print STDERR "remaining section lengths are: ";
#      foreach $c (@chroms) { print STDERR "[" . length($c) . "] "; }
#      print STDERR "\n";
#    }
#
#    $dcodecmd="$NDECODE $GENECODE $ADDSTOPS | $CONV2JBITS -n $CONF2BITS $dchromfile txt";
#  }

  $nchr=$#chroms+1;

  if ($VERBOSE) { print STDERR "executing $dcodecmd..\n"; }
  # this will create nchr decoded jbits-converted files, each named as
  # dchromfile_i.txt, where i is the chromosome number
  open(DCHROM,"| $dcodecmd") || die ("failed executing $dcodecmd");
  foreach $chr (@chroms) {
    print DCHROM $chr . "\n";
  }
  close(DCHROM);
  if ($VERBOSE) { print STDERR "done.\n"; }

  # construct circuit from instructions in the decoded chromosome
  # and then evaluate it
  if ($STANDARDGA) {	# standard GA
    $cdchromfile=$dchromfile . "_1.txt"; # it is the only chrom
    $saveoption=($SAVEBEST eq "true") ? "-s $TESTBIT" : "";
    $testcmd="$TEST $saveoption $LAYOUTBIT \"$INIOBS\" \"$OUTIOBS\" \"$INCLBLES\" \"$OUTCLBLES\" \"$MINCLB\" \"$MAXCLB\" $CONTENTIONFILE \"$cdchromfile\"";
    if ($VERBOSE) { print STDERR "executing $testcmd ..\n"; }
    $fitres=`$testcmd`;
  } elsif ($MG) {	# morphogenesis
    $cdchromfile=$dchromfile . "_1.txt"; # it is the only chrom
    if (!-e $cdchromfile) { # there was no genes so no conv decoded chrom file
      #$fitres="0.0 @ 0 : 0 ; 0\n"; # dummy result
      if ($VERBOSE) { print STDERR "no genes, no test required - result automatically 0..\n"; }
      if ($VERBOSE) { print STDERR "cleaning up temp chromosome file ($tchromfile).."; }
      unlink $tchromfile;
      if ($VERBOSE) { print STDERR "done.\n"; }
      return 0;
    } else { # chrom has genes - OK
      $saveoption=($SAVEBEST eq "true") ? "-s $TESTBIT" : "";
      $testcmd="$TEST $saveoption $LAYOUTBIT \"$INIOBS\" \"$OUTIOBS\" \"$INCLBLES\" \"$OUTCLBLES\" \"$MINCLB\" \"$MAXCLB\" $CONTENTIONFILE $cdchromfile $CYTOFILE $NUMTESTITER $NUMGROWTHSTEPS $TRANSCRIBERATE $POLYTOGENE $POLYTHRESHOLD $MGSEED";
      if ($VERBOSE) { print STDERR "executing $testcmd ..\n"; }
      $fitres=`$testcmd`;
    }
  }
  # depreceated
#  else {	# non-developmental move-set instructions
#    $cdchromfile="";
#    for ($i=1; $i<=$nchr; $i++) {
#      if (-e "$dchromfile\_$i.txt") { # just in case section was empty
#        $cdchromfile=$cdchromfile . $dchromfile . "_$i.txt;";
#      }
#    }
#    chop($cdchromfile);	# get rid of trailing semicolon
#    $saveoption=($SAVEBEST eq "true") ? "-s $TESTBIT" : "";
#    $testcmd="$TEST $saveoption $LAYOUTBIT \"$INIOBS\" \"$OUTIOBS\" \"$INCLBLES\" \"$OUTCLBLES\" \"$MINCLB\" \"$MAXCLB\" $CONTENTIONFILE \"$cdchromfile\"";
#    if ($VERBOSE) { print STDERR "executing $testcmd ..\n"; }
#    $fitres=`$testcmd`;
#  }

  # using VirtexDS, we may get some lines to STDOUT (for eg when setting the
  # speed grade) that we don't want, but are unable (as far as I know) to
  # turn off. so we just keep the last line and ignore the rest.
  if ($fitres ne "") {
    @fitreslines=split(/\n/,$fitres);
    $fitres=$fitreslines[$#fitreslines] . "\n";
  }

  if ($VERBOSE) { print STDERR "result=$fitres\n"; }

  # the last line of output from the test should be:
  # 		fitness [" @ " iteration " : " seed [ " ; " gene count ] ]
  if ($MG) {
    if ($fitres=~/^\d+.* @ \d+ : \d+/) {
      chop($fitres);
      ($fitness,$itsd)=split(/\s*@\s*/,$fitres);
      ($atIter,$seed)=split(/\s*:\s*/,$itsd);
      if ($seed=~/;/) {
        ($seed,$GENECNT)=split(/\s*;\s*/,$seed);
      }
    } else {
      # failed to construct circuit.. possibly an intermittant VirtexDS prob
      if ($EXITONCCTFAIL eq "false") {
	$fitness=0;
	$atiter=0;
	$seed=0;
	$GENECNT=0;
        print STDERR "*** Warning at gen $GEN! failed to construct, grow and test circuit ***\n";
	print STDERR "continuing...\n";
      } else {
        die("Unable to construct, grow and test circuit...\n".$fitres);
      }
    }
  } else {
    if ($fitres=~/^\d+/) {
      chop($fitres);
      $fitness=$fitres;
      $atIter="";
      $seed="";
    } else {
      # failed to construct circuit.. possibly an intermittant VirtexDS prob
      if ($EXITONCCTFAIL eq "false") {
	$fitness=0;
        $atIter="";
        $seed="";
        print STDERR "*** Warning at gen $GEN! failed to construct, grow and test circuit ***\n";
	print STDERR "continuing...\n";
      } else {
        die("Unable to test constructed circuit...\n".$fitres);
      }
    }
  }

  if ($VERBOSE) { print STDERR "fitness=$fitness\n"; }

  # work out what the limit is for number of genes
  # if any more than this, then a non-best fitness will be scaled down

  if ($ANTIGENEBLOAT eq "true") {
    if ($BESTGENECNT ne "" && $GENECEILING ne "") {
      $meanbestceilgcnt=int(($BESTGENECNT+$GENECEILING)/2);
      if ($meanbestceilgcnt==0) { $meanbestceilgcnt=1; }
    } elsif ($GENECEILING ne "") {
      $meanbestceilgcnt=$GENECEILING;
    } else {
      $meanbestceilgcnt=$BESTGENECNT;
    }
  }

  # check if we have a new best solution, and if so update details of fittest
  # and save best chromosome & possibly bitstream

  if ($fitness>$maxfitness) {
    updateBest($chromosome,$fitness,$atIter,$seed,$maxfitness,$BESTGEN,$maxfititer,$BESTGENECNT,$PREVBEST);
    $BESTGEN=$GEN;
    $maxfitness=$fitness;
  } elsif ($ANTIGENEBLOAT eq "true" && $fitness <= $maxfitness && $fitness>0 &&
           $GENECNT ne "" && $GENECNT>0 && $BESTGENECNT ne "" &&
           $GENECNT>$meanbestceilgcnt) {
    # scale fitness by ratio of genes to mean of best and ceiling counts
    # to hopefully slow gene bloat
    if ($VERBOSE) { print STDERR "scaling fitness by ratio to avg of best & ceiling's gene cnt: $meanbestceilgcnt/$GENECNT ..\n"; }
    $fitness=$fitness*($meanbestceilgcnt/$GENECNT); 
    if ($VERBOSE) { print STDERR "updated fitness=$fitness\n"; }
  }
  if ($VERBOSE) {
    print STDERR "\ncurrent GEN=$GEN, MAX FITNESS"; 
    if ($BESTGEN ne "") {
      print STDERR "=$maxfitness occurred at generation $BESTGEN";
    } else {
      print STDERR " so far is $maxfitness";
    }
    print STDERR "\n\n";
  }

  # cleanup temp decoded chromosome file(s)
  $cdchromfile=~s/;/ /g;
  if ($VERBOSE) { print STDERR "cleaning up temp converted decoded chromosome file(s) ($cdchromfile).."; }
  $delcnt=unlink split(/ /,$cdchromfile);
  #eval "unlink <$cdchromfile>";
  if ($VERBOSE) { print STDERR " and temp chrom file ($tchromfile).."; }
  $delcnt+=unlink $tchromfile;
  if ($VERBOSE) { print STDERR " deleted $delcnt files. done.\n"; }

  # we need to delete all but the best bitstreams
  if ($SAVEBEST eq "true") { cleanup(); }

  # return the fitness of this circuit
  $fitness;
}



sub getMaxFitness {
  my ($max,$mitsd,$miter,$mseed);
  my ($bestscnt);

  $BESTGEN=$BESTGENECNT="";
  if ($VERBOSE) { print STDERR "reading $FITTESTDETS ..\n"; }
  open(FDFILE,"$FITTESTDETS") || return -1; # coz no fittest yet
  while(<FDFILE>) {
    if (/generation (\d+)/) { $BESTGEN=$1; }
    # depreceated
    #if (/(\d+) sections/) { $bestscnt=$1; }
    if (/(\d+) genes/) { $BESTGENECNT=$1; }
    if (/prevbest: (.*)$/) { $PREVBEST=$1; }
    next if (/^\s*#/ || /^\s*$/);	# skip empty and comment lines
    chop;
    s/\s*//;
    ($max,$mitsd)=split(/\s*@\s*/);	# maxfitness [ @ iter : seed ]
    if ($mitsd ne "") {
      ($miter,$mseed)=split(/\s*:\s*/,$mitsd);	# iter : seed
    }
  }
  close(FDFILE);
  if ($VERBOSE) {
    print STDERR "done. max=$max, at gen=$BESTGEN";
    # depreceated
    #if ($bestscnt) { print STDERR ", with $bestscnt sections"; }
    if ($bestgcnt) { print STDERR ", with $BESTGENECNT genes, atIter='$miter', seed='$mseed'"; }
    print STDERR "\n";
    if ($PREVBEST) { print STDERR "previous best: $PREVBEST\n"; }
  }
 
  ($max,$miter);
}



sub updateBest {
  my ($chromosome,$fitness,$atIter,$seed,$prevbestfit,$prevbestgen,$prevmaxfititer,$prevbestgenecnt,$oldbestdetails) = @_;
  my ($bitfilename);
  my ($oldets);

  # update FITTESTDETS
  if ($VERBOSE) {
    print STDERR "updating $FITTESTDETS ";
    if ($GEN ne "") { print STDERR "at generation $GEN "; }
    print STDERR "fittest=$fitness, atIter='$atIter'..\n";
  }
  open(FDETS,">$FITTESTDETS") || die("Unable to create $FITTESTDETS to store fittest's details");
  if ($MG) { print FDETS "$fitness @ $atIter : $seed\n"; }
  else { print FDETS "$fitness\n"; }
  if ($GENECNT ne "") { print FDETS "# fittest chrom contains $GENECNT genes\n"; }
  # depreceated
  #if ($CHROMSECTIONCNT ne "") { print FDETS "# fittest chrom was split into $CHROMSECTIONCNT sections\n"; }
  if ($GEN ne "") { print FDETS "# fittest occurred at generation $GEN\n"; }
  if ($GEN ne "" && $GEN>0) {
    $oldets="gen $prevbestgen\: $prevbestfit";
    if ($prevmaxfititer ne "") { # morphogenesis
      $oldets.=" @ $prevmaxfititer gcnt=$prevbestgenecnt";
    }
    if ($oldbestdetails ne "") {
      # commented out code is untested and probably unnecessary
      #if ($GEN>$prevbestgen) {
        $oldets="$oldets; $oldbestdetails";
      #} else { # if get more than 1 best per generation, just keep the last
      #  $oldets=$oldbestdetails;
      #}
    }
    print FDETS "# prevbest: $oldets\n";
  }
  close(FDETS);
  if ($VERBOSE) { print STDERR "done.\n"; }

  # if we are maintaining the bitstream for the best circuit evolved so
  # far then we need to replace old (if exists) with this one
  if ($SAVEBEST eq "true") {
  # generated test bitstreams will be test.bit[.iter#]
  # best bitstream should be named best.bit
    $bitfilename=($atIter ne "") ? $TESTBIT.".".$atIter : $TESTBIT;
    if ($VERBOSE) { print STDERR "keeping best bitstream ($bitfilename) .. "; }

    if (-e $BESTBIT) { unlink($BESTBIT); }

    rename($bitfilename,$BESTBIT) || die "Unable to rename test bitstream $bitfilename to $BESTBIT";
    if ($VERBOSE) { print STDERR "done.\n"; }
  }
  # keep previous fittest chrom - we may want to analyse it later
  if (-e $BESTCHROM) {
    rename($BESTCHROM,"$BESTCHROMFN.($prevbestfit).txt");
  }
  if ($VERBOSE) { print STDERR "keeping best chromosome ($BESTCHROM) .. "; }
  open(BFILE,">$BESTCHROM") || die "Unable to create a copy of fittest chromosome to file $BESTCHROM";
  print BFILE $chromosome . "\n";
  close BFILE;
  if ($VERBOSE) { print STDERR "done.\n"; }

}



sub cleanup {
  # remove all the test.bit* files, don't die on fail as may have renamed
  # the fittest to best.bit
  if ($VERBOSE) { print STDERR "deleting old $TESTBIT (and associated) files..\n"; }
  eval "unlink <$TESTBIT*>";
  if ($VERBOSE) { print STDERR "done.\n"; }
}


# depreceated NDEHW code
# this is the core alignsplit.pl, but putting here saves hassles with
# creating more temporary files
sub alignsplit {
  my ($str,$delim,$align) = @_;
  my ($idx,$delimlen,$strlen,$substrlen,@strs);
  my @aligned=();

  $delimlen=length($delim);
  $strlen=length($str);

  @strs=split(/$delim/,$str);
  # this is just in case last delim is unaligned and at end of string
  if (rindex($str,$delim)==$strlen-$delimlen) { push(@strs,""); }

#print STDERR "split str into ". ($#strs+1) . " sections\n";
  $idx=$strlen;
  # work backwards from end of split list
#print STDERR "idx=$idx ";
  while ($#strs+1>0) {
#print STDERR "(".$strs[$#strs].")";
    $substrlen=length($strs[$#strs]);
    # if aligned or only substr left then add to aligned substr's
    if (($idx-$delimlen-$substrlen) % $align == 0 || $#strs+1<1) {
#print STDERR "\n";
      $idx=$idx-$delimlen-$substrlen;
      push(@aligned,pop(@strs));
    } else {				# otherwise merge with prev substr
      $strs[$#strs-1]=join($delim,$strs[$#strs-1],$strs[$#strs]);
      pop(@strs);
    }
#print STDERR " .. idx=$idx ";
  }
#print STDERR "\n";

  if ($strs[0] eq "") { shift(@strs); } # get rid of trailing empty entry
  reverse(@aligned);
}


# get rid of empty strings in list
sub removeEmpty {
  my (@strs) = @_;
  my @nestrs=();

  foreach $s (@strs) { if ($s ne "") { push (@nestrs,$s); } }

  @nestrs;
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

  print STDERR <<EOH;
  Synopsis:
  [perl] EHWfitness.pl [-v] [ -l | -f ] [-gen gen#]
 
  Args:
  	-v	- output (to STDERR) some extra information
  	-l	- output (to STDERR) chromosomes's line number
  	-f	- output (to STDOUT) fitness and chromosome together
 	-gen gen# the current generation, so that this can be stored with
		  the fittest's details
 
  Inputs: (stdin)
 		- lines of chromosomes in base 4, or base 2 if standard GA
  	  (EHW_config.txt)
  	  	- holds the parameters used by the EHW system
 
  Returns:
  		lines of output
  	    	- (stdout) chromosomes' fitness ( '>' chromosome )
  		- (stderr) ( line count ': ' ) ( extra info '; ' )

		($FITTESTDETS) fitness [@ growth step : seed ; numgenes]
		               of best cct
		($BESTCHROM) chromosome of fittest circuit

  		optional (if SAVEBEST=true in EHW_config.txt)
		- ($BESTBIT) bitstream of fittest circuit
 
  Description:
 	EHWfitness takes chromosomes (from stdin), comprised of bases
	specified as [0-3] (equivalent to DNA [TCAG]), or [01] (binary) if
	a standard GA, and calculates their fitness, by constructing and
	testing the encoded circuit.  Optionally, the bitstream of the
	fittest circuit may be kept.
EOH

}




