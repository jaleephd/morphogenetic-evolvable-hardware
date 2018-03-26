
# resattrset2jbits.pl by J.A. Lee, December 2003
# 			(added -n option, Jan 2004)
#
#  Synopsis:
#  [perl] resattrset2jbits.pl [-d] [-c | -n] resToJBitsConf outfilename outfext
# 
#  Args:
#  		-d		- output extra info for debugging to STDERR
#  		-c		- input/output is cytoplasmic determinants
#  		-n		- input/output is non-developmental chromosome
#		conv2JBitsConf	- configuration file that contains the
#      				  user-defined functions for converting from
#      				  resources with attributes and settings, into
#      				  JBits bits and value constants where possible
#		outfilename	- the basename for the output converted chrom.
#		outfext		- the extension for the output converted chrom
#				  output files will have names of:
#				  	outfilename_CHROMOSOME#.outfext
#
#  Inputs: (stdin)
#  		- lines of:
#  		    if -c option:
#			cellrow, cellcol: cytodets
#		        - cellrow, cellcol identifies which cell to
#		          instantiate the cytoplasmic determinant (cytodets)
#		        - cytodets is in the form of:
#		   	    Virtex-resource,attribute,setting1,setting2, ...
#		          or
# 			    "TF","Local"/"Morphogen"/"Cytoplasm",spread,bindseq
# 		    if -n option
# 		        chromno: horizmove,vertmove,slicemove: details
# 		        - horizmove and vertmove are one of 0,+1,-1;
# 		          indicating respectively no movement, move +1 CLB,
# 		          move -1 CLB
#		  	- slice-move is one of "0", "1", ".", "-";
#		  	  indicating respectively slice 0, slice 1, same
#		  	  slice, swap slice
#		        - details is a list of semi-colon delimited nondevdets
#		  	  in the format:
#		   	    Virtex-resource,attribute,setting1,setting2, ...
#		    otherwise (default option):
#			chromno: geneno,gstart: feat,+/-offset,length: details
#		  - chromno identifies which chromosome according to its
#		    occurence in the input.
#		  - geneno gives the number of the gene starting with 1 at
#		    the 5' end of the chromosome
#		  - gstart gives the start location of the gene's coding
#		    region on the chromosome
#		  - feat is one of "gene", "prom", "enhb", "repb"; which
#		    indicate, respectively, gene coding region, promoter,
#		    enhancer binding site, repressor binding site
#		  - offset gives the distance in bases upstream (-) or
#		    downstream (+) from the gene start
#		  - length is the coding/binding/sequence length in bases
#		  - details is a list of semi-colon delimited elements, with
#		    each element itself being comma-delimited.
#		      feature	    detail		element
#		       prom	sequence
#		       gene	elem1;elem2;...	   offset,len,elemdets
#		       enhb	elem1;elem2;...    offset,len,elemdets
#		       repb	elem1;elem2;...    offset,len,elemdets
#		    where elemdets is the details of that element, in the
#		    form of:
#		    	Virtex-resource,attribute,setting1,setting2, ...
#		    or
# 			"TF", "Local"/"Morphogen"/"Cytoplasm", spread, bindseq
#
#  Outputs: to files:
#		     (-c option) outfilename.outfext
#		     (otherwise) outfilename_CHROMOSOME#.outfext
#		  output file(s) are in the same format to the input, except
#		  minus the leading chromosome number for decoded chromosomes,
#		  in lines of:
#			(-c option)
#				cellrow, cellcol: details
#			(-n option)
# 		        	horizmove,vertmove,slicemove: details
#			(otherwise)
#				geneno,gstart: feat,+/-offset,length: details
#		  all fields remain the same as specified for inputs, except
#		  for cytodets/nondevdets/elemdets, which becomes:
#			resource-type "=" resource-details
#		    where resource-type is one of: TF, jbits, SliceRAM, LUT,
#		    LUTIncrFN
#		    and resource-details for TF is in the form of:
#			TF-type,spreadfromsrc,bind-seq
#		    and for SliceRAM is in the form of:
#			slice0class,bitsconst1,bitsconst2,../
#			slice1class,bitsconst1,bitsconst2,..
#		    and for jbits, LUT, LUTIncrFN is in the form of:
#			bits "," val [ "/" bits "," val ]
#		    where bits is a com.xilinx.JBits.Virtex.Bits package
#		    bits constant, and val is a value constant from the same
#		    package for a jbits resource-type, a 16-bit binary digit
#		    for a LUT (already inverted for storage), or an expression
#		    in the form: set "," op "," lines; for a LUTIncrFN
#		    Note that when the optional "/" is used, then the 2 sets
#		    of bits,val will be specifying slice-specific constants
#		    eg: S0G4.S0G4, S0G4.OUT_EAST6 / S1G4.S1G4, S1G4.OUT_WEST1 
#
#  Description:
#		resattrset2bitsval.pl either takes a cytoplasmic determinants
#		file, or a decoded set of chromosomes, and outputs a file (or
#		one for each chromosome) which has basically the same format
#		but with the resource,attribute,settings elements replaced
#		with JBits bits and value constants, for use with a java/JBits
#		evolvable hardware generation program
#
#  See Also:

