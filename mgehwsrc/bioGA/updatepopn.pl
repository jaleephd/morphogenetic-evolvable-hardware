
# updatepopn.pl by J.A. Lee, March 2003
# updated March 2004 to give option of doing standard GA operations on a
# binary-digit fixed-length chromosome
#
# Synopsis:
# [perl] updatepopn.pl [-a] [-ga] [-gen gen#] popfile fitscript reprate[+] [mincs] imin imax xrate mrate irate [ idrate dbias ]
# 
# Args:
# 		-a		- generate additional operator info to STDERR
# 		-ga		- use standard GA on standard binary fixed
# 				  length chrom. this also means that 'mincs',
# 				  'idrate', 'dbias' params shouldnt be provided
# 		-gen gen#	- if supplied, this, the current generation,
# 				  will be passed to the fitness evaluator
#  		popfile		- population file of chromosomes with fitness
#  		fitscript	- fitness evaluator (eg viableFitness.pl)
#  				  that returns lines of fitness '>' chromosome
#  				  (if cmdline switches needed, enclose script
#  				  and switches in single or double quotes)
# 		reprate[+]	- number of unfit members to replace with new
# 				  (maximum is 1/4 population size). If
#				  followed by a '+', then both offspring of
#				  mated parents are introduced into the popn,
#				  otherwise only the best is.
#  		mincs		- minimum crossover length for homxover
#  				  (not used if '-ga' is specified)
#  		imin		- minimum length of inversion
#  		imax		- maximum length of inversion
#  		xrate		- rate of crossover in offspring (0-1)
#  		mrate		- rate of mutation of bases in offspring (0-1)
#  		irate		- rate of inversion in offspring (0-1)
#  		idrate		- rate of insert/delete of bases (0-1)
#  				  (not used if '-ga' is specified)
#  		dbias		- bias (0-1) towards deletion in base ins/del
#  				  (not used if '-ga' is specified)
# 
# Inputs: (popfile)
# 		  - lines in the form: fitness '>' chromosome, in the
# 		    supplied population file
#
# Outputs:
# 	 (popfile)
# 		  - lines in the form: fitness '>' chromosome, in an
# 		    updated population file
#	 (stderr) 
#		  - 'mated chromosome pairs with fitnesses:' followed by
#		     tab-delimited lines of parent1-fitness & length,
#		     parent2-fitness & length
#	 	  - 'performed operations:' followed by tab-delimited lines
#	 	    containing operator info as follows:
# 		     'hxovr' p1-len p2-len mincs lcsst-len csst-len child-len
#       	     '1xovr' p1-len, p2-len, xover point, child-len
# 		     'inver' chromosome-len imin imax inversion-length
# 		     'mutat' chromosome-len mrate mutation-count
#		     'insdl' chr-len idrate dbias new-len ins-count del-count
#		  - 'bred chromosomes with fitness - lengths:' followed by 
#		    blank-space delimited lines of chrom-fitness, chrom-length
#		  - 'removed chromosomes with fitness - lengths:' followed by
#		    blank-space delimited lines of chrom-fitness, chrom-length
# 
# Description:
# 		This is the core of a stable state genetic algorithm,
#		providing tournament selection and replacement; it does one
#		update of the population - replacing 'reprate' tournament
#		losers with the offspring of the chosen tournament winners.
#		The default is to choose 2 x 'reprate' tournament winners
#		for breeding, and introducing the best of each winner
#		couple's offspring into the population, however, by appending
#		'+' to 'reprate' then 'reprate' tournament winners are
#		selected for breeding, and both offspring of each couple are
#		introduced into the population. This requires that 'reprate'
#		be an even number, however.
#
# Notes:
# 		Uses tournament.pl, and so needs to create temporary files
# 		with the extensions: .wnr, .lsr, .otr, .rst; and also
#		creates temporary files (for passing chromosomes to genetic
#		operator scripts) with extensions .chl and .000 - .004 - so
#		it's a good idea not to use these for population files.
#		(although the filenames have the PID appended to avoid such
#		conflicts).



# set the environment stuff

$PID=$$;

#$STDERR="nul";				# coz ActivePerl runs from win
$STDERR=(-e "/dev/null") ? "/dev/null" : "nul"; # assume DOS if not unix
$STDERR_REDIRECT="2>$STDERR";

$CAT="perl concat.pl";

$INVER="inverop.pl";
$MUTATE4="mutbase4.pl";
$MUTATE2="mutbase2.pl";
$INSDEL="insdelbase4.pl";
$HOMXOVER="homxover.pl";
$ONEPOINTXOVER="oneptxover.pl";
$TOURNAMENT="tournament.pl";
#$VIABLEFITNESS="viableFitness.pl -f";

