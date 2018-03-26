
# genchroms.pl by J.A. Lee, March 2003
#
# Synopsis: [perl] genchroms.pl popsize base minlen maxlen
#
# Args:
# 	popsize		- the number of chromosomes to generate
# 	base		- the base of the alphabet (2-10). Base 2 is binary;
# 			  base 4 is 0-3; base 10 is 0-9; etc.
# 	minlen		- minimum length of chromosome
# 	maxlen		- maximum length of chromosome
#
# Inputs: (none)
#
# Outputs: (stdout)	Lines of characters in the specified base (alphabet).
# 			Each line represents a chromosomes in the population.
#
# Dscription:
#	Generate a population of 'popsize' chromosomes, each being comprised
#	of randomly generated bases in the specified 'base' (2-10), and
#	having a random length that lies between 'minlen' and 'maxlen'.
#	The resulting population is output to stdout.

$argc="".@ARGV;

if ($argc<4 || $ARGV[0] eq "--help") {
  printUsage();
  exit(1);
}

if ($ARGV[0]=~/^([0-9]+)$/) {	# an unsigned integer
  $popsize=$1;
  if (($ARGV[1]=~/^([0-9]+)$/) && $1>1 && $1<=10) {
    $base=$1;
    if ($ARGV[2]=~/^([0-9]+)$/) {
      $minlen=$1;
      if ($ARGV[3]=~/^([0-9]+)$/) {
	$maxlen=$1;
      }
    }
  }
}


srand();

for ($i=0; $i<$popsize; $i++) {
  $len=sprintf("%d",rand($maxlen-$minlen+1) + $minlen);
  for ($j=0; $j<$len; $j++) {
    printf("%d",rand($base));
  }
  printf("\n");
}



sub printUsage {

  print STDERR "
  Synopsis: [perl] genchroms.pl popsize base minlen maxlen
 
  Args:
  	popsize		- the number of chromosomes to generate
  	base		- the base of the alphabet (2-10). Base 2 is binary;
  			  base 4 is 0-3; base 10 is 0-9; etc.
  	minlen		- minimum length of chromosome
  	maxlen		- maximum length of chromosome
 
  Inputs: (none)
 
  Outputs: (stdout)	Lines of characters in the specified base (alphabet).
  			Each line represents a chromosomes in the population.
 
  Dscription:
 	Generate a population of 'popsize' chromosomes, each being comprised
 	of randomly generated bases in the specified 'base' (2-10), and
 	having a random length that lies between 'minlen' and 'maxlen'.
 	The resulting population is output to stdout.

  ";

}


