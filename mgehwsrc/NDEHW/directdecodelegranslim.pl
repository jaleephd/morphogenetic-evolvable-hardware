
# directdecodelegranslim.pl by J.A. Lee, March 2004
#
# Synopsis:
# 	[perl] directdecodelegranslim.pl [ -d ] outfilename outfext
#
#  Args:
#  		-d		- output extra info for debugging to STDERR
#		outfilename	- the basename for the output converted chrom.
#		outfext		- the extension for the output converted chrom
#				  output files will have names of:
#				  	outfilename_CHROMOSOME#.outfext
#
#  Inputs: (stdin)
#  		- lines of: chromosomes in base 2
#
#  Outputs: to files outfilename_CHROMOSOME#.outfext
#  		- for each chromosome a file is created with lines of:
#  		  (first line)		"@,@,@: " details
#  		  (following lines)	"+,+,+: " details
#  		  details is in format:
#		  	resource, attribute, [ setting1, [ setting2, ... ] ]
#		  "@,@,@" indicates place in first (corner) cell
#		  "+,+,+" indicates place in next cell according to some
#		  sequential ordering that fills the evolvable CLB region
#
#  Description:
# 		directdecodelegranslim takes chromosomes (from stdin),
# 		comprised of bases in binary, which indicate the settings for
# 		each logic element (LUT-flip flop pair) in the evolvable
# 		region. Each logic element configuration is specified by its
# 		input line, LUT fn, out bus mux settings, and out bus to single
# 		mux settings, in that order. These take up 4, 2, 2, 4 bits,
# 		respectively. Hence the chromosome is divided into 12 bit
# 		sections, one for each logic element, with excess bits
# 		discarded.


$LECODELEN=12; # require 12 bits to encode a slim-down LE: 4in+2fn,2bus,4out


($DEBUG,$outfname,$outfext)=scanArgs();


# read chromosomes in, decode them and print out the decoded information

$chromno=0;

while (<STDIN>) {			# get next chromosome
  chop;
  if ($_) {
    ++$chromno;
if ($DEBUG) { print STDERR "read chromosome $chromno.. decoding.. "; }
    @decodedLEs=();
    foreach $bits (getLEbits($_)) {	# split into groups of bits for each LE
      push(@decodedLEs,decodeBits($bits)); # decode each LE
    }
    if ($#decodedLEs+1>0) {
      $decodedLEs[0]=~s/^\+,\+,\+/@,@,@/;  # first specifies move to start
    }

if ($DEBUG) { print STDERR "done.\n"; }
if ($DEBUG) { print STDERR "decoded to:\n" . join("\n",@decodedLEs) . "\n"; }
    writeDecodedChromToFile($outfname,$outfext,$chromno,@decodedLEs);
  }
}


### end main()



sub getLEbits {
  my ($chrom) = @_;
  my @bits=();

  while (length($chrom)>=$LECODELEN) {		# will discard trailing bits
    push(@bits,substr($chrom,0,$LECODELEN));	# get leadin group of bits
    $chrom=substr($chrom,$LECODELEN);		# and remove them from chrom
  }

  @bits;
}

# for each cell the settings bits will look like: iiiiffbboooo
# decoded as:
#	iiii	-> input line #
#	ff	-> LUT FN # 			0, pass signal, invert, 1
#	bb	-> OUT selection # =		mux out1=b1, out2=b2
#	oooo	-> OUT to Single line settings	mux line1=o1, line2=o2, ..
#
# returns semicolon-delimited resource,attribute,settings for each of these
sub decodeBits {
  my ($bits) = @_;
  my ($input,$lut,$bus,$out);
  my $decodedLE; 

  $input=decode_input(substr($bits,0,4));
  $lut=decode_lut(substr($bits,4,2));
  $bus=decode_bus(substr($bits,6,2));
  $output=decode_output(substr($bits,8,4));

  $decodedLE="+,+,+:" . join(";",$input,$lut,$bus,$output);
}