# NB that the following 4 extensions must be the same as those set
#    in tournament.pl!!! (but, other extensions are local to here)

$WEXT=".wnr";				# winner file extension
$LEXT=".lsr";				# loser file extension
$OEXT=".otr";				# other (not win/lose) extension
$REXT=".rst";				# the rest (didn't compete)

$CEXT=".chl";				# children - from mating winners

$UNIQFN="%$PID";			# might not work in some environs

$FWIDTH=18;				# precision/field width for printing

###### end ENVironment declarations


srand();				# seed random number generator

# scan commandline args, and exit if not correct or missing

$ADDITIONAL=0;
$STANDARDGA=0;
$GEN="";
($popfile,$fitscript,$reprate,$keepbothchild,$mincs,$imin,$imax,$xrate,$mrate,$irate,$idrate,$dbias)=getArgs();

#$TOURNAMENT .= " -d";
#print STDERR "(popn=$popfile, fit=$fitscript, rep=$reprate, repopt=$keepbothchild, mincs=$mincs, i($imin-$imax), x=$xrate, m=$mrate, i=$irate, id=$idrate, db=$dbias)\n";

if ($STANDARDGA) {
  $XOVER=$ONEPOINTXOVER;
  $MUTATE=$MUTATE2;
} else {
  $XOVER=$HOMXOVER;
  $MUTATE=$MUTATE4;
}

if ($ADDITIONAL) {
  $XOVER .= " -a";
  $MUTATE .= " -a";
  $INVER .= " -a";
  $INSDEL .= " -a";
  @opinfo=@childops=();
}


# setup GLOBAL temp file names

$fname=$popfile; $fext="";
if (($ri=rindex($popfile,"."))>-1) {
  $fname=substr($popfile,0,$ri);	
  $fext=substr($popfile,$ri+1);
}
# ($fname,$fext)=split(/\./,$popfile);	# assumes only 1 extension

$fname=$fname . $UNIQFN;
$wkpopfile=$fname . "." . $fext;
$wpopfname=$fname . $WEXT;
$lpopfname=$fname . $LEXT;
$opopfname=$fname . $OEXT;
$rpopfname=$fname . $REXT;
$cpopfname=$fname . $CEXT;
$t1popfname=$fname . ".001";	# main: non-losers from breeding tournament
$t2popfname=$fname . ".002";	# main: losers from breeding tournament
$t3popfname=$fname . ".003";	# mate: holds offspring genes for genetic ops
$t4popfname=$fname . ".004";	# mate: holds result of offspring genetic op
$tmpstderr=$fname . ".000";	# mate: redirect STDERR from operator -a's

if ($ADDITIONAL) {
  $STDERR_REDIRECT= "2>$tmpstderr";
}

# select REPRATE (or REPRATE*2) winners randomly for breeding
#   mate each pair to produce 2 offspring
#   keep (the fittest of) the 2 offspring of each pair
# select REPRATE losers for removal through REPRATE # of tournaments
# new popn = winners + offspring + (non-losers of losers tournament)

# so want to divide the population into winners (for breeding) and losers
# (for replacing) this means conducting popsize/2 tournaments of size 2
# however this also limits us to a max replace rate of 25% of the popn
# as only losers of loser's tournaments are replaced (1/2 of 1/2 = 1/4)

$popsize=popCnt($popfile);
$numt=int($popsize/2);

if ($reprate>int($popsize/4)) {
  print STDERR "reprate ($reprate) argument too large for population of size $popsize!\nUnable to replace more than 1/4 the population at one time. Exiting ...\n";
  exit(1);
}


# create working copy of population file so temp files use uniq name
if (!copyFile($popfile,$wkpopfile)) {
  die("Unable to create working copy '$wkpopfile' of '$popfile'. Exiting..\n");
}

# do winners/losers tournament
$popsplit=`perl $TOURNAMENT $wkpopfile $numt 2` || die("empty tournament result from $TOURNAMENT $wkpopfile $numt 2");
($nw,$nl,$no,$nr)=split(/ /,$popsplit);


# %breedpair contains the individual's index as the key, and partner's
# as the value. It is used so that as we read in the popn file, only keep
# each chromosome in memory till their partner is found and they mate

%breedpair=();
if ($keepbothchild) {
  @breedidx=nRandIdxs($reprate,$nw,1,1);
} else {
  @breedidx=nRandIdxs($reprate*2,$nw,1,1);	# need 2x as only keep 1 child
}


