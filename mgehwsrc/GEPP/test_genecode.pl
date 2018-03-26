
# test_genecode.pl by J.A. Lee, Sept 2004 (based on decodegenes.pl)
#
# Synopsis:
# 	[perl] test_genecode.pl [-f] [-b] codefile
#
# Args:
#     -f	- full output
#     -b	- use bindcode rather than gene code
#      codefile - config file that contains the genetic/bind code table
#
# Inputs: (stdin)
# 		- lines of codons in base 4
#
# Outputs: (stdout) 
# 		- lines of: gene/bindsite # ': ' [index (spaces) length ': ']
# 		  followed by comma delimited: resource, attribute,
# 		  [ setting1, [ setting2, ... ] ]
#
# Description:
#  		test for gene/bind code tables and conversion routines


#### Global declarations


$GeneticCodeTable="";	# this gets read in from file, and decoded
$BindCodeTable=""; # this gets read in from file, and decoded

# the following are the decoded Genetic/Bind CodeTable
%CodonDecodeMap=();			# codon to resource info lookup
%ResourceToCodonTable=();		# lookup a resource's codon code


### end Global declarations



$argc="".@ARGV;

$FULLOUT=0;
$BIND=0;

if ($argc>0 && $ARGV[0] eq "-f") {
  $FULLOUT=1;
  shift; $argc--;
}

if ($argc>0 && $ARGV[0] eq "-b") {
  $BIND=1;
  shift; $argc--;
}

unless ($argc>0 && !($ARGV[0]=~/^-/)) {
  printUsage();
  exit(1);
}


# read in the gentic code table (stored in $GenticCodeTable), and the
# associated subroutines for decoding each resource's attributes and settings

$codefile=$ARGV[0];
unless (-f $codefile) { die("Can't find genetic/bind code file ($codefile)\n"); }
do $codefile;


@tablelines=split(/\n/,(($BIND)? $BindCodeTable : $GeneticCodeTable));

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



# read genes in, decode them and print out the decoded information

$geneno=0;

while (<STDIN>) {
  chop;
  if ($_) {
    ++$geneno;
    @codons=getCodons($_);
    @decodelist=decodeCodons(@codons);
    printDecoded($geneno, @decodelist);
  }
}

#printA("codons",@codons);

### end main()


# extract all the codons

sub getCodons {
  my ($seq) = @_;
  my @codonlist;
  my $codon;
  my $i=0;

  if (length($seq) % 3 != 0) {
    die("sequence has trailing base(s).. should only be codons!\n");
  }

  while ($i<length($seq)) {
    $codon=substr($seq,$i,3);
    push(@codonlist,$codon);
    $i+=3;
  }

  @codonlist;
}



sub printDecoded {
  my ($geneno, @decodelist) = @_;
  my ($decoded,$loc,$res,$cidx,$clen,$idx,$len,$gn);

  foreach $decoded (@decodelist) {
    ($loc,$res)=split (/:/,$decoded);
    ($cidx,$clen)=split (/,/,$loc);
      $gn=sprintf("%9d",$geneno);
      $idx=sprintf("%12d",($cidx*3)+3); # add 3 for (removed) start codon
      $len=sprintf("%12d",$clen*3);
    print "$gn: ";
    print "$idx  $len: " if ($FULLOUT);
    print "$res\n";
  }
}



# decodeCodons: takes an array of codons, and decodes these using the genetic
# code table. It returns an array of strings, each of which is a decoded
# resource its attribute and settings separated by commas.

sub decodeCodons {
  my (@codonlist) = @_;
  my @reslist;
  my (@codons, @varlenstops, @settings);
  my ($resource, $attributes, $attr, $len, $setting, $reslt);
  my ($numcodons,$minl,$i,$j,$ci,$clen);
  my $codon;

  $i=0;
  $numcodons=$#codonlist+1;
  while ($i<$numcodons) {
    # decode a resource and its attribute
    $codon=$codonlist[$i];
    ($resource,$attr,$len,@varlenstops)=lookupCodon($codonlist[$i]);
    $i++;
#print STDERR "i=$i, codon=($codon), attr=$attr, len=$len\n";

    # if it's an Undefined resource , then don't try to decode it
    if ($resource eq "Undef" || $resource eq "") { next; }

# its var len, need to find where its stop is - if no stop, then its the
# end of the gene. note length includes its stop codon
    if ($len=~/^([0-9]*)[?]/) { 		# variable len
      $minl=$1;
      $minl=($1 eq "") ? 0 : $1;		# may be no minimum length
      $len=$numcodons-$i;
      for ($j=$i+$minl; $j<$numcodons; $j++) {  # skip minlen codons before
        if (isIn($codonlist[$j],@varlenstops)) {# checking for stop
	  $len=$j-$i;
          last;
	}
      }
      $len++;					# add one for stop marker
    } elsif ($len=~/^[0-9]+$/) {		# fixed length
      if ($i+$len>$numcodons) { $len=$numcodons-$i; }
    } else {
      die("Error: Invalid length specification ($len) for $resource, $attr\n");
    }
    @codons=@codonlist[$i .. $i+$len-1];

    # now decode the resource's settings
    ($attribute,@settings)=processResource($resource,$attr,@codons);

    # idx & len need to account for preceding processed resource codon
    $ci=$i-1; $clen=$len+1;
    # store as a comma-separated string, preceded by location & length
    $reslt= "$ci,$clen\:" . $resource . "," . $attribute . ",";
    foreach $setting (@settings) {
      # if resource settings got cut off by a premature end marker then
      # setting may be an empty string, so we skip it
      $reslt .= $setting . "," if ($setting ne "");
     }
    chop($reslt);			# remove trailing comma
    # store the decoded resource/attribute/settings
    push (@reslist,$reslt);

    # and move past processed codons
    $i+=$len;
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

  $decodefunction=(($BIND) ? "bind_" : "decode_") . $resource;
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

  $res=$CodonDecodeMap{$codon} || die("undefined codon ($codon) in genetic code! Exiting..\n");
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
  	[perl] test_genecode.pl [-f] [-b] codefile
 
  Args:
      -f	- full output
      -b	- use bindcode rather than gene code
      codefile  - config file that contains the genetic/bind code table
 
  Inputs: (stdin)
  		- lines of genes in base 4
 
  Outputs: (stdout) 
 		- lines of: gene/bindsite # ': ' [index (spaces) length ': ']
 		  followed by comma delimited: resource, attribute,
 		  [ setting1, [ setting2, ... ] ]
 
  Description:
  		test for gene/bind code tables and conversion routines

  ";
}





