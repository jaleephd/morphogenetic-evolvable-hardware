
# inverop.pl by J.A. Lee, March 2003
#
# Synopsis:
# 		[perl] inverop.pl [-a] (min | min%) (max | max%)
#
# Args:
#  		-a	- return additional information to STDERR
#		min	- minimum length of inversion (>0, <= max)
#		min%	- same but specified as percent/100 (0.0-1.0]
#		max	- maximum length of inversion (>0, >= min)
#		max%	- same but specified as percent/100 (0.0-1.0]
#		
#
# Inputs: (stdin)
# 		- chromosomes separated by newlines
#
# Returns:
# 	   (stdout)	- modified chromosomes separated by newlines
#	   (stderr) 	- tab-delimited string length, inversion length,
#	   		  and start index of inversion.
#
# Description:
# 		inverop.pl is an inversion operator that takes chromosomes
# 		(from stdin) and inverts a randomly chosen sub-sequence of
# 		the supplied chromosome. The length of the inversion will
# 		lie between the lengths specified by command line arguments.


srand();				# seed random number generator

$ADDITIONAL=0;
($min,$max,$minp,$maxp)=getArgs();

# read chromosomes in, mutate, and print them back out

$minl=$min;
$maxl=$max;

while (<STDIN>) {
  chop;

  # if min, max are 0, it means use min %, max %
  if (!$min) {
    $minl=int(length($_)*$minp) || 1;
  }
  if (!$max) {
    $maxl=int(length($_)*$maxp) || 1;
  }

  print inverStr($_,$minl,$maxl), "\n";
}


####### end main() #######


sub inverStr {
  my ($str,$min,$max) = @_;
  my ($i1,$i2,$d,$len,$slen);

  $slen=length($str);
  if ($max>$slen) { $max=$slen; }
  if ($min<1) { $min=1; }
  if ($max<$min) { $max=$min; }
#print STDERR "max=$max, min=$min\n";
  $len=int(rand($max-$min+1))+$min;
  $i1=int(rand($slen-$len+1));
  
  # divide string into 0..i1-1, i1..i1+len-1, i1+len..slen-1
  $s1=($i1>0) ? substr($str,0,$i1) : "";
  $s2=reverse(substr($str,$i1,$len));
  $s3=($i1+$len<$slen) ? substr($str,$i1+$len) : "";
  $str= $s1.$s2.$s3;

#print STDERR "inversion: slen=$slen, len=$len, i1=$i1 ($s1) ($s2) ($s3)\n";

  if ($ADDITIONAL) {
    print STDERR "$slen\t$len\t$i1\n";
  }

  $str;
}



sub getArgs {

  my $argc;
  my ($min, $max, $minp, $maxp);

  $min=$max=-1;
  $minp=$maxp=0;

  $argc="".@ARGV;

  if ($argc>0 && $ARGV[0] eq "-a") {
    $ADDITIONAL=1;
    shift @ARGV;
    --$argc;
  }

  if ($argc==2) {
    if ($ARGV[0]=~/^([0-9]*[.]?[0-9]+)$/) {
      $min=$1;
      if ($ARGV[1]=~/^([0-9]*[.]?[0-9]+)$/) {
        $max=$1;
      }
      else {
        $min=-1;			# mark invalid
      }
    } 
  } else {
    printUsageExit();
  }


  if ($min=~/[.]/) {
    $minp=$min;
    $min=0;
    if ($minp==0 || $minp>1) {
      die("Percentage argument ($minp) must be > 0.0 and <= 1.0\n");
    }
  }
  elsif ($min==0) {
    die("Min argument must be greater than 0!\n");
  }
    
  if ($max=~/[.]/) {
    $maxp=$max;
    $max=0;
    if ($maxp==0 || $maxp>1) {
      die("Percentage argument ($maxp) must be > 0.0 and <= 1.0\n");
    }
  }
  elsif ($max==0) {
    die("Max argument must be greater than 0!\n");
  }

# some more checks to see min & max vals are appropriate, although in
# the case of mixed integer & percentages, we have to rely on inverStr()
# to correct this, by setting max to min, and setting min or max<1 to 1 

# if max is not 0 then max is an integer length, so even if minp is used,
# max must be greater
  if ($max && $max<$min) {
    die("Max argument must be greater than Min argument!\n");
  }
  elsif((!$max && !$min) && $maxp<$minp) {
    die("Max argument must be greater than Min argument!\n");
  }

  if ($max<0 || $min<0) {
    printUsageExit();
  }

  ($min, $max, $minp, $maxp);
}



sub printUsageExit {

  print STDERR "
  Synopsis:
  		[perl] inverop.pl [-a] (min | min%) (max | max%)
 
  Args:
   		-a	- return additional information to STDERR
 		min	- minimum length of inversion (>0, <= max)
 		min%	- same but specified as percent/100 (0.0-1.0]
 		max	- maximum length of inversion (>0, >= min)
 		max%	- same but specified as percent/100 (0.0-1.0]
 		
 
  Inputs: (stdin)
  		- chromosomes separated by newlines
 
  Returns:
  	   (stdout)	- modified chromosomes separated by newlines
 	   (stderr) 	- tab-delimited string length, inversion length,
 	   		  and start index of inversion.
 
  Description:
  		inverop.pl is an inversion operator that takes chromosomes
  		(from stdin) and inverts a randomly chosen sub-sequence of
  		the supplied chromosome. The length of the inversion will
  		lie between the lengths specified by command line arguments.

  ";

  exit(1);
}