#print STDERR "reprate=$reprate, nw=$nw\n";
#printA("breedidx",@breedidx);


for ($i=0; $i<$#breedidx+1; $i+=2) {
  $breedpair{$breedidx[$i]}=$breedidx[$i+1];
  $breedpair{$breedidx[$i+1]}=$breedidx[$i];
#print STDERR "pairing: $breedidx[$i], $breedidx[$i+1]...\n";
}


@bredf=@bredl=@breederf=@breederl=();
%popn=();
$linenum=0;
open(WFILE,"<$wpopfname") || die "Unable to open tournament winners file '$wpopfname'\n";
open(CFILE,">$cpopfname") || die "Unable to create '$cpopfname'\n";

while (<WFILE>) {		# scan tournament winners file for breeders
  chop;
  ($fit,$gene)=split(/>/);
  if ($gene) {					# ignore lines w/out fit>gene
    $linenum++;
    if ($p2=$breedpair{$linenum}) {		# if its a breeder
      if ($fg2=$popn{$p2}) {			# if partner already in
	($f2,$g2)=split(/>/,$fg2);
	delete $popn{$p2};			# remove from breeding pool
        if ($ADDITIONAL) {
	  push(@breederf,($fit, $f2));
	  push(@breederl,(length($gene), length($g2)));
        }
        ($c1f,$c1g,$c2f,$c2g,@childops)=mate($gene,$g2,$mincs,$imin,$imax,$xrate,$mrate,$irate,$idrate,$dbias,$fitscript,@childops);
	if ($c1f!=$c2f) { 			# compare children for best
	  $c_choice=($c1f>$c2f) ? 1 : 2;
        } else {				# or if equal chose randomly
	  $c_choice=int(rand(2))+1;
        }
        if ($c_choice==1 || $keepbothchild) {
	  print CFILE "$c1f>$c1g\n";		# store child in file
          if ($ADDITIONAL) { @opinfo=(@opinfo, @childops[0,2,4,6]); }
	  if ($ADDITIONAL) { push(@bredf,$c1f); push(@bredl,length($c1g)); }
        }
	if ($c_choice==2 || $keepbothchild) {
	  print CFILE "$c2f>$c2g\n";		# store child in file
          if ($ADDITIONAL) { @opinfo=(@opinfo, @childops[1,3,5,7]); }
	  if ($ADDITIONAL) { push(@bredf,$c2f); push(@bredl,length($c2g)); }
        }
      } else {
        $popn{$linenum}="$fit>$gene";		# store till partner found
      }
    }
  }
}

close(CFILE);
close(WFILE);


# merge non-losers from breeding tournament with new offspring into popn

$merge1str="$CAT $cpopfname $wpopfname $rpopfname $opopfname > $t1popfname";

system($merge1str) && die "Unable to merge non-loser population\n";
rename($lpopfname,$t2popfname) || die "Unable to rename loser population file\n";
unlink($wpopfname,$cpopfname,$rpopfname,$opopfname) || die "Unable to cleanup tournament temp files\n";


# want to chose 'reprate' losers from the loser population for removal
# this means conducting reprate tournaments of size nl/reprate

$tsize=int($nl/$reprate);
# do losers tournament
$popsplit=`perl $TOURNAMENT $t2popfname $reprate $tsize` || die("Empty tournament result from perl $TOURNAMENT $t2popfname $reprate $tsize");


# get fitness & length's of losers

if ($ADDITIONAL) {
  @llen=();
  @lfit=();
  $llinenum=0;
  open(LFILE,"<$lpopfname") || die "Unable to open tournament losers file '$lpopfname'\n";
  while (<LFILE>) {
    chop;
    ($fit,$gene)=split(/>/);
    if ($gene) {				# ignore lines w/out fit>gene
      $lfit[$llinenum]=$fit;
      $llen[$llinenum]=length($gene);
      $llinenum++;
    }
  }
  close(LFILE);
}


# merge non-losers from elimation tournament with the rest of the popn
# then cleanup temporary files and exit with updated popn in the
# $popfname population file

$merge2str="$CAT $t1popfname $wpopfname $rpopfname $opopfname > $popfile";
system($merge2str) && die "Unable to merge population\n";
unlink($t1popfname,$t2popfname,$wpopfname,$rpopfname,$opopfname,$lpopfname) || die "Unable to cleanup loser tournament temp files. Perl returned error: $!\n";
unlink($wkpopfile) || die "Unable to cleanup temp copy of population file. Perl returned error: $!\n";



