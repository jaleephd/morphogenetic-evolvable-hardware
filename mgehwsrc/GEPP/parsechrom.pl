
# parsechrom.pl by J.A. Lee, April 2003
#
# Synopsis:
# [perl] parsechrom.pl [-v] [-f] [p:promoter-list] [s:start-list] [e:stop-list]
#
# Args:
# 	-v		- verbose output (to STDERR)
# 	-f		- full output - outputs actual regions of chromosome,
# 			  not just the location's of the region 
# 	p:promoter-list	- comma separated list with no spaces that give the
# 			  sequence that will be interpreted as a promoter
# 	s:start-list	- comma separated list with no spaces that give the
# 			  sequence that will be interpreted as a start codon
# 	e:stop-list	- comma separated list with no spaces that gives the
# 			  sequence that will be interpreted as a stop codon
#
# Inputs: (stdin)
# 		- lines of chromosomes in base 4
#
# Outputs:
# 	    	- (stdout) lines of chromosomes' region details, given as
# 	    	  white-space separated: chromosome number (in population),
# 	    	  region type, region start location, and either region
# 	    	  length or region sequence; the former being the default,
# 	    	  while the latter is selected by the full output parameter.
# 	    	  Note that chromosome locations start at 0, while chromosome
# 	    	  numbers begin at 1. Region types, are: 'gene', 'promoter',
# 	    	  'enhancer', 'repressor', and 'junk'.
# 		- (stderr) for each chromosome in population, a line with:
# 		  chromosome number: short-hand chromosome region details
#
# Description:
#		parsechrom takes chromosomes (from stdin), comprised of
#		bases in the alphabet [0-3] (equivalent to DNA [TCAG], RNA
#		[UCAG]), and parses these to extract location and length
#		information on genes, promoters, regulatory regions, and
#		junk areas.



#### Global declarations

# default chromosome region sequences

$STARTCODON_AUG="203";				# AUG
$STOPCODON_AMBER="023";				# UAG
$STOPCODON_OCHRE="022";				# UAA
$STOPCODON_OPAL="032";				# UGA
$PROMOTER_TATA="0202";				# TATA


$PMIN=3;				# shortest allowable promoter sequence


# chromosome feature type-symbols used in chromosome info associative array

$PROMOTERSTARTSYM="p";				# start of promoter symbol
$PROMOTERENDSYM="P";				# end of promoter symbol
$STARTCODONSYM="<";				# START codon symbol
$STOPCODONSYM=">";				# STOP codon symbol
$ENDOFCHROMSYM="!";				# mark (past) end of chrom


### end Global declarations


$argc="".@ARGV;
$VERBOSE=0;
$FULLOUT=0;


while ($argc>0) {
  if ($ARGV[0] eq "-v") { $VERBOSE=1; }
  elsif ($ARGV[0] eq "-f") { $FULLOUT=1; }
  elsif ($ARGV[0]=~/^p:/) { @PROMOTERS=split(/,/,substr($ARGV[0],2)); }
  elsif ($ARGV[0]=~/^s:/) { @STARTCODONS=split(/,/,substr($ARGV[0],2)); }
  elsif ($ARGV[0]=~/^e:/) { @STOPCODONS=split(/,/,substr($ARGV[0],2)); }
  else { printUsage(); exit(1); }
  shift @ARGV;
  $argc--;
}


# if no start/stop/promoter sequences were given, then use defaults

unless ($#STARTCODONS+1) {
  push(@STARTCODONS,$STARTCODON_AUG);
}
unless ($#STOPCODONS+1) {
  push(@STOPCODONS,$STOPCODON_OCHRE);
  push(@STOPCODONS,$STOPCODON_AMBER);
  push(@STOPCODONS,$STOPCODON_OPAL);
}
unless ($#PROMOTERS+1) {
  push(@PROMOTERS,$PROMOTER_TATA);
}


unless (codesvalid(@STARTCODONS)) {
  print STDERR "bad start codon - codons must consist of 3 valid bases [0-3]!\n";
  printA("start codons", @STARTCODONS);
  exit(1);
}

