

# pophstats.pl by J.A. Lee, April 2003
#
# Synopsis:
#	[perl] pophstats.pl [-v] popfile [nbins]
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
# 		statistics for the homology between randomly sampled
# 		chromosome pairs in the population:
#	  	- The 1st line gives the following (labelled if verbose mode)
#	  	  space-separated values:
# 		  population size; sum, minimum, maximum, mean, standard
# 		  deviation and standard error
# 		- the following lines contain 3 fields separated by spaces:
# 		  lower non-inclusive bound of container, upper inclusive
# 		  bound, and count of data within those bounds. In verbose
# 		  mode, the output is in a similar form as follows:
# 		  '(' lower-bound ',' upper-bound ']' count
# 	   (stderr)
# 	   	In verbose mode, as sampling is done, a count gets sent to
# 	   	STDERR. This is to track the progress of sampling due to the
# 	   	lengthy time it can take.
#
# Description:
#  		Scans the specified population file to collect population
#  		homology stats.

$PID=$$;

#$STDERR="nul"; # coz ActivePerl runs from dos/win
$STDERR=(-e "/dev/null") ? "/dev/null" : "nul"; # assume DOS if not unix
$STDERR_REDIRECT="2>$STDERR";

$CAT="perl concat.pl";
$LCSSTR="lcsstr.exe";

$TMPFILE="TMP_hstat$PID.tmp";

####################



$NBINS_DEFAULT=12;	# how many bins to sort vals in for finding median
$FWIDTH=16;		# field width for printing bin upper & lower bounds


($popfname,$nbins,$verbose)=scanArgs();


# count the population size from file - in form: [fitness '>'] chromosome

$numchroms=0;

open(PFILE,"<$popfname") || die "Unable to open population file ($popfname)!\n";
while (<PFILE>) {
  chop;
  ($fit,$gene)=split(/>/);
  if ($gene eq "" && $fit ne "") {		# in case no fitness
    $gene=$fit; $fit="";			# just grab gene
  }
  if ($gene) {					# ignore lines w/out genes
    $numchroms++;
  }
}
close(PFILE);

if (int($numchroms/2)<$nbins) { $nbins=int($numchroms/2); }
if ($numchroms<1) { exit(0); }			# nothing to get stats on



# now split popn into 2 groups for sampling homology

@hpairidx=nRandIdxs($numchroms,$numchroms,1,1);
#printA("hpairidx",@hpairidx);


# Do longest common substr between each pair & store lcsstr-len
# %hpair contains the individual's index as the key, and partner's
# as the value. It is used so that as we read in the popn file, only keep
# each chromosome in memory till their partner is found and their
# homology can be calculated

%hpair=();
for ($i=0; $i<$#hpairidx+1; $i+=2) {
  $hpair{$hpairidx[$i]}=$hpairidx[$i+1];
  $hpair{$hpairidx[$i+1]}=$hpairidx[$i];
#print STDERR "pairing: $hpairidx[$i], $hpairidx[$i+1]...\n";
}



%popn=();			# to store gene till other gene found
$linenum=$samplenum=0;
open(PFILE,"<$popfname") || die "Unable to open population file ($popfname)!\n";

if ($verbose) { print STDERR "sampling: "; }
while (<PFILE>) {		# scan population file for partners
  chop;
  ($fit,$gene)=split(/>/);
  if ($gene eq "" && $fit ne "") {		# in case no fitness
    $gene=$fit; $fit="";			# just grab gene
  }
  if ($gene) {					# ignore lines w/out genes
    $linenum++;
#print STDERR "got gene ($linenum)..\n";
    $partner=$hpair{$linenum}; 			# find its partner
    if ($gene2=$popn{$partner}) {		# if partner already in
      delete $popn{$partner};			# don't need to keep any more
      if ($verbose) { $samplenum++; print STDERR $samplenum % 10; }
#$homlen=abs(length($gene)-length($gene2));	# this line for test only
      $homlen=getHomologyLen($gene,$gene2);
      push(@homlg,$homlen);			# store homology
#print STDERR "$homlen\n";
    } else {
      $popn{$linenum}=$gene;			# store gene til partner found
    }
  }
}

if ($verbose) { print STDERR " .. done!\n"; }
close(WFILE);


#printA("homology array",@homlg);

# output chromosome homology stats and exit
printStats("homology length",$verbose,$FWIDTH,$nbins,@homlg);



#################### end main() ########################



sub getHomologyLen {
  
  my ($g1,$g2) = @_;
  my ($l1,$l2,$maxsize,$hlen);
  my $execlcstr;

  # store genes in file for passing to lcsstr
  open(TFILE,">$TMPFILE") || die "Unable to create '$TMPFILE'\n";
  print TFILE "$g1\n$g2\n";
  close(TFILE);

  # need length of longest chromosome to pass to lcsstr,
  # as lcsstr's default (31000) may not be enough!

  $l1=length($g1);
  $l2=length($g2);
  $maxsize=($l1>$l2) ? $l1+1 : $l2+1;

#print STDERR "executing:\n$CAT $TMPFILE | $LCSSTR -m $maxsize $STDERR_REDIRECT\n";

  $execlcstr="$CAT $TMPFILE | $LCSSTR -m $maxsize $STDERR_REDIRECT";
  $lcsres=`$execlcstr`;
  # lcsst returns:	 lcsstr-idx1	lcsstr-idx2	lcsstr-len
  if($lcsres=~/\s(\d+)$/) {		# get last value - the length
    $hlen=$1;
  } else {
    unlink($TMPFILE);
    die("Invalid return values from '$execlcstr': $lcsres\n");
  }

  unlink($TMPFILE) || die "Unable to cleanup temporary file\n";

  $hlen;
}