# encoded as iiii -> input line # = (i1*8+i2*4+i3*2+i1) % # lines
# each LE needs its input line mapped appropriately for LE granularity
sub decode_input {
  my ($inputbits) = @_;
  my ($nlines,$line0,$line1,$line2,$line3);
  my $bitsval="";
  my $lineno=toDecimal($inputbits);

  my %intable = (
	S0G4 => [ "OFF", "SINGLE_EAST4", "SINGLE_NORTH7", "SINGLE_SOUTH18", "SINGLE_WEST11" ],
	S0F4 => [ "OFF", "SINGLE_EAST19", "SINGLE_NORTH3", "SINGLE_SOUTH9", "SINGLE_WEST3" ],
	S1G1 => [ "OFF", "SINGLE_EAST22", "SINGLE_NORTH10", "SINGLE_SOUTH8" ],
	S1F1 => [ "OFF", "SINGLE_EAST16", "SINGLE_NORTH5", "SINGLE_SOUTH2", "SINGLE_WEST14" ]
	);

  # get the line, wrap around if lineno greater than available lines
  $nlines=$#{$intable{"S0G4"}}+1;
  $line0=$intable{"S0G4"}->[$lineno % $nlines];

  $nlines=$#{$intable{"S0F4"}}+1;
  $line1=$intable{"S0F4"}->[$lineno % $nlines];

  $nlines=$#{$intable{"S1G1"}}+1;
  $line2=$intable{"S1G1"}->[$lineno % $nlines];

  $nlines=$#{$intable{"S1F1"}}+1;
  $line3=$intable{"S1F1"}->[$lineno % $nlines];

  $bitsval="jbits=S0G4.S0G4,S0G4.$line0/S0F4.S0F4,S0F4.$line1/" .
                 "S1G1.S1G1,S1G1.$line2/S1F1.S1F1,S1F1.$line3";
}



# encoded as ff	-> LUT FN #
# when using a singleinput line:
#	F1/G1 only: LUT out = in    = 0101010101010101
#		        or inverted = 1010101010101010
#	F4/G4 only: LUT out = in    = 0000000011111111
#		        or inverted = 1111111100000000
#   	any LUT:    LUT out = 0     = 0000000000000000
#                   	or inverted = 1111111111111111
# so for any LUT there are 4 available FN's: 0, pass input, invert, 1

sub decode_lut {
  my ($lutbits) = @_;
  my ($bits0G,$bits0F,$bits1G,$bits1F,$val0,$val1);
  my $bitsval="";

  $bits0G="LUT.SLICE0_G";
  $bits0F="LUT.SLICE0_F";
  $bits1G="LUT.SLICE1_G";
  $bits1F="LUT.SLICE1_F";

  # note that slice 0 uses line 4, slice 1 uses line 1
  if ($lutbits eq "00") {
    $val0=$val1="0000000000000000";	# LUT out is always 0
  } elsif ($lutbits eq "01") {
    $val0="0000000011111111";
    $val1="0101010101010101";		# pass signal
  } elsif ($lutbits eq "10") {
    $val0="1111111100000000";
    $val1="1010101010101010";		# invert signal
  } else { # $lutbits eq "11"
    $val0=$val1="1111111111111111";	# LUT out is always 1
  }

  $val0=~tr/01/10/;			# need to be inverted to store
  $val1=~tr/01/10/;			# need to be inverted to store

  $bitsval="LUT=$bits0G,$val0/$bits0F,$val0/$bits1G,$val1/$bits1F,$val1";
}



#	bb	-> OUT selection # =		mux out1=b1, out2=b2
# bit 1 sets out0,out2,out4,out3 to OFF or S0_YQ,S0_XQ,S0_YQ,S0_XQ respectively
# bit 2 sets out1,out5,out6,out7 to OFF or S0_YQ,S0_XQ,S0_YQ,S0_XQ respectively

sub decode_bus {
  my ($busbits) = @_;
  my ($bitsval1,$bitsval2);
  my $bitsval="";

  if (substr($busbits,0,1) eq "0") {
    $bitsval1="jbits=OUT0.OUT0,OUT0.OFF/OUT2.OUT2,OUT2.OFF/" .
	            "OUT4.OUT4,OUT4.OFF/OUT3.OUT3,OUT3.OFF";
  } else {
    $bitsval1="jbits=OUT0.OUT0,OUT0.S0_YQ/OUT2.OUT2,OUT2.S0_XQ/" .
	            "OUT4.OUT4,OUT4.S1_YQ/OUT3.OUT3,OUT3.S1_XQ";
  }

  if (substr($busbits,1,1) eq "0") {
    $bitsval2="jbits=OUT1.OUT1,OUT1.OFF/OUT5.OUT5,OUT5.OFF/" .
                    "OUT6.OUT6,OUT6.OFF/OUT7.OUT7,OUT7.OFF";
  } else {
    $bitsval2="jbits=OUT1.OUT1,OUT1.S0_YQ/OUT5.OUT5,OUT5.S0_XQ/" .
                    "OUT6.OUT6,OUT6.S1_YQ/OUT7.OUT7,OUT7.S1_XQ";
  }

  $bitsval="$bitsval1;$bitsval2";
}



# oooo	-> OUT to Single line settings	mux line1=o1, line2=o2, ..
# bit 1     2     3     4
#    0_S3  1_E3  1_N2  1_W4
#    2_N8  2_S5  2_S7  5_W16
#    4_E14 4_W19 6_N18  -
#    3_N9  3_S10 3_E11 7_W22

