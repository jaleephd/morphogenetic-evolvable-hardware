
# dummyFitness.pl by J.A. Lee, Feb 2004
#
#   Synopsis:
# 	[perl] dummyFitness.pl [-v] [ -l | -f ] [-gen gen#]
#
# Args:
# 	-v	- output (to STDERR) some extra information
# 	-l	- output (to STDERR) chromosomes's line number
# 	-f	- output (to STDOUT) fitness and chromosome together
# 	-gen gen# is not used here, but needs to be accepted if passed to it
#
# Inputs: (stdin)
# 		- lines of chromosomes in base 4
#
# Returns: lines of output
# 	    	- (stdout) chromosomes' fitness ( '>' chromosome )
# 		- (stderr) ( line count ': ' ) ( extra info '; ' )
#
# Description:
#	dummyFitness takes chromosomes (from stdin), comprised of bases
#	specified as [0-3] (equivalent to DNA [TCAG]) and assigns them all
#	fitness values of 0. This is just for using with genpopn.pl when
#	generating an initial random population but not yet evolving it.




$LINECNT=0;
$FULLOUT=0;
$VERBOSE=0;

$argc="".@ARGV;

if ($argc>0 && $ARGV[0] eq "-v") {
  $VERBOSE=1;
  shift @ARGV;
  --$argc;
}

if ($argc>0 && $ARGV[0] eq "-l") {
  $LINECNT=1;
  $lineno=0;
  shift @ARGV;
  --$argc;
} elsif ($argc>0 && $ARGV[0] eq "-f") {
  $FULLOUT=1;
  shift @ARGV;
  --$argc;
}

if ($argc>0) {
  if ($ARGV[0] eq "-gen" && $argc>1) {
    # just ignore it
  } else {
    printUsage();
    exit(1);
  }
}

# read chromosomes in, calculate fitness, and print fitness out


while (<STDIN>) {
  if ($LINECNT) {
    ++$lineno;
    print STDERR "$lineno: ";
  }
  chop;
  if ($_) {
    print calcFitness($_);
    if ($FULLOUT) {
      print ">$_";
    }
    print "\n";
  }
  print STDERR "\n" if ($LINECNT || $VERBOSE);
}


### end main()




sub calcFitness {

  my ($chromosome) = @_;
  my $fitness=0;

  $fitness;
}




sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] dummyFitness.pl [-v] [ -l | -f ] [-gen gen#]
 
  Args: 
  	-v	- output (to STDERR) some extra information
  	-l	- output (to STDERR) chromosomes's line number
  	-f	- output (to STDOUT) fitness and chromosome together
  	-gen gen# is not used here, but needs to be accepted if passed to it

  Inputs: (stdin)
  		- lines of chromosomes in base 4
 
  Returns: lines of output
  	    	- (stdout) chromosomes' fitness ( '>' chromosome )
  		- (stderr) ( line count ': ' ) ( extra info '; ' )
 
  Description:
	dummyFitness takes chromosomes (from stdin), comprised of bases
	specified as [0-3] (equivalent to DNA [TCAG]) and assigns them all
	fitness values of 0. This is just for using with genpopn.pl when
	generating an initial random population but not yet evolving it.

  ";
}




