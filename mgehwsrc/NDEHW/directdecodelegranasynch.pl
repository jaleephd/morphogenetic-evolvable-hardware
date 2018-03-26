
# directdecodelegranasynch.pl
# adapted from directdecodetrim.pl by J.A. Lee, Sept 2005
#
# Synopsis:
# 	[perl] directdecodelegranasynch.pl [ -d ] outfilename outfext
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
#  		  (first line)		"@,@,@: " details1;details2;..
#  		  (following lines)	"+,+,+: " details1;details2;..
#  		  details is in format:
#		  	resource, attribute, [ setting1, [ setting2, ... ] ]
#		  "@,@,@" indicates place in first (corner) cell
#		  "+,+,+" indicates place in next cell according to some
#		  sequential ordering that fills the evolvable CLB region
#
#  Description:
# 		directdecodelegranasynch takes chromosomes (from stdin),
# 		comprised of bases in binary, which indicate the settings for
# 		each logic element (LUT, ins, outs & singles) in the evolvable
# 		region. Each logic element configuration is specified by its
#		input lines, LUT fn, async out mux settings & out bus to single
# 		mux settings, in that order. These take up 17, 16, 8, 12 bits,
# 		respectively. Hence the chromosome is divided into 53 bit
# 		sections, one for each logic element, with excess bits
# 		discarded.


$LECODELEN=53;  # require 53 bits to encode a LE: 17in+16fn,2bus,12out


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

# for each cell the settings bits will look like: ii..iiffffbbbbbbbboooooooo
# decoded as:
#	iiii		 -> input1 (with 12 inputs) line #
#	iiii		 -> input2 (with 9 inputs) line #
#	iiii		 -> input3 (with 15 inputs) line #
#	iiiii	 	 -> input4 (with 20 inputs) line #
#	ffffffffffffffff -> LUT FN truth table
#	bbbb		 -> slice OUT a (with 4 inputs+off) mux setting
#	bbbb		 -> slice OUT b (with 4 inputs+off) mux setting
#	oooooooooooo	 -> OUT to Single line settings	 mux ln1=o1, ln2=o2, ..
#
# returns semicolon-delimited resource,attribute,settings for each of these
sub decodeBits {
  my ($bits) = @_;
  my ($input,$lut,$bus,$out);
  my $decodedLE; 

  $input1=decode_input(substr($bits,0,4),1);
  $input2=decode_input(substr($bits,4,4),2);
  $input3=decode_input(substr($bits,8,4),3);
  $input4=decode_input(substr($bits,12,5),4);
  $lut=decode_lut(substr($bits,17,16));
  $bus=decode_bus(substr($bits,33,8));
  $output=decode_output(substr($bits,41,12));

  $decodedLE="+,+,+:" . join(";",$input1,$input2,$input3,$input4,$lut,$bus,$output);
}



# input encoded as iiii.. -> input line # = (..i1*8+i2*4+i3*2+i1) % # lines
# each LE needs its input line mapped appropriately for LE granularity
# note that here we group together all the LUT inputs that have the same
# connecting lines:
# 	1 -> S0G1/S0F1/S1G4/S1F4
# 	2 -> S0G2/S0F2/S1G3/S1F3
# 	3 -> S0G3/S0F3/S1G2/S1F2
# 	4 -> S0G4/S0F4/S1G1/S1F1

