
# fitest.pl by J.A. Lee, April 2003
#
# Synopsis:
# 	[perl] fitest.pl [ -l | -f ] [-gen gen#]
#
# Args:
# 	-l	- output (to STDERR) chromosomes's line number
# 	-f	- output (to STDOUT) fitness and chromosome together
# 	-gen gen# is not used here, but needs to be accepted if passed to it
#
# Inputs: (stdin)
# 		- lines of chromosomes in base 4
#
# Returns: lines of output
# 		- (stderr) (optional) line count: 
# 	    	- (stdout) chromosomes' fitness ( '>' chromosome )
#
# Description:
#	fitest is a simple fitness evaluator for testing purposes, it takes
#	chromosomes (from stdin), comprised of bases specified as [0-3]
#	(equivalent to DNA [TCAG]) and calculates their fitness based on
#	the number of promoter sequences (0202) found in the first 100
#	bases, and the length of the chromosome (up to 100 bases).




$LINECNT=0;
$FULLOUT=0;

$argc="".@ARGV;

if ($argc>0) {
  if ($ARGV[0] eq "-l" && $argc>0) {
    $LINECNT=1;
    $lineno=0;
  }
  elsif ($ARGV[0] eq "-f" && $argc>0) {
    $FULLOUT=1;
  }
  elsif ($ARGV[0] eq "-gen" && $argc>1) {
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
}


### end main()


# calcFitness is used to evaluate the chromosome. the following is a simple
# evaluation of little intrinsic use, but can be used for testing the GA.
# for real problems, the evaluation would likely be done by some external
# process, and this function would just be used to return the fitness that
# would need to be queried from a file, database, or some other device.

sub calcFitness {
  my ($str) = @_;
  my ($fitness, $tatacnt, $chromlen, @ts);
#my $i;

  @ts=($str=~/0202/g);
#foreach $i (@ts) { print STDERR "($i)"; }
  $tatacnt=$#ts+1;
  $chromlen=length($str);
  if ($chromlen>100) { $chromlen=100; }

  # tatacnt is unaffected until chromlen around 70, then rapid drop 80-100
  $fitness = $tatacnt * (1-exp(-0.005*((100-$chromlen)**2)));
}



sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] fitest.pl [ -l | -f ] [-gen gen#]
 
  Args: 
  	-l	- output (to STDERR) chromosomes's line number
  	-f	- output (to STDOUT) fitness and chromosome together
  	-gen gen# is not used here, but needs to be accepted if passed to it

  Inputs: (stdin)
  		- lines of chromosomes in base 4
 
  Returns: lines of output
  		- (stderr) (optional) line count: 
  	    	- (stdout) chromosomes' fitness ( '>' chromosome )
 
  Description:
 	fitest is a simple fitness evaluator for testing purposes, it takes
 	chromosomes (from stdin), comprised of bases specified as [0-3]
 	(equivalent to DNA [TCAG]) and calculates their fitness based on
 	the number of promoter sequences (0202) found in the first 100
 	bases, and the length of the chromosome (up to 100 bases).

  ";
}