sub decode_output {
  my ($outputbits) = @_;
  my ($bitsval1,$bitsval2,$bitsval3,$bitsval4,$onoff);
  my $bitsval="";

  $onoff="OutMuxToSingle." . ((substr($outputbits,0,1) eq "0") ? "OFF" : "ON");
  $bitsval1="jbits=OutMuxToSingle.OUT0_TO_SINGLE_SOUTH3,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_NORTH8,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_EAST14,$onoff/" .
                  "OutMuxToSingle.OUT3_TO_SINGLE_NORTH9,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,1,1) eq "0") ? "OFF" : "ON");
  $bitsval2="jbits=OutMuxToSingle.OUT1_TO_SINGLE_EAST3,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_SOUTH5,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_WEST19,$onoff/" .
                  "OutMuxToSingle.OUT3_TO_SINGLE_SOUTH10,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,2,1) eq "0") ? "OFF" : "ON");
  $bitsval3="jbits=OutMuxToSingle.OUT1_TO_SINGLE_NORTH2,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_SOUTH7,$onoff/" .
		  "OutMuxToSingle.OUT6_TO_SINGLE_NORTH18,$onoff/" .
                  "OutMuxToSingle.OUT3_TO_SINGLE_EAST11,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,3,1) eq "0") ? "OFF" : "ON");
  $bitsval4="jbits=OutMuxToSingle.OUT1_TO_SINGLE_WEST4,$onoff/" .
		  "OutMuxToSingle.OUT5_TO_SINGLE_WEST16,$onoff/" .
		  "-/" .
                  "OutMuxToSingle.OUT7_TO_SINGLE_WEST22,$onoff";

  $bitsval="$bitsval1;$bitsval2;$bitsval3;$bitsval4";
}


sub writeDecodedChromToFile {
  my ($outfname,$outfext,$chromno,@decodedLEs) = @_;
  my $decoded=join("\n",@decodedLEs) . "\n";

if ($DEBUG) { print STDERR "writing file '$fname'.. "; }
  # filename will be the supplied name with chromosome num as its suffix
  $fname=sprintf("%s_%d.%s",$outfname,$chromno,$outfext);

  open(OFILE,">$fname") || die ("Unable to create file '$fname'\n");
  print OFILE $decoded;
  close(OFILE);
if ($DEBUG) { print STDERR "done.\n"; }
}
 


sub toDecimal {
  my ($bin) = @_;
  my $dec=0;
  my $mult=2**(length($bin)-1);

  while (length($bin)>0) {
    $dec=$dec + (substr($bin,0,1) * $mult);
    $mult/=2;
    $bin=substr($bin,1);
  }

  $dec;
}



sub printA {                            # for debugging to print arrays
  my ($str,@a) = @_;
  my $i;

  print STDERR "$str: ";
  for ($i=0; $i<$#a+1; ++$i) {
    print STDERR "($a[$i])";
  }
  print STDERR "\n";
}



sub scanArgs {
  my ($argc);
  my ($debug,$outfname,$outfext);

  $argc=$#ARGV+1;
  $debug=0;

  if ($argc>0 && $ARGV[0] eq "-d") { $debug=1; shift @ARGV; $argc--; }

  unless ($argc==2 && !($ARGV[0]=~/^-/)) {
    printUsage();
    exit(1);
  }

  $outfname=$ARGV[0];
  $outfext=$ARGV[1];

  ($debug,$outfname,$outfext);
}



sub printUsage {

  print STDERR <<EOH
  Synopsis:
  [perl] directdecodelegranslim.pl [ -d ] outfilename outfext
 
   Args:
   		-d		- output extra info for debugging to STDERR
 		outfilename	- the basename for the output converted chrom.
 		outfext		- the extension for the output converted chrom
 				  output files will have names of:
 				  	outfilename_CHROMOSOME#.outfext
 
   Inputs: (stdin)
   		- lines of: chromosomes in base 2
 
   Outputs: to files outfilename_CHROMOSOME#.outfext
   		- for each chromosome a file is created with lines of:
  		  (first line)		"@,@,@: " details
  		  (following lines)	"+,+,+: " details
   		  details is in format:
 		  	resource, attribute, [ setting1, [ setting2, ... ] ]
		  "@,@,@" indicates place in first (corner) cell
 		  "+,+,+" indicates place in next cell according to some
 		  sequential ordering that fills the evolvable CLB region
 
   Description:
  		directdecodelegranslim takes chromosomes (from stdin),
  		comprised of bases in binary, which indicate the settings for
  		each logic element (LUT-flip flop pair) in the evolvable
  		region. Each logic element configuration is specified by its
  		input line, LUT fn, out bus mux settings, and out bus to single
  		mux settings, in that order. These take up 4, 2, 2, 4 bits,
  		respectively. Hence the chromosome is divided into 12 bit
  		sections, one for each logic element, with excess bits
  		discarded.

EOH

}



