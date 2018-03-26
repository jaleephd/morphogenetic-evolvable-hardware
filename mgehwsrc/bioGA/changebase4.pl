
# changebase4.pl by J.A. Lee, March 2003
#
# Synopsis:
# 	[perl] changebase4.pl [dr4][dDrR4]
#
# Args:
#	[dr4]	    convert from alphabet
#			d - DNA		[TCAGtcag]*
#			r - RNA		[UCAGucag]*
#			4 - decimal	[0123]*
#	[dDrR4]    to alphabet
#			d/D, r/R (upper/lowercase DNA, RNA),
#			4 - decimal
#  
#
# Inputs: (stdin)
# 		- chromosomes separated by newlines
#
# Returns: (stdout)
# 		- chromosomes in new alphabet separated by newlines
#
# Description:
#	changebase4 takes chromosomes (from stdin), comprised of bases
#	specified in decimal [0-3], DNA [TCAG], or RNA [UCAG] alphabets,
#	and converts chromosomes to the equivalent in a different base 4
#	alphabet (DNA/RNA/decimal)
#


$argc="".@ARGV;

if ($argc<1 || !($ARGV[0]=~/^[dr4][dDrR4]$/)) {
  printUsage();
  exit(1);
}

$fra=substr($ARGV[0],0,1);
$toa=substr($ARGV[0],1,1);

# read chromosomes in, translate to new alphabet, and print them back out

$lineno=0;

while (<STDIN>) {
  ++$lineno;
  chop;
  print convertAlphabet($_,$fra,$toa), "\n";
}


### end main()




sub convertAlphabet {

  my ($str,$fra,$toa) = @_;
  my ($set1, $set2, $cnt, $unmatched);

  # first convert to lowercase if d or r

  if ($fra eq "d" || $fra eq "r") {
    $str=~tr/UTCAG/utcag/;
  }

  $set1=getSet($fra);
  $set2=getSet($toa);

  $_ = $str;
  $cnt=eval "tr/$set1/$set2/"; # or die $@;	# actual conversion
  $unmatched=length($str)-$cnt;

  if ($unmatched>0) {
    print STDERR "Warning: $unmatched unmatched characters on line $lineno!\n";
  }

  $_;

}


sub getSet {

  my($alph) = @_;
  my $set;

  if ($alph eq "4") {
    $set="0123";
  }
  elsif ($alph eq "D") {
    $set="TCAG";
  }
  elsif ($alph eq "d") {
    $set="tcag";
  }
  elsif ($alph eq "R") {
    $set="UCAG";
  }
  elsif ($alph eq "r") {
    $set="ucag";
  }
  else {
    print STDERR "Invalid alphabet specified in 'getSet()': $alph. exiting ...\n";
    exit(1);
  }

  $set;
}



sub printUsage {
  print STDERR "
  Synopsis:
  	[perl] changebase4.pl [dr4][dDrR4]
 
  Args:
 	[dr4]	    convert from alphabet
 			d - DNA		[TCAGtcag]*
 			r - RNA		[UCAGucag]*
 			4 - decimal	[0123]*
 	[dDrR4]    to alphabet
			d\/D, r\/R (upper\/lowercase DNA, RNA),
 			4 - decimal
 
  Inputs: (stdin)
  		- chromosomes separated by newlines
 
  Returns: (stdout)
  		- chromosomes in new alphabet separated by newlines
 
  Description:
 	changebase4 takes chromosomes (from stdin), comprised of bases
 	specified in decimal [0-3], DNA [TCAG], or RNA [UCAG] alphabets,
	and converts chromosomes to the equivalent in a different base 4
	alphabet (DNA\/RNA\/decimal)

  ";
}


