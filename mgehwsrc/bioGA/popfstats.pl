

# popfstats.pl by J.A. Lee, March 2003
#
# Synopsis:
#	[perl] popfstats.pl [-v] popfile [nbins]
#
# Args:
# 		-v	- verbose output - labels outputs
#  		popfile	- population file of chromosomes with fitness
#  		nbins	- granularity of discretisation - the number of
#  			  containers to bin the fitness values in (must be
#  			  at least 1). The default is 12.
#
# Inputs: (popfile)
# 		- lines in the form: fitness '>' chromosome, in the
# 		  supplied population file
#
# Returns: (stdout)
#	  	- The 1st line gives the following (labelled if verbose mode)
#	  	  space-separated values: population size; sum, minimum,
#	  	  maximum, mean and standard deviation of fitness values
# 		- the following lines contain 3 fields separated by spaces:
# 		  lower non-inclusive bound of container, upper inclusive
# 		  bound, and count of data within those bounds. In verbose
# 		  mode, the output is in a similar form as follows:
# 		  '(' lower-bound ',' upper-bound ']' count
#
# Description:
# 		Scans the specified population file to collect fitness stats.

$NBINS_DEFAULT=12;
$PWIDTH=16;		# precision width for print bin upper & lower bounds


($popfname,$nbins,$verbose)=scanArgs();


# read population from file - in form: fitness '>' chromosome
# storing linenumber and fitness

$numchroms=0;

open(PFILE,"<$popfname") || die "Unable to open $popfname\n";
while (<PFILE>) {
  ($fit,$gene)=split(/>/);
  if ($gene) {					# ignore lines w/out fit>gene
    $fitness[$numchroms]=$fit;
    $numchroms++;
  }
}
close(PFILE);


if ($numchroms<$nbins) { $nbins=$numchroms; }
if ($numchroms<1) { exit(0); }			# nothing to get stats on

# output population fitness stats
printStats("population fitness",$verbose,$PWIDTH,$nbins,@fitness);



#################### end main() ########################


# prints stats for @data array
# if verbose  = 1 then will print data label and label mean etc
# pwidth is the precision width for printing numbers
# nbins is the number of bins to divide the data sample into

sub printStats {

  my ($label,$verbose,$pwidth,$nbins,@data) = @_;
  my ($i,$numvals,$sumv,$minv,$maxv,$meanv,$sdv,$dwidth);
  my ($lobound,$hibound,$cnt);
  my @binnedv;

  if ($#data+1<1) { die("unable to get stats for $label! Exiting...\n"); }

  ($numvals,$sumv,$meanv,$sdv)=getNumSumMeanSD(@data);
  ($minv,$maxv)=getMinMax(@data);
  ($dwidth,@binnedv)=binVals($nbins,@data);

  if ($verbose) {
    #printA($label,@data);
    print "$label\: N=$numvals sum=$sumv min=$minv max=$maxv mean=$meanv SD=$sdv\n";
  } else {
    print "$numvals   $sumv   $minv   $maxv   $meanv   $sdv\n";
  }

  for ($i=0; $i<$nbins; ++$i) {
    $lobound=sprintf("%-*.*f",$pwidth+3,$pwidth,$binnedv[$i*2]-($dwidth/2));
    $hibound=sprintf("%-*.*f",$pwidth+3,$pwidth,$binnedv[$i*2]+($dwidth/2));
    $cnt=$binnedv[($i*2)+1];
    if ($verbose) {
      #      x in (lobound,hibound] is equiv to lobound < x <= hibound
      print "( $lobound ,\t$hibound ]\t$cnt\n";
    } else {
      print "$lobound\t$hibound\t$cnt\n";
    }
  }

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
  #my ($truncerr,$flerr);
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

  # calculate rounding error (in number of intervals) due to use of floats
  # this is used to give the correct index when rounding down occurs
  #$truncerr=(($maxv-$minv)/$dwidth)-int(($maxv-$minv)/$dwidth);
  #$flerr=($truncerr==0) ? 0 : 1-$truncerr;
#print STDERR "dwidth=$dwidth, truncerr=$truncerr, flerr=$flerr\n";

  # initialise bins with centre value, and zero count
  for ($i=0; $i<$nb; ++$i) {
    $binned[$i*2]=$minv+($dwidth*$i); 			# centre val
    $binned[($i*2)+1]=0;				# count
  }

  # now put the (count of) vals in appropriate bins
  for ($i=0; $i<$numvals; ++$i) {
    #$bi=int((($vals[$i]-$floor)/$dwidth)+$truncerr);	# find bin index
    $bi=int(($vals[$i]-$floor)/$dwidth);		# find bin index
    $binned[($bi*2)+1]++;				# cnt occurences
  }

  ($dwidth, @binned);
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
 	[perl] popfstats.pl [-v] popfile [nbins]
 
  Args:
  		-v	- verbose output - labels outputs
   		popfile	- population file of chromosomes with fitness
   		nbins	- granularity of discretisation - the number of
			  containers to bin the fitness values in (must be
			  at least 1). The default is $NBINS_DEFAULT.

  Inputs: (popfile)
  		- lines in the form: fitness '>' chromosome, in the
  		  supplied population file
 
  Returns: (stdout)
 	  	- The 1st line gives the following (labelled if verbose mode)
 	  	  space-separated values: population size; sum, minimum,
 	  	  maximum, mean and standard deviation of fitness values
  		- the following lines contain 3 fields separated by spaces:
  		  lower non-inclusive bound of container, upper inclusive
  		  bound, and count of data within those bounds. In verbose
  		  mode, the output is in a similar form as follows:
  		  '(' lower-bound ',' upper-bound ']' count
 
  Description:
  		Scans the specified population file to collect fitness stats.

  ";
}




