

# popgstats.pl by J.A. Lee, March 2004.
# adapted from poplstats.pl and viablefitness.pl
#
# Synopsis:
#	[perl] popgstats.pl [-v] popfile [nbins]
#
# Args:
# 		-v	- verbose output - labels outputs
#  		popfile	- population file of chromosomes with or without
#  			  fitness values
#  		nbins	- granularity of discretisation - the number of
#  			  containers to bin the fitness values in (must be
#  			  at least 1). The default is 12.
#  			  Because only dealing with integer data, if nbins
#  			  exceeds the range of the data, it is automatically
#  			  decreased to fit this range.
#
# Inputs: (popfile)
# 		- lines in the form: [fitness '>'] chromosome, in the
# 		  supplied population file
#
# Returns: (stdout)
# 		statistics for the number of genes in population's chromosomes
#	  	- The 1st line gives the following (labelled if verbose mode)
#	  	  space-separated values:
# 		  population size; sum, minimum, maximum, mean and
# 		  standard deviation
# 		- the following lines contain 3 fields separated by spaces:
# 		  lower non-inclusive bound of container, upper inclusive
# 		  bound, and count of data within those bounds. In verbose
# 		  mode, the output is in a similar form as follows:
# 		  '(' lower-bound ',' upper-bound ']' count
#
# Description:
# 		Scans the specified population file to collect stats on the
# 		number of genes in chromosomes


#### Gene declarations

$STARTCODON="203";				# AUG
$STOPCODON_AMBER="023";				# UAG
$STOPCODON_OCHRE="022";				# UAA
$STOPCODON_OPAL="032";				# UGA
$PROMOTER="0202";				# TATA
$PLEN=length($PROMOTER);

#### stats declarations

$NBINS_DEFAULT=12;	# how many bins to sort vals in for finding median
$FWIDTH=16;		# field width for printing bin upper & lower bounds

### end Global decalrations


($popfname,$nbins,$verbose)=scanArgs();



# read population from file - in form: [fitness '>'] chromosome
# storing linenumber and fitness, and recording their lengths

$numchroms=0;

open(PFILE,"<$popfname") || die "Unable to open population file ($popfname)!\n";
while (<PFILE>) {
  chop;
  ($fit,$chrom)=split(/>/);
  if ($chrom eq "" && $fit ne "") {		# in case no fitness
    $chrom=$fit; $fit="";			# just grab chromosome
  }
  if ($chrom) {					# ignore lines w/out chroms
    $gcnt=numGenes($chrom);
    $gcntch[$numchroms]=$gcnt;
    $numchroms++;
  }
}
close(PFILE);

if (int($numchroms/2)<$nbins) { $nbins=int($numchroms/2); }
if ($numchroms<1) { exit(0); }			# nothing to get stats on


# output chromosome length stats
printStats("chromosome gene count",$verbose,$FWIDTH,$nbins,@gcntch);


#################### end main() ########################




# the length of a gene (G) = gene end - gene start (incl start/stop codons)
# the length of suppressor region (S) = gene start - promoter end
# the length of enhancer region (E) = promoter start - prev gene end 
# the length of promoter region (P)


sub numGenes {

  my ($chrom) = @_;
  my ($gcnt);				# gene count

  %chrominfo=parseChrom($chrom);
  %chrstat=getChromRegionStats(%chrominfo);
  $gcnt=$chrstat{"genecnt"}; 		# genes w/ promoter
}



