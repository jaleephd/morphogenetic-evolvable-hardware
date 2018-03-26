
#
# homxover.pl by J.A. Lee, March 2003
#
# Synopsis:
# 	[perl] homxover.pl [-a] [mincs]
#
# Args:
#	-a    - return additional information to STDERR
# 	mincs - specifies the smallest length of homology required for
# 		a substring to be considered as common, and hence for
#		crossover to take place. (mincs must be > 0)
#
# Inputs: (stdin)
# 		     parent chromosome 1
#	 	     parent chromosome 2
#
# Returns:
# 	  (stdout) - child chromosome 1
# 		     child chromosome 2
#	  (stderr) - tab-delimited line containing: parent 1 length,
#	  	     parent 2 length, longest common substring length,
#	  	     chosen common substring length, child 1 length,
#	  	     and child 2 length.
#
# Description:
#	homxover takes 2 chromosomes (from stdin) and generates 2 new
#	chromosomes using homologous crossover, the 2 parent
#	chomosomes are aligned on a common substring of sufficient
#	length, and then ends are exchanged to give the 2 offspring.
# 
# Note: xover.pl calls lcsstr (longest common substring), and as such
#       requires the LCSSTR variable to be set to the  correct executable
#       name (and extension) 
#
# NB:	It seems that running (DOS/Win32) Perl on top of cygwin creates
# 	some issues: cygwin environment variables aren't available;
# 	directories are specified in the DOS convention '\'; and
# 	requires DOS "nul" to be used instead of unix "/dev/null".


$PID=$$;

$TMPXOVERFILE="TMP_xover$PID.tmp";

#$STDERR="nul";	# coz ActivePerl runs from dos/win
$STDERR=(-e "/dev/null") ? "/dev/null" : "nul"; # assume DOS if not unix
$STDERR_REDIRECT="2>$STDERR";

$CAT="perl concat.pl";
$LCSSTR="lcsstr.exe";

####################



$ADDITIONAL=0;

$mincs=getArg();

# read in the two parent chromosomes

#$parent1="missixissipix\n";		# for testing
#$parent2="mxissisxsip\n";		# for testing
#$STDERR_REDIRECT="";			# for testing

$parent1=<STDIN>;
$parent2=<STDIN>;

$parents=$parent1 . $parent2;	# keeping newlines for passing to lcsstr
chop($parent1);
chop($parent2);


# create a temporary file for storing the chromosomes in
# to get around possible command line string length limitations 

open(TMP,">$TMPXOVERFILE") || die "Unable to create $TMPXOVERFILE\n";
print TMP "$parents";
close(TMP);


$l1=length($parent1);
$l2=length($parent2);
$maxsize=($l1>$l2) ? $l1+1 : $l2+1;

$cmdargs="-a -n $mincs -m $maxsize";

# find a common substring of at least 'mincs' length 
$execlcstr="$CAT $TMPXOVERFILE | $LCSSTR $cmdargs $STDERR_REDIRECT";
$lcsres=`$execlcstr`;
unlink($TMPXOVERFILE) || die "Unable to remove $TMPXOVERFILE\n";

# lcsstr -a gives us LCSSt + rnd + sum, in the following format:
# lcsst: str1-idx	str2-idx	cstr-len
# rcsst: str1-idx	str2-idx	cstr-len
# sumcs: cstr-cnt	cstr-sum

# note that the sumcs stuff is basically useless, as the count includes
# that of all the substrings within longer common substrings, and
# likewise for the sum

($lcsmax,$lcsrnd,$lcssum)=split(/\n/,$lcsres);

unless ($lcsmax=~/^[-]?[0-9]+\s*[-]?[0-9]+\s*([0-9]+)/) {
  die ("Invalid return values from '$execlcstr': $lcsres\n");
}

$lcstrlen=$1;


unless ($lcsrnd=~/^([-]?[0-9]+)\s*([-]?[0-9]+)\s*([0-9]+)/) {
  die ("Invalid return values from '$execlcstr': $lcsres\n");
}

$ci1=$1;
$ci2=$2;
$cl=$3;


if ($cl==0) {		# no common substring of at least mincs length
# print STDERR "No homology sequence >= $mincs - crossover failed!\n";
  if ($ADDITIONAL) {
    print STDERR "$l1\t$l2\t$lcstrlen\t$cl\t0\t0\n"; # the child lengths are 0
  }
  exit(2); # indicate failed to find a sufficient length common substring
}


# recombine the parent chromosomes at the point of LCSSt
# to generate 2 child chromosomes

$child1=substr($parent1,0,$ci1) . substr($parent2,$ci2);
$child2=substr($parent2,0,$ci2) . substr($parent1,$ci1);
$ch1len=length($child1);
$ch2len=length($child2);

# output the generated child chromosomes

if ($ADDITIONAL) {
  print STDERR "$l1\t$l2\t$lcstrlen\t$cl\t$ch1len\t$ch2len\n";
}

print $child1, "\n";
print $child2, "\n";


#### end main()





sub getArg {

  my $mincs=0;

  $argc="".@ARGV;

  #print "argc=$argc, argv[0]=$ARGV[0]\n";
  if ($argc>0 && $ARGV[0] eq "-a") {
    $ADDITIONAL=1;
    shift @ARGV;			# toss the argument
    --$argc;
  }

  if ($argc>0) {			# get mincs arg
    if ($ARGV[0]=~/^([0-9]+)$/) {	# an unsigned integer
      $mincs=$1;
    }
  }
  else {
    $mincs=1;
  }

  if ($mincs<1) {
    printUsage();
    exit(1);
  }

  $mincs;
}


sub printUsage {
  print STDERR "
  Synopsis:
  	[perl] homxover.pl [-a] [mincs]
 
  Args:
 	-a    - return additional information to STDERR
  	mincs - specifies the smallest length of homology required for
  		a substring to be considered as common, and hence for
 		crossover to take place. (mincs must be > 0)
 
  Inputs: (stdin)
  		     parent chromosome 1
 	 	     parent chromosome 2
 
  Returns:
  	  (stdout) - child chromosome 1
  		     child chromosome 2
 	  (stderr) - tab-delimited line containing: parent 1 length,
 	  	     parent 2 length, longest common substring length,
 	  	     chosen common substring length, child 1 length,
 	  	     and child 2 length.
 
  Description:
 	homxover takes 2 chromosomes (from stdin) and generates 2 new
 	chromosomes using homologous crossover. The 2 parent
 	chomosomes are aligned on a common substring of sufficient
 	length, and then ends are exchanged to give the 2 offspring.

  ";
}



#sub dumpInfo {
#
#  my $p1end=length($parent1)-1;
#  my $p2end=length($parent2)-1;
#  my $i1=$ci1-1;
#  my $i2=$ci2-1;
#
#  print STDERR "\n";
## print STDERR "         00000000001111111112222222222333333333344444444445\n";
## print STDERR "         01234567890123456790123456789012345678901234567890\n";
#  print STDERR "parent1: $parent1\nparent2: $parent2\n";
#  print STDERR "child1:  $child1\nchild2:  $child2\n\n";
#  print STDERR "Xover: C1=P1: 0..$i1 P2: $ci2..$p2end\n";
#  print STDERR "Xover: C2=P2: 0..$i2 P1: $ci1..$p1end\n\n";
#  print STDERR "LCSSt command: $LCSSTR $cmdargs\n";
#  print STDERR "LCSSt returned:\n$lcsres\n";
#  print STDERR "  LCSst: i1 i2 len\nrndCSst: i1 i2 len\n>=mincs: cntCSst sumCSst\n";
#
#}



