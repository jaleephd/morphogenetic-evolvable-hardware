
# tournament.pl by J.A. Lee, March 2003
#
# Synopsis:
# 	[perl] tournament.pl [-d] file numt tsize
#
# Args:
# 	-d	- dump tournament info to STDERR (for debugging)
# 	file	- name of file holding fitness-chromosome pairs
#	numt	- number of tournaments to conduct (>0)
#	tsize	- size of each tournament (number of participants) (>1)
#
# Inputs: (file)
# 		- lines in the form: fitness '>' chromosome
#
# Returns:
# 		- (stdout)	#winners #losers #others #the-rest
# 		- lines in the form: fitness '>' chromosome, in 4 files
# 		  (file.wnr)	tournament winners
# 		  (file.lsr)	tournament losers
# 		  (file.otr)	those who neither win or lose tournament
# 		  (file.rst)	the rest of the population
#
# Description:
# 		tournament.pl takes a file of fitness-chromosome pairs,
# 		separated by a '>', and then conducts the specified
# 		number of tournaments, in which participants are randomly
# 		chosen from the general population, and a winner and loser
# 		are chosen. At the end of the tournaments, the population
# 		will have been divided into 4 groups: winners, losers, 
# 		those that were neither winners or losers (for tsize>2).
# 		and the rest (those who didn't compete). These groups are
# 		stored in 4 different files, having the same filename as
# 		the original population file, but different file extensions,
# 		and the number of members of each is printed to STDOUT.
# Note:
# 		The population is dealt with as linenumber-fitness pairs,
# 		this has 2 important advantages:
# 			- in the case of long chromosomes, it minimises the
# 			  memory requirements
# 			- it allows for the existence of non-unique
# 			  chromosomes


$WEXT=".wnr";				# winner file extension
$LEXT=".lsr";				# loser file extension
$OEXT=".otr";				# other (not win/lose) extension
$REXT=".rst";				# the rest (didn't compete)


srand();
$DUMP=0;				# debug info off my default

($popfname,$numt,$tsize)=scanArgs();

$fname=$popfname; $fext="";
if (($ri=rindex($popfname,"."))>-1) {
  $fname=substr($popfname,0,$ri);
  $fext=substr($popfname,$ri+1);
}
#($fname,$fext)=split(/\./,$popfname);	# assumes only 1 extension

$wpopfname=$fname . $WEXT;
$lpopfname=$fname . $LEXT;
$opopfname=$fname . $OEXT;
$rpopfname=$fname . $REXT;

# read population from file - in form: fitness '>' chromosome
# store as linenumber and fitness - to avoid memory overhead with
# large chromosomes - only fitness and an index to the gene are needed

%popn=();
$linenum=0;
open(PFILE,"<$popfname") || die "Unable to open $popfname\n";
while (<PFILE>) {
  ($fit,$gene)=split(/>/);
  if ($gene) {					# ignore lines w/out fit>gene
    $linenum++;
    $popn{$linenum-1}=$fit;			# popn is line#,fitness pairs
    $popnScored[$linenum-1]="?";
  }
}
close(PFILE);

#foreach $v (values(%popn)) { print STDERR "$v .. "; }
#print STDERR "\n";

$popsize=$linenum;

if ($numt*$tsize>$popsize) {
  print STDERR "Error: Population too small for numt=$numt, tsize=$tsize!\n";
  exit(1);
}

%winners=%losers=%others=();			# used for stats

for ($i=0; $i<$numt; $i++) {			# number of tournaments

if ($DUMP) { $ii=$i+1; print STDERR "tournament round $ii, of size $tsize...\n"; }

  @p=%popn;					# turn into array for idxing
  $popsize=keys(%popn);
#print STDERR "population size=$popsize\n";

  @rndidx=nRandIdxs($tsize,$popsize,2,1);	# get list competitor (idx's)
  @chosen=@p[@rndidx];				# fitnesses of competitors
#printA("rndidx",@rndidx);
#printA("chosen",@chosen);
  @bis=(@rndidx[maxAtIdxs(@chosen)]);		# index(s) of best
  @wis=(@rndidx[minAtIdxs(@chosen)]);		# index(s) of worst

#printA("bis",@bis);
#printA("wis",@wis);

  $bi=$wi=-1;
  while($bi==$wi) {				# winner & loser can't be same
    $bl=$#bis+1;
    $wl=$#wis+1;
    $bi=$bis[rand($bl)];
    $wi=$wis[rand($wl)];
#print STDERR "blen=$bl wlen=$wl bi=$bi, wi=$wi...\n";
  }

  foreach $idx (@rndidx) {			# for each competitor
    $fit=$p[$idx];
    $geneIdx=$p[$idx-1];			# gene is key so b4 in array
    if ($idx==$bi) {
      $scoredPopn[$geneIdx]="+";		# mark as a winner
      $winners{$geneIdx}=$fit;
if ($DUMP) { print STDERR "winner ($idx): <$fit> $geneIdx\n"; }
    }
    elsif ($idx==$wi) {
      $scoredPopn[$geneIdx]="-";		# mark as a loser
      $losers{$geneIdx}=$fit;
if ($DUMP) { print STDERR "loser ($idx): <$fit> $geneIdx\n"; }
    }
    else {
      $scoredPopn[$geneIdx]=".";		# competed w/out win/lose 
      $others{$geneIdx}=$fit;
if ($DUMP) { print STDERR "other ($idx): <$fit> $geneIdx\n"; }
    }
    delete $popn{$geneIdx};			# remove from general popn
  }

}



# output to 4 files (in format - fitness '>' chromosome)
# 	winner file, loser file, others file, and the rest file

