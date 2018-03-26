
# dchromstats.pl by J.A. Lee, Feb 2004
#
#  Synopsis:
#  [perl] dchromstats.pl [-v]
# 
#  Args:
#		-v	verbose mode. labels the output information
#
#  Inputs: (stdin)
#  		- lines of output from decodechrom.pl:
#			chromno: geneno,gstart: feat,+/-offset,length: details
#		    or further processed (converted) by resattrset2bitsval.pl:
#			geneno,gstart: feat,+/-offset,length: details
#		  - chromno identifies which chromosome according to its
#		    occurence in the input.
#		  - geneno gives the number of the gene starting with 1 at
#		    the 5' end of the chromosome
#		  - gstart gives the start location of the gene's coding
#		    region on the chromosome
#		  - feat is one of "gene", "prom", "enhb", "repb"; which
#		    indicate, respectively, gene coding region, promoter,
#		    enhancer binding site, repressor binding site
#		  - offset gives the distance in bases upstream (-) or
#		    downstream (+) from the gene start
#		  - length is the coding/binding/sequence length in bases
#		  - details is the encoded details, and is ignored here
#
#  Outputs:
#  		- lines of:
#			chromno: gpromcnt, enhbcnt, repbcnt, genecnt : \
#			                   genesum, enhbsum, repbsum : \
#			                   geneavg, enhbavg, repbavg : \
#			                   avg#enhb/genep, avg#repb/genep
#		  note that avg#enhb/genep and avg#repb/genep are the average
#		  number of enhancer/repressor bind sites per gene-promoter
#		  genes without promoters have no bind sites.
#		  if verbose mode, then these are also labeled, and split
#		  into lines in the same manner as here (minus the "\").
#
# Description:
# 		This gives some basic information about decoded chromosome(s)
# 		as output from decodechrom.pl, and may also be used with
# 		converted decoded chromosomes (as further processed by
# 		resattrset2bitsval.pl) which will give more accurate values
# 		as unused genes, etc have been removed. To use with undecoded
# 		chromosome(s) these must be passed through decodechrom.pl as
# 		for eg (the "\"'s are used here for cosmetic purposes only):
# 			cat pop.txt | \
# 			perl decodechrom.pl GeneticCode.conf | \
# 			perl dchromstats.pl -v