#### Global declarations

### define constants

# this gives the prefix to add to resource names for giving the appropriate
# conversion function to call in the provided conversion configuration file
$CONVJBITS_FN_PREFIX="conv2jbits_";

# feature types, these must be the same as those expected in the input
$PROM="prom";
$ENHB="enhb";
$REPB="repb";
$GENE="gene";


### arrays and vars


### end Global declarations


($DEBUG,$option,$conv2jbitsconf,$outfname,$outfext)=scanArgs();

if ($option eq "CYTO") { $CYTO=1; }
if ($option eq "NDEV") { $NDEV=1; }

# read in the subroutines for encoding each resource's attributes and settings
# as JBits bits and value constants

do $conv2jbitsconf;


@cytodets=();
@chrom=();
$ocno=1;
$dg=0;			# number of deleted genes in this chrom
$delgene=-1;		# to remove deleted gene's promoter & bindsites

# read in the decoded chromosomes, and re-encode the encoded details for
# gene coding regions and bind sites, with JBits bits and value constants
# (where possible), and discard incomplete details

while (<STDIN>) {
  chop;
  # ignore lines that are empty or start with a comment "#" character
  next if ((/^\s*$/) || (/^\s*#/));

  if ($CYTO) {
    # xcoord, ycoord: details
    ($coords,$details)=split(/\s*:\s*/);
    ($res,$attr,@settings)=split(/\s*,\s*/,$details);
if ($DEBUG) { print STDERR "read: ($coords): $details...\n"; }
    # if resource settings JBits conversion function exists then call it
    $convfunction=$CONVJBITS_FN_PREFIX . $res;
    if (!exists($main::{$convfunction})) {
      die("Error: Unable to convert $res - '$convfunction' not found!\n" .
	    "coords: $coords, details: $details\n");
    }
    $conv=&{$convfunction}($attr,@settings);

    # if res attr settings were complete, then will be converted, so save
    if ($conv ne "") { 
      push(@cytodets,join(":",$coords,$conv));
if ($DEBUG) { print STDERR "converted details to: $conv...\n"; }
    }
    next; # coz we aren't processing a chromosome
  }

  if ($NDEV) {
    # chromno: horizmove,vertmove,slicemove: nondevdets
    ($CNO,$movedet,$details)=split(/\s*:\s*/);
if ($DEBUG) { print STDERR "read: ($CNO,($movedet),$details)...\n"; }
  } else {
    # chromno: geneno,gstart: feat,+/-offset,length: details
    ($CNO,$genedet,$featdet,$details)=split(/\s*:\s*/);
    ($geneno,$genestart)=split(/\s*,\s*/,$genedet);
    ($feat,$featoffset,$featlen)=split(/\s*,\s*/,$featdet);
if ($DEBUG) { print STDERR "read: ($CNO,($genedet),$featdet,details)...\n"; }
  }

  if ($CNO>$ocno) {		# next chrom so write processed chrom to file
    writeChromToFile($outfname,$outfext,$ocno,@chrom);
    $ocno=$CNO;
    @chrom=();
    $dg=0;
    $delgene=-1;
  }

  if ($NDEV) {
    $convdets=convert2jbits("NDEV",$details,0,0);
    # we can allow empty convdets - this will allow moves without sets
    push(@chrom,join(":",$movedet,$convdets));
  } else {
    if ($geneno==$delgene) {
      $convdets="";
    } else {
      $convdets=convert2jbits($feat,$details,$geneno,$featoffset);
    }
    if ($convdets ne "") {
      # note that if using a subset of resources, a completely specified
      # resource may not be converted if it isn't supported within the subset
      if ($dg>0) { $genedet=join(",",$geneno-$dg,$genestart); }
      push(@chrom,join(":",$genedet,$featdet,$convdets));
    } else {
if ($DEBUG && $geneno!=$delgene) { print STDERR "removing $featdet lacking any complete elements after conversion ($details), in chrom $CNO, gene $geneno\n"; }
if ($DEBUG && $geneno==$delgene) { print STDERR "removing $featdet from empty gene $geneno in chrom $CNO\n"; }
      # note we rely on the fact that the gene coding region comes before
      # promoters and regulatory regions - this allows us to remove all
      # the junk gene's related sites
      if ($feat eq "gene") {
	$dg++;
	$delgene=$geneno;
if ($DEBUG) { print STDERR "removing gene $geneno in chrom $CNO\n"; }
      }
    }
  }

#printA("chrom",@chrom);
}


if ($CYTO) {
  writeCytoToFile($outfname,$outfext,@cytodets);
} elsif ($#chrom>=0) {
  # process last gene/chromosome when hit EOF
  writeChromToFile($outfname,$outfext,$ocno,@chrom);
}




#################  end main()   ####################

# re-encode the details for gene coding regions and bind sites, with JBits
# bits and value constants (where possible) and discard incomplete details
# promoters details don't need to be modified

sub convert2jbits
{
  my ($type,$details,$gno,$featoff) = @_;
  my ($offset,$len,$res,$attr,@settings);
  my ($e,$conv,$convfunction,@convlist,@elems);
  my $convresult;

  if ($type eq "NDEV") {
    @elems=split(/;/,$details); # details is list of semicolon delimited elems
    foreach $e (@elems) { 
      #  each element is comma-delimited and comprised of:
      #		resource,attribute,setting1,setting2, ...
      ($res,$attr,@settings)=split(/,/,$e);

      # if resource settings JBits conversion function exists then call it
      $convfunction=$CONVJBITS_FN_PREFIX . $res;
      if (!exists($main::{$convfunction})) {
        die("Error: Unable to convert $res - '$convfunction' not found!\n" .
	  "chromosome $CNO: res,attr,settinglist=\n".$e);
       }
      $conv=&{$convfunction}($attr,@settings);
      # if res attr settings were complete, then will be converted, so save
      if ($conv ne "") { push(@convlist,$conv); }
    }
    $convresult=join(";",@convlist);
if ($DEBUG) { print STDERR "converted details for $type to: $convresult...\n"; }
  } elsif ($type eq $GENE || $type eq $ENHB || $type eq $REPB) {
    @elems=split(/;/,$details); # details is list of semicolon delimited elems
    foreach $e (@elems) { 
      #  each element is comma-delimited and comprised of:
      #		offset,len,resource,attribute,setting1,setting2, ...
      ($offset,$len,$res,$attr,@settings)=split(/,/,$e);

      # if resource settings JBits conversion function exists then call it
      $convfunction=$CONVJBITS_FN_PREFIX . $res;
      if (!exists($main::{$convfunction})) {
        die("Error: Unable to convert $res - '$convfunction' not found!\n" .
	  "chromosome $CNO, gene $gno: offset,len,res,attr,settinglist=\n".$e);
       }
      $conv=&{$convfunction}($attr,@settings);
      # if res attr settings were complete, then will be converted, so save
      if ($conv ne "") {
	push(@convlist,"$offset,$len,$conv");
      }
    }
    $convresult=join(";",@convlist);
if ($DEBUG) { print STDERR "converted details for $type to: $convresult...\n"; }
  }
  elsif ($type eq $PROM) {
    # details for the promoter is: offset,len,seq (offset is 0 - only 1 prom)
    # but no conversion needs to be done for it, so just return it as is
    $convresult=$details;
  }
  else {
    die("Invalid feature type ($type) in chromosome $CNO, gene $gno, feature offset $featoff");
  }

  $convresult;
}





sub writeChromToFile {
  my ($outfname,$outfext,$chromno,@chrom) = @_;
  my $chromosome=join("\n",@chrom) . "\n";

  # filename will be the supplied name with chromosome num as its suffix
  $fname=sprintf("%s_%d.%s",$outfname,$chromno,$outfext);

  open(OFILE,">$fname") || die ("Unable to create file '$fname'\n");
  print OFILE $chromosome;
  close(OFILE);
if ($DEBUG) { print STDERR "wrote file '$fname'\n"; }
}



sub writeCytoToFile {
  my ($outfname,$outfext,@cytodets) = @_;
  my $cytoplasmicdets=join("\n",@cytodets) . "\n";

  # filename will be the supplied name
  $fname="$outfname.$outfext";

  open(OFILE,">$fname") || die ("Unable to create file '$fname'\n");
  print OFILE $cytoplasmicdets;
  close(OFILE);
if ($DEBUG) { print STDERR "wrote file '$fname'\n"; }
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
  my ($argc);
  my ($debug,$type,$conv2jbitsconf,$outfname,$outfext);

  $argc=$#ARGV+1;
  $debug=0;
  $type="";

  if ($argc>0 && $ARGV[0] eq "-d") { $debug=1; shift @ARGV; $argc--; }

  if ($argc>0 && $ARGV[0] eq "-c") {
    $type="CYTO"; shift @ARGV; $argc--;
  } elsif ($argc>0 && $ARGV[0] eq "-n") {
    $type="NDEV"; shift @ARGV; $argc--;
  }

  unless ($argc==3 && !($ARGV[0]=~/^-/)) {
    printUsage();
    exit(1);
  }

  $conv2jbitsconf=$ARGV[0];
  $outfname=$ARGV[1];
  $outfext=$ARGV[2];

  unless (-f $conv2jbitsconf) {
    die("Can't find config file ($conv2jbitsconf)\n");
  }

  ($debug,$type,$conv2jbitsconf,$outfname,$outfext);
}



sub printUsage {

  print STDERR <<EOF;
  Synopsis:
  [perl] resattrset2jbits.pl [-d] [-c | -n] resToJBitsConf outfilename outfext
 
  Args:
  		-d		- output extra info for debugging to STDERR
  		-c		- input/output is cytoplasmic determinants
#  		-n		- input/output is non-developmental chromosome
		conv2JBitsConf	- configuration file that contains the
      				  user-defined functions for converting from
      				  resources with attributes and settings, into
      				  JBits bits and value constants where possible
		outfilename	- the basename for the output converted chrom.
		outfext		- the extension for the output converted chrom
				  output files will have names of:
				  	outfilename_CHROMOSOME#.outfext

  Inputs: (stdin)
  		- lines of:
  		    if -c option:
			cellrow, cellcol: cytodets
		        - cellrow, cellcol identifies which cell to
		          instantiate the cytoplasmic determinant (cytodets)
		        - cytodets is in the form of:
		   	    Virtex-resource,attribute,setting1,setting2, ...
		          or
			    "TF","Local"/"Morphogen"/"Cytoplasm",spread,bindseq
 		    if -n option
 		        chromno: horizmove,vertmove,slicemove: details
 		        - horizmove and vertmove are one of 0,+1,-1;
 		          indicating respectively no movement, move +1 CLB,
 		          move -1 CLB
		  	- slice-move is one of "0", "1", ".", "-";
		  	  indicating respectively slice 0, slice 1, same
		  	  slice, swap slice
		        - details is a list of semi-colon delimited nondevdets
		  	  in the format:
		   	    Virtex-resource,attribute,setting1,setting2, ...
		    otherwise (default option):
			chromno: geneno,gstart: feat,+/-offset,length: details
		  - chromno identifies which chromosome according to its
		    occurence in the input.
		  - geneno gives the number of the gene starting with 1 at
		    the 5' end of the chromosome
		  - gstart gives the start location of the gene's coding
		    region on the chromosome
		  - feat is one of "gene", "prom", "enhb", "repb"; which
		    indicate, respectively, gene coding region, promoter,
		    enhancer binding site, repressor binding site
		  - offset gives the distance in bases upstream (-) or
		    downstream (+) from the gene start
		  - length is the coding/binding/sequence length in bases
		  - details is a list of semi-colon delimited elements, with
		    each element itself being comma-delimited.
		      feature	    detail		element
		       prom	sequence
		       gene	elem1;elem2;...	   offset,len,elemdets
		       enhb	elem1;elem2;...    offset,len,elemdets
		       repb	elem1;elem2;...    offset,len,elemdets
		    where elemdets is the details of that element, in the
		    form of:
		    	Virtex-resource,attribute,setting1,setting2, ...
		    or
 			"TF", "Local"/"Morphogen"/"Cytoplasm", spread, bindseq

  Outputs: to files:
  		     (-c option) outfilename.outfext
  		     (otherwise) outfilename_CHROMOSOME#.outfext
		  output file(s) are in the same format to the input, except
		  minus the leading chromosome number for decoded chromosomes,
		  in lines of:
			(-c option)
				cellrow, cellcol: details
			(-n option)
 		        	horizmove,vertmove,slicemove: details
			(otherwise)
				geneno,gstart: feat,+/-offset,length: details
		  all fields remain the same as specified for inputs, except
		  for cytodets/nondevdets/elemdets, which becomes:
			resource-type "=" resource-details
		    where resource-type is one of: TF, jbits, SliceRAM, LUT,
		    LUTIncrFN
		    and resource-details for TF is in the form of:
			TF-type,spreadfromsrc,bind-seq
		    and for SliceRAM is in the form of:
			slice0class,bitsconst1,bitsconst2,../
			slice1class,bitsconst1,bitsconst2,..
		    and for jbits, LUT, LUTIncrFN is in the form of:
			bits "," val [ "/" bits "," val ]
		    where bits is a com.xilinx.JBits.Virtex.Bits package
		    bits constant, and val is a value constant from the same
		    package for a jbits resource-type, a 16-bit binary digit
		    for a LUT (already inverted for storage), or an expression
		    in the form: set "," op "," lines; for a LUTIncrFN
		    Note that when the optional "/" is used, then the 2 sets
		    of bits,val will be specifying slice-specific constants
		    eg: S0G4.S0G4, S0G4.OUT_EAST6 / S1G4.S1G4, S1G4.OUT_WEST1 

  Description:
		resattrset2bitsval.pl either takes a cytoplasmic determinants
		file, or a decoded set of chromosomes, and outputs a file (or
		one for each chromosome) which has basically the same format
		but with the resource,attribute,settings elements replaced
		with JBits bits and value constants, for use with a java/JBits
		evolvable hardware generation program

EOF

}

 
