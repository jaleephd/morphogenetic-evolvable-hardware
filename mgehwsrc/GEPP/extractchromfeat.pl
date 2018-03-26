

# extractchromfeat.pl by J.A. Lee, April 2003
#
# Synopsis:
# 	[perl] extractchromfeat.pl [-f] [chromno] feature
#
# Args:
# 	-f	- output full details, not just chromosome portion sequence
# 	chromno - only extract from the specified chromosome in population
#	feature - extract chromosome portion(s) of this feature type:
#		  junk, gene, promoter, enhancer, repressor, regulator (which
#		  covers both enhancer and repressor), or ? for any 
#
# Inputs: (stdin)
# 		- lines of parsed chromosomes' regions given as white-space
#		  separated: chromosome number, region type, start index,
#		  and chromosome region's sequence.
#
# Outputs: (stdout)
# 		- lines of chromosome sequences from the desired region from
# 		  either, all or just the specified chromosome (in the
# 		  population). If -f option is specified, then output is in
# 		  the same format as input (chromosome number, region type,
#		  region index and sequence)
#
# Description:
# 	extractchromfeat acts as an intermediatary between parsechrom.pl
#	and other scripts (such as decodegene.pl) that process specific
#	regions of a chromosome. It simply extracts the desired region
#	from all chromosomes, or a specified chromosome (identified by
#	position in a population file) and outputs this region of the
#	chromosome(s).



$argc="".@ARGV;
$wantedfeature="";
$FULLOUT=$onlychromno=0;

if ($argc>0 && $ARGV[0] eq "-f") {
  $FULLOUT=1;
  shift @ARGV;
  $argc--;
}

if ($argc>0 && ($ARGV[0]=~/^([0-9]+)$/)) {
  $onlychromno=$1;
  shift @ARGV;
  $argc--;
}

if ($argc>0) {
  $wantedfeature=$ARGV[0];
  shift @ARGV;
  $argc--;
}

if ($argc>0 || !$wantedfeature) {
  printUsage();
  exit(1);
}


#print STDERR "wfeat=$wantedfeature, only=$onlychromno\n";

while (<STDIN>) {
  chop;
  if ($_) {
    $str=$_;
    ($chrno,$feat,$idx,$chrom)=split(/\s+/);
     
#print STDERR "chrno=$chrno, feat=$feat, idx=$idx, chrom=$chrom\n";
    if ((!$onlychromno || $chrno==$onlychromno)
       && (($wantedfeature eq "?")
       || ($feat eq $wantedfeature || ($wantedfeature eq "regulator"
       && ($feat eq "enhancer" || $feat eq "repressor"))))) {
      print (($FULLOUT) ? "$str\n" : "$chrom\n");
    }
  }
}



sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] extractchromfeat.pl [-f] [chromno] feature
 
  Args:
  	-f	- output full details, not just chromosome portion sequence
  	chromno - only extract from the specified chromosome in population
	feature - extract chromosome portion(s) of this feature type:
 		  junk, gene, promoter, enhancer, repressor, regulator (which
		  covers both enhancer and repressor), or ? for any
 
  Inputs: (stdin)
  		- lines of chromosomes' regions given as white-space
		  separated: chromosome number, region type, start index,
		  and chromosome region's sequence.
 
  Outputs: (stdout)
  		- lines of chromosome sequences from the desired region from
  		  either, all or just the specified chromosome (in the
  		  population). If -f option is specified, then output is in
  		  the same format as input (chromosome number, region type,
		  region index and sequence)
 
  Description:
  	extractchromfeat acts as an intermediatary between parsechrom.pl
	and other scripts (such as decodegene.pl) that process specific
	regions of a chromosome. It simply extracts the desired region
	from all chromosomes, or a specified chromosome (identified by
	position in a population file) and outputs this region of the
	chromosome(s).

  ";
}