# output to STDERR:
#   - lines of the fitnesses of pairs of parent chromosomes
#   - lines of operator type : operator info, for all offspring
#       hxovr parent1-len, parent2-len, mincs, lcsst-len, csst-len, child-len
#       1xovr p1-len, p2-len, xover point, child-len
#       inver chromosome-len, imin, imax, inversion-length
#       mutat chromosome-len, mrate, mutation-count
#     @opinfo also stores the operators not executed as "\n", so skip these
#   - tab-delimited fitnesses of child chromosomes
#   - fitness & length details of those removed from population

if ($ADDITIONAL) {
  print STDERR "\nmated chromosome pairs with fitness";
  if (!$STANDARDGA) { print STDERR " - lengths"; }
  print STDERR ":\n";
  $bln1=$bln2="";
  for ($i=0;$i<$#breederf+1;$i+=2) {
    $fns1=sprintf("%-.*f",$FWIDTH-2,$breederf[$i]);
    $fns2=sprintf("%-.*f",$FWIDTH-2,$breederf[$i+1]);
    if (!$STANDARDGA) {
      $bln1=sprintf("%*d",32-$FWIDTH,$breederl[$i]);
      $bln2=sprintf("%*d",32-$FWIDTH,$breederl[$i+1]);
    }
    print STDERR "$fns1  $bln1\t$fns2  $bln2\n";
  }
  print STDERR "\nperformed operations:\n";
  for ($i=0;$i<$#opinfo+1;$i++) {
    if (length($opinfo[$i])>1) {	# ignore empty (with only '\n') lines
      print STDERR $opinfo[$i];
    }
  }
  print STDERR "\nbred chromosomes with fitness";
  if (!$STANDARDGA) { print STDERR " - lengths"; }
  print STDERR ":\n";
  $bln="";
  for ($i=0;$i<$#bredf+1;$i++) {
    $fns=sprintf("%-.*f",$FWIDTH-2,$bredf[$i]);
    if (!$STANDARDGA) {
      $bln=sprintf("%*d",$FWIDTH,$bredl[$i]);
    }
    print STDERR "$fns\t$bln\n";
  }
  print STDERR "\nremoved chromosomes with fitness";
  if (!$STANDARDGA) { print STDERR " - lengths"; }
  print STDERR ":\n";
  $lln="";
  for ($i=0;$i<$llinenum; $i++) {
    $fns=sprintf("%-.*f",$FWIDTH-2,$lfit[$i]);
    if (!$STANDARDGA) {
      $lln=sprintf("%*d",$FWIDTH,$llen[$i]);
    }
    print STDERR "$fns\t$lln\n";
  }
}


#################### end main() ########################


# mate() stores parent chromosomes in a file, then attempts crossover,
# inversion, mutation on the chromosomes in that file, at each stage
# storing the result in that file (this means that unsuccessful xover,
# etc will not result in losing offspring and decreasing popn size
# when losers of tournaments are eliminated in main )
# each operator takes 2 chromosomes and returns 2 chromosomes, hence
# both offspring have operators applied to them in parallel, although
# only 1 of these will survive after their fitness is evaluated, thus
# keeping the xrate,irate,mrate,idrate to the correct levels
#
# mate() returns offspring as fitness-gene pairs, and returns an array
# of 8 operator-operator_info, with indexes 0,2,4,6 used for child 1,
# and 1,3,5,7 for operations on child 2

