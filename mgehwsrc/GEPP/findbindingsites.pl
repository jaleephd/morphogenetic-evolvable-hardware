
# findbindingsites.pl by J.A. Lee, May 2003
#
# Synopsis:
# 	[perl] findbindingsites.pl [-f] code-file [(+|-)bound]
#
# Args:
# 	-f	- full output - outputs actual regions of chromosome, not
# 		  just the locations and lengths of sites in the region 
# 	-s	- sequence only - output only binding sequence
#     code-file - A config file that contains the bind code table, giving
#     		  a mapping from codons to resource-types, and associated
#     		  user-defined functions for resources that decode the
#     		  following codons into the attributes and settings for that
#     		  resource. This is used solely for locating BindSites and
#     		  calculating their lengths.
#     bound	- specifies bound in bases downstream (+), for repressor, or
#		  upstream (-), for enhancer, of promoter (preceding or
#		  following region, respectively) on where elements may bind
#		  to the regulatory region
#
# Inputs: (stdin)
# 		- lines of regulatory regions in base 4 (the alphabet [0-3]
# 		  which is equivalent to the DNA alphabet [TCAG])
#
# Outputs: (stdout) 
# 		- lines of: region-number ':' index-in-region (spaces)
# 		  len of bind site including start & stop ':' bind sequence
#
# Description:
#		findbindingsites narrows down the portions of regulatory
#		regions that can be binded to for regulating gene expression.
#		The regulatory regions are inverted, reversed, and have every
#		3rd base skipped to form sequences (offsets 0,1,2) which are
#		scanned to find BindSite start and stop codons, the area
#		between which is a binding site, to which resources or TFs
#		may bind. Where there is a binding site that covers other
#		binding sites, the covering site is ignored (ie choose smaller
#		sites). Overlaps are, however, allowed.
#		
# See Also:	egGeneticCode.conf for a more detailed description of
# 		the genetic code table and resources' decode functions.
# 		The bind code table is based largely on the genetic code.
#
# Note:
#     	the config file is responsible for creating '$BindCodeTable'
#     	which contains the top level (resource-attribute) bind code, and
#     	for the creation of the subroutines which further decode the
#     	resource's attributes and settings (only need this to identify
#     	the BindSite codon and BindSite stop marker)


#### Global declarations

$DECODEFNPREFIX="bind_";	# prepend this to resource to get decode fn

$BindCodeTable="";	# this gets read in from file, and decoded

# the following are the decoded BindCodeTable
%CodonDecodeMap=();			# codon to resource info lookup
%ResourceToCodonTable=();		# lookup a resource's codon code


### end Global declarations



$argc="".@ARGV;

$FULLOUT=0;
$SEQONLY=0;
if ($argc>0 && $ARGV[0] eq "-f") {
  $FULLOUT=1;
  shift @ARGV;
  $argc--;
} elsif ($argc>0 && $ARGV[0] eq "-s") {
  $SEQONLY=1;
  shift @ARGV;
  $argc--;
}

$codefile="";

if ($argc>0 && !($ARGV[0]=~/^-/)) {
  $codefile=$ARGV[0];
  shift; $argc--;
  if ($argc>0) {
    if ($ARGV[0]=~/^([+-])([0-9]+)$/) {	# if specify a bound
      $boundsign=$1;
      $boundlimit=$2;
      shift; $argc--;
    } else {
      $codefile="";
    }
  }
}

unless ($argc==0 && $codefile ne "") {
  printUsage();
  exit(1);
}


# read in the gentic code table (stored in $GenticCodeTable), and the
# associated subroutines for decoding each resource's attributes and settings

unless (-f $codefile) { die("Can't find bind code file ($codefile)\n"); }
do $codefile;


@tablelines=split(/\n/,$BindCodeTable);

