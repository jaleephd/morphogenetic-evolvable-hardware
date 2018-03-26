
			      README.txt

		for Gene Expression Chromosome Preprocessor

			initial version 24/6/2003
			last modified 13/9/2004

			      J.A. Lee



Overview:
--------

This directory contains scripts that can be used as a preprocessor to extract
all the relevant information from base 4 chromosomes for use in a single
chromosome developmental system driven by gene expression.

decodechrom.pl is the main script, which would generally be the one run by the
end user, however, the other scripts can also be used independently.

The information extracted by the chromosome preprocessing scripts, for use
with a developmental system, includes gene locations, the decoded gene coding
region, relative location of the gene's promoter (if any), relative locations
of gene regulators (if any), which may be either enhancers or repressors, and
what binds to these sites to activate them.

Gene products are of 2 types: transcription factors (TFs) used solely for
activating or repressing gene expression; and instantiable resources, having
associated attributes and settings. The construction of these resources into a
construct with the desired functionality is the aim of evolution and
development; TFs are just used to aid the developmental process.

Gene control in the targeted developmental system is achieved through
transcription regulation, achieved by the binding of TFs or resources, with
specific motifs, to the binding sites of the gene's regulatory regions. All
the TFs and resources that may bind to each bind site to effect gene
transcription are elbaorated by the chromosome preprocessor.

Note: decodechrom.pl and bindmatch.pl make the assumption that gene product
morphogen TFs have spread from source in the range 0-3 (in any direction)
as encoded in VirtexGeneticCode.conf and implemented in TFresources.java.
If this is changed (increased), then this will require appropriate changes
to be made here.



Description:
-----------

The function of the chromosome preprocessor is to parse the chromosome,
locating its genes, their coding regions, promoters and binding sites on
regulatory regions. The coding regions are then decoded into a list of gene
products, according to the supplied genetic code, and the binding of resources
and TFs to the gene's binding sites are determined, again using the supplied
genetic code, but here to determine their motifs.

This extracted information can then be used by a developmental system that is
driven by transcription-level gene regulation. This eliminates the need to
re-parse and extract, translate and collate information from the chromosome
more than once. The extracted information can be re-used at each iteration
of the developmental process, and by each cell.

Chromosomes are comprised of genes that code for resources (proteins), and
which may have promoters and regulatory regions that control their expression.
The rest of the chromosome is junk that doesn't participate in gene coding or
control, but may act as a scratch pad for evolution.

Within regulatory regions are located bind sites; these are sites at which TFs
or resources may bind, if their motifs/signatures match, and it is these sites
which are responsible for activating the owning regulator. These regulators
exert either positive (enhancers) or negative (repressors) influence on
transcription of their associated gene, thus increasing or decreasing the
likelyhood of the gene being expressed.

Genes are identified by a start codon, some sequence of coding codons, and are
terminated by a stop codon. Several gene products are able to be encoded
within a single gene coding region (like prokyarotes). However, gene promoters
and regulatory regions act solely on the gene directly downstream, and have no
influence on other genes preceding or following that gene.

Genes produce resources and TFs, and are in turn, regulated by these. TFs are
basically just strings with no meaning outside of the developmental process,
whose sole function is to bind to bind sites in gene regulatory regions to
effect the expression of genes. TFs have no predetermined sequence, and are
totally modifiable by evolution. This is in contrast with resources, which
have a limited number of mappings to gene sequences, according to their
attributes and settings, and have a meaning beyond their function in the
developmental process - ie they are are both gene products (physical or
otherwise) and triggers of gene expression; they may also exist prior to the
commencement of the developmental process.

A Genetic code configuration file needs to be provided to perform the mapping
from codons (triplets of base 4 characters) to resources, their attributes and
settings. This same genetic code is also used to find which resources, with
specific attributes and settings, bind to the DNA sequences in binding sites
located on the genes' regulatory regions.

Cytoplasmic determinants and morphogens, generally transcription factors (TFs)
with symmetry breaking properties or varying distance-from-source, are often
used for triggering patterns of gene expression, and thus development.
However, for locating binding locations of all resources and TFs requires that
all TFs that will occur in the developmental process are known a priori to
prevent exhaustive elaboration of all possible TFs that can bind somewhere,
which would be everywhere due to the nature of TFs binding regions. Hence,
only TFs that are included in the cytoplasmic determinants file, or are
produced by genes in the chromosome, are tested for binding on regulatory
region's binding sites.



