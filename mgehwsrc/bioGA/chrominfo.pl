
# chrominfo.pl by J.A. Lee, march 2004
#
#   Synopsis:
# 	[perl] chrominfo.pl
#
# Args: (none)
#
# Inputs: (stdin)
# 		- lines of chromosomes in base 4
#
# Returns: lines of output
# 	    	- (stdout) line count : chromosomes' details
#
# Description:
#	chrominfo takes chromosomes (from stdin), comprised of bases
#	specified as [0-3] (equivalent to DNA [TCAG]) and provides some
#	information on the length, number of genes, etc, in the chromosomes


#### Global declarations

$STARTCODON="203";				# AUG
$STOPCODON_AMBER="023";				# UAG
$STOPCODON_OCHRE="022";				# UAA
$STOPCODON_OPAL="032";				# UGA
$PROMOTER="0202";				# TATA
$PLEN=length($PROMOTER);

### end Global decalrations

if ($argc>0) { printUsage(); exit(1); }

# read chromosomes in, calculate fitness, and print fitness out

$lineno=0;
while (<STDIN>) {
  ++$lineno;
  print "$lineno: ";
  chop;
  if ($_) {
    print chrominfo($_);
  }
  print "\n";
}


### end main()



sub chrominfo {

  my ($chrom) = @_;
  my ($chromlen,$genecnt,$codelen,$enhlen,$replen,$promlen,$junklen);
  my $info="";

  my %chrominfo=parseChrom($chrom);
  my %chrstat=getChromRegionStats(%chrominfo);


  $chromlen=length($chrom);
  $genecnt=$chrstat{"genecnt"};
  $codelen=$chrstat{"codelen"};
  $enhlen=$chrstat{"enhlen"};
  $replen=$chrstat{"replen"};
  $promlen=$chrstat{"promcnt"}*$PLEN;
  $junklen=$chromlen-($codelen+$enhlen+$replen+$promlen);

  $info="genes=$genecnt; len=$chromlen\: code=$codelen, prom=$promlen, enh=$enhlen, rep=$replen, junk=$junklen";
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
  	[perl] chrominfo.pl
 
  Args: (none)
 
  Inputs: (stdin)
  		- lines of chromosomes in base 4
 
  Returns: lines of output
  	    	- (stdout) line count : chromosomes' details
 
  Description:
 	chrominfo takes chromosomes (from stdin), comprised of bases
 	specified as [0-3] (equivalent to DNA [TCAG]) and provides some
 	information on the length, number of genes, etc, in the chromosomes

  ";
}