sub decode_input {
  my ($inputbits,$inputnum) = @_;
  my ($nlines,$line0,$line1);
  my ($s0in,$s1in,$c0g,$c0f,$c1g,$c1f);
  my $bitsval="";
  my $lineno=toDecimal($inputbits);

  my %intable = (
	1 => [ "OFF", "SINGLE_NORTH15", "SINGLE_SOUTH6", "SINGLE_WEST5", "SINGLE_NORTH19", "SINGLE_SOUTH12", "SINGLE_WEST17", "SINGLE_NORTH22", "SINGLE_SOUTH13", "SINGLE_WEST18", "SINGLE_SOUTH21", "OUT_WEST1" ],
	2 => [ "OFF", "SINGLE_EAST11", "SINGLE_NORTH12", "SINGLE_SOUTH1", "SINGLE_WEST2", "SINGLE_EAST23", "SINGLE_NORTH17", "SINGLE_WEST20", "OUT_WEST0" ],
	3 => [ "OFF", "SINGLE_EAST5", "SINGLE_NORTH13", "SINGLE_SOUTH0", "SINGLE_WEST6", "SINGLE_EAST7", "SINGLE_SOUTH14", "SINGLE_WEST8", "SINGLE_EAST9", "SINGLE_SOUTH20", "SINGLE_WEST15", "SINGLE_EAST10", "SINGLE_WEST23", "SINGLE_EAST21", "OUT_EAST7" ],
	4 => [ "OFF", "SINGLE_EAST4", "SINGLE_NORTH0", "SINGLE_SOUTH2", "SINGLE_WEST3", "SINGLE_EAST16", "SINGLE_NORTH1", "SINGLE_SOUTH8", "SINGLE_WEST11", "SINGLE_EAST17", "SINGLE_NORTH3", "SINGLE_SOUTH9", "SINGLE_WEST14", "SINGLE_EAST19", "SINGLE_NORTH5", "SINGLE_SOUTH18", "SINGLE_EAST22", "SINGLE_NORTH7", "SINGLE_NORTH10", "OUT_EAST6" ]
	);

  $s0in=$inputnum;		#   S0F/G1-4
  $s1in=5-$inputnum;		# = S1F/G4-1

  # get the line, wrap around if lineno greater than available lines
  $nlines=$#{$intable{$inputnum}}+1;
  $line=$intable{$inputnum}->[$lineno % $nlines];

  $c0g="S0G" . $s0in;
  $c0f="S0F" . $s0in;
  $c1g="S1G" . $s1in;
  $c1f="S1F" . $s1in;
  $bitsval="jbits=$c0g.$c0g,$c0g.$line/$c0f.$c0f,$c0f.$line/" .
                 "$c1g.$c1g,$c1g.$line/$c1f.$c1f,$c1f.$line";

}



# encoded as ffffffffffffffff -> LUT FN
# for any LUT there are 2^16 available FN's

sub decode_lut {
  my ($lutbits) = @_;
  my ($bits0G,$bits0F,$bits1G,$bits1F,$val);
  my $bitsval="";

  $bits0G="LUT.SLICE0_G";
  $bits0F="LUT.SLICE0_F";
  $bits1G="LUT.SLICE1_G";
  $bits1F="LUT.SLICE1_F";

  $val=$lutbits;
  $val=~tr/01/10/;			# need to be inverted to store

  $bitsval="LUT=$bits0G,$val/$bits0F,$val/$bits1G,$val/$bits1F,$val";
}



# bbbbb	-> OUT selection # 0-15 = 5xsliceout + 1 extra OFF
# bits 0-3 sets out0,out2,out4,out3 to OFF,S0_Y,S0_X,S0_Y,S0_X
# bits 4-7 sets out1,out5,out6,out7 to OFF,S0_Y,S0_X,S0_Y,S0_X

sub decode_bus {
  my ($busbits) = @_;
  my ($bitsval0,$bitsval1);
  my $bitsval="";

  my @sliceouttable = ("OFF", "S0_Y", "S0_X", "S1_Y", "S1_X");

  my $select1=$sliceouttable[toDecimal(substr($bits,0,4)) % 5];
  my $select2=$sliceouttable[toDecimal(substr($bits,5,4)) % 5];

  $bitsval1="jbits=OUT0.OUT0,OUT0.$select1/OUT2.OUT2,OUT2.$select1/" .
                  "OUT4.OUT4,OUT4.$select1/OUT3.OUT3,OUT3.$select1";

  $bitsval2="jbits=OUT1.OUT1,OUT1.$select2/OUT5.OUT5,OUT5.$select2/" .
                  "OUT6.OUT6,OUT6.$select2/OUT7.OUT7,OUT7.$select2";

  $bitsval="$bitsval1;$bitsval2";
}



