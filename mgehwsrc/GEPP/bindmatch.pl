
# bindmatch.pl by J.A. Lee, June 2003
# 		as a merge of bindmatchresources.pl and bindmatchTFs.pl
# 		(fixed binding of TFs July 2004)
#
# Synopsis:
#  	[perl] bindmatch.pl code-file TF-file [(+|-)bound]
#
# Args:
#     code-file - A config file that contains the bind code table, giving
#     		  a mapping from codons to resource-types, and associated
#     		  user-defined functions for resources that decode the
#     		  following codons into the attributes and settings for that
#     		  resource. Note that the config file is responsible for
#     		  creating '$BindCodeTable' which contains the top level
#     		  (resource-attribute) bind code, and for the creation of
#     		  the subroutines which further decode the resource's
#     		  attributes and settings.
# 	TF-file	- a file that contains the transcription factors (and their
# 		  distance from source) to be matched against the regulatory
# 		  regions for binding
#	bound	- specifies bound in bases downstream (+), for repressor, or
#		  upstream (-), for enhancer, of promoter (preceding or
#		  following region, respectively) on where elements may bind
#		  to the regulatory region
#
# Inputs:
#       (stdin)   - lines of regulatory regions in base 4
# 	(TF-file) - lines of TFs in format: type "," dist-from-src (binary)
# 		      "," binding-seq (base 4)
# 		    type may be (M|m)orphogen, (C|c)ytoplasm or (L|l)ocal
# 		    (only first character is used to identify type). Locals
# 		    should have an empty distance from source field, otherwise
# 		    it is ignored. For Cytoplasm type TFs, which includes
# 		    preplaced morphogens, these will have a binary distance
# 		    from source. Gene product morphogens have a spread from
# 		    source field, indicated by a '?', which mean that all
# 		    possible distances from source (0-3) should be generated.
# 		    Any sequences on a regulatory region that match the
# 		    dist+bind sequences of these TFs are added to the binding
# 		    resources.
#
# Outputs: (stdout) 
# 		- lines of: region-number ':' (spaces) offset-in-region
# 		    (spaces) length of binding match ':' (spaces) followed by
# 		      comma delimited binding resource, attribute, [ setting1,
# 		      [ setting2, ... ] ]
# 		  TFs are also converted to resource,attribute,settings format
# 		  TF,TF,(dist-base2),binding-sequence-base4
# 		  Note that the offset is actually for the (settings) end of
# 		  the resource, as resources bind in reverse; and the
# 		  dist-from-source field will be empty for local TFs or
# 		  morphogens at their source.
#
# Description:
#		bindmatch takes regions (from stdin), comprised of bases
#		in the alphabet [0-3] (equivalent to DNA [TCAG]), and finds
#		what resources and TFs bind, where, and the length of the
#		binding site.
#		Note that binding is done by a 'folding' of the chromosome
#		region, such that every 3rd base is ommitted. Hence binding
#		lengths will be longer than the coded resources or TFs
#		themselves, and as well as this, the binding length includes
#		the prepended binary distance-from-source sequence for TFs.
#		Note also that resources that lack settings (due to running
#		out of bases to match against) aren't properly dealt with
#		here so, their entries should be ignored.
#
# See Also:	egGeneticCode.conf for a more detailed description of
# 		the genetic code table and resources' decode functions.
# 		The bind code table is based largely on the genetic code.
#
# Note:
#
#	the binary distance-from-source component of TFs is converted to
#	base 4. the resulting distance sequence is then pre-pended to the
#	TF's binding sequence, and this sequence is then matched against
#	the regulatory region(s). note that in the case of binary numbers
#	with an odd number of digits, the leading digit is treated as odd or
#	even, rather than as a specific number, and matched accordingly
#
#	the dist-from-src encoding should be viewed as a sequence of N binary
#	digits, where N indicates the distance from source, thus this seq
#	provides a diffusion property (ie chemical gradient), as the prob. of
#	it matching decreases (halves) as N increases. so, when a morphogen
#	moves from distance N to N+1, then it is randomly, or iteratively,
#	assigned one of the 2^(N+1) possible binary distance sequences
#
# 	the reason for having a parameter which specifies upper or lower
# 	bounds on extracting a sequence, is for ignoring elements that bind
# 	too far away from the promoter can be ignored.


#### Global declarations

$DECODEFNPREFIX="bind_";	# prepend this to resource to get decode fn

$BindCodeTable="";	# this gets read in from file, and decoded