foreach $line (@tablelines) {
  $line=~s/#.*$//;			# strip comment
  $line=~s/\s*$//;			# strip trailing whitespace
  ($codon,$resource,$attribute,$len,@varlenstops)=split(/\s*,\s*/,$line);
  if ($codon) {
    $CodonDecodeMap{$codon}=join(",", ($resource,$attribute,$len,@varlenstops));
    # store resource-type and its codon (so can lookup codon by resource too)
    if ($ResourceToCodonTable{$resource} ne "") {
      $ResourceToCodonTable{$resource} .= ",";
    }
    $ResourceToCodonTable{$resource} .= "$codon";
  }
}


# read regions in, invert, reverse, and skip every 3rd base on them to
# find BindSite start and stop marker codons.
# print out bind site locations and details (location & length + sequence)

$regionum=0;

while (<STDIN>) {
  chop;
  $regionstr=$_;

  # limit the region to a bound up or downstream of the promoter
  ($region,$boundoffset)=boundregion($_,$boundsign,$boundlimit);
  $region=reverse(invertBase4($region));
  if (($regionlen=length($region))>2) { # ignore regions less than 3 bases
#print STDERR "region=$region\n";
    $regionlen=length($region);
    ++$regionum;
    # because we only bind on the 1st 2 of every 3 bases, we can generate
    # 3 strings, starting at offsets 0,1,2, each of which omits every 3rd
    # base, and which we can match exactly against
    $offs0=skip3rdbase($region);
    $offs1=skip3rdbase(substr($region,1));
    $offs2=skip3rdbase(substr($region,2));

#print STDERR "offsets 0,1,2:\n($offs0)\n($offs1)\n($offs2)\n";
    # now convert to codons, but ignoring any trailing bases, we don't
    # match against them
    ($trailb0,@codons0)=getCodons($offs0);
    ($trailb1,@codons1)=getCodons($offs1);
    ($trailb2,@codons2)=getCodons($offs2);
#printA("codons0",@codons0);
#printA("codons1",@codons1);
#printA("codons2",@codons2);

    # and find locations of BindSite tyes which indicate binding sites
    @bsbindlist0=findBindSites(@codons0);
    @bsbindlist1=findBindSites(@codons1);
    @bsbindlist2=findBindSites(@codons2);
#printA("bsbindlist0",@bsbindlist0);
#printA("bsbindlist1",@bsbindlist1);
#printA("bsbindlist2",@bsbindlist2);

    # codon index and length of binding locations need to be converted
    # to their actual location on the chromosome (regulatory) region.
    # to do this, have to remember that we reversed the chromosome, and
    # may have offset 1 or 2 bases from the start

#print STDERR "finding locations on region (len=$regionlen) for offset 0 ...\n";
    @bindlist=locInRegion($regionlen,0,$boundoffset,@bsbindlist0);
#print STDERR "finding locations on region (len=$regionlen) for offset 1 ...\n";
    push(@bindlist,locInRegion($regionlen,1,$boundoffset,@bsbindlist1));
#print STDERR "finding locations on region (len=$regionlen) for offset 2 ...\n";
    push(@bindlist,locInRegion($regionlen,2,$boundoffset,@bsbindlist2));

    @bindlist=sortBindSites(@bindlist);
    printBinding($regionum, $regionstr, @bindlist);
  }
}


################################ end main()



sub printBinding {
  my ($regionum, $regionstr, @bindlist) = @_;
  my ($bindloc,$bindidx,$bindlen,$rnum,$idx,$len,$bindstr);

  foreach $bindloc (@bindlist) {
    ($bindidx,$bindlen)=split(/,/,$bindloc);
    $rnum=sprintf("%8d",$regionum);
    $idx=sprintf("%12d",$bindidx);
    $len=sprintf("%8d",$bindlen);
    $bindstr=substr($regionstr,$idx,$len);

    print "$rnum\: $idx $len" unless ($SEQONLY);
    print "\:   " if ($FULLOUT);
    print "$bindstr" if ($FULLOUT || $SEQONLY);
    print "\n";
  }
}



# sortBindSites() takes a list of binding sites represented as strings of
# 	location "," length of bind
# it sorts these into ascending order and removes any binding sites that
# completely cover other sites. The remaining sorted list is returned in
# the same format of location "," length of bind

