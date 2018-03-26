
# fitest2.pl by J.A. Lee, March 2004
#
# Synopsis:
# 	[perl] fitest2.pl [ -l | -f ] [-gen gen#]
#
# Args:
# 	-l	- output (to STDERR) chromosomes's line number
# 	-f	- output (to STDOUT) fitness and chromosome together
# 	-gen gen# is not used here, but needs to be accepted if passed to it
#
# Inputs: (stdin)
# 		- lines of chromosomes in base 2
#
# Returns: lines of output
# 		- (stderr) (optional) line count: 
# 	    	- (stdout) chromosomes' fitness ( '>' chromosome )
#
# Description:
#	fitest2 is a simple fitness evaluator for testing purposes, it takes
#	chromosomes (from stdin), comprised of binary digits and calculates
#	their fitness based on the longest sequence of alternating digits
#	(ie ..01010..) as a fraction of the length of the chromosome.




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
  my ($fitness, $oscilen, $chromlen);

  # my ($o,@osc);
  # the following matches more than we want (when storing matches in array)
  # but works correctly in while loop below
  # @osc=($str=~/(1?(01)+0?|10)/g);
  # the following uses 'Independent subexpressions', perl 5.6.0 (experimental)
  # whereby sub expr has higher priority than total expression matches.
  # it doesn't work with the while loop below, however
  # @osc=($str=~/(?>01)+|(?>10)+/g); 
  # foreach $o (@osc) { print STDERR "<$o>"; }

  $oscilen=0;
  while ($str=~/(1?(01)+0?|10)/g) {
    if (length($1)>$oscilen) { $oscilen=length($1); }
#print STDERR "($1)";
  }

  $chromlen=length($str);
#print STDERR " .. longest oscillating sequence=$oscilen. fitness=";
  $fitness = ($chromlen>0) ? $oscilen/$chromlen : 0;
}



sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] fitest2.pl [ -l | -f ] [-gen gen#]
 
  Args: 
  	-l	- output (to STDERR) chromosomes's line number
  	-f	- output (to STDOUT) fitness and chromosome together
  	-gen gen# is not used here, but needs to be accepted if passed to it

  Inputs: (stdin)
  		- lines of chromosomes in base 2
 
  Returns: lines of output
  		- (stderr) (optional) line count: 
  	    	- (stdout) chromosomes' fitness ( '>' chromosome )
 
  Description:
 	fitest2 is a simple fitness evaluator for testing purposes, it takes
 	chromosomes (from stdin), comprised of binary digits and calculates
 	their fitness based on the longest sequence of alternating digits
 	(ie ..01010..) as a fraction of the length of the chromosome.

  ";
}