Examples:
--------

the following are some examples of how to use decodechrom.pl, the main script
for chromosome preprocessing, noting that the use of the backslash at the
end of the line, is simply for cosmetic purposes - the entire command line
parameters can be put on a single line.

$ perl decodechrom.pl -h

$ cat testfiles/pop.txt | perl decodechrom.pl VirtexGeneticCode.conf

$ cat testfiles/pop.txt | perl decodechrom.pl VirtexGeneticCode.conf \
       testfiles/cytotest.txt

$ cat testfiles/pop.txt | perl decodechrom.pl VirtexGeneticCode.conf \
       testfiles/cytotest.txt pb=100 rb=90 eb=80

$ cat testfiles/pop.txt | perl decodechrom.pl VirtexGeneticCode.conf \
       testfiles/cytotest.txt p:02020,02022 s:000 e:001,002,003


The first example simply provides the synopsis and some 'useful' information.

The second example decodes the chromosomes supplied in the pop.txt file, with
genes decoded and binding of resources to bind sites being determined
according to the genetic code supplied in the file VirtexGeneticCode.conf

The third example also includes cytoplasmic determinants, specified in
testfiles/cytotest.txt. This is needed when TFs are used to trigger
development, as only TFs directly encoded in the genes or specified in this
cytoplasmic file are checked for binding on the binding regions of regulatory
regions.

The fourth example also includes a bound on the distance upstream from the
gene that a promoter will be effective. In this case, promoters further than
100 bases upstream will be ignored (pb=100), which also means that the region
between the promoter and the gene will be treated as junk, rather than as a
regulatory region, and likewise for the region upstream of the promoter. Also
there are bounds on binding regions distances from the promoter: enhancer
bindsites must within 80 bases upstream of the promoter (eb=80); and repressor
bindsites must be within 90 bases downstream of the promoter (rb=90).

The last example replaces the default promoter and start and stop codons with
customised ones.

The default promoter, start and stop codons are modeled on those in nature:
	- default promoter is 0202 (TATA)
	- default start codon is 203 (AUG)
	- default stop codons are:
		- Amber 023 (UAG)
		- OCHRE 022 (UAA)
		- OPAL  032 (UGA)

These can be changed by using the p: s: e: command line parameters, as shown
in the example, which sets the promoter to be either 02020 or 02022, the start
codon to be 000, and the stop codons to be 001,002,003. Note that start and
stop codons must be base-4 numbers with 3 digits.



Description of GE components:
----------------------------

The following attempts to outline the uses of the scripts. Most of the
information contained in this section is gleaned from the online help
of the scripts - which can be seen by issuing the script with a '-h', '-?',
'--help' or invalid command line arguments. Hence for more details on the
usage of the script, run the desired script with this option.

The files included in this distribution are as follows:

  GE_README.txt			(this file)
  decodechrom.pl		main script

  VirtexGeneticCode.conf	genetic code for translation and binding
  VirtexGeneticCodeNoTFs.conf	genetic code w/out TFs - for comparing
				performance of morphogenesis with and
				without TFs, hence TF codons haven't been
				re-allocated to other resources.
  VirtexGeneticCodeLUTsmore.conf genetic code with TF codons re-allocated
				 to LUT configuring resources. the three
				 variants on this that follow are for
				 comparing performance of different LUT
				 encodings:
  VirtexGeneticCodeBitLUTs.conf		as above but only LUTBitFN's
  VirtexGeneticCodeIncrLUTs.conf	as above but only LUTIncrFN's
  VirtexGeneticCodeActiveLUTs.conf	as above but only LUTActiveFN's
  
  egGeneticCode.conf		example of genetic code config file

  parsechrom.pl			parses chrom to locate features
  extractchromfeat.pl		extracts a particular feature from chrom
  decodegenes.pl		decodes gene coding region into resources
  findbindingsites.pl		finds binding sites on regulatory regions
  bindmatch.pl			finds what TFs and resources bind to site

  dchromstats.pl		gives some basic info about decoded chrom(s)
  test_genecode.pl		Template for inserting gene/bind code to test

  /testfiles/pop.txt		test population
  /testfiles/cytotest.txt	test cytoplasmic determinants
  /testfiles/tftest.txt		test TFs for use with bindmatch.pl
  /testfiles/tftest2.txt	test TFs for use with bindmatch.pl