sub getChromRegionStats {

  my (%chrominfo) = @_;
  my @chrom;				# to unravel hash
  my %chrstat;				# to collect stats
  my ($typ, $typ2, $ptyp);		# type info
  my ($pge,				# previous gene's stop codon (index)
      $gp,				# promoter index
      $gs,				# gene start (codon) index
      $ge);				# gene end (stop codon) index
  my ($clen,				# gene coding length between start-stop
      $rlen,				# repressor length
      $elen,				# enhancer length (from prev gene end)
      $jlen);				# inter-gene junk length
  my $i;

  @chrom=(sort { $a <=> $b } keys (%chrominfo)); # in location order

  $chrstat{"genecnt"}=$chrstat{"promcnt"}=0;
  $chrstat{"codelen"}=0;
  $chrstat{"replen"}=$chrstat{"enhlen"}=0;
  $chrstat{"junklen"}=0;
  #$chrstat{"ucodelen"}=$chrstat{"ureplen"}=$chrstat{"uenhlen"}=0;

  #my $GMAX=15; # len longest fixed-length gene: LUTBitFn - 5 codons = 15 bases
  # note that in the following the *4/3 is to deal with the way regulatory
  # regions are decoded, where they ignore each 3rd base, thus requiring 4 bases
  # to encode a codon
  #my $BINDMARKERLEN=(3+3)*4/3;
  #my $BINDMAX=($GMAX*4/3)+$BINDMARKERLEN;
  #my $BINDMIN=(3*4/3)+$BINDMARKERLEN; # shortest bindable sequence

  $ptyp="g";				# to get leading junk region info
  $pge=$ge=-3;				# -3 coz align on pretend codon

  while ($i<$#chrom+1) {
    $idx=$chrom[$i++];
    $typ=$chrominfo{$idx};
    if ($typ eq "<") {
      $gs=$idx;
      $ge=$chrom[$i++];
      $typ2=$chrominfo{$ge};
      unless ($typ2 eq ">") { die("unmatched gene start ($gs), found $typ2 at $ge\n"); }
      $clen=$ge-$gs-3;			# don't include start/stop codons
      $jlen = ($ptyp eq "g") ? $gs-($pge+3) : 0;
      $codingregion=substr($str,$gs+3,$clen);
      $pge=$ge;
      if ($ptyp eq "p") {		# only include genes with promoters
        $chrstat{"genecnt"}++;
        $chrstat{"codelen"}+=$clen;
        # define "useful" region as no longer than longest fixed-len gene
        # and add the start and stop codons
	#$chrstat{"ucodelen"} += ($clen>$GMAX) ? $GMAX+6 : $clen+6;
	#$chrstat{"junklen"}+=$jlen;
      } else {				# no promoter - junk it!
        $chrstat{"junklen"}+=$jlen+$clen+6;
      }
      $ptyp="g";
#print STDERR ".$jlen." if ($jlen>0);
#print STDERR "<$clen>";
    } elsif ($typ eq "p") {
      $gp=$idx;
      $idx=$chrom[$i];
      $typ2=$chrominfo{$idx};
      $gs = ($typ2 eq "<") ?  $idx : $gp + $PLEN;
      $rlen=$gs-$gp-$PLEN;		# don't include promoter
      $elen=$gp-$pge-1;			# don't include end/promoter
      $enhancer=substr($str,$pge+3,$elen);
      $repressor=substr($str,$gp+$PLEN,$rlen);
      $ptyp="p";
      $chrstat{"promcnt"}++;
      $chrstat{"enhlen"}+=$elen;
      $chrstat{"replen"}+=$rlen;
      # define "useful" regulatory region as no longer than longest bindable
      # resource and if less than shortest bind seq, then not useful at all
      #$ue=($elen>$BINDMAX) ? $BINDMAX : $elen;
      #$ur=($rlen>$BINDMAX) ? $BINDMAX : $rlen;
      #$ue=($ue<$BINDMIN) ? 0 : $ue;
      #$ur=($ur<$BINDMIN) ? 0 : $ur;
      #$chrstat{"uenhlen"} += $ue;
      #$chrstat{"ureplen"} += $ur;
#print STDERR "+$elen+p-$rlen-";
    } else {
      die ("unknown gene feature type ($typ)\n");
    }
  }
#print STDERR "\n";

  %chrstat;

}



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
  my ($i,$p,$pp,$gs,$ge);
  my (@plist,@glist);
  my (%chrinfo);
  my $k;

  # find all promoters (which must precede a gene or at end of chromosome)
  # and coding regions

  $i=0;
  $pp=$ge=-1;
  while ($i>-1) {
    $gs=index($chrom,$STARTCODON, $i);
    $p=($gs>3) ? rindex($chrom,$PROMOTER,$gs-1) : index($chrom,$PROMOTER,$i);
#print STDERR "i($i)ge($ge)p($p)pp($pp)gs($gs)";
    # ensure use promoter only once, & after prev gene end & b4 next gene start
    if ($p>-1 && $p>$pp && $p>$ge && ($gs<0 || $p+$PLEN<$gs)) {	# valid
      push(@plist,$p);
      $chrinfo{$p}="p";
      $i=$p+$PLEN;				# next search after promoter
      $pp=$p;
    } else {
      $p=-1;
    }
    if ($gs>-1) {
      $ge=findStopCodon($chrom,$gs+length($STARTCODON));
#print STDERR "ge($ge)";
      if ($ge>-1) {
        push(@glist,($gs,$ge));
        $chrinfo{$gs}="<";
        $chrinfo{$ge}=">";
        $i=$ge+1;				# next search after gene ends
      } else {					# no genes left on chromosome
        $gs=-1;
      }
    }
#print STDERR " ";
    if ($p<0 && $gs<0) {			# no promoters or genes left
      $i=-1;
    }
  }