sub mate {
  my ($p1,$p2,$mincs,$imin,$imax,$xrate,$mrate,$irate,$idrate,$dbias,$fitscript,@childops)=@_;
  my ($xretn,$xoverstr,$inverstr,$mutatstr,$insdelstr);
  my ($cfitgen,$fg1,$fg2,$f1,$f2,$g1,$g2);
  my (@childxop,@childiop,@childmop,@childdop);

  @childxop=@childiop=@childmop=@childdop=("\n","\n");


# store parent genes in file for manipulating with gene operator scripts 
  open(MFILE,">$t3popfname") || die "Unable to create '$t3popfname'\n";
  print MFILE "$p1\n";
  print MFILE "$p2\n";
  close(MFILE);


# apply xover, mutation, inversion, and base ins/del according to probabilities
# noting that source is always those (2) chromosomes in t3popfilename, and
# result goes into t4popfilename, which is then moved back to t3popfilename
# for the next operation

  if ($STANDARDGA) { 
    $xoverstr="$CAT $t3popfname | perl $XOVER > $t4popfname $STDERR_REDIRECT";
  } else {
    $xoverstr="$CAT $t3popfname | perl $XOVER $mincs > $t4popfname $STDERR_REDIRECT";
  }
  $inverstr="$CAT $t3popfname | perl $INVER $imin $imax > $t4popfname $STDERR_REDIRECT";
  $mutatstr="$CAT $t3popfname | perl $MUTATE $mrate > $t4popfname $STDERR_REDIRECT";
  $insdelstr="$CAT $t3popfname | perl $INSDEL $idrate $dbias > $t4popfname $STDERR_REDIRECT";

  if (rand()<$xrate) {				# attempt crossover
    $xretn=system($xoverstr);
    if (!$xretn) {				# xover successful
#print STDERR "xover succesful\n";
      unlink($t3popfname) || die "Unable to remove pre-xover offspring file ($t3popfname). Perl returned error: $!\n";
      if (!rename($t4popfname,$t3popfname)) {
#print STDERR "************* WARNING WARNING WARNING ****************\n";
	# note this is necessary as there is an intermittant fault that occurs
	# on this rename() where a 'permission denied' error is returned. this
	# has only occured with the standard GA, which uses one point xover
	# which takes very little time to execute due to its simplicity, and
	# so may be due to the 1ptxover process occasionaly not having released
	# resources quickly enough. This code thus gives some extra delay
	# for the process to clean up and release resources
        print STDERR "Warning! failed to rename post-xover offspring file ($t4popfname) to ($t3popfname). Perl returned error: $!\n";
        print STDERR "waiting for 1 sec..";
	#select(undef, undef, undef, 0.5);	# sleep for 0.5 secs
	sleep(1);				# sleep for 1 sec
        print STDERR " trying again.. ";
        rename($t4popfname,$t3popfname) || die "\nUnable to update post-xover offspring file ($t4popfname). Perl returned error: $!\n";
        print STDERR "OK.\n";

#print STDERR "2nd attempt to rename($t4popfname,$t3popfname) succeeded\n";
#exit(1);

      }
    } else {
      # homxover may fail if mincs is too large & chromosomes have no common
      # sub sequence, which may even happen with a small mincs. so need to
      # check exit value. 255 means a real error. 1 indicates a wrong param,
      # 2 means xover failed. 
      if ($xretn!=2) {
        die(".. xover failed, with exit code $xretn, using: $xoverstr");
      }
    }
    if ($ADDITIONAL) {
      if ($STANDARDGA) {
        @childxop=getOpInfo("1xovr",$tmpstderr,$mincs,$imin,$imax,$mrate,$idrate,$dbias);
      } else {
        @childxop=getOpInfo("hxovr",$tmpstderr,$mincs,$imin,$imax,$mrate,$idrate,$dbias);
      }
    }
  }

  if (rand()<$irate) {
    if (!system($inverstr)) {	# inversion successful
#print STDERR "inversion OK\n";
      unlink($t3popfname) || die "Unable to remove pre-inversion offspring file ($t3popfname). Perl returned error: $!\n";
      rename($t4popfname,$t3popfname) || die "Unable to update post-inversion offspring file ($t4popfname). Perl returned error: $!\n";
      if ($ADDITIONAL) {
        @childiop=getOpInfo("inver",$tmpstderr,$mincs,$imin,$imax,$mrate,$idrate,$dbias);
      }
    } else {
      die(".. inversion failed using: $inverstr");
    }
  }

  if (!system($mutatstr)) {			# mutation successful
#print STDERR "mutate\n";
    unlink($t3popfname) || die "Unable to remove pre-mutation offspring file ($t3popfname). Perl returned error: $!\n";
    rename($t4popfname,$t3popfname) || die "Unable to update post-mutation offspring file ($t4popfname). Perl returned error: $!\n";
    if ($ADDITIONAL) {
      @childmop=getOpInfo("mutat",$tmpstderr,$mincs,$imin,$imax,$mrate,$idrate,$dbias);
    }
  } else {
    die(".. mutation failed using: $mutatstr");
  }

  if (!system($insdelstr)) {			# ins/del successful
#print STDERR "ins/del bases\n";
    unlink($t3popfname) || die "Unable to remove pre-ins/del offspring file ($t3popfname). Perl returned error: $!\n";
    rename($t4popfname,$t3popfname) || die "Unable to update post-ins/del offspring file ($t4popfname). Perl returned error: $!\n";
    if ($ADDITIONAL) {
      @childdop=getOpInfo("insdl",$tmpstderr,$mincs,$imin,$imax,$mrate,$idrate,$dbias);
    }
  } else {
    die(".. base insertion/deletion failed using: $insdelstr");
  }

  if ($ADDITIONAL) {
    unlink($tmpstderr) || die "Unable to delete '$tmpstderr'. Perl returned error: $!\n";
    @childops=(@childxop,@childiop,@childmop,@childdop);# 8 lines of op: info
  }


# calculate fitness of offspring
  if ($GEN ne "") {	# pass the generation number to the fitness script 
    $cfitgen=`$CAT $t3popfname | perl $fitscript -gen $GEN` || die ("Unable to calculate offspring fitness from $t3popfname using script '$fitscript -gen $GEN'\n");
  } else {
    $cfitgen=`$CAT $t3popfname | perl $fitscript` || die ("Unable to calculate offspring fitness from $t3popfname using script '$fitscript'\n");
  }

  ($fg1,$fg2)=split(/\n/,$cfitgen);
  ($f1,$g1)=split(/>/,$fg1);
  ($f2,$g2)=split(/>/,$fg2);

# cleanup temp file
  unlink($t3popfname) || die "Unable to cleanup offspring file. Perl returned error: $!\n";

# return offspring as fitness-gene pairs, and return operator info
  ($f1,$g1,$f2,$g2,@childops);
}