Brief descriptions of these follow, their synopsis and other information can
be found by running them with the -h switch, or in the case of the genetic
code config files, through reading their inline documentation.



- main program

  decodechrom.pl: the driving code for decoding the chromosome

  	This is the main program of the chromsome decoding, and would be the
	one that the user would run. It in turn passes tasks on to other
	scripts to perform, and although these can be used independently, it
	should generally be unneccessary to do do. It requires a user-defined
	genetic code configuration file to be supplied. An example is provided
	in egGeneticCode.conf

	decodechrom.pl reads chromosomes in base 4 and parses and decodes the
	information in these, to return a list of genes with information on
	location, relative position of promoter and regulatory sites, and the
	decoded contents of the gene and the resources that bind to the
	regulatory sites. This information can be used to construct a working
	model of gene expression for the supplied chromosome.

	Note that decodechrom.pl creates several temporary files for passing
	information between scripts. These files have names of the form:
	TMP[012c]????_ge, where ???? is the process ID, and [012c] indicates
	that one of these characters is used following the TMP.

  Synopsis:
  [perl] decodechrom.pl [-notfbind] code-file [cyto-file] [p:promoter-list] [s:start-list] [e:stop-list] [pb=bound] [eb=bound] [rb=bound] 
 
  Args:
      -notfbind	- don't check for TF binding,
      code-file - A config file that contains the genetic code table, giving
      		  a mapping from codons to resource-types, and associated
      		  user-defined functions for resources that decode the
      		  following codons into the attributes and settings for that
      		  resource. This is used for both decoding genes, and for
      		  locating binding sites on regulatory regions and their
      		  associated binding resources.
      cyto-file	- file containing lines of cytoplasmic determinants in format:
 	          row, col: resource,attribute,setting1[,setting2[, ..]]
                  we only use those that have resource="TF"; attribute may be
 		  (M|m)orphogen, (L|l)ocal, (C|y)toplasm (only first character
 		  used); setting1 is distance from source, and is given by the
 		  number of binary digits, so morphogens at source will have
 		  an empty distance field, and local/cytoplasm TFs will have
 		  this field ignored; setting2 is the TF binding sequence.
      p:promoter-list	- comma separated list with no spaces that give the
  			  sequence that will be interpreted as a promoter
      s:start-list	- comma separated list with no spaces that give the
  			  sequence that will be interpreted as a start codon
      e:stop-list	- comma separated list with no spaces that gives the
  			  sequence that will be interpreted as a stop codon
      pb=bound	- limits distance upstream of gene for promoter effect
      eb=bound	- limits distance upstream of promoter for enhancer binding
      rb=ound	- limits distance downstream of promoter for repressor binding
      
 
  Inputs: (stdin)
  		- lines of chromosomes in base 4
 
  Outputs:
  	    	- (stdout) lines of:
 			chromno: geneno,gstart: feat,+/-offset,length: details
 		  - chromno identifies which chromosome according to its
 		    occurence in the input.
 		  - geneno gives the number of the gene starting with 1 at
 		    the 5' end of the chromosome
 		  - gstart gives the start location of the gene's coding
 		    region on the chromosome
 		  - feat is one of 'gene', 'prom', 'enhb', 'repb'; which
 		    indicate, respectively, gene coding region, promoter,
 		    enhancer binding site, repressor binding site
 		  - offset gives the distance in bases upstream (-) or
 		    downstream (+) from the gene start
 		  - length is the coding/binding/sequence length in bases
 		  - details is a list of semi-colon delimited elements, with
 		    each element itself being comma-delimited.
 		      feature	    detail		element
 		       prom	sequence
 		       gene	elem1;elem2;...	   offset,len,resattrset
 		       enhb	elem1;elem2;...    offset,len,bindets
 		       repb	elem1;elem2;...    offset,len,bindets
		    where resattrset is in the form:
		    	resource,attribute,setting1,setting2, ...
 		    and bindets in form:
 			"TF", "Local"/"Morphogen"/"Cytoplasm", dist, bindseq
 		     or
 			resource,attribute,setting1,setting2,...
  		- (stderr)
 
  Description:
  		decodechrom reads chromosomes in base 4 and parses and
  		decodes the information in these, to return a list of genes
  		with information on location, relative position of promoter
  		and regulatory sites, and the decoded contents of the gene
  		and the resources that bind to the regulatory sites. This
  		information can be used to construct a working model of gene
  		expression for the supplied chromosome.
 
  See Also:	egGeneticCode.conf for a more detailed description of
  		the genetic code table and resources' decode functions.