# oooooooooooo -> OUT to Single line settings	mux line1=o1, line2=o2, ..
# bit 1     2     3     4     5     6     7     8     9     10    11    12
#    0_E2  0_N0  0_N1  0_S1  0_S3  0_W7  1_E3  1_E5  1_N2  1_S0  1_W4  1_W5
#    2_E6  2_N6  2_N8  2_S5  2_S7  2_W9  5_E15 5_E17 5_N14 5_S12 5_W16 5_W17
#    4_E14 4_N12 4_N13 4_S13 4_S15 4_W19 6_E18 6_N18 6_N20 6_S17 6_S19 6_W21
#    3_E8  3_E11 3_N9  3_S10 3_W10 3_W11 7_E20 7_E23 7_N21 7_S22 7_W22 7_W23

sub decode_output {
  my ($outputbits) = @_;
  my ($bitsval1,$bitsval2,$bitsval3,$bitsval4,
      $bitsval5,$bitsval6,$bitsval7,$bitsval8,
      $bitsval9,$bitsval10,$bitsval11,$bitsval12,$onoff);
  my $bitsval="";

  $onoff="OutMuxToSingle." . ((substr($outputbits,0,1) eq "0") ? "OFF" : "ON");
  $bitsval1="jbits=OutMuxToSingle.OUT0_TO_SINGLE_EAST2,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_EAST6,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_EAST14,$onoff/" .
                  "OutMuxToSingle.OUT3_TO_SINGLE_EAST8,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,1,1) eq "0") ? "OFF" : "ON");
  $bitsval2="jbits=OutMuxToSingle.OUT0_TO_SINGLE_NORTH0,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_NORTH6,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_NORTH12,$onoff/" .
                  "OutMuxToSingle.OUT3_TO_SINGLE_EAST11,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,2,1) eq "0") ? "OFF" : "ON");
  $bitsval3="jbits=OutMuxToSingle.OUT0_TO_SINGLE_NORTH1,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_NORTH8,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_NORTH13,$onoff/" .
                  "OutMuxToSingle.OUT3_TO_SINGLE_NORTH9,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,3,1) eq "0") ? "OFF" : "ON");
  $bitsval4="jbits=OutMuxToSingle.OUT0_TO_SINGLE_SOUTH1,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_SOUTH5,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_SOUTH13,$onoff/" .
                  "OutMuxToSingle.OUT3_TO_SINGLE_SOUTH10,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,4,1) eq "0") ? "OFF" : "ON");
  $bitsval5="jbits=OutMuxToSingle.OUT0_TO_SINGLE_SOUTH3,$onoff/" .
		  "OutMuxToSingle.OUT2_TO_SINGLE_SOUTH7,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_SOUTH15,$onoff/" .
		  "OutMuxToSingle.OUT3_TO_SINGLE_WEST10,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,5,1) eq "0") ? "OFF" : "ON");
  $bitsval6="jbits=OutMuxToSingle.OUT0_TO_SINGLE_WEST7,$onoff/" .
                  "OutMuxToSingle.OUT2_TO_SINGLE_WEST9,$onoff/" .
		  "OutMuxToSingle.OUT4_TO_SINGLE_WEST19,$onoff/" .
		  "OutMuxToSingle.OUT3_TO_SINGLE_WEST11,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,6,1) eq "0") ? "OFF" : "ON");
  $bitsval7="jbits=OutMuxToSingle.OUT1_TO_SINGLE_EAST3,$onoff/" .
                  "OutMuxToSingle.OUT5_TO_SINGLE_EAST15,$onoff/" .
                  "OutMuxToSingle.OUT6_TO_SINGLE_EAST18,$onoff/" .
		  "OutMuxToSingle.OUT7_TO_SINGLE_EAST20,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,7,1) eq "0") ? "OFF" : "ON");
  $bitsval8="jbits=OutMuxToSingle.OUT1_TO_SINGLE_EAST5,$onoff/" .
                  "OutMuxToSingle.OUT5_TO_SINGLE_EAST17,$onoff/" .
                  "OutMuxToSingle.OUT6_TO_SINGLE_NORTH18,$onoff/" .
		  "OutMuxToSingle.OUT7_TO_SINGLE_EAST23,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,8,1) eq "0") ? "OFF" : "ON");
  $bitsval9="jbits=OutMuxToSingle.OUT1_TO_SINGLE_NORTH2,$onoff/" .
                  "OutMuxToSingle.OUT5_TO_SINGLE_NORTH14,$onoff/" .
		  "OutMuxToSingle.OUT6_TO_SINGLE_NORTH20,$onoff/" .
		  "OutMuxToSingle.OUT7_TO_SINGLE_NORTH21,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,9,1) eq "0") ? "OFF" : "ON");
  $bitsval10="jbits=OutMuxToSingle.OUT1_TO_SINGLE_SOUTH0,$onoff/" .
                  "OutMuxToSingle.OUT5_TO_SINGLE_SOUTH12,$onoff/" .
		  "OutMuxToSingle.OUT6_TO_SINGLE_SOUTH17,$onoff/" .
		  "OutMuxToSingle.OUT7_TO_SINGLE_SOUTH22,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,10,1) eq "0") ? "OFF" : "ON");
  $bitsval11="jbits=OutMuxToSingle.OUT1_TO_SINGLE_WEST4,$onoff/" .
                  "OutMuxToSingle.OUT5_TO_SINGLE_WEST16,$onoff/" .
		  "OutMuxToSingle.OUT6_TO_SINGLE_SOUTH19,$onoff/" .
		  "OutMuxToSingle.OUT7_TO_SINGLE_WEST22,$onoff";

  $onoff="OutMuxToSingle." . ((substr($outputbits,11,1) eq "0") ? "OFF" : "ON");
  $bitsval12="jbits=OutMuxToSingle.OUT1_TO_SINGLE_WEST5,$onoff/" .
                  "OutMuxToSingle.OUT5_TO_SINGLE_WEST17,$onoff/" .
		  "OutMuxToSingle.OUT6_TO_SINGLE_WEST21,$onoff/" .
		  "OutMuxToSingle.OUT7_TO_SINGLE_WEST23,$onoff";

  $bitsval="$bitsval1;$bitsval2;$bitsval3;$bitsval4;$bitsval5;$bitsval6;$bitsval7;$bitsval8;$bitsval9;$bitsval10;$bitsval11;$bitsval12";
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
  [perl] directdecodelegranasynch.pl [ -d ] outfilename outfext
 
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
  		  (first line)		"@,@,@: " details1;details2;..
  		  (following lines)	"+,+,+: " details1;details2;..
   		  details is in format:
 		  	resource, attribute, [ setting1, [ setting2, ... ] ]
		  "@,@,@" indicates place in first (corner) cell
 		  "+,+,+" indicates place in next cell according to some
 		  sequential ordering that fills the evolvable CLB region
 
   Description:
  		directdecodelegranasynch takes chromosomes (from stdin),
  		comprised of bases in binary, which indicate the settings for
 		each logic element (LUT, ins, outs & singles) in the evolvable
 		region. Each logic element configuration is specified by its
		input lines, LUT fn, async out mux settings & out bus to single
 		mux settings, in that order. These take up 17, 16, 8, 12 bits,
 		respectively. Hence the chromosome is divided into 53 bit
  		sections, one for each logic element, with excess bits
  		discarded.

EOH

}