unless (codesvalid(@STOPCODONS)) {
  print STDERR "bad stop codon - codons must consist of 3 valid bases [0-3]!\n";
  printA("stop codons", @STOPCODONS);
  exit(1);
}

unless (promotesvalid(@PROMOTERS)) {
  print STDERR "bad promoter - promoters must consist of at least $PMIN valid bases [0-3]!\n";
  printA("promoters", @PROMOTERS);
  exit(1);
}


#printA("start codons", @STARTCODONS);
#printA("stop codons", @STOPCODONS);
#printA("promoters", @PROMOTERS);


# read chromosomes in, parse them and print out the parsed information

$chromno=0;

while (<STDIN>) {
  chop;
  if ($_) {
    ++$chromno;
    $chrom=$_;
    print STDERR "chromosome ($chromno): " if ($VERBOSE);
    %chrominfo=parseChrom($chrom);
    @chromregions=extractChromRegions(%chrominfo);
    printChromRegions($chromno, $chrom, @chromregions);
  }
}


### end main()



# printChromRegions() prints the extracted chromosome region information.
# takes 3 arguments: the chromosome number (in the population), the
# chromosome, and an array of triplets of: chromosome-region-type,
# region-start-index, and region-length.
# Returns: nothing!

sub printChromRegions{

  my ($chromno, $chrom, @chromregion) = @_;
  my $i,$cn,$feat,$idx,$len,$region;

  for ($i=0; $i<$#chromregion+1; $i+=3) {
    if ($FULLOUT) {
      $feat=sprintf("%-9s",$chromregion[$i]);
      $cn=sprintf("%-9d",$chromno);
      $idx=sprintf("%12d",$chromregion[$i+1]);
      $len=$chromregion[$i+2];
      $region=substr($chrom,$idx,$len);
      print "$cn  $feat  $idx   $region\n";
    } else {
      $feat=sprintf("%-9s",$chromregion[$i]);
      $cn=sprintf("%-9d",$chromno);
      $idx=sprintf("%12d",$chromregion[$i+1]);
      $len=sprintf("%12d",$chromregion[$i+2]);
      print "$cn  $feat  $idx   $len\n";
    }
  }
  
}



# extractChromRegions() extracts region information from a parsed chromosome
# takes a single argument: the parsed chromosome, in the form of an
# associative array of index - feature pairs, where the index refers to
# the position on the chromosome, and the feature is one of: promoter start,
# promoter end, gene start codon, or gene stop codon.
# returns an array of triplets of chromosome-region-type, region-start-index,
# and region-length. Chromosome region types are of the following types:
# promoters, enhancers, repressors, genes, and junk regions.