- Genetic Code Configuration

  VirtexGeneticCode.conf: genetic code for Virtex-based EHW
  VirtexGeneticCodeNoTFs.conf: same genetic code but TF codons are null'ed
  VirtexGeneticCodeLUTsmore.conf: TF codons re-allocated to LUT config
  VirtexGeneticCodeBitLUTs.conf:	as above but only LUTBitFN's
  VirtexGeneticCodeIncrLUTs.conf:	as above but only LUTIncrFN's
  VirtexGeneticCodeActiveLUTs.conf:	as above but only LUTActiveFN's

  egGeneticCode.conf: an example of a genetic code config file

	Genes are decoded by mapping each resource types to a specific codon,
	as given in a resource decode table (a genetic code), and then by
	decoding that resource's settings according to the resource's own
	code. This is done by having a resource decode table, followed by a
	set of resource-attribute-setting's decode functions. The decoded
	values will be a list of: resource-name, attribute (as returned from
	function or default), setting1, setting2, ...

	The genetic code is implemented as a user-configurable file, in the
	form of a perl script that defines the genetic table, stored in a
	string (the scalar $GeneticCodeTable), and associated resource
	decoding functions, specified as "sub decode_resourcename { .. ".

	The codons that should be provided in the genetic code are only
	those that are extracted from within the coding region of a gene.
	Start and stop codons (and promoters) are defined at the level of
	chromosome parsing.

	The resource decode format is: codon, resource, resource attribute,
	number of codons following for settings, or '?' for variable length -
	in which case the termination symbols follow as comma separated
	codons.

	To decode each specific resource's settings, a decode function needs
	to be provided, that takes the resource attribute as its first
	parameter, and the unprocessed settings codons as an array for its
	second parameter, and translates these into the resource's
	attribute-settings. The return value from the function is a list of:
	resource-attribute (possibly just the supplied attribute parameter),
	resource-setting1, setting2, ..

	For more details, see egGeneticCode.conf

	The binding of resources to the regulatory regions on a chromosome
	are also determined in this file, using the resource bind table
	(a bind code), which is also codon based and has the same format as
	for the genetic code.

	For each bindable resource, a bind_RESOURCE function needs to be
	supplied, these take the same arguments, and return values in the
	same manner as the decode_RESOURCE functions.



- component scripts

  parsechrom.pl: parses chromosome to locate its various regions

	parsechrom takes chromosomes (from stdin), comprised of bases in the
	alphabet [0-3] (equivalent to DNA [TCAG], RNA [UCAG]), and parses
	these to extract the location and length information for genes,
	promoters, regulatory regions, and junk areas.


  extractchromfeat.pl: extracts the desired region from parsed chromosome

  	extractchromfeat acts as an intermediatary between parsechrom.pl
	and other scripts (such as decodegene.pl) that process specific
	regions of a chromosome. It simply extracts the desired region
	from all chromosomes, or a specified chromosome (identified by
	position in a population file) and outputs this region of the
	chromosome(s).


  decodegene.pl: decodes gene coding region

	decodegenes takes genes (from stdin), comprised of bases in the
	alphabet [0-3] (equivalent to RNA [UCAG]), and translates these into a
	representation that can be used to generate the phenotype (anologous
	to the translation of RNA to amino acids, which then are folded into
	proteins) according to the mapping given in the supplied genetic code
	file.  Note that the first and last codons are assumed to be the start
	and stop codons, and so are stripped off prior to translation.


  findbindingsites.pl: finds binding sites on regulatory regions

	findbindingsites takes regions (from stdin), comprised of bases in the
	alphabet [0-3] (equivalent to DNA [TCAG]), and locates binding sites
	within this region. Regions are inverted, reversed, and have every 3rd
	base skipped to form sequences (offsets 0,1,2) which are scanned to
	find TFs, which are used to indicate binding sites. Where there is a
	binding site that covers other binding sites, the covering site is
	ignored (ie choose smaller sites). Overlaps are, however, allowed.


  bindmatch.pl:	matches motifs to DNA sequences for binding

	bindmatch takes regions (from stdin), comprised of bases in the
	alphabet [0-3] (equivalent to DNA [TCAG]), and finds what resources
	and TFs bind, where, and the length of the binding site.


