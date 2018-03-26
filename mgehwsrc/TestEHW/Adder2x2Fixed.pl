
# Adder2x2Fixed.pl by J.A. Lee, May 2005
#
#  Synopsis:
#  [perl] Adder2x2Fixed.pl (-mux | -6200 | -lut) java javaoptions testclass paramlist
# 
#  Args:
#		-mux		uses fixed routing (muxes), only LUTs can
#				be modified
#		-6200		emulates a 6200-like logic element architecture
#				with fixed (output) routing, and only LUTs
#				function and input muxes can be modified.
#		-lut		uses fixed LUT (functions), only muxes can
#				be modified
#		java		the call to the java interpreter
#		javaoptions	and its command line options
#		testclass	the java class to construct and test cct
#				based on modified chromosome
#		paramlist	the parameters for the java program
#
#  Inputs: (none)
#
#  Outputs: (stdout)
#  		- the output from the test class
#
# Description:
# 		This simply sits between the GA and the test class, and
#		removes any entries from the decoded converted chromosome
#		before passing it on to the test class.


$STDERR=(-e "/dev/null") ? "/dev/null" : "nul"; # assume DOS if not unix
$STDERR_REDIRECT="2>$STDERR";

$CAT="perl concat.pl";



$argc=$#ARGV+1;
$javaoptions="";
$VERBOSE=0;

# here expect at least: -mux/6200/lut java TestFnInToOut ..
if ($argc>3 && ($ARGV[0]=~/-mux/ || $ARGV[0]=~/-6200/ || $ARGV[0]=~/-lut/)) {
  $fixedparam=shift;
  shift;		# "java"
  # now get all the java options if any
  while ($ARGV[0]=~/^-/) {
    if ($javaoptions eq "") {
      $javaoptions = shift;		# no separating space
    } else {
      $javaoptions = $javaoptions . " " . shift;
    }
    $argc--;
  }
  $testclass=shift;
  $argc-=2;
} else {
  printUsage();
  exit(1);
}

# the test class and its parameters are given below
#
# TestFnInToOut [-v | -d] [-le] [-sil] [-norecurse] [-activelutfnsonly] [-synchonly] [-maxdelay D] [-incrhamming | -consechamming] [ -inout | -onebitadder | -twobitadder | -fnseq iii.. ooo.. ] [ -s outbit | -sr outbit ] [-load clbSettingsRecord.txt] XCV50CLK1.bit inIOB outIOB inCLBslice outCLBslice minCLB maxCLB contentionFile [ dchromfilelist | dchromfile cytofile numTestIter nGrowthStepsPerTest [ transcriptionRate npoly2gene polyThreshold ] [ seed ] ]

# extract the dchromfile parameter and index
for ($i=0; $i<$argc; $i++) {
  if ($ARGV[$i]=~/\.bit$/ && $i>1 && ($ARGV[$i-1] ne "-s" || $ARGV[$i] ne "-sr")) {
    # found the XCV50CLK1.bit param, which is the first non-optional param
    if ($argc<$i+8) { printUsage(); exit(1); } # not enough parameters
    $dc_chrom_idx=$i+8;
    $dc_chrom=$ARGV[$i+8];
    last;
  }
}

# create new name for temp fixed decoded chrom file
$exti=rindex($dc_chrom,".");
if ($exti>=0) {
  # chrom is probably in format datadir/PID_dc_chrom_CNO.txt
  $dcf_chrom=substr($dc_chrom,0,$exti) . "_fxd.txt";
} else {
  # unknown format
  $dcf_chrom=$dc_chrom . "_fxd.txt";
}

# insert temp fixed decoded chrom filename back for java params
$ARGV[$dc_chrom_idx]=$dcf_chrom;


# check for a verbose or debug flag
for ($i=0; $i<$argc; $i++) {
  # check if have run out of optional parameters (XCV50CLK1.bit is 1st non opt)
  last if ($ARGV[$i]=~/\.bit$/ && $i>1 && ($ARGV[$i-1] ne "-s" || $ARGV[$i] ne "-sr"));
  if ($ARGV[$i] eq "-v" || $ARGV[$i] eq "-d") {
    $VERBOSE=1; last;
  }
}


# now remove from the chrom's gene coding regions all resources that are fixed
# and write 'fix'ed chromosome to new chrom file

$fixrescmd="$CAT $dc_chrom | perl fixedres.pl $fixedparam > $dcf_chrom";
if ($VERBOSE) { print STDERR "'fix'ing chromosome with: $fixrescmd ..\n"; }
system($fixrescmd) && die("Failed to 'fix' dc chrom with \"$fixrescmd\"");

# now call the test class with all its parameters, and the modified chrom
$testcmd="java $javaoptions $testclass " . join(" ",@ARGV);
if ($VERBOSE) { print STDERR "executing test class with: $testcmd ..\n"; }
system($testcmd) && die("Failed with 'fix'ed dc chrom when running \"$testcmd\"");

if ($VERBOSE) { print STDERR "cleaning up temp 'fixed' chromosome file ($dcf_chrom).."; }
unlink $dcf_chrom;
if ($VERBOSE) { print STDERR "done.\n"; }



sub printUsage {

  print STDERR <<EOH;
   Synopsis:
   [perl] Adder2x2Fixed.pl (-mux | -6200 | -lut) java javaoptions testclass paramlist

   Args:
 		-mux		uses fixed muxes (routing), only LUTs can
 				be modified
		-6200		emulates a 6200-like logic element architecture
				with fixed (output) routing, and only LUTs
				function and input muxes can be modified.
 		-lut		uses fixed LUT (functions), only muxes can
 				be modified
 		java		the call to the java interpreter
 		javaoptions	and its command line options
 		testclass	the java class to construct and test cct
 				based on modified chromosome
 		paramlist	the parameters for the java program
 
   Inputs: (none)
 
   Outputs: (stdout)
   		- the output from the test class
 
  Description:
  		This simply sits between the GA and the test class, and
 		removes any entries from the decoded converted chromosome
 		before passing it on to the test class.

EOH
}