$VERBOSE=0;
if ($#ARGV+1>0 && $ARGV[0] eq "-v") { $VERBOSE=1; shift; }
if ($#ARGV+1>0) { printUsage(); exit(1); }


$cno=$ocno=1;
$ogno=-1;
resetstats();


# read in the decoded chromosomes, and construct a representation for each
# gene (consisting of promoter, regulators and coding region) which is 
# used to generate chromosome stats
# note that within each chromosome gene features should be ordered by locus.

while (<STDIN>) {
  chop;
  # ignore lines that are empty or start with a comment "#" character
  next if ((/^\s*$/) || (/^\s*#/));
# chromno: geneno,gstart: feat,+/-offset,length: details
  ($cno,$genedet,$featdet,$details)=split(/\s*:\s*/);
  # if it's a converted decoded chromosome, then there will be no chrom# field
  # so shift every field over by one
  if ($cno=~/,/) {
    $details=$featdet;
    $featdet=$genedet;
    $genedet=$cno;
    $cno=1; # there is only 1 chromosome per converted decoded chrom file
  }
  ($geneno,$genestart)=split(/\s*,\s*/,$genedet);
  if ($cno>$ocno) {
    print chromstats($ocno,$ogno) . "\n";
    resetstats();
    %chromsum=();
    %chromcnt=();
    $ocno=$cno;
  }

  # extract feature'd details and add this to the current gene description
  ($feat,$featoffset,$featlen)=split(/\s*,\s*/,$featdet);
  if ($feat eq "enhb" || $feat eq "repb") {
    # findbindingsites.pl doesn't include bindsite marker interleaved-codon's
    # in the bindsite, so need to remember that there will be an extra 4 bases
    # each for the BindSite start 'codon' and stop 'codon'. thus we add 8 to
    # the length (would also subtract 4 from the offset if wanted this info),
    # to include these markers, so they aren't counted as 'junk'
    $featlen+=8; # it may be possible that it is only 4 if at start of chrom
    $featoffset-=4; # starts & ends 4 bases either side of actual bind region
  }

  if ($feat ne "prom") { $chromsum{$feat}+=$featlen; }
  $chromcnt{$feat}++; 
  $ogno=$geneno;
}

# process last chromosome when hit EOF
print chromstats($cno,$ogno) . "\n";


sub chromstats {
  my ($chromno,$lastgno) = @_;
  my $statstr;
  my ($gcnt, $ecnt, $rcnt, $gsum, $esum, $rsum);

  $pcnt=$chromcnt{"prom"};
  $gcnt=$chromcnt{"gene"};
  $ecnt=$chromcnt{"enhb"};
  $rcnt=$chromcnt{"repb"};
  $gsum=$chromsum{"gene"};
  $esum=$chromsum{"enhb"};
  $rsum=$chromsum{"repb"};

  $statstr = $chromno . ": ";
  if ($VERBOSE) { $statstr .= "gpromcnt="; }
  $statstr .= $pcnt . ", ";
  if ($VERBOSE) { $statstr .= "genecnt="; }
  $statstr .= $gcnt . ", ";
  if ($VERBOSE) { $statstr .= "enhbcnt="; }
  $statstr .= $ecnt . ", ";
  if ($VERBOSE) { $statstr .= "repbcnt="; }
  $statstr .= $rcnt . ": ";
  if ($VERBOSE) { $statstr .= "\n\t\tgenesum="; }
  $statstr .= $gsum . ", ";
  if ($VERBOSE) { $statstr .= "enhbsum="; }
  $statstr .= $esum . ", ";
  if ($VERBOSE) { $statstr .= "repbsum="; }
  $statstr .= $rsum . ": ";
  if ($VERBOSE) { $statstr .= "\n\t\tgeneavg="; }
  if ($gcnt>0) {
    $statstr .= sprintf("%.2f",$gsum/$gcnt) . ", ";
  } else {
    $statstr .= "0, ";
  }
  if ($VERBOSE) { $statstr .= "enhbavg="; }
  if ($ecnt>0) {
    $statstr .= sprintf("%.2f",$esum/$ecnt) . ", ";
  } else {
    $statstr .= "0, ";
  }
  if ($VERBOSE) { $statstr .= "repbavg="; }
  if ($rcnt>0) {
    $statstr .= sprintf("%.2f",$rsum/$rcnt) . ": ";
  } else {
    $statstr .= "0, ";
  }
  if ($VERBOSE) { $statstr .= "\n\t\tavg#enhb/genep="; }
  if ($pcnt>0) {
    $statstr .= sprintf("%.2f",$ecnt/$pcnt) . ", ";
  } else {
    $statstr .= "0, ";
  }
  if ($VERBOSE) { $statstr .= "avg#repb/genep="; }
  if ($pcnt>0) {
    $statstr .= sprintf("%.2f",$rcnt/$pcnt);
  } else {
    $statstr .= "0, ";
  }

  $statstr;
}



sub resetstats {

  $chromcnt{"gene"}=0;
  $chromcnt{"prom"}=0;
  $chromcnt{"enhb"}=0;
  $chromcnt{"repb"}=0;
  $chromsum{"gene"}=0;
  $chromsum{"enhb"}=0;
  $chromsum{"enhb"}=0;

}



sub printUsage {

  print STDERR <<EOH;
   Synopsis:
   [perl] dchromstats.pl [-v]
  
   Args:
 		-v	verbose mode. labels the output information
 
   Inputs: (stdin)
   		- lines of output from decodechrom.pl:
 			chromno: geneno,gstart: feat,+/-offset,length: details
		    or further processed (converted) by resattrset2bitsval.pl:
			geneno,gstart: feat,+/-offset,length: details
 		  - chromno identifies which chromosome according to its
 		    occurence in the input.
 		  - geneno gives the number of the gene starting with 1 at
 		    the 5' end of the chromosome
 		  - gstart gives the start location of the gene's coding
 		    region on the chromosome
 		  - feat is one of "gene", "prom", "enhb", "repb"; which
 		    indicate, respectively, gene coding region, promoter,
 		    enhancer binding site, repressor binding site
 		  - offset gives the distance in bases upstream (-) or
 		    downstream (+) from the gene start
 		  - length is the coding/binding/sequence length in bases
 		  - details is the encoded details, and is ignored here
 
   Outputs:
   		- lines of:
 			chromno: gpromcnt, genecnt, enhbcnt, repbcnt : \
 			                   genesum, enhbsum, repbsum : \
 			                   geneavg, enhbavg, repbavg : \
			                   avg#enhb/genep, avg#repb/genep
		  note that avg#enhb/genep and avg#repb/genep are the average
		  number of enhancer/repressor bind sites per gene-promoter
		  genes without promoters have no bind sites.
 		  if verbose mode, then these are also labeled, and split
 		  into lines in the same manner as here (minus the \"\\\").
 
  Description:
  		This gives some basic information about decoded chromosome(s)
 		as output from decodechrom.pl, and may also be used with
 		converted decoded chromosomes (as further processed by
 		resattrset2bitsval.pl) which will give more accurate values
 		as unused genes, etc have been removed. To use with undecoded
  		chromosome(s) these must be passed through decodechrom.pl as
  		for eg (the "\"'s are used here for cosmetic purposes only):
  			cat pop.txt | \
  			perl decodechrom.pl GeneticCode.conf | \
  			perl dchromstats.pl -v

EOH
}

