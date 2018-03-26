
# viablefitness2.pl by J.A. Lee, April 2003
#
#   Synopsis:
# 	[perl] viablefitness2.pl [-v] [ -l | -f ] [ maxgenes ] [-gen gen#]
#
# Args:
# 	-v		- output (to STDERR) some extra information
# 	-l		- output (to STDERR) chromosomes's line number
# 	-f		- output (to STDOUT) fitness and chromosome together
# 	maxgenes	- scales and gives a ceiling to number of genes that
# 			  will contribute towards fitness (defaults to 100)
# 	-gen gen#	- current generation, this isn't used here
#
# Inputs: (stdin)
# 		- lines of chromosomes in base 4
#
# Returns: lines of output
# 	    	- (stdout) chromosomes' fitness ( '>' chromosome )
# 		- (stderr) ( line count ': ' ) ( extra info '; ' )
#
# Description:
#	viablefitness2 takes chromosomes (from stdin), comprised of bases
#	specified as [0-3] (equivalent to DNA [TCAG]) and calculates
#	their fitness based on the number of genes with promoters and the
#	percentage of the chromosome that is used for coding and regulating
#	these genes - the 'useful' percentage.
#
#	The aim of this is to aid evolution in the process of seeding a
#	population with chromosomes that may have functional gene expression
#	(hence # genes), while both trying to eliminate too much or too
#	little junk regions (hence percentage 'useful').
#
# Note:
# 	Although this outwardly appears similar to viableFitness.pl, it does
# 	a proper parsing of the chromosome, which involved a complete
# 	rewrite of the internals. In this case, coding regions have higher
# 	priority than promoters, which may result in a great divergence
# 	in the number of genes, percentage useful, and fitness of chromosomes
# 	between the two fitness evaluation scripts.


#### Global declarations

$STARTCODON="203";				# AUG
$STOPCODON_AMBER="023";				# UAG
$STOPCODON_OCHRE="022";				# UAA
$STOPCODON_OPAL="032";				# UGA
$PROMOTER="0202";				# TATA
$PLEN=length($PROMOTER);

$GMAX=15; # length of longest fixed-length gene: LUTBitFn - 5 codons = 15 bases

# note that in the following the *4/3 is to deal with the way regulatory
# regions are decoded, where they ignore each 3rd base, thus requiring 4 bases
# to encode a codon
$BINDMARKERLEN=(3+3)*4/3;
$BINDMAX=($GMAX*4/3)+$BINDMARKERLEN;
$BINDMIN=(3*4/3)+$BINDMARKERLEN; # shortest bindable sequence

### end Global decalrations

$LINECNT=0;
$FULLOUT=0;
$VERBOSE=0;
$MAXGENES=100;

$argc="".@ARGV;

if ($argc>0 && $ARGV[0] eq "-v") {
  $VERBOSE=1;
  shift;
  --$argc;
}

if ($argc>0) {
  if ($ARGV[0] eq "-l") { $LINECNT=1; $lineno=0; shift; $argc--; }
  elsif ($ARGV[0] eq "-f" && $argc>=1) { $FULLOUT=1; shift; $argc--; }
}

if ($argc>0) {
  if ($ARGV[0]=~/^[0-9]+/) {
    $MAXGENES=$ARGV[0]; shift; $argc--;
    if ($ARGV[0] eq "-gen" && $ARGV[1]=~/^[0-9]+/) { shift; shift; $argc-=2; }
  } elsif ($ARGV[0] eq "-gen" && $ARGV[1]=~/^[0-9]+/) {
    shift; shift; $argc-=2;
    if ($ARGV[0]=~/^[0-9]+/) { $MAXGENES=$ARGV[0]; shift; $argc--; }
  } else { printUsage(); exit(1); }
}


# read chromosomes in, calculate fitness, and print fitness out


while (<STDIN>) {
  if ($LINECNT) {
    ++$lineno;
    print STDERR "$lineno: ";
  }
  chop;
  if ($_) {
    print calcFitness($_);
    if ($FULLOUT) {
      print ">$_";
    }
    print "\n";
  }
  print STDERR "\n" if ($LINECNT || $VERBOSE);
}


### end main()




