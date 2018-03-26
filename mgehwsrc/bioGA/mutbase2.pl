
# mutbase2.pl by J.A. Lee, March 2004
#
# Synopsis:
# 	[perl] mutbase2.pl [-a] rate
#
# Args:
# 	-a	- return additional information to STDERR
#	rate	- mutation rate, given as a floating point number between
#		  0 and 1, specifying the probability of a base being mutated.
#
# Inputs: (stdin)
# 		- chromosomes in base 2 (binary), separated by newlines
#
# Returns:
# 	   (stdout)	- mutated chromosomes, separated by newlines
#	   (stderr) 	- tab-delimited string length and mutation count
#
# Description:
#	mutbase2 takes chromosomes (from stdin), comprised of binary digits
#	and generates new chromosomes by randomly mutating digits, with the
#	probability of mutation being given by the commandline argument.
#	Mutations are simply bit flips.


$ADDITIONAL=0;

$argc="".@ARGV;

if ($argc>0 && $ARGV[0] eq "-a") {	# to dump some debug info
  $ADDITIONAL=1;
  shift @ARGV;				# for processing the rest
  --$argc;
}

if ($argc==1) {
  if ($ARGV[0]=~/^([0-9]*[.]?[0-9]+)$/) {
    $mrate=$1;
  }
}

if ($mrate eq "") {
  printUsage();
  exit(1);
}

srand();				# seed random number generator


# read chromosomes in, mutate, and print them back out

while (<STDIN>) {
  chop;
  print mutateStr($_,$mrate), "\n";
}


### end main()


sub mutateStr {

  my ($str,$mutrate) = @_;
  my ($i, $r, $strlen, $mut_cnt);

  $mut_cnt=0;
  $strlen=length($str);
  for ($i=0; $i<$strlen; ++$i) {
    $r=rand();
#print STDERR "mutrate=$mutrate, rand()=$r\n";
    if ($r<$mutrate) {		# mutate according to given probability
      $mut_cnt++;
      $base=substr($str,$i,1);
      substr($str,$i,1)=($base eq "0") ? "1" : "0";	# bit flip
    }
  }

if ($ADDITIONAL) {
  print STDERR "$strlen\t$mut_cnt\n";
}

  $str;
}



sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] mutbase2.pl [-a] mutation_rate
 
  Args:
  	-a	- return additional information to STDERR
 	rate	- mutation rate, given as a floating point number between
 		  0 and 1, specifying the probability of a base being mutated.

  Inputs: (stdin)
  		- chromosomes in base 2 (binary), separated by newlines
 
  Returns:
  	   (stdout)	- mutated chromosomes, separated by newlines
 	   (stderr) 	- tab-delimited string length and mutation count
 
  Description:
 	mutbase2 takes chromosomes (from stdin), comprised of binary digits
 	and generates new chromosomes by randomly mutating digits, with the
 	probability of mutation being given by the commandline argument.
 	Mutations are simply bit flips.

  ";
}