# the following are the decoded BindCodeTable
%CodonDecodeMap=();			# codon to resource info lookup
%ResourceToCodonTable=();		# lookup a resource's codon code


%TFs=();	# stores TF bind region "," dist as key, value is unused


### end Global declarations



$argc="".@ARGV;
$codefile="";
$TFfile="";

if ($argc>0 && !($ARGV[0]=~/^-/)) {
  $codefile=$ARGV[0];
  shift; $argc--;
  if ($argc>0 && !($ARGV[0]=~/^-/)) {
    $TFfile=$ARGV[0];
    shift; $argc--;
    if ($argc>0) {
      if ($ARGV[0]=~/^([+-])([0-9]+)$/) {	# if specify a bound
        $boundsign=$1;
        $boundlimit=$2;
        shift; $argc--;
      } else {
        $TFfile=$codefile="";
      }
    }
  }
}


unless ($argc==0 && $TFfile ne "" && $codefile ne "") {
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

#if ($codon) { print STDERR "$codon: $resource - $attribute, $len; ", $#varlenstops+1, "\n"; }
}


#printA("codonmap",%CodonDecodeMap);
#printA("ResourceToCodonTable",%ResourceToCodonTable);



# read TFs from file & store for matching against chrom. regulatory regions

unless (-f $TFfile) { die("Can't find TF file ($TFfile)\n"); }
open(TFILE,"<$TFfile") || die "Unable to open $TFfile\n";

while (<TFILE>) {
  chop;
#print STDERR "read entry '$_' from TFfile\n";
  ($type,$dist,$bindstr)=split(/,/);
  $type=~s/^[\s]*//;				# remove leading space
  #$type=~tr/A-Z/a-z/;				# lowercase it
  #$type=substr($type,0,1);		     # (m)orphogen (l)ocal (c)ytoplasm
  $dist=~s/^[\s]*//;				# remove leading space
  $bindstr=~s/^[\s]*//;				# remove leading space
  if ($bindstr=~/^[0-3]+$/) {			# we have a valid TF
    if ($type=~/^[lL]/) { $dist=""; }		# local's have no dist field
    $TFs{"$bindstr,$dist"}=();
  }
}
close(TFILE);

#printA("TFs",@TFs);


# read regulatory regions in and skip every 3rd base on them to match against
# resources codes and TF binding regions and distance from source, to test bind
# print out those that bind, and their binding details

$regionum=0;

while (<STDIN>) {
  chop;

  # limit the region to a bound up or downstream of the promoter
  ($region,$boundoffset)=boundregion($_,$boundsign,$boundlimit);

  # invert & reverse regulatory region for matching TFs to
  $region=reverse(invertBase4($region));
  if (($regionlen=length($region))>2) { # ignore regions less than 3 bases
#print STDERR "str=$str\n";
    ++$regionum;

    # because we only bind on the 1st 2 of every 3 bases, we can generate
    # 3 strings, starting at offsets 0,1,2, each of which omits every 3rd
    # base, and which we can match exactly against
    $offs0=skip3rdbase($region);
    $offs1=skip3rdbase(substr($region,1));
    $offs2=skip3rdbase(substr($region,2));
#print STDERR "offset0:$offs0\n";
#print STDERR "offset1:$offs1\n";
#print STDERR "offset2:$offs2\n";

    # now convert to codons, but ignoring any trailing bases, we don't
    # match against them
    ($trailb0,@codons0)=getCodons($offs0);
    ($trailb1,@codons1)=getCodons($offs1);
    ($trailb2,@codons2)=getCodons($offs2);

#printA("codons0",@codons0);
#printA("codons1",@codons1);
#printA("codons2",@codons2);

    # and convert codons to arrays of resource,attribute,settings
    @decodelist0=decodeCodons(@codons0);
    @decodelist1=decodeCodons(@codons1);
    @decodelist2=decodeCodons(@codons2);
#printA("decodelist0",@decodelist0);
#printA("decodelist1",@decodelist1);
#printA("decodelist2",@decodelist2);

    # when codons are decoded, their codon index and length are stored
    # now we need to convert these to their actual location on the
    # chromosome (regulatory) region, so that we so know where the resource
    # binds to, but to get this, have to remember that we reversed the
    # string, and may have offset 1 or 2 bases from the start

#print STDERR "finding locations on region (len=$regionlen) for offset 0 ...\n";
    @rbindlist0=locInRegion("res",$regionlen,0,$boundoffset,@decodelist0);
#print STDERR "finding locations on region (len=$regionlen) for offset 1 ...\n";
    @rbindlist1=locInRegion("res",$regionlen,1,$boundoffset,@decodelist1);
#print STDERR "finding locations on region (len=$regionlen) for offset 2 ...\n";
    @rbindlist2=locInRegion("res",$regionlen,2,$boundoffset,@decodelist2);

 
    # find TF binding locations on reversed,inverted,sampled chromosome
    @TFlist0=findTFbindsites($offs0,0);
    @TFlist1=findTFbindsites($offs1,1);
    @TFlist2=findTFbindsites($offs2,2);

    # translate to real lengths and locations of binding on chromosome region
    @tbindlist0=locInRegion("tf",$regionlen,0,$boundoffset,@TFlist0);
    @tbindlist1=locInRegion("tf",$regionlen,1,$boundoffset,@TFlist1);
    @tbindlist2=locInRegion("tf",$regionlen,2,$boundoffset,@TFlist2);

#printA("tf bindlist 0",@tbindlist0) if ($#tbindlist0>-1); 
#printA("tf bindlist 1",@tbindlist1) if ($#tbindlist1>-1);
#printA("tf bindlist 2",@tbindlist2) if ($#tbindlist2>-1);

    # now put them all together and sort
    @bindlist=sort { $a <=> $b } ( @rbindlist0, @rbindlist1, @rbindlist2,
                                   @tbindlist0, @tbindlist1, @tbindlist2);
#printA("bindlist",@bindlist);

    # and now output the result
    printBinding($regionum, @bindlist);
  }
}


