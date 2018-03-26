
# fixedres.pl by J.A. Lee, May 2005
#
#  Synopsis:
#  [perl] fixedres.pl [ -d ] (-mux | -6200 | -lut | -all)
# 
#  Args:
#  		-d		output debugging info to STDERR
#		-mux		FPGA design uses fixed routing (muxes),
#				only LUTs can be modified
#		-6200		FPGA design uses fixed (output) routing,
#				only LUTs and LUT inputs can be modified
#		-lut		FPGA design uses fixed LUT (functions),
#				only muxes can be modified
#		-all		no FPGA resources can be modified
#				only TFs can be produced
#
#  Inputs: (stdin)
#  		- lines of decoded and converted chromosomes in format:
#			geneno,gstart: feat,+/-offset,length: details
#		    or for directly encoded GA chromosome in format:
#		    	"@,@,@: " or "+,+,+: " elemdets1;elemdets2;..
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
#			resource-type "=" resource-details
#		    where resource-type is one of: TF, jbits, SliceRAM, LUT,
#		    LUTIncrFN, LUTActiveFN
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
#		    in the form: set "," op "," lines; for a LUTIncrFN, or
#		    op "," lines for a LUTActiveFN
#		    Note that when the optional "/" is used, then the 2 sets
#		    of bits,val will be specifying slice-specific constants
#		    eg: S0G4.S0G4, S0G4.OUT_EAST6 / S1G4.S1G4, S1G4.OUT_WEST1 
#
#  Outputs:
#  		- lines in the same format, but with some gene elements
#  		  removed
#
# Description:
# 		fixedres.pl is used to prevent genes expressing 'proteins'
# 		that will modify the (Mux or LUT) components of the FPGA's
# 		configuration. To do this, all undesired configuring
# 		'proteins' are removed from the coding region of all genes
# 		in the chromosome, and any resulting empty genes are removed.
# 		Bind sites are not modified at all, unless they belong to a
# 		deleted gene, so that genes are able to be activated by the
# 		fixed settings. This approach can also be used by the direct
# 		encoding GA approach.
# 		Note that the -6200 option fixes the (output) routing muxes,
# 		while the LUT's function and input muxes are able to be
# 		configured - this gives a logic element with a _similar_
# 		virtual architecture to the 6200 series FPGAs. 

$DEBUG=0;

$argc=$#ARGV+1;

if ($argc>0 && $ARGV[0] eq "-d") { $DEBUG=1; shift; $argc--; }

$FIXEDMUX=$FIXED6200=$FIXEDLUT=0;

if ($argc>0) {
  if ($ARGV[0] eq "-mux") {
    $FIXEDMUX=1;
  } elsif ($ARGV[0] eq "-6200") {
    $FIXED6200=1;
  } elsif ($ARGV[0] eq "-lut") {
    $FIXEDLUT=1;
  } elsif ($ARGV[0] eq "-all") {
    $FIXEDMUX=$FIXEDLUT=1;
  } else {
    printUsage(); exit(1);
  }
} else {
  printUsage(); exit(1);
}



# read in the decoded converted chromosome, and for each gene's coding region
# remove any entries that could modify the fixed parts of the FPGA's
# configuration

$delgene=-1; # genenumber to delete if removed everything from coding region