sub sortBindSites {
  my (@bindlist) = @_;
  my (@bindlocs,@locs,@lens);
  my $i;

  # sort binding sites according to location, and secondly by length
  @locs=@lens=();
  for (@bindlist) { push @locs, /^(\d+)/; push @lens, /,(\d+)/ }
  @bindlocs = @bindlist[ sort { $locs[$a] <=> $locs[$b]
                                          ||
                                $lens[$a] <=> $lens[$b] }
			0..$#bindlist ];


  # now store the sorted locations and lengths in separate arrays
  @locs=@lens=();
  for (@bindlocs) { push @locs, /^(\d+)/; push @lens, /,(\d+)/ }

  # now for each set of sites with the same start location, keep only the
  # first site, as this will be the one with the shortest bind length

  $i=0;
  while ($i<$#locs) {				# this is deliberately N-1
    if ($locs[$i]==$locs[$i+1]) {
      @locs=@locs[0 .. $i, ($i+2) .. $#locs];   # remove following entry
      @lens=@lens[0 .. $i, ($i+2) .. $#lens];
    } else {
      $i++;					# check next set of sites
    }
  }

  # now, for each site, check that its location+length doesnt reach the
  # location+length of the following site (ie doesn't cover it). if it
  # does, then remove the covering site

  $i=0;
  while ($i<$#locs) {					# deliberately N-1
    if ($locs[$i]+$lens[$i]>=$locs[$i+1]+$lens[$i+1]) { # if it covers next
      @locs=@locs[0 .. ($i-1), ($i+1) .. $#locs];	# remove it
      @lens=@lens[0 .. ($i-1), ($i+1) .. $#lens];
    } else {
      $i++;						# check next pair
    }
  }

  @bindlocs=();
  for ($i=0; $i<$#locs+1; ++$i) {
    push(@bindlocs,join(",",$locs[$i],$lens[$i]));
  }

  @bindlocs;
}


# boundregion()   limits the distance upstream (-) or downstream (+) of the
#		  promoter that will be used, by removing all bases beyond
#		  this bound.
# takes:   the region to be bounded, a '-' or '+' indicating up/down stream,
#          and the bound length to limit the sequence to
# returns: the bounded region, and the offset to add to the index of binding
# 	   sites, to give the correct index in the unbounded region

sub boundregion {
  my ($wholeregion,$boundsign,$bound) = @_;
  my ($region,$offset);
  my $len=length($wholeregion);

  $offset=0;
  $region=$wholeregion;
  if ($bound) { # bound must exist and be >0
    if ($boundsign eq "+") {
      # bounding upstream (
      $region=substr($wholeregion,0,$bound);
    } elsif ($boundsign eq "-") {
      $region=substr($wholeregion,-$bound);
      $offset=$len-$bound;
    }
  }

#print STDERR "len=$len, bound=$bound, offset=$offset\n";
#print STDERR "bounded region: $region\n";
  ($region,$offset)
}



sub invertBase4 {
  my ($str) = @_;

  $str=~tr/[0123]/[2301]/;

  $str;	# add this line as tr returns cnt of matches, we want to return string
}



sub skip3rdbase {
  my ($str) = @_;

  $str=~s/(..)./$1/g;
  $str;	# add this line as s// returns match cnt, we want to return string
}



# extract all the codons from the region, and return as a list, and
# include trailing bases

sub getCodons {
  my ($region) = @_;
  my $len=length($region);
  my $trailing="";
  my @codonlist;
  my $codon;
  my $i=0;

  $trail=$len % 3;
  if ($len % 3 != 0) {
    $trailing=substr($region,-($len % 3));	# get trailing bases
    $region=substr($region,0,-($len % 3));	# strip trailing bases
  }

  while ($i<length($region)) {
    $codon=substr($region,$i,3);
    push(@codonlist,$codon);
    $i+=3;
  }

  ($trailing,@codonlist);
}



# locInRegion: calculates the actual index and length in bases of the binding
# 	       resource within the chromosome region, given the index and
# 	       length in codons in the reversed, shifted and sampled region
# regionlen  - the length (in bases) of the region
# offset     - the offset from the start of the reversed region, caused by
# 	       shifting by 0,1,2 to get all possible codons on region
# bndoffset  - the offset to add to the index of binding sites, to give the
#              correct index in the unbounded region
# bslist     - an array of BindSite locations and lengths (in codons)
# 
# returns: an array of strings, one for each matched binding resource
# 	   in the format of index ',' length

sub locInRegion {
  my ($regionlen,$offset,$bndoffset,@bslist) = @_;
  my ($bssitestr);
  my ($rvidx1,$rvidx2,$idx1,$idx2,$len);
  my ($bindidx1,$bindidx2,$bindlen);
  my @bindlist;

  foreach $bssitestr (@bslist) {
    # extract the index and length info from the string in the array
    ($idx1,$len)=split(/,/,$bssitestr);
    $idx2=$idx1+$len-1;

    # convert indexes on string which had every 3rd base skipped
    # first, have to convert from codon-based index, to indexing by bases
    # and then have to incorporate the skipped bases, by adding a base for
    # every 2nd base kept
    $rvidx1=$idx1*3;				# convert to bases
    $rvidx1+=int($rvidx1/2)+$offset;		# add skipped bases
    $rvidx2=($idx2*3) + 2; 			# +2 as including end of codon
    $rvidx2+=int($rvidx2/2)+$offset;		# add skipped bases

#print STDERR "idx1=$idx1, idx2=$idx2, rvidx1=$rvidx1, rvidx2=$rvidx2\n";
    # the above indexes are relative to the end of the region, as the region
    # was reversed before checking for BindSites. however we need
    # the binding indexes relative to the start of the region
    $bindidx1=$regionlen-$rvidx2-1;	# rvidx2 is closer to start on
    $bindidx2=$regionlen-$rvidx1-1;	# unreversed region rvidx1 follows
    $bindlen=$bindidx2-$bindidx1+1;
#print STDERR "bindidx1=$bindidx1, bindidx2=$bindidx2, bindlen=$bindlen\n";

    # need to make index relative to start of whole region, not just that
    # portion which was checked, so add the boundoffset to the index, the
    # length is unaffected
    $bindidx1+=$bndoffset;

    # now store this extracted info
    push(@bindlist,join(",",$bindidx1,$bindlen));
  }

  @bindlist;
}



# findBindSites() takes an array of codons from a regulatory region,
# and decodes these using the bind code table to find the location and
# lengths of BindSite types - which indicate binding sites within
# regulatory regions.
# Returns: an array of strings, in the format of index "," length (in codons)
# 	   indicating location and lengths of BindSites

sub findBindSites {
  my (@codonlist) = @_;
  my @reslist;
  my (@codons, @varlenstops, @settings);
  my ($resource, $attributes, $attr, $len, $setting, $reslt);
  my ($numcodons,$minl,$i,$j);
  my $codon;

  $i=0;
  $numcodons=$#codonlist+1;
  while ($i<$numcodons) {
    # decode a resource and its attribute
    $codon=$codonlist[$i];
    ($resource,$attr,$len,@varlenstops)=lookupCodon($codonlist[$i]);
    $i++;
#print STDERR "i=$i, codon=($codon), attr=$attr, len=$len\n";

    # if we find a BindSite we need to get its length
    if ($resource eq "BindSite") {

      # if its var len, need to find where its stop is - if no stop marker,
      # then use the end of the region.
      # NOTE: we don't include bind start & stop codons in the bindsite,
      # which even though it will result in these markers counted as 'junk'
      # if collecting stats on chrom usage (just need to remember to add
      # the appropriate 4 bases for start codon and 4 bases for end), it
      # prevents introducing biases for binding resources that share part of
      # the bind start/stop codons' sequence in their codon, due to the
      # interleaved nature of the original sequence. To remove these sections
      # in bindmatch.pl would be unneccessarily complex, so we put up with
      # some slight misinformation - a possibly better alternative would be
      # to implement bindmatch within here!

      if ($len=~/^([0-9]*)[?]/) { 		# variable len
        $minl=$1;
        $minl=($1 eq "") ? 0 : $1;		# may be no minimum length
        $len=$numcodons-$i;
        for ($j=$i+$minl; $j<$numcodons; $j++) {  # skip minlen codons before
          if (isIn($codonlist[$j],@varlenstops)) {# checking for stop
	    #$len=$j-$i+1; #  length includes stop codon
	    $len=$j-$i; # don't include the stop codon in the seq length
            last;
	  }
        }
      } elsif ($len=~/^[0-9]+$/) {		# fixed length
        if ($i+$len>$numcodons) { $len=$numcodons-$i; }
      } else {
        die("Error: Invalid length specification ($len) for $resource, $attr\n");
      }

      # store start index and length (in codons) as a comma-separated string
      #$reslt=($i-1) . "," . ($len+1); # include start codon index, in len
      $reslt=$i . "," . $len; # don't include start codon
#print STDERR "start,length=".$reslt." ...\n";

      # store the BindSite's index and length
      push (@reslist,$reslt);
    
      # because we are looking for binding sites rather than decoding a gene,
      # we only move along by 1 codon (which was done above)
    }
  }
  @reslist;
}



# processResource passes the attribute and codons to the resource's function
# for processing. if there is no such function, then the supplied attribute
# and an empty list of settings will be returned

sub processResource {
  my ($resource,$attr,@codons) = @_;
  my $decodefunction;
  my $attribute=$attr;
  my @settings=();

  $decodefunction=$DECODEFNPREFIX . $resource;
#print "ProcessResource: decodefunction=$decodefunction\n";

# if the resource settings decode function exists (in the symbol table)
# then call it
  if (exists($main::{$decodefunction} ) ) {
    ($attribute,@settings)=&{$decodefunction}($attr,@codons);
#print "ProcessResource: got - $attribute. ";
  }
#printA("settings",@settings);

  ($attribute,@settings);
}



# find the resource-decoding information from the CodonDecodeMap, which will
# be a string of comma-separated values, which will contain: resource,
# attribute,num_settings_codons,stop-code1, stop-code2, ..
# these are then put in an array to be returned

sub lookupCodon {
  my ($codon) = @_;
  my $res;
  my @resa;

  $res=$CodonDecodeMap{$codon};
  @resa=split(/,/,$res);
#printA("codon maps to",@resa); @resa;
}



# find the codons that map to the given resource in ResourceToCodonTable
# this will be a string of comma-separated codons, and return as an
# array of codons

sub lookupResource {
  my ($resource) = @_;
  my $cn;
  my $cna;

  $cn=$ResourceToCodonTable{$resource};
  @cna=split(/,/,$res);
}



sub isIn {
  my ($str, @alist) = @_;
  my $s;

  foreach $s (@alist) { if ($s eq $str) { return 1; } }

  0;
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
  	[perl] findbindingsites.pl [-f] code-file [(+|-)bound]
 
  Args:
  	-f	- full output - outputs actual regions of chromosome, not
  		  just the locations and lengths of sites in the region 
      code-file - A config file that contains the bind code table, giving
      		  a mapping from codons to resource-types, and associated
      		  user-defined functions for resources that decode the
      		  following codons into the attributes and settings for that
      		  resource. This is used solely for locating BindSites and
      		  calculating their lengths.
      bound	- specifies bound in bases downstream (+), for repressor, or
 		  upstream (-), for enhancer, of promoter (preceding or
 		  following region, respectively) on where elements may bind
 		  to the regulatory region
 
  Inputs: (stdin)
  		- lines of regulatory regions in base 4
 
  Outputs: (stdout) 
  		- lines of: region-number ':' index-in-region (spaces)
 		  len of bind site including start & stop ':' bind sequence
 
  Description:
		findbindingsites narrows down the portions of regulatory
		regions that can be binded to for regulating gene expression.
		The regulatory regions are inverted, reversed, and have every
		3rd base skipped to form sequences (offsets 0,1,2) which are
		scanned to find BindSite start and stop codons, the area
		between which is a binding site, to which resources or TFs
		may bind. Where there is a binding site that covers other
		binding sites, the covering site is ignored (ie choose smaller
		sites). Overlaps are, however, allowed.
";
}




