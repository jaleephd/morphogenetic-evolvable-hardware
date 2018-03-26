
$CONVJBITS_FN_PREFIX="conv2jbits_";

$data=<<EOD;
TF,Local,0,332132
SliceIn,BX,4
SliceToOut,4,6
OutToSingleDir,S,3,1
OutToSingleDir,E,0,0
OutToSingleBus,0,6,1
OutToSingleBus,4,1,0
LUTBitFN,F,1000000001101111
LUTBitFN,G,1001001001101111
LUTIncrFN,G,XOR,OR,001-
LUTIncrFN,F,XOR,MJR0,001-
LUTIncrFN,G,XOR,MJR0,-11-
LUTIncrFN,G,XOR,MJR0,1---
SliceRAM,DUAL_OFF,LUT
EOD

@entries=split(/\n/,$data);

foreach $entry (@entries) {
  print "converting: $entry ...\n";
  
  ($res,$attr,@settings)=split(/\s*,\s*/,$entry);

  $convfunction=$CONVJBITS_FN_PREFIX . $res;
  if (!exists($main::{$convfunction})) {
    die("Error: Unable to convert $res - '$convfunction' not found!\n");
  }
  $conv=&{$convfunction}($attr,@settings);

  print "converted: $conv\n\n";
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



################ conv2jbits_RESOURCE functions go after this ############