- extra scripts

  dchromstats.pl: gives some basic info about decoded chrom(s)

  	gives some basic information about decoded chromosome(s) (as output
	from decodechrom.pl). such as counts of gene promoters, enhancer bind
	sites, repressor bind sites, and genes; sum of gene coding regions,
	enhancer bind sites, repressor bind sites; averages of lengths of gene
	coding region, enhancer bind sites, repressor bind sites; and average
	number of enhancer and repressor bind sites per gene.


- test files

  /testfiles/pop.txt		test population
  /testfiles/cytotest.txt	test cytoplasmic determinants
  /testfiles/tftest.txt		test TFs for use with bindmatch.pl
  /testfiles/tftest2.txt	test TFs for use with bindmatch.pl

	these files can be used for testing, and as examples of the formats
	required.
		- pop.txt contains some test chromosomes - basically just
		  lines of base 4 strings.
		- cytotest.txt contains specifications for cytoplasmic
		  determinants, as lines of:
		  	x,y:resource,attribute,setting1,setting2,...
		  where x and y are the coordinates of the cell where these
		  are to be placed at the commencement of the developmental
		  process.
		  Note that this is only used by the chromosome preprocessor
		  to extracting TFs for checking against binding sites.
		- tftest.txt and tftest2.txt are used solely for testing of
		  bindmatch.pl, independently from decodechrom.pl. They
		  contain lines of:
			TFtype,TFdist,bindsequence
		  where TFtype can be local or morphogen; TFdist is distance
		  from source in base 2 (with only the number of digits being
		  used to represent the distance, not their values), or empty
		  string to represent local TF or morphogen at its source; and
		  bindsequence is a base 4 sequence that is used (in
		  combination with the distance from source) for binding to
		  DNA.



Environment/platform notes:
--------------------------

Note that this has been developed on Win2000, with Cygwin version 1.3.10
using:
	- Perl version v5.6.1 built for MSWin32-x86-multi-thread
	  with local patch ActivePerl Build 633,  Compiled at Jun 17 2002,
	  for all perl scripts. Note: win32 dll is external to Cygwin.

Note that, for portability, there is no "#!/usr/bin/perl" interpreter
location directive at the start of scripts, hence scripts are executed
with "perl scriptname.pl". Also, note the use of "perl concat.pl" rather
than the unix "cat" or DOS "type" commands, to aid portability. concat.pl
is included with the bioGA files, and is a one-line perl script containing:
while (<>) { print $_; }

Calls to the shell have been made as generic as possible to avoid the need
to change them if the platform is changed. Also, the setup of stderr
redirection involves checking for the existence of /dev/null, and if
present using it, otherwise it assumed that the script is being run from
DOS/Windows and stderr is redirected to "nul".




Design decisions:
----------------

Decodechrom.pl was designed to minimise the number of repeat calls to its
component scripts, and these scripts were designed to handle mutliple lines of
inputs of the same type (eg chromosomes, genes, gene regions, etc) and to
produce output that is labelled according to its location in the input stream
(generally its line number - ignoring blank lines).

As such, all the chromosomes in the population presented to decodechrom.pl are
read in at one time, parsed and stored in a temporary file. The each of the
parsed chromosomes is processed one at a time, with all its information being
passed off to the relevant scripts. For example, all of the chromosome's gene
coding regions are passed to decodegene.pl at one time. This approach makes
the logic and data structures in decodechrom.pl more complex than necessary,
but means that the component scripts only get called once for each chromosome,
rather than multiple times - for each gene, regulatory region, etc. This
should minimise the disk accesses and swapping etc associated with running a
new instance of a script.