# extract the offpsrings' operator info from the first 2 lines STDERR
# redirect file, corresponding to the operator applied to the 1st and
# 2nd offspring. In the case of xover, however, there is only 1 line
# (2 offspring are produced at the same time from the 2 parents)
# and the relevant info is extracted for each offspring

# hxovr op returns: p1-len, p2-len, lcsst-len, csst-len, c1-len, c2-len
# 1xovr op returns: p1-len, p2-len, xover point, c1-len, c2-len
# inver op returns: chromosome length, inversion len, start inver index
# mutat op returns: chromosome length, mutation count
# insdl op returns: orig chromosome length, new length, ins cnt, del cnt

# getOpInfo returns:
# hxovr p1-len, p2-len, mincs, lcsst-len, csst-len, child-len
# 1xovr p1-len, p2-len, xover point, child-len
# inver chromosome-len, imin, imax, inversion-len
# mutat chromosome-len, mrate, mutation-cnt
# insdl orig length, insdelrate, delbias, new length, ins count, del count

sub getOpInfo {
  my ($oper,$fname,$mincs,$imin,$imax,$mrate,$idrate,$dbias) = @_;
  my ($l1,$l2);

  $l1=$l2="";

  open(OPFILE,"<$fname") || die ("Unable to open operator ($oper) stderr redirect file ($fname)!\n");
  if ($_=<OPFILE>) {
    if ($oper eq "hxovr") {
#print STDERR "xoverresult#\t$_";
      #       p1len p2len  lcslen csslen c1len  c2len
      if (/^(\d+\s+\d+\s+)(\d+\s+\d+\s+)(\d+)\s+(\d+)/) {
	$l1 = $oper . "\t" . $1 . $mincs . "\t" . $2 . $3 . "\n";
	$l2 = $oper . "\t" . $1 . $mincs . "\t" . $2 . $4 . "\n";
      }
    }
    elsif ($oper eq "1xovr") {
      #       p1len p2len xover-pt c1len  c2len
      if (/^(\d+\s+\d+\s+)(\d+\s+)(\d+)\s+(\d+)/) {
	$l1 = $oper . "\t" . $1 . $2 . $3 . "\n";
	$l2 = $oper . "\t" . $1 . $2 . $4 . "\n";
      }
    }
    elsif ($oper eq "inver") {
      #     chr-len inv-len (ignoring start inv idx)
      if (/^(\d+\s+)(\d+)/) {
	$l1 = $oper . "\t" . $1 . $imin . "\t" . $imax . "\t" . $2 . "\n";
        if ($_=<OPFILE>) { 		# inver runs on both individuals
          if (/^(\d+\s+)(\d+)/) {
            $l2 = $oper . "\t" . $1 . $imin . "\t" . $imax . "\t" . $2 . "\n";
	  }
	}
      }
    }
    elsif ($oper eq "mutat") {
      #     chr-len m-count
      if (/^(\d+\s+)(\d+)/) {
	$l1 = $oper . "\t" . $1 . $mrate . "\t" . $2 . "\n";
        if ($_=<OPFILE>) {		# mutat runs on both individuals
          if (/^(\d+\s+)(\d+)/) {
	    $l2 = $oper . "\t" . $1 . $mrate . "\t" . $2 . "\n";
	  }
	}
      }
    }
    elsif ($oper eq "insdl") {
      # orig chr-len, new chr-len, ins-cnt, del-cnt
      if (/^(\d+\s+)(\d+\s+\d+\s+\d+)/) {
	$l1 = $oper . "\t" . $1 . $idrate . "\t" . $dbias . "\t" . $2 . "\n"; 
        if ($_=<OPFILE>) {		# insdl runs on both individuals
          if (/^(\d+\s+)(\d+\s+\d+\s+\d+)/) {
	    $l2 = $oper . "\t" . $1 . $idrate . "\t" . $dbias . "\t" . $2 . "\n"; 
          }
	}
      }
    }
    else {
      die("Invalid operator ($oper) to getOpInfo() in updatepopn.pl!"); 
    }
  }
  close(OPFILE);

  if (length($l1)==0 || length($l2)==0) {
    die("Unable to extract operator ($oper) info from stderr redirect file ($fname)!\n");
  }

#print STDERR "l1>$l1";
#print STDERR "l2>$l2";
  ($l1, $l2);
}