while (<STDIN>) {
  chop;
  # ignore lines that are empty or start with a comment "#" character
  unless ((/^\s*$/) || (/^\s*#/)) {
    # geneno,gstart: feat,+/-offset,length: details
    # or (GA) "@,@,@"|"+,+,+" ":" details
    ($genedet,$featdet,$details)=split(/\s*:\s*/);
    $geneno=substr($genedet,0,index($genedet,","));
    if ($genedet eq "@,@,@" || $genedet eq "+,+,+") {
      print STDERR "detected GA. moving featdet to details\n" if ($DEBUG);
      $details=$featdet;
      # no featdet for direct encoding GA, just use it as a flag
      $featdet="GA";
    }
    print STDERR "genedet=$genedet; featdet=$featdet; details=$details\n" if ($DEBUG);
    # check if it's a gene (coding region) or direct encoding GA
    # enhb's and repb's and prom's are ignored here
    if ($featdet=~/^gene/ || $featdet eq "GA") {
      $delgene=-1; # new gene - reset delete gene flag
      print STDERR "processing coding region/direct encoding\n" if ($DEBUG);
      # details are in the form: elem1;elem2;...
      # with each elem being in the form of: offset,len,elemdets
      # elemdets is in the form of: resource-type "=" resource-details
      if ($FIXEDMUX) {
	print STDERR "removing jbits and SliceRAM types from details..\n" if ($DEBUG);
	# remove all elements with a Mux resource-type (jbits or SliceRam)
	if ($featdet ne "GA") {
	  # note that these will always be downstream of the gene start,
	  # so there is no sign in front of the offset
	  $details=~s/\d+,\d+,jbits=[^;]*//g;
	  $details=~s/\d+,\d+,SliceRAM=[^;]*//g;
	} else {
	  $details=~s/jbits=[^;]*//g;
	  $details=~s/SliceRAM=[^;]*//g;
	}
      } elsif ($FIXED6200) {
	print STDERR "removing jbits out* and SliceRAM types from details..\n" if ($DEBUG);
	# remove all jbits OUT[0-7] and OutMuxToSingle routing muxes
	# and remove all SliceRam configurations
	if ($featdet ne "GA") {
	  $details=~s/\d+,\d+,jbits=O[Uu][Tt][^;]*//g;
	  $details=~s/\d+,\d+,SliceRAM=[^;]*//g;
	} else {
	  $details=~s/jbits=O[Uu][Tt][^;]*//g;
	  $details=~s/SliceRAM=[^;]*//g;
	}
      }
      if ($FIXEDLUT) {
	print STDERR "removing LUT types from details..\n" if ($DEBUG);
	# remove all elements with a LUT* resource-type
	if ($featdet ne "GA") {
	  $details=~s/\d+,\d+,LUT[a-zA-Z]*=[^;]*//g;
	} else {
	  $details=~s/LUT[a-zA-Z]*=[^;]*//g;
	}
      }
      # now clean up the left behind semicolons
      print STDERR "cleaning up removed elements..\n" if ($DEBUG);
      $details=~s/;[;]+/;/g;	# removed a middle element
      $details=~s/^;//;		# removed first gene element
      $details=~s/;$//;		# removed last gene element
    }

    # now output the entry with any "offending" entries removed
    if ($featdet eq "GA") {
      # note: haven't tested for empty GA details - this shouldn't occur!
      print STDERR "outputing fixed GA details..\n" if ($DEBUG);
      print $genedet . ":" . $details . "\n";
    } else { # can be gene, prom, enhb or repb
      # MG system expects non-empty genes
      if ($featdet=~/^gene/ && $details eq "") {
	$delgene=$geneno;
        print STDERR "removing now empty gene $delgene..\n" if ($DEBUG);
        print STDERR "removed gene: $delgene coding region..\n" if ($DEBUG);
      } elsif (!($featdet=~/^gene/) && $delgene==$geneno) {
        print STDERR "removed gene: $delgene" . "'s $featdet region..\n" if ($DEBUG);
      } else {
        print STDERR "outputing 'fixed' gene: $geneno $featdet details..\n" if ($DEBUG);
        print $genedet . ":" . $featdet . ":" . $details . "\n";
      }
    }
  } else {
    print STDERR "skipping line:$_\n" if ($DEBUG);
    print $_ . "\n";
  }
}



sub printUsage {

  print STDERR <<EOH;
   Synopsis:
   [perl] fixedres.pl [ -d ] (-mux | -6200 | -lut | -all)

   Args:
  		-d		output debugging info to STDERR
 		-mux		FPGA design uses fixed routing (muxes),
 				only LUTs can be modified
		-6200		FPGA design uses fixed (output) routing,
				only LUTs and LUT inputs can be modified
 		-lut		FPGA design uses fixed LUT (functions),
 				only muxes can be modified
 		-all		no FPGA resources can be modified
 				only TFs can be produced

   Inputs: (stdin)
   		- lines of decoded and converted chromosomes in format:
 			geneno,gstart: feat,+/-offset,length: details
		    or for directly encoded GA chromosome in format:
		    	"@,@,@: " or "+,+,+: " elemdets1;elemdets2;..
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
 			resource-type "=" resource-details
 		    where resource-type is one of: TF, jbits, SliceRAM, LUT,
 		    LUTIncrFN, LUTActiveFN
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
		    in the form: set "," op "," lines; for a LUTIncrFN, or
		    op "," lines for a LUTActiveFN
 		    Note that when the optional "/" is used, then the 2 sets
 		    of bits,val will be specifying slice-specific constants
 		    eg: S0G4.S0G4, S0G4.OUT_EAST6 / S1G4.S1G4, S1G4.OUT_WEST1 

   Outputs:
   		- lines in the same format, but with some gene elements
   		  removed

  Description:
 		fixedres.pl is used to prevent genes expressing 'proteins'
 		that will modify the (Mux or LUT) components of the FPGA's
 		configuration. To do this, all undesired configuring
 		'proteins' are removed from the coding region of all genes
 		in the chromosome, and any resulting empty genes are removed.
 		Bind sites are not modified at all, unless they belong to a
 		deleted gene, so that genes are able to be activated by the
 		fixed settings. This approach can also be used by the direct
 		encoding GA approach.
 		Note that the -6200 option fixes the (output) routing muxes,
 		while the LUT's function and input muxes are able to be
 		configured - this gives a logic element with a _similar_
 		virtual architecture to the 6200 series FPGAs. 

EOH
}