################################ end main()



sub printBinding {
  my ($regionum, @bindlist) = @_;
  my ($bound,$match,$bindloc,$bindidx,$bindlen);
  my ($rnum,$idx,$len);
  my (@bindlocs,@locs,@lens);

  # sort binding sites according to location, and secondly by length
  @locs=@lens=();
  for (@bindlist) { push @locs, /^(\d+)/; push @lens, /,(\d+)\:/ }
  @bindlocs = @bindlist[ sort {
    				$locs[$a] <=> $locs[$b]
                                	 ||
                        	$lens[$a] <=> $lens[$b]
      				} 0..$#bindlist ];

  foreach $bound (@bindlocs) {
    # split stored string into its binding location and identity parts
    ($bindloc,$match)=split(/:/,$bound);
    ($bindidx,$bindlen)=split(/,/,$bindloc);
    $rnum=sprintf("%8d",$regionum);
    $idx=sprintf("%12d",$bindidx);
    $len=sprintf("%8d",$bindlen);
    print "$rnum\: $idx $len\:   $match\n";
  }
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



# base2tobase4() converts a binary string to base 4, with any extra leading
# digit converted to its 2 alternative halfbases (0 -> 0,2 ; 1 -> 1,3)
# Args:
# 	$binstr - binary string to be converted
# returns:
# 	$halfbases	- extra leading digit (odd number of binary digits)
# 			  is converted to its 2 possible values: 0 -> "02",
# 			  1 -> "13". if no trailing digit, returns "" value
# 	$strb4		- binary string converted to base 4 equivalent

sub base2tobase4 {
  my ($binstr) = @_;
  my ($halfbases,$strb4);
  my ($i,$strlen,$b2,$b4);

  $strlen=length($binstr);
  $halfbases=$strb4="";
  if ($strlen % 2) { 				# if any remainder digit
    $b2=substr($binstr,0,1);
    $halfbases=($b2==0) ? "02" : "13";		# 0 -> 0,2 ; 1 -> 1,3
    $binstr=substr($binstr,1);			# strip leading digit
    $strlen--;					# and adjust length
  }
  $i=0;
  while ($i<$strlen) {
    # grab each pair of binary digits and convert to equivalent number 0-3
    $b4=(substr($binstr,$i,1) * 2) + substr($binstr,$i+1,1);
#print STDERR "base2tobase4: b4=$b4\n";
    # append to strb4 string
    $strb4 .= $b4;
    $i+=2;
  }

  ($halfbases,$strb4);
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


# decodeCodons: takes an array of codons, and decodes these using the bind
# code table. It returns an array of strings, each of which is a decoded
# resource in the format of: index and length of decoded resource (in codons)
# separated by a comma, ":", and comma-separated decoded resource, its
# attribute and settings

sub decodeCodons {
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

    # if it was a TF, a BindSite, start or stop codon, or some other non-
    # resource-coding codon then we just skip it, as no matching resource
    # to bind (or in case of TFs, these need to be treated specially).
    # These should return a resource of "" (empty) or the "Undef" type,
    # or "BindSite" for bind sites or "TF" for transcription factors
    if ($resource ne "" && $resource ne "Undef" && $resource ne "BindSite"
                        && $resource ne "TF") {

# its var len, need to find where its stop is - if no stop, then its the
# end of the region. note length includes its stop codon

      if ($len=~/^([0-9]*)[?]/) { 		# variable len
        $minl=$1;
        $minl=($1 eq "") ? 0 : $1;		# may be no minimum length
        $len=$numcodons-$i;
        for ($j=$i+$minl; $j<$numcodons; $j++) {  # skip minlen codons before
          if (isIn($codonlist[$j],@varlenstops)) {# checking for stop
	    $len=$j-$i+1;
            last;
	  }
        }
      } elsif ($len=~/^[0-9]+$/) {		# fixed length
        if ($i+$len>$numcodons) { $len=$numcodons-$i; }
      } else {
        die("Error: Invalid length specification ($len) for $resource, $attr\n");
      }
      @codons=@codonlist[$i .. $i+$len-1];

      # now decode the resource's settings
      ($attribute,@settings)=processResource($resource,$attr,@codons);

      # store start index and length (both in codons) along with the decoded
      # resource, its attribute and settings as a comma-separated string
      $reslt=($i-1) . "," . ($len+1) . ":" . $resource . "," . $attribute . ",";
      foreach $setting (@settings) {
        # if resource settings got cut off by a premature end marker then
        # setting may be an empty string, so we skip it
        $reslt .= $setting . "," if ($setting ne "");
      }

      chop($reslt);			# remove trailing comma
      # store the decoded resource/attribute/settings
      push (@reslist,$reslt);
    
      # because we are looking for binding sites rather than decoding a gene,
      # we only move along by 1 codon (which was done above)
      # $i+=$len; 			# and move past processed codons
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




# findTFbindsites() takes an inverted, reversed, and sampled (each 3rd base
# skipped) region of a chromosome for which binding sites are to be found
# using the transcription factors stored in the global hash %TFs, as
# key= tf-bind-seq "," dist-from-src ; value is not used.
# Distances, for non-local TFs, are prepended to the TFs binding string;
# which means they are compared against bases that follow the TF on the
# chromosome. If the dist from src field is a '?', this indicates a gene
# product morphogen which will be released from a cell and be able to spread
# distances from 0-3 cells away from its source, which gives the generated
# distance from source field possible values of:
# 	'', 0,1,00,01,10,11,000,001,010,011,100,101,110,111
# Args:
# 	$region - the (sampled/inverted/reversed) chromosome region to bind
# 		  TFs to
# 	$offset - offsets can be 0, 1, or 2 - which indicate where the first
# 		  base begins at, in the sequence where the 3rd base is
# 		  skipped (eg offset 0 skips base 2,5,8,..)
# Returns: an array of:
# 	   tf-bind-sequence "," dist-from-src, ":" , index in region ",",
# 	   length of binding site on region (including distance sequence)

sub findTFbindsites {
  my ($region,$offset) = @_;
  my ($k,$tf,$tfa,$tfb,$regionlen,
      $tfdist,$dist,$distb4,$halfbases);
  my (@matches,@matches1,@matches2);
  my @tfmatches=();			# function returns array of matches
  my @mdists=("","0","1","00","01","10","11","000","001","010","011","100","101","110","111");
  my @dists;

#print STDERR "findTFbindsites: offset=$offset, region: $region\n";
  $regionlen=length($region);
  foreach $k (keys %TFs) {
    @matches2=();
    ($tf,$tfdist)=split(/,/,$k);

#if ($tfdist eq "?") {
#print STDERR "checking morphogen sequence ($tf) with ";
#printA("dists ",@mdists);
#}
    @dists=($tfdist eq "?") ? @mdists : ($tfdist);
    foreach $dist (@dists) {
#print STDERR "checking TFs{$k}, tf=($tf), dist=($dist)...\n";
      if ($dist ne "") {				# if its not local
      # if a TF is non local, prepend distance converted from base 2 to base 4
        ($halfbases,$distb4)=base2tobase4($dist);
#print STDERR "dist=$dist, halfbases=$halfbases, distb4=$distb4\n";
        if ($halfbases ne "") {
        # if there is an odd number of binary digits this results in 2
        # equivalent leading bases that need to be checked for binding
          $distb4a=substr($halfbases,0,1) . $distb4;
          $distb4b=substr($halfbases,1,1) . $distb4;
          $tflen=length($tf . $distb4a);
          @matches1=matchTF($region,$tf,$dist,$distb4a);
          @matches2=matchTF($region,$tf,$dist,$distb4b);
        } else {
          $tflen=length($tf . $distb4);
          @matches1=matchTF($region,$tf,$dist,$distb4);
        }
      } else {					# its a local TF
        $tflen=length($tf);
        @matches1=matchTF($region,$tf,$dist,"");
      }

      @matches=(@matches1, @matches2);
      @tfmatches=(@tfmatches, @matches);
    }
#printA("matches",@matches);
#printA("tfmatches",@tfmatches);
  }

  @tfmatches;
}



# matchTF() takes a region, which it tries to find matches for the supplied
# transcription factor, which if it has a distance-from-source (in base 4)
# will be prepended to the TF's binding sequence for matching. Note that the
# binary dist-frm-src argument is only used for storing in the matched
# results; it should be consistent with the distb4 argument, which should be
# an empty string if dist=0 (remember leading zeros are stripped)
# returns an array of strings consisting of:
# tf-bind-sequence "," dist-from-src, ":" index of match "," length match

sub matchTF {
  my ($region,$tf,$dist,$distb4)=@_;
  my ($i,$ti,$tfwdist,$lenwdist);
  my (@matches);

  $tfwdist=$distb4 . $tf;
  $lenwdist=length($tfwdist);
  $i=0;
#print STDERR "tfwdist=$tfwdist ..\n";
  while (($ti=index($region,$tfwdist,$i))>-1) { # find a match
#print STDERR "tfwdist ($tfwdist) match found at $ti\n";
    push(@matches,"$tf,$dist\:$ti,$lenwdist");
    $i=$ti+1;			# to find next possibly overlapping occurence
  }

#printA("matchTF",@matches);
  @matches;
}




# locInRegion: calculates the actual index and length of the binding element
# 	       (including optional distance from source, if TF) within the
# 	       chromosome region, given the index and length in the reversed,
# 	       shifted and sampled region
# regionlen  - the length of the region
# offset     - the offset from the start of the reversed region, caused by
# 	       shifting by 0,1,2 to get all possible codons on region
# bndoffset  - the offset to add to the index of binding sites, to give the
#              correct index in the unbounded region
# matchlist  - an array of strings of the TF "," distance from source ":" 
# 	       comma separated list of indexes of locations and lengths of
# 	       match where the TF binds on the reversed, inverted, sampled
# 	       region
#
# returns: the indexes of the binding sites on the unmodified (original)
# 	   chromosome region as an array of strings, one for each binding
# 	   site in matchlist. Each element in the array is in the format of:
# 	   length of binding site on (real) region ":"
# 	   	comma-separated resource, attribute, and settings
# 	   TFs are also converted to format: TF,TF,(dist),bind-sequence


sub locInRegion {
  my ($type,$regionlen,$offset,$bndoffset,@matchlist) = @_;
  my ($dil,$resourcematch,$decodestr);
  my ($matchstr,$tfidstr,$tfbindstr,$tf);
  my ($rvidx1,$rvidx2,$idx1,$idx2,$len,$dist,$matched);
  my ($bindidx1,$bindidx2,$bindlen);
  my @bindlist;

#print STDERR "regionlen=$regionlen\n";
  foreach $matchstr (@matchlist) {
#print STDERR "matchstr=$matchstr\n";

    if ($type eq "res") {
    # split stored string into its identity and binding location parts
      ($dil,$resourcematch)=split(/:/,$matchstr);
      ($idx1,$len)=split(/,/,$dil);
      $matched=$resourcematch;
    } else {
      ($tfidstr,$bindstr)=split(/:/,$matchstr);
      # extract the TF binding sequence (tf) and the TF distance from source
      ($tf,$dist)=split(/,/,$tfidstr);
      # extract the index and length info from the string in the array
      ($idx1,$len)=split(/,/,$bindstr);
      #$dist=~s/^[\s0]*//;		# remove leading space and 0's
      $dist=~s/^\s*//;			# remove leading space
      # convert TF to the same format as other resources: res,attr,settings
      $matched=join(",","TF,TF",$dist,$tf);
    }
    $idx2=$idx1+$len-1;

    # if dealing with resources, then first need to convert from
    # codon-based index, to indexing by bases
    if ($type eq "res") {
      $idx1*=3;
      $idx2=($idx2*3) + 2;			# +2 as including end of codon
    }

    # convert indexes on string which had every 3rd base skipped
    # need to incorporate the skipped bases, by adding a base for
    # every 2nd base kept
    $rvidx1=$idx1+int($idx1/2)+$offset;
    $rvidx2=$idx2+int($idx2/2)+$offset;

#print STDERR "idx1=$idx1, idx2=$idx2, len=$len, rvidx1=$rvidx1, rvidx2=$rvidx2\n";
    # the above indexes are relative to the end of the region, as the region
    # was reversed before checking for matching resources. however we need
    # the binding indexes relative to the start of the region
    $bindidx1=$regionlen-$rvidx2-1;	# rvidx2 is closer to start on
    $bindidx2=$regionlen-$rvidx1-1;	# unreversed region rvidx1 follows
    $bindlen=$bindidx2-$bindidx1+1;
#print STDERR "bindidx1=$bindidx1, bindidx2=$bindidx2, bindlen=$bindlen\n";

    # need to make index relative to start of whole region, not just that
    # portion which was checked, so add the boundoffset to the index, the
    # length is unaffected
    $bindidx1+=$bndoffset;

    # now store all this info
    push(@bindlist,join(":", join(",",$bindidx1,$bindlen), $matched));
  }

  @bindlist;
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

  print STDERR <<EOF;
  Synopsis:
  	[perl] bindmatch.pl code-file TF-file [(+|-)bound]
 
  Args:
      code-file - A config file that contains the bind code table, giving
      		  a mapping from codons to resource-types, and associated
      		  user-defined functions for resources that decode the
      		  following codons into the attributes and settings for that
      		  resource. Note that the config file is responsible for
      		  creating '$BindCodeTable' which contains the top level
      		  (resource-attribute) bind code, and for the creation of
      		  the subroutines which further decode the resource's
      		  attributes and settings.
  	TF-file	- a file that contains the transcription factors (and their
  		  distance from source) to be matched against the regulatory
  		  regions for binding
 	bound	- specifies bound in bases downstream (+), for repressor, or
 		  upstream (-), for enhancer, of promoter (preceding or
 		  following region, respectively) on where elements may bind
 		  to the regulatory region
 
  Inputs:
        (stdin)   - lines of regulatory regions in base 4
  	(TF-file) - lines of TFs in format: type "," dist-from-src (binary)
  		      "," binding-seq (base 4)
 		    type may be (M|m)orphogen, (C|c)ytoplasm or (L|l)ocal
 		    (only first character is used to identify type). Locals
 		    should have an empty distance from source field, otherwise
 		    it is ignored. For Cytoplasm type TFs, which includes
 		    preplaced morphogens, these will have a binary distance
 		    from source. Gene product morphogens have a spread from
 		    source field, indicated by a '?', which mean that all
 		    possible distances from source (0-3) should be generated.
 		    Any sequences on a regulatory region that match the
 		    dist+bind sequences of these TFs are added to the binding
 
  Outputs: (stdout) 
  		- lines of: region-number ':' (spaces) offset-in-region
  		    (spaces) length of binding match ':' (spaces) followed by
  		      comma delimited binding resource, attribute, [ setting1,
  		      [ setting2, ... ] ]
  		  TFs are also converted to resource,attribute,settings format
  		  TF,TF,(dist-base2),binding-sequence-base4
  		  Note that the offset is actually for the (settings) end of
  		  the resource, as resources bind in reverse; and the
  		  dist-from-source field will be empty for local TFs or
  		  morphogens at their source.
 
  Description:
 		bindmatch takes regions (from stdin), comprised of bases
 		in the alphabet [0-3] (equivalent to DNA [TCAG]), and finds
 		what resources and TFs bind, where, and the length of the
 		binding site.
 		Note that binding is done by a 'folding' of the chromosome
 		region, such that every 3rd base is ommitted. Hence binding
 		lengths will be longer than the coded resources or TFs
 		themselves, and as well as this, the binding length includes
 		the prepended binary distance-from-source sequence for TFs.
 		Note also that resources that lack settings (due to running
 		out of bases to match against) aren't properly dealt with
 		here so, their entries should be ignored.
 
  See Also:	egGeneticCode.conf for a more detailed description of
  		the genetic code table and resources' decode functions.
 		The bind code table is based largely on the genetic code.
 
EOF

}