sub extractChromRegions {

  my (%chrominfo) = @_;
  my @chrom;				# to unravel hash
  my @chromregions;			# store regions as feature, idx, len
  my ($typ, $typ2, $ptyp);		# type info
  my ($idx,				# index on chromosome
      $pge,				# previous gene's stop codon (index)
      $gp,				# promoter index
      $gs,				# gene start (codon) index
      $ge,				# gene end (stop codon) index
      $pidx);				# prev feature's idx
  my ($clen,				# gene coding length incl start & stop
      $plen,				# promoter length
      $rlen,				# repressor length
      $elen,				# enhancer length (from prev gene end)
      $jlen);				# inter-gene junk length
  my $i;

  @chrom=(sort { $a <=> $b } keys (%chrominfo)); # in location order

  $ptyp="g";				# to get leading junk region info
  $pidx=$pge=$ge=-3;			# -3 coz align on pretend codon

  while ($i<$#chrom+1) {
    $idx=$chrom[$i++];
    $typ=$chrominfo{$idx};
    if ($typ eq $STARTCODONSYM) {
      $gs=$idx;
      $ge=$chrom[$i++];
      $typ2=$chrominfo{$ge};
      unless ($typ2 eq "$STOPCODONSYM") {
        die("unmatched gene start ($gs), found $typ2 at $ge\n");
      }
      $clen=$ge-$gs+3;			# include start/stop codons
      if ($ptyp eq "g") {		# no promoter for this gene
        $jlen=$gs-($pge+3);		# pre-gene header is thus junk
        push @chromregions, ("junk", $pge+3, $jlen) if ($jlen>0);
      } else {
        $jlen=0;
      }
      push @chromregions, ("gene", $gs, $clen);
      $pge=$ge;
      $ptyp="g";
      $pidx=$ge;
print STDERR ".$jlen." if ($jlen>0 && $VERBOSE);
print STDERR "<<<",$clen-6,">>>" if ($VERBOSE);
    } elsif ($typ eq $PROMOTERSTARTSYM) {
      $gp=$idx;				# promoter start index
      $idx=$chrom[$i++];		# get index and
      $typ2=$chrominfo{$idx};		# type of following chrom. feature
      unless ($typ2 eq $PROMOTERENDSYM) {
        die("promoter start ($gp) expects promoter end; found $typ2 at $idx\n");
      }
      $plen=$idx-$gp+1;
      $idx=$chrom[$i];			# get index and
      $typ2=$chrominfo{$idx};		# type of following chrom. feature
      if ($typ2 eq $STARTCODONSYM) {	# should be gene start
	$gs=$idx;
        $elen=$gp-($pge+3);		# don't include end/promoter
        $rlen=$gs-$gp-$plen;		# don't include promoter
        push @chromregions, ("enhancer", $pge+3, $elen) if ($elen>0);
        push @chromregions, ("promoter", $gp, $plen);
        push @chromregions, ("repressor", $gp+$plen, $rlen) if ($rlen>0);
print STDERR "+$elen+=$plen=-$rlen-" if ($VERBOSE);
      } else {				# should only occur at end of chrom
        $jlen=$gp-($pge+3);		# pre-promoter is thus junk
        push @chromregions, ("junk", $pge+3, $jlen) if ($jlen>0);
        push @chromregions, ("promoter", $gp, $plen);
print STDERR ".$jlen." if ($jlen>0 && $VERBOSE);
print STDERR "=$plen=" if ($VERBOSE);
      }
      $ptyp="p";
      $pidx=$gp;
    } elsif ($typ eq $ENDOFCHROMSYM) {
      $pidx += ($ptyp eq "g") ? 3 : $plen;	# move past last feature
      $jlen = $idx - $pidx;			# the rest is junk
      push @chromregions, ("junk", $pidx, $jlen) if ($jlen>0);
      $ptyp="!";
      $pidx=$idx;
print STDERR ".$jlen." if ($jlen>0 && $VERBOSE);
#print STDERR "!" if ($VERBOSE);
    } else {
      die ("unknown gene feature type ($typ)\n");
    }
  }

print STDERR "\n" if ($VERBOSE);

  @chromregions;

}




# parseChrom() parses the supplied chromosome parameter (a string in base 4)
# according to the grammar below (this grammar specifies the default start
# and stop codons and promoter, however, these may differ if set by user),
# and and returns an associative array of index - feature pairs, where the
# index refers to the position on the chromosome, and the feature is one of:
# 	$PROMOTERSTARTSYM   - indicating the start of the promoter,
# 	$PROMOTERENDSYM   - indicating the end of the promoter,
# 	$STARTCODONSYM - start of gene coding region (start codon), and
# 	$STOPCODONSYM  - end of gene coding region (stop codon)

# <chromosome>	  = <gene>* (<regulator> <gene>+)* <regulator>?
# <regulator>	  = <base>* PROMOTER <base>*
# <gene>	  = <start-codon> <codon>* <stop-codon>
# <start-codon>	  = STARTCODON
# <stop-codon>	  = STOPCODON_AMBER || STOPCODON_OCHRE || STOPCODON_OPAL 
# <codon>	  = <base> <base> <base>
# <base>	  = 0 | 1 | 2 | 3		(RNA equiv U | C | A | G)
# PROMOTER	  = 0202			(DNA equiv TATA)
# STARTCODON	  = 203				(RNA equiv AUG)
# STOPCODON_AMBER = 023				(RNA equiv UAG)
# STOPCODON_OCHRE = 022				(RNA equiv UAA)
# STOPCODON_OPAL  = 032				(RNA equiv UGA)