sub calcFitness {

  my ($chromosome) = @_;
  my ($numGenes, $sumUGL, $percentUseful);
  my $fitness;

  ($numGenes,$sumUGL)=getChromGeneStats($chromosome);
  $percentUseful=$sumUGL/length($chromosome) * 100;

  if ($VERBOSE) {
    print STDERR "len=" . length($chromosome);
    print STDERR ", # genes=$numGenes, sumUGL=$sumUGL, \%useful=";
    print STDERR sprintf("%.2f",$percentUseful);
    print STDERR "\; fit=";
  }

# the fitness is dependent on 2 factors:
#    number of genes (gaussian, fast rise from 20-70, plateau around 100)
#    and scaled to max genes (plateau at maxgenes)
#    % useful chromosome (gaussian, rise 0-30%, plateau 30-70%, fall 70-100%)

  $fitness = (1-exp(-0.0005*(((100/$MAXGENES)*$numGenes)**2)))
	   * (1-exp(-0.004*(50-abs(50-$percentUseful))**2));

  #  $fitness=sprintf("%f",$fitness); # to ensure not scientific notation

}




# the length of a gene (G) = gene end - gene start (incl start/stop codons)
# the length of suppressor region (S) = gene start - promoter end
# the length of enhancer region (E) = promoter start - prev gene end 
# the length of promoter region (P)
#
# the useful length of a gene (UGL) is given by:
#     G+6 (where G<=Gmax, else Gmax +6; +6 for start/stop codons)
#   + E (where E<=bindmax, else bindmax)
#   + S (where S<=bindmax, else bindmax)
#   + Plen


sub getChromGeneStats {

  my ($chrom) = @_;
  my ($gcnt,				# gene count
      $sumUGL);				# total of chromosome's "useful" length

  %chrominfo=parseChrom($chrom);
  %chrstat=getChromRegionStats(%chrominfo);
  $gcnt=$chrstat{"genecnt"};
  $sumUGL=$chrstat{"uenhlen"} + $chrstat{"ureplen"}
         + ($chrstat{"promcnt"}*$PLEN)
	 + $chrstat{"ucodelen"};
#print STDERR "\npcnt=$pcnt,useful=$sumUGL,total=",length($chrom),"\n";
  ($gcnt,$sumUGL);		# genes w/ promoter
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
  $chrstat{"junklen"}=0;
  $chrstat{"codelen"}=$chrstat{"ucodelen"}=0;
  $chrstat{"replen"}=$chrstat{"enhlen"}=0;
  $chrstat{"ureplen"}=$chrstat{"uenhlen"}=0;

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
        $chrstat{"ucodelen"} += ($clen>$GMAX) ? $GMAX+6 : $clen+6;
        $chrstat{"junklen"}+=$jlen;
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
      $ue=($elen>$BINDMAX) ? $BINDMAX : $elen;
      $ur=($rlen>$BINDMAX) ? $BINDMAX : $rlen;
      $ue=($ue<$BINDMIN) ? 0 : $ue;
      $ur=($ur<$BINDMIN) ? 0 : $ur;
      $chrstat{"uenhlen"} += $ue;
      $chrstat{"ureplen"} += $ur;
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
  	[perl] viablefitness2.pl [-v] [ -l | -f ] [ maxgenes ]  [-gen gen#]
 
  Args: 
 	-v		- output (to STDERR) some extra information
  	-l		- output (to STDERR) chromosomes's line number
  	-f		- output (to STDOUT) fitness and chromosome together
 	maxgenes	- scales and gives a ceiling to number of genes that
 			  will contribute towards fitness (defaults to 100)
  	-gen gen#	- current generation, this isn't used here

  Inputs: (stdin)
  		- lines of chromosomes in base 4
 
  Returns: lines of output
  	    	- (stdout) chromosomes' fitness ( '>' chromosome )
  		- (stderr) ( line count ': ' ) ( extra info '; ' )
 
  Description:
 	viablefitness2 takes chromosomes (from stdin), comprised of bases
 	specified as [0-3] (equivalent to DNA [TCAG]) and calculates
 	their fitness based on the number of genes with promoters and the
 	percentage of the chromosome that is used for coding and regulating
 	these genes - the 'useful' percentage.
 
 	The aim of this is to aid evolution in the process of seeding a
 	population with chromosomes that may have functional gene expression
 	(hence # genes), while both trying to eliminate too much or too
 	little junk regions (hence percentage 'useful').

  ";
}




