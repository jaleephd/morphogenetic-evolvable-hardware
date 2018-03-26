
# insdelbase4.pl by J.A. Lee, April 2003
#
# Synopsis:
# 	[perl] insdelbase4.pl [-a] rate [delbias]
#
# Args:
# 	-a	- return additional information to STDERR
#	rate	- insertion/deletion rate, given as a floating point number
#		  between 0 and 1, specifying the probability of a base
#		  insertion or deletion occuring at any (existing) base.
#	delbias - deletion bias (0-1) gives the probability that the
#		  insertion/deletion is a deletion. The default is 0.5.
#
# Inputs: (stdin)
# 		- chromosomes in base 4, separated by newlines
#
# Returns:
# 	   (stdout)	- mutated chromosomes, separated by newlines
#	   (stderr) 	- tab-delimited original string length, resulting
#	   		  string length, insertion count and deletion count
#
# Description:
#	insdelbase4 takes chromosomes (from stdin), comprised of bases
#	specified as [0-3] (equivalent to DNA [TCAG]) and generates new
#	chromosomes by randomly inserting or deleting bases, with the
#	probability of this occuring being given by the commandline
#	arguments.


$ADDITIONAL=0;

$argc="".@ARGV;

if ($argc>0 && $ARGV[0] eq "-a") {	# to dump some debug info
  $ADDITIONAL=1;
  shift @ARGV;				# for processing the rest
  --$argc;
}

$rate=-1;
$delbias=0.5;

if ($argc>0) {
  if ($ARGV[0]=~/^([0-9]*[.]?[0-9]+)$/) {
    $rate=$1;
  }
  if ($argc>1) {
    if ($ARGV[1]=~/^([0-9]*[.]?[0-9]+)$/) {
      $delbias=$1;
    } else {
      $delbias=-1;
    }
  }
}

if ($rate<0 || $delbias <0 || $rate>1 || $delbias >1) {
  printUsage();
  exit(1);
}

srand();				# seed random number generator


# read chromosomes in, mutate, and print them back out

while (<STDIN>) {
  chop;
  print insdelStr($_,$rate,$delbias), "\n";
}


### end main()



sub insdelStr {

  my ($str,$rate,$dbias) = @_;
  my ($i, $b, $str1len, $inscnt, $delcnt);

  $inscnt=$delcnt=0;
  $str1len=length($str);

# note that insertions are before the current character index
# and deletions are of the character at the current index
# hence incr till get past the end of str to allow adding base to end

  for ($i=0; $i<=length($str); ++$i) {
#print STDERR "i=$i char (", substr($str,$i,1), ")..\n"; 
    if (rand()<$rate) {
      if (rand()<$dbias && $i<length($str)) { # can't del past last char
#print STDERR "deletion at i=$i of char (", substr($str,$i,1), ") from $str..\n"; 
	$delcnt++;
	$str=substr($str,0,$i) . substr($str,$i+1); # delete char at this index
#print STDERR "resulting in: $str\n"; 
        $i--;
      }
      else {
        $inscnt++;
        $b=int(rand(4));
#print STDERR "insertion at i=$i of char ($b) from $str..\n"; 
        $str=substr($str,0,$i) . $b . substr($str,$i); # insert preceding index
#print STDERR "resulting in: $str\n"; 
        $i++;
      }
    }
  }

  if ($ADDITIONAL) {
    print STDERR "$str1len\t", length($str), "\t$inscnt\t$delcnt\n";
  }

  $str;
}



sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] insdelbase4.pl [-a] rate [delbias]
 
  Args:
  	-a	- return additional information to STDERR
 	rate	- insertion/deletion rate, given as a floating point number
 		  between 0 and 1, specifying the probability of a base
 		  insertion or deletion occuring at any (existing) base.
 	delbias - deletion bias (0-1) gives the probability that the
 		  insertion/deletion is a deletion. The default is 0.5.
 
  Inputs: (stdin)
  		- chromosomes in base 4, separated by newlines
 
  Returns:
  	   (stdout)	- mutated chromosomes, separated by newlines
 	   (stderr) 	- tab-delimited original string length, resulting
 	   		  string length, insertion count and deletion count
 
  Description:
 	insdelbase4 takes chromosomes (from stdin), comprised of bases
 	specified as [0-3] (equivalent to DNA [TCAG]) and generates new
 	chromosomes by randomly inserting or deleting bases, with the
 	probability of this occuring being given by the commandline
 	arguments.

  ";
}