# note that genes have higher precedence than promoters, in that any
# promoter found within a coding region is treated as part of a gene
# and ignored as a promoter

sub parseChrom {
  my ($chrom) = @_;
  my ($i,$p,$pp,$gs,$ge,$plen);
  my (%chrinfo);
  my $k;

  # find all promoters (which must precede a gene or be at the end of the
  # chromosome), and coding regions. to do this, first we find the start of
  # the next gene (if any), and then its (upstream promoter), and the gene's
  # end; if no gene just locate the first promoter from end of last gene

  $i=0;
  $pp=-2;				# ensure less than p when start
  $ge=-3;				# -3 + (codon length) = start
  while ($i>-1) {
    $gs=nextStartCodon($chrom,$i);
    # need both start and end of gene, otherwise not a gene (at end of chrom)
    if ($gs>-1) { unless (findStopCodon($chrom,$gs+3)>-1) { $gs=-1; } }
    if ($gs>-1) {
#print STDERR "found gene at $gs, finding preceding promoter\n";
# get closest upstream promoter, note that it must be after prev gene
      ($prom,$p)=upstreamPromoter($chrom,$gs-1,$ge+3);
      if ($p>-1) {				# found promoter
        $plen=length($prom);
        $chrinfo{$p}=$PROMOTERSTARTSYM;
        $chrinfo{$p+length($prom)-1}=$PROMOTERENDSYM;
        $i=$p+$plen;				# next search after promoter
        $pp=$p;
      } 
      $ge=findStopCodon($chrom,$gs+3);
#print STDERR "ge($ge)";
      $chrinfo{$gs}=$STARTCODONSYM;
      $chrinfo{$ge}=$STOPCODONSYM;
      $i=$ge+3;					# next search after gene ends
    } else {					# no gene found
#print STDERR "no gene - at end of chrom, finding nearest promoter\n";
# at end of chromosome, just grab the closest downstream promoter only
      ($prom,$p)=downstreamPromoter($chrom,$i,length($chrom)-1);
      if ($p>-1) {
        $chrinfo{$p}=$PROMOTERSTARTSYM;
        $chrinfo{$p+length($prom)-1}=$PROMOTERENDSYM;
        $p=-1; 					# no more genes or promoters
      }
    }
#print STDERR "i($i)ge($ge)p($p)pp($pp)gs($gs)";
#print STDERR " ";
    if ($p<0 && $gs<0) {			# no promoters or genes left
      $i=-1;
    }
  }

  $chrinfo{length($chrom)}=$ENDOFCHROMSYM;	# append terminator

#print STDERR "\n";

#foreach $k (sort { $a <=> $b } keys (%chrinfo)) { print STDERR "$chrinfo{$k}"; } print STDERR "\n";

  %chrinfo;
}



# find the closest promoter searching backwards from the current location
# (includes the current position) to the specified location (previous gene)

sub upstreamPromoter {
  my ($chrom, $i, $backto) = @_;
  my ($prom,$p,$prevprom,$prevpi);

  $prevprom="";
  $prevpi=-1;

  foreach $prom (@PROMOTERS) { 
    if ($i-length($prom)+1>-1) {
      $p=rindex($chrom,$prom, $i-length($prom)+1);
      if ($p>=$backto && $p>$prevpi) {
	$prevpi=$p;
	$prevprom=$prom;
      }
    }
  }
#print STDERR "prev (i=$i) (backto=$backto) chose promoter ($prevprom) at $prevpi\n";

  ($prevprom,$prevpi);
}



# find the closest promoter searching forwards from the current location

