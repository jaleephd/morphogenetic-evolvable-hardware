
#
# oneptxover.pl by J.A. Lee, March 2004
#
# Synopsis:
# 	[perl] oneptxover.pl [-a]
#
# Args:
#	-a    - return additional information to STDERR
#
# Inputs: (stdin)
# 		     parent chromosome 1
#	 	     parent chromosome 2
#
# Returns:
# 	  (stdout) - child chromosome 1
# 		     child chromosome 2
#	  (stderr) - tab-delimited line containing: parent 1 len,
#	  	     parent 2 len, xover point, child 1 len, child 2 len
#
# Description:
#	oneptxover takes 2 chromosomes (of the same length) and generates
#	2 new chromosomes using one point crossover. This is done by randomly
#	choosing a point on one chromosome, and exhanging the ends of the
#	chromosomes at that point to produce the 2 offspring chromosomes.



####################



$argc="".@ARGV;
if ($argc>0 && $ARGV[0] eq "-a") {
  $ADDITIONAL=1;
  shift @ARGV;			# toss the argument
  --$argc;
} else {
  $ADDITIONAL=0;
}

if ($argc>0) {
  printUsage();
  exit(1);
}


# read in the two parent chromosomes

$parent1=<STDIN>;
$parent2=<STDIN>;

chop($parent1);
chop($parent2);


$l1=length($parent1);
$l2=length($parent2);
$len=($l1>$l2) ? $l2 : $l1;	# limit xover length to within shortest chrom

$xoverpt=int(rand($len));

# recombine the parent chromosomes at the chosen xover point
# to generate 2 child chromosomes

$child1=substr($parent1,0,$xoverpt) . substr($parent2,$xoverpt);
$child2=substr($parent2,0,$xoverpt) . substr($parent1,$xoverpt);
$ch1len=length($child1);
$ch2len=length($child2);

# output the generated child chromosomes

print $child1, "\n";
print $child2, "\n";

if ($ADDITIONAL) {
  print STDERR "$l1\t$l2\t$xoverpt\t$ch1len\t$ch2len\n";
}


#### end main()




sub printUsage {
  print STDERR "
  Synopsis:
  	[perl] oneptxover.pl [-a]
 
  Args:
 	-a    - return additional information to STDERR
 
  Inputs: (stdin)
  		     parent chromosome 1
 	 	     parent chromosome 2
 
  Returns:
  	  (stdout) - child chromosome 1
  		     child chromosome 2
 	  (stderr) - tab-delimited line containing: parent 1 len,
 	  	     parent 2 len, xover point, child 1 len, child 2 len
 
  Description:
 	oneptxover takes 2 chromosomes (of the same length) and generates
 	2 new chromosomes using one point crossover. This is done by randomly
 	choosing a point on one chromosome, and exhanging the ends of the
 	chromosomes at that point to produce the 2 offspring chromosomes.

  ";
}


