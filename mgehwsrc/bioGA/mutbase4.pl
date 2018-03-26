
# mutbase4.pl by J.A. Lee, March 2003
#
# Synopsis:
# 	[perl] mutbase4.pl [-a] rate
#
# Args:
# 	-a	- return additional information to STDERR
#	rate	- mutation rate, given as a floating point number between
#		  0 and 1, specifying the probability of a base being mutated.
#
# Inputs: (stdin)
# 		- chromosomes in base 4, separated by newlines
#
# Returns:
# 	   (stdout)	- mutated chromosomes, separated by newlines
#	   (stderr) 	- tab-delimited string length and mutation count
#
# Description:
#	mutbase4 takes chromosomes (from stdin), comprised of bases
#	specified as [0-3] (equivalent to DNA [TCAG]) and generates new
#	chromosomes by randomly mutating bases, with the probability of
#	mutation being given by the commandline argument. Mutations are
#	of two types:
#	    - transversion 0<-->2, 1<-->3 (in DNA: T<-->A, C<-->G); and
#	    - transition   0<-->1, 2<-->3 (in DNA: T<-->C, A<-->G).
#


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
      substr($str,$i,1)=mutateBase(substr($str,$i,1));
    }
  }

if ($ADDITIONAL) {
  print STDERR "$strlen\t$mut_cnt\n";
}

  $str;
}


# Note: mutateBase expects a decimal number in the range 0-3 inclusive

sub mutateBase {

  my ($d) = @_;
  my ($i, @b);

  @b=(int($d/2),$d%2);			# convert to binary

#printf("d=%d, b=%d%d.. mutating to .. ",$d,$b[0],$b[1]);
  $i=int(rand(2));			# choose high or low bit
  $b[$i]=!$b[$i];			# flip that bit
  $d=$b[0]*2+$b[1];			# convert back to base4
#printf("b=%d%d, d=%d .. i=%d\n",$b[0],$b[1],$d,$i);

  $d;
}
  


sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] mutbase4.pl [-a] mutation_rate
 
  Args:
  	-a	- return additional information to STDERR
 	rate	- mutation rate, given as a floating point number between
 		  0 and 1, specifying the probability of a base being mutated.

  Inputs: (stdin)
  		- chromosomes in base 4, separated by newlines
 
  Returns:
  	   (stdout)	- mutated chromosomes, separated by newlines
 	   (stderr) 	- tab-delimited string length and mutation count
 
  Description:
 	mutbase4 takes chromosomes (from stdin), comprised of bases
	specified as [0-3] (equivalent to DNA [TCAG]) and generates new
 	chromosomes by randomly mutating bases, with the probability of
 	mutation being given by the commandline argument. Mutations are
 	of two types:
 	    - transversion 0<-->2, 1<-->3 (in DNA: T<-->A, C<-->G); and
 	    - transition   0<-->1, 2<-->3 (in DNA: T<-->C, A<-->G).

  ";
}