# prints stats for @data array
# if verbose  = 1 then will print data label and label mean etc
# pwidth is the precision width for printing numbers
# nbins is the number of bins to divide the data sample into
# note that because dealing with integers only, if the range is too small
# then the number of bins is reduced to match the range of integers

sub printStats {

  my ($label,$verbose,$fwidth,$nbins,@data) = @_;
  my ($i,$numvals,$sumv,$minv,$maxv,$meanv,$sdv,$sev,$dwidth);
  my ($lobound,$hibound,$cnt);
  my @binnedv;

  if ($#data+1<1) { die("unable to get stats for $label! Exiting...\n"); }

  ($numvals,$sumv,$meanv,$sdv,$sev)=getNumSumMeanSDSE(@data);
  ($minv,$maxv)=getMinMax(@data);

# because homology is int don't use more bins than possible integer values 
  if ($maxv-$minv+1<$nbins) { $nbins=$maxv-$minv+1; }
  ($dwidth,@binnedv)=binVals($nbins,@data);

  if ($verbose) {
#printA($label,@data);
    print "$label\: N=$numvals sum=$sumv min=$minv max=$maxv mean=$meanv SD=$sdv SE=$sev\n";
  } else {
    print "$numvals   $sumv   $minv   $maxv   $meanv   $sdv   $sev\n";
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
#
# Standard Error (SE) of sample mean is given by:
# SE = SD / sqrt sample_size
# ( strictly speaking SD should be that of population, but for
#   sample size >= 30 sample SD is safe to use as an estimate.
#   ref: Derek Rowntree, Statistics Without Tears, Penguin 1991. )
# 
# we need SE for homology stats, as only randomly sampling from the population
# unlike for length or fitness stats where whole population data is available

sub getNumSumMeanSDSE {
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

  $se=$sd/sqrt($numv);

  ($numv,$sumv,$meanv,$sd,$se);
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



# nRandIdxs: randomly select 'num' indexes into an array of size 'arraySize'
# to choose only certain subsets of these (eg even), a scaling factor of >1
# can be supplied, and likewise there is an offset to offset these from 0
# 	num	  - number of indexes to create (ie size of @rndidx)
# 	arraySize - indexes are from 0..arraySize-1
# 	scale	  - scale index by some factor
# 	offset	  - offset index start - rather than default 0

sub nRandIdxs {
  my ($num, $arraySize, $scale, $offset)=@_;
  my ($r, $i);
  my @rndidx=();
  my @idxs=(0..$arraySize-1);

  while ($#rndidx+1<$num && $#idxs+1>0) {
    $r=int(rand($#idxs+1));
    push(@rndidx,$idxs[$r]);
    @idxs=@idxs[0 .. $r-1, $r+1 .. $#idxs];
  }

#printA("rndidx",@rndidx);
#printA("idxs",@idxs);

  for ($i=0; $i<=$#rndidx; ++$i) {
    $rndidx[$i] *= $scale;
    $rndidx[$i] += $offset;
    $r=$rndidx[$i];
  }

  @rndidx;
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
 	[perl] pophstats.pl [-v] popfile [nbins]
 
  Args:
  		-v	- verbose output - labels outputs
   		popfile	- population file of chromosomes with or without
   			  fitness values
   		nbins	- granularity of discretisation - the number of
   			  containers to bin the fitness values in (must be
   			  at least 1). The default is $NBINS_DEFAULT.
			  Because only dealing with integer data, if nbins
			  exceeds the range of the data, it is automatically
			  decreased to fit this range.
 
  Inputs: (popfile)
  		- lines in the form: [fitness '>'] chromosome, in the
  		  supplied population file
 
  Returns: (stdout) 
  		statistics for the homology between randomly sampled
  		chromosome pairs in the population:
 	  	- The 1st line gives the following (labelled if verbose mode)
 	  	  space-separated values:
  		  population size; sum, minimum, maximum, mean, standard
		  deviation and standard error
  		- the following lines contain 3 fields separated by spaces:
  		  lower non-inclusive bound of container, upper inclusive
  		  bound, and count of data within those bounds. In verbose
  		  mode, the output is in a similar form as follows:
  		  '(' lower-bound ',' upper-bound ']' count
  	   (stderr)
  	   	In verbose mode, as sampling is done, a count gets sent to
  	   	STDERR. This is to track the progress of sampling due to the
  	   	lengthy time it can take.
 
  Description:
  		Scans the specified population file to collect population
  		homology stats.

  ";

}