sub downstreamPromoter {
  my ($chrom, $i, $upto) = @_;
  my ($prom,$p,$nextprom,$nextpi);

  $nextprom="";
  $nextpi=length($chrom);

  foreach $prom (@PROMOTERS) { 
    $p=index($chrom,$prom, $i);
    if ($p>=0 && $p+length($prom)-1<=$upto && $p<$nextpi) {
      $nextpi=$p;
      $nextprom=$prom;
    }
  }

  if ($nextpi==length($chrom)) { $nextpi=-1; $nextprom=""; }
#print STDERR "next (i=$i) (upto=$upto) chose promoter ($nextprom) at $nextpi\n";
  ($nextprom,$nextpi);
}



# find the closest start codon from the current location

sub nextStartCodon {
  my ($chrom, $i) = @_;
  my ($startc,$s,$nextsc);

  $nextsc=length($chrom);

  foreach $startc (@STARTCODONS) { 
    $s=index($chrom,$startc, $i);
    if ($s<$nextsc && $s>=0) { $nextsc=$s; }
  }

  if ($nextsc==length($chrom)) { $nextsc=-1; }
  $nextsc;
}



# scan through string in groups of 3 bases (codons) to find
# the index of the next stop codon, if it exists

sub findStopCodon {

  my ($chrom, $i) = @_;
  my ($codon, $stopc);

  $stopc=0;
  while($i<length($chrom)) {
    $codon=substr($chrom,$i,3);
#print STDERR "($codon)";
    if (isStopCodon($codon)) {
      $stopc=1;
      last;
    } else {
      $i+=3;
    }
  }

  if (!$stopc) {
    $i=-1;
  }

  $i;
}



sub isStartCodon {
  my ($codon) = @_;

  isIn($codon,@STARTCODONS);

}



sub isStopCodon {
  my ($codon) = @_;

  isIn($codon,@STOPCODONS);

}



sub isIn {
  my ($str, @alist) = @_;
  my $s;

  foreach $s (@alist) { if ($s eq $str) { return 1; } }

  0;
}



sub codesvalid {
  my (@codes) = @_;
  my $c;

  foreach $c (@codes) {
    unless($c=~/^[0-3][0-3][0-3]$/) { return 0; }
  }
  return 1;
}



sub promotesvalid {
  my (@proms) = @_;
  my $p;

  foreach $p (@proms) {
    if (length($p)<$PMIN || ($p=~/[^0-3]/)) { return 0; }
  }
  return 1;
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



sub printUsage {

  print STDERR "
  Synopsis:
  [perl] parsechrom.pl [-v] [-f] [p:promoter-list] [s:start-list] [e:stop-list]
 
  Args:
  	-v	- verbose output (to STDERR)
  	-f	- full output - outputs actual regions of chromosome, not
  		  just the location's of the region 
  	p:promoter-list	- comma separated list with no spaces that give the
  			  sequence that will be interpreted as a promoter
  	s:start-list	- comma separated list with no spaces that give the
  			  sequence that will be interpreted as a start codon
  	e:stop-list	- comma separated list with no spaces that gives the
  			  sequence that will be interpreted as a stop codon
 
  Inputs: (stdin)
  		- lines of chromosomes in base 4
 
  Outputs:
  	    	- (stdout) lines of chromosomes' region details, given as
  	    	  white-space separated: chromosome number (in population),
  	    	  region type, region start location, and either region
  	    	  length or region sequence; the former being the default,
  	    	  while the latter is selected by the full output parameter.
  	    	  Note that chromosome locations start at 0, while chromosome
  	    	  numbers begin at 1. Region types, are: 'gene', 'promoter',
  	    	  'enhancer', 'repressor', and 'junk'.
  		- (stderr) for each chromosome in population, a line with:
  		  chromosome number: short-hand chromosome region details
 
  Description:
 		parsechrom takes chromosomes (from stdin), comprised of
 		bases in the alphabet [0-3] (equivalent to DNA [TCAG], RNA
 		[UCAG]), and parses these to extract location and length
 		information on genes, promoters, regulatory regions, and
 		junk areas.

  ";
}