open(PFILE,"<$popfname") || die "Unable to open $popfname\n";
open(WFILE,">$wpopfname") || die "Unable to create $wpopfname\n";
open(LFILE,">$lpopfname") || die "Unable to create $lpopfname\n";
open(OFILE,">$opopfname") || die "Unable to create $opopfname\n";
open(RFILE,">$rpopfname") || die "Unable to create $rpopfname\n";

$linenum=0;
while (<PFILE>) {
  ($fit,$gene)=split(/>/);
  if ($gene) {					# ignore lines w/out fit>gene
    if ($scoredPopn[$linenum] eq "+") {
      print WFILE $_;
    }
    elsif ($scoredPopn[$linenum] eq "-") {
      print LFILE $_;
    }
    elsif ($scoredPopn[$linenum] eq ".") {
      print OFILE $_;
    }
    else {
      print RFILE $_;
    }
    $linenum++;
  }
}

close(PFILE);
close(WFILE);
close(LFILE);
close(OFILE);
close(RFILE);

$ws=keys(%winners);
$ls=keys(%losers);
$ts=keys(%others);
$ps=keys(%popn);

print "$ws $ls $ts $ps\n";


########### end main() ############



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



sub isin {
  my ($num, @alist) = @_;
  my $n;

  foreach $n (@alist) { if ($n==$num) { return 1; } }

  0;
}



sub maxAtIdxs {
  my (@list)= @_;
  my (@maxIdx, $maxval, $i);

  @maxIdx=();
  if (@list) {				# avoid need for Infinity val
    @maxIdx=(0);
    $maxval=$list[0];
#print STDERR "maxAtIdx: maxval=$maxval .. i=0\n";
  }

  for ($i=1; $i<$#list+1; ++$i) {
#print STDERR "maxAtIdx: maxval=$maxval .. list[$i]=$list[$i]\n";
    if ($list[$i]>$maxval) {
      $maxval=$list[$i];
      @maxIdx=($i);
#print STDERR "maxAtIdx: maxval=$maxval .. i=$i\n";
    } elsif ($list[$i]==$maxval) {
#print STDERR "maxAtIdx: maxval=$maxval .. pushing i=$i\n";
      push (@maxIdx,$i);		# in case multiple max idx's
    }
  }

#printA("maxIdx",@maxIdx);
  @maxIdx;
}



sub minAtIdxs {
  my (@list)= @_;
  my (@minIdx, $minval, $i);

  @minIdx=();
  if (@list) {				# avoid need for -Infinity val
    @minIdx=(0);
    $minval=$list[0];
#print STDERR "minAtIdx: minval=$minval .. i=0\n";
  }

  for ($i=1; $i<$#list+1; ++$i) {
#print STDERR "minAtIdx: minval=$minval .. list[$i]=$list[$i]\n";
    if ($list[$i]<$minval) {
      $minval=$list[$i];
      @minIdx=($i);
#print STDERR "minAtIdx: minval=$minval .. i=$i\n";
    } elsif ($list[$i]==$minval) {
      push (@minIdx,$i);		# in case multiple min idx's
#print STDERR "minAtIdx: minval=$minval .. pushing i=$i\n";
    }
  }

#printA("minIdx",@minIdx);
  @minIdx;
}


sub printA {				# for debugging to print arrays
  my ($str,@a) = @_;
  my $i;

  print STDERR "$str: ";
  for ($i=0; $i<$#a+1; ++$i) {
    print STDERR "($a[$i])";
  }
  print STDERR "\n";
}



sub scanArgs {

  my ($popfname,$numt,$tsize);

  $argc="".@ARGV;

  $numt=$tsize=0;

  if ($argc>0 && $ARGV[0] eq "-d") {	# to dump some debug info
    $DUMP=1;
    shift @ARGV;			# for processing the rest
    --$argc;
  }

  if ($argc==3) {
    $popfname=$ARGV[0];
    if ($ARGV[1]=~/^([0-9]+)$/) {
      $numt=$1;
    }
    if ($ARGV[2]=~/^([0-9]+)$/) {
      $tsize=$1;
    }
  }

  if ($numt<1 || $tsize<2) {
    printUsage();
    exit(1);
  }

  ($popfname,$numt,$tsize);

}



sub printUsage {

  print STDERR "
  Synopsis:
  	[perl] tournament.pl [-d] file numt tsize
 
  Args:
  	-d	- dump tournament info to STDERR (for debugging)
  	file	- name of file holding fitness-chromosome pairs
 	numt	- number of tournaments to conduct (>0)
 	tsize	- size of each tournament (number of participants) (>1)
 
  Inputs: (file)
  		- lines in the form: fitness '>' chromosome
 
  Returns:
  		- (stdout)	#winners #losers #others #the-rest
  		- lines in the form: fitness '>' chromosome, in 4 files
  		  (file.$WEXT)	tournament winners
  		  (file.$LEXT)	tournament losers
  		  (file.$OEXT)	those who neither win or lose tournament
  		  (file.$REXT)	the rest of the population
 
  Description:
  		tournament.pl takes a file of fitness-chromosome pairs,
  		separated by a '>', and then conducts the specified
  		number of tournaments, in which participants are randomly
  		chosen from the general population, and a winner and loser
  		are chosen. At the end of the tournaments, the population
  		will have been divided into 4 groups: winners, losers, 
  		those that were neither winners or losers (for tsize>2).
  		and the rest (those who didn't compete). These groups are
  		stored in 4 different files, having the same filename as
  		the original population file, but different file extensions,
  		and the number of members of each is printed to STDOUT.

  ";
}