#print STDERR "\n";

#printA("coding regions",@glist);
#printA("promoters ",@plist);

#foreach $k (sort { $a <=> $b } keys (%chrinfo)) { print STDERR "$chrinfo{$k}"; }
#print STDERR "\n";

  %chrinfo;
}




# scan through string in groups of 3 bases (codons) to find
# the index of the next stop codon, if it exists

sub findStopCodon {

  my ($str, $i) = @_;
  my ($codon, $stop);

  $stop=0;
  while($i<length($str)) {
    $codon=substr($str,$i,3);
#print STDERR "($codon)";
    if ($codon eq $STOPCODON_AMBER || $codon eq $STOPCODON_OCHRE || $codon eq $STOPCODON_OPAL ) {
      $stop=1;
      last;
    } else {
      $i+=3;
    }
  }

  if (!$stop) {
    $i=-1;
  }

  $i;
}


#################### end gene scanning ########################

# prints stats for @data array
# if verbose  = 1 then will print data label and label mean etc
# pwidth is the precision width for printing numbers
# nbins is the number of bins to divide the data sample into
# note that because dealing with integers only, if the range is too small
# then the number of bins is reduced to match the range of integers

sub printStats {

  my ($label,$verbose,$fwidth,$nbins,@data) = @_;
  my ($i,$numvals,$sumv,$minv,$maxv,$meanv,$sdv,$dwidth);
  my ($lobound,$hibound,$cnt);
  my @binnedv;

  if ($#data+1<1) { die("unable to get stats for $label! Exiting...\n"); }

  ($numvals,$sumv,$meanv,$sdv)=getNumSumMeanSD(@data);
  ($minv,$maxv)=getMinMax(@data);

# because length is int don't use more bins than possible integer values 
  if ($maxv-$minv+1<$nbins) { $nbins=$maxv-$minv+1; }
  ($dwidth,@binnedv)=binVals($nbins,@data);

  if ($verbose) {
#printA($label,@data);
    print "$label\: N=$numvals sum=$sumv min=$minv max=$maxv mean=$meanv SD=$sdv\n";
  } else {
    print "$numvals   $sumv   $minv   $maxv   $meanv   $sdv\n";
  }

#printA("binnedv",@binnedv);
  for ($i=0; $i<$nbins; ++$i) {
    # note: even though only dealing with ints, the ints are the midpoints
    # hence the need for a decimal point to show correct bounds
    $lobound=sprintf("%*.1f",$fwidth,$binnedv[$i*2]-($dwidth/2));
    $hibound=sprintf("%*.1f",$fwidth,$binnedv[$i*2]+($dwidth/2));
    $cnt=$binnedv[($i*2)+1];
    if ($verbose) {
      #      x in (lobound,hibound] is equiv to lobound < x <= hibound
      print "( $lobound  ,\t$hibound ]\t$cnt\n";
    } else {
      print "$lobound\t$hibound\t$cnt\n";
    }
  }

}



# binVals(@vals,$nb)
# 	params:
# 		$nb	- how many bins to sort values into
# 		@vals	- an array of values
# 	returns:
# 		$dwidth	- width of discretisation
# 		@binned - contains centre-values and freq of occurence
# 			  binned[i*2] contains the ith centre value
# 			  binned[i*2+1] contains its count
#
# note that values are sorted according to the centre-values, with min
# and max being the 1st and last center-values, not the boundary values
# which would give the wrong results from rounding down.
# A linear order way to do this is as follows:
# 	(max-min)/(numbins-1) gives the width of discretisation (dwidth)
#	for some value x (min <= x <= max) its bin index (0..nb-1) is
#	given by (x-floor)/dwidth; where floor = min-(dwidth/2)
#	furthermore, this implicitly sorts by the centre-values of the bins.
# Note that values are sorted into bins such that bmin < v <= bmax
#      where bmin, bmax are the lower and upper boundaries of the bin

sub binVals {
  my ($nb,@vals) = @_;
  my ($numvals,$minv,$maxv,$dwidth,$floor,$i,$bi);
  my @binned;

  $numvals=$#vals+1;
  unless ($numvals>0 && $nb>0) { return; }

  # find min and max vals, and calc discretisation width
  ($minv,$maxv)=getMinMax(@vals);

  # special cases where only 1 bin - to avoid div by 0 errors
  if ($nb==1 || $minv==$maxv) {
    $dwidth=$maxv-$minv;
    @binned=($minv,$#vals+1);
    return ($dwidth,@binned);
  }

  $dwidth=($maxv-$minv)/($nb-1);
  $floor=$minv-($dwidth/2);

  # initialise bins with centre value, and zero count
  for ($i=0; $i<$numvals; ++$i) {
    $binned[$i*2]=$minv+($dwidth*$i);	 		# centre val
    $binned[($i*2)+1]=0;				# count
  }

  # now put the (count of) vals in appropriate bins
  for ($i=0; $i<$numvals; ++$i) {
    $bi=int(($vals[$i]-$floor)/$dwidth);		# find bin index
    $binned[($bi*2)+1]++;				# cnt occurences
  }

  ($dwidth, @binned);
}



# to calc standard deviation:
# S^2 = 1/(n-1) * sum (x_j - x_mean)^2
# S.D. = sqrt S^2

sub getNumSumMeanSD {
  my (@vals) = @_;
  my ($numv,$sumv,$meanv,$i,$sd,$ssqr);

  $numv=$#vals+1;		# remember that $# gives idx of last not length
  $sumv=0;
  for ($i=0; $i<$numv; ++$i) { $sumv+=$vals[$i]; }
  $meanv=$sumv/$numv;

  $ssqr=0;
  for ($i=0; $i<$numv; ++$i) { $ssqr+=($vals[$i]-$meanv) ** 2; }
  $ssqr = $ssqr / ($numv-1);
  $sd = sqrt($ssqr);

  ($numv,$sumv,$meanv,$sd);
}



sub getMinMax {
  my (@vals) = @_;
  my ($min,$max,$numvals);
  my (@ascendv);

  $numvals=$#vals+1;
  unless ($numvals) { return; }

  # find min and max vals
  @ascendv=sort { $a <=> $b } @vals;		# to get lowest N vals
  # @descendv=sort { $b <=> $a } @vals;		# to get highest N fitnesses
  $minv=$ascendv[0];
  $maxv=$ascendv[$numvals-1];

  ($minv,$maxv);
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

  my ($argc,$popfname,$nbins,$verbose);

  $argc="".@ARGV;

  $nbins=$NBINS_DEFAULT;
  $verbose=0;

  if ($argc>0 && $ARGV[0] eq "-v") {	# verbose output
    $verbose=1;
    shift @ARGV;			# for processing the rest
    --$argc;
  }

  if ($argc>0 && !($ARGV[0]=~/^-/)) {
    $popfname=$ARGV[0];
  }

  if ($argc>1) {
    if ($ARGV[1]=~/^([0-9]+)$/) { 	# number of bins
      $nbins=$1;
    } else {
      $nbins=-1;
    }
  }


  if ($nbins<1 || !$popfname) {
    printUsage();
    exit(1);
  }

  ($popfname,$nbins,$verbose);
}




sub printUsage {

  print STDERR "
  Synopsis:
 	[perl] popgstats.pl [-v] popfile [nbins]
 
  Args:
  		-v	- verbose output - labels outputs
   		popfile	- population file of chromosomes with or without
   			  fitness values
   		nbins	- granularity of discretisation - the number of
   			  containers to bin the fitness values in (must be
   			  at least 1).  The default is $NBINS_DEFAULT.
			  Because only dealing with integer data, if nbins
			  exceeds the range of the data, it is automatically
			  decreased to fit this range.
 
  Inputs: (popfile)
  		- lines in the form: [fitness '>'] chromosome, in the
  		  supplied population file
 
  Returns: (stdout) 
  		statistics for the number of genes in population's chromosomes
 	  	- The 1st line gives the following (labelled if verbose mode)
 	  	  space-separated values:
  		  population size; sum, minimum, maximum, mean and
  		  standard deviation
  		- the following lines contain 3 fields separated by spaces:
  		  lower non-inclusive bound of container, upper inclusive
  		  bound, and count of data within those bounds. In verbose
  		  mode, the output is in a similar form as follows:
  		  '(' lower-bound ',' upper-bound ']' count
 
  Description:
  		Scans the specified population file to collect stats on the
  		number of genes in chromosomes

  ";

}