sub popCnt {
  my ($popfname) = @_;
  my $linecnt=0;

  $linecnt=0;
  open(PFILE,"<$popfname") || die "Unable to open '$popfname' for head count\n";
  while (<PFILE>) {
    ($fit,$gene)=split(/>/);
    if ($gene) {			# ignore lines w/out fit>gene
      $linecnt++;
    }
  }
  close(PFILE);

  $linecnt;
}


sub copyFile {
  my ($srcfile,$destfile) = @_;

  open(SRCFILE,"<$srcfile") || return 0;
  open(DESTFILE,">$destfile") || return 0;
  while (<SRCFILE>) {
    print DESTFILE $_;
  }

  close DESTFILE;
  close SRCFILE;

  return 1;
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



sub getArgs {

  my $argc;
  my ($min, $max);
  my ($popfile,$fitscript,$reprate,$keepbothchild,$mincs,$xrate,$mrate,$irate,$idrate,$dbias);
  my $childsurvival="";

  $argc="".@ARGV;

  if ($argc>0 && $ARGV[0] eq "-a") {	# to generate some additional info
    $ADDITIONAL=1;
    shift @ARGV;			# for processing the rest
    --$argc;
  }

  if ($argc>0 && $ARGV[0] eq "-ga") {	# use standard GA
    $STANDARDGA=1;
    shift @ARGV;			# for processing the rest
    --$argc;
#print STDERR "******* STANDARD GA\n";
  }

  if ($argc>0 && $ARGV[0] eq "-gen") {	# specify current generation
    shift @ARGV;
    --$argc;
    if ($argc>0 && $ARGV[0]=~/^\d+/) {
      $GEN=shift @ARGV;
      --$argc;
    } else {
      die("empty or non-numeric generation # '" . $ARGV[0] . "' supplied with -gen switch");
    }
  }

#print STDERR "******* argc=$argc, ARGV[0]=" . $ARGV[0] . "\n";
  if ((($STANDARDGA==1 && $argc==8) || ($STANDARDGA==0 && $argc==11)) &&
  	!($ARGV[0]=~/^-/)) {
    $popfile=$ARGV[0];
    if ($ARGV[1]=~/^["'](.*)["']/) {		# in case cmdline switches
      $fitscript=$1;				# to script
    } else {
      $fitscript=$ARGV[1];
    }
    if ($ARGV[2]=~/^([0-9]+)([+]?)$/) {		# an unsigned integer
      $reprate=$1;
      $childsurvival=$2;
      if (!$STANDARDGA && $ARGV[3]=~/^([0-9]+)$/) {
        $mincs=$1;
	$argc--;
	shift @ARGV;
      } else {
#print STDERR "******* STANDARD GA - ommitting mincs\n";
        $mincs=0;
      }
      if ($ARGV[3]=~/^([0-9]*[.]?[0-9]+)$/) {	# can be float or int
        $imin=$1;
	if ($ARGV[4]=~/^([0-9]*[.]?[0-9]+)$/) { # can be float or int
          $imax=$1;
          if ($ARGV[5]=~/^([0-9]*[.]?[0-9]+)$/) {	# a floating pt number
    	    $xrate=$1;
            if ($ARGV[6]=~/^([0-9]*[.]?[0-9]+)$/) {
    	      $mrate=$1;
              if ($ARGV[7]=~/^([0-9]*[.]?[0-9]+)$/) {
    	        $irate=$1;
	        if ($STANDARDGA) {
#print STDERR "******* STANDARD GA - ommitting idrate,dbias\n";
		  $idrate=$dbias=0;
		} else {
                  if ($ARGV[8]=~/^([0-9]*[.]?[0-9]+)$/) {
    	            $idrate=$1;
                    if ($ARGV[9]=~/^([0-9]*[.]?[0-9]+)$/) {
    	              $dbias=$1;
		    }
		  }
		}
	      }
	    }
  	  }
  	}
      }
    }
  }

  if ($dbias eq "") { 		# if the last arg was OK, then prev OK
    printUsageExit();
  }

  if ($childsurvival eq "+") {
    $keepbothchild=1;
    if ($reprate % 2 != 0) {
      die("The reprate parameter must be an even number if the '+' option is selected\n");
    }
  } else {
    $keepbothchild=0;
  }

  if ($xrate>1 || $mrate>1 || $irate>1 || $idrate>1) {
    die("Operator rate parameters must lie between 0 - 1!\n");
  }
#print STDERR "OK\n";

  ($popfile,$fitscript,$reprate,$keepbothchild,$mincs,$imin,$imax,$xrate,$mrate,$irate,$idrate,$dbias);
}



sub printUsageExit {

  print STDERR "
  Synopsis:
  [perl] updatepopn.pl [-a] [-ga] [-gen gen#] popfile fitscript reprate[+] [mincs] imin imax xrate mrate irate [ idrate dbias ]
 
  Args:
 		-a		- generate additional operator info to STDERR
  		-ga		- use standard GA on standard binary fixed
 				  length chrom. this also means that 'mincs',
 				  'idrate', 'dbias' params shouldnt be provided
  		-gen gen#	- if supplied, this, the current generation,
  				  will be passed to the fitness evaluator
  		popfile		- population file of chromosomes with fitness
  		fitscript	- fitness evaluator (eg viableFitness.pl)
  				  that returns lines of fitness '>' chromosome
  				  (if cmdline switches needed, enclose script
  				  and switches in single or double quotes)
  		reprate[+]	- number of unfit members to replace with new
  				  (maximum is 1/4 population size). If
				  followed by a '+', then both offspring of
				  mated parents are introduced into the popn,
				  otherwise only the best is.
  		mincs		- minimum crossover length for homxover
   				  (not used if '-ga' is specified)
  		imin		- minimum length of inversion
  		imax		- maximum length of inversion
  		xrate		- rate of crossover in offspring (0-1)
  		mrate		- rate of mutation of bases in offspring (0-1)
  		irate		- rate of inversion in offspring (0-1)
   		idrate		- rate of insert/delete of bases (0-1)
   				  (not used if '-ga' is specified)
   		dbias		- bias (0-1) towards deletion in base ins/del
   				  (not used if '-ga' is specified)
 
  Inputs: (popfile)
  		  - lines in the form: fitness '>' chromosome, in the
  		    supplied population file
 
  Outputs:
  	 (popfile)
  		  - lines in the form: fitness '>' chromosome, in an
  		    updated population file
 	 (stderr)
 		  - 'mated chromosome pairs with fitnesses:' followed by
 		     tab-delimited lines of parent1-fitness & length,
 		     parent2-fitness & length
 	 	  - 'performed operations:' followed by tab-delimited lines
 	 	    containing operator info as follows:
  		     'hxovr' p1-len p2-len mincs lcsst-len csst-len child-len
        	     '1xovr' p1-len, p2-len, xover point, child-len
  		     'inver' chromosome-len imin imax inversion-length
  		     'mutat' chromosome-len mrate mutation-count
 		     'insdl' chr-len idrate dbias new-len ins-count del-count
 		  - 'bred chromosomes with fitness - lengths:' followed by 
 		    blank-space delimited lines of chrom-fitness, chrom-length
 		  - 'removed chromosomes with fitness - lengths:' followed by
 		    blank-space delimited lines of chrom-fitness, chrom-length
 
  Description:
  		This is the core of a stable state genetic algorithm,
  		providing tournament selection and replacement; it does one
  		update of the population - replacing 'reprate' tournament
		losers with the offspring of the chosen tournament winners.
		The default is to choose 2 x 'reprate' tournament winners
  		for breeding, and introducing the best of each winner
		couple's offspring into the population, however, by appending
		'+' to 'reprate' then 'reprate' tournament winners are
		selected for breeding, and both offspring of each couple are
		introduced into the population. This requires that 'reprate'
		be an even number, however.

  ";

  exit(1);
}


