
			      README.txt

		        for Morpho-genetic EHW

			last modified 2/2/2006

			      J.A. Lee



Overview:
--------

This directory contains a utility for generating and placing cytoplasmic
determinants and morphogens, and classes for performing the morphogenesis
process on evolvable hardware.


Description:
-----------

Morphogenesis is a growth process that is driven by gene expression,
whereby the presence and absence of certain molecules in the cell
determines the activation and repression of specific genes, each of which
when activated, produces molecules that can in turn activate or repress
genes. In an evolvable hardware system, we don't have molecules as such,
but by treating the settings of Virtex resources as specific molecules
that are produced by transcribing specific DNA sequences, and in turn
giving each of these a specific binding affininy to DNA sequences on the
chromosome, we are able to emulate the processes of protein production,
and activation and repression of genes by binding of these proteins to
sites on gene regulatory regions. Along with Virtex 'proteins', it is
useful to have other gene products who's sole purpose is the regulation
of genes, analoguous to intronic RNA in eukyarotes. These allow a higher
level of regulation, at an architectural level, whereas instantiatable
Virtex gene products (proteins) are primarily the building blocks of the
organism. This system uses both, along with a gene model that is a
hybrid between prokyarotic and eukyarotic models.



Description of morphoHW files:
-----------------------------

The following files comprise the morphoHW component of the morphogenetic EHW
system:

  morphoHW_README.txt (this file)

  MhwMatrix.java		The main class for running morphogenesis
  CellResourcesMatrix.java	All resources in system are accessed via this
  TFResources.java		Interface to all TFs in system from above class
  MhwCell.java			Implements morphogenesis in a single cell
  TFCell.java			Implements TFs in a single cell

  CellCoords.java		Data structure for storing cell coordinates
  CellOrdering.java		Provides a cell update ordering

  ChromInfo.java		Stores the decoded chromosome's information
  GeneInfo.java			Stores a decoded gene's info
  GeneState.java		Stores the current state of a gene
  Polymerase.java		Stores current state of a RNA Poly 2 molecule

  cytogen.pl			Script to generate cytoplasmic determinants




Classes:
-------

The main class here is MhwMatrix, which drives the growth process in
its component MhwCell's, each of which maps to an individual CLB slice
on the Virtex FPGA. MhwMatrix is meant to be instantiated and driven by
a user program, such as by the (fitness) testing code in the TestEHW
directory. MhwMatrix and its component classes, provide the ability to
save and restore the state of the entire morphogenetic system at any
stage of development, thus allowing growth to be paused and resumed at
any time if desired.



CellCoords:	this class stores a cell's coordinates and provides methods
		for converting from CLB/slice coordinates to cell-based
		(row,col) coordinates, as well as providing an equality
		test and a conversion to String method


CellOrdering:	this class implements the ordering for cell updates in a
		matrix of cells, given a set of input and ouput cells


CellResourcesMatrix:	this class stores the resources (both TFs and JBits)
		for each cell, and provides methods for setting, aging (TFs
		only), querying the binding of resources in a given cell,
		and for loading and saving the state of both JBits and TF
		resources


ChromInfo:	this class is used to parse a decoded chromosome (from file)
		and uses this information to create an array of genes'
		details, stored as GeneInfo objects, for the morphogenesis
		system to utilise. The decoded chromosome file is comprised
		of lines in the format of:
			geneno,gstart: feat,+/-offset,length: details


GeneInfo:	this class stores the information pertaining to a gene,
		including the gene products produced at various points in
		the transcription region, the enhancer and repressor bind
		sites and what resources bind to them, the promoter
		location and sequence, etc.
		Instances of this class are created by ChromInfo, which
		stores the information obtained from a decoded chromosome
		in these, for use with the morphogenesis system
		note that need to know promoter location before able to add
		bind sites, hence the requirement for the promoter's
		details in the constructor


GeneState:	this class stores a gene's state, that being whether or
		not it is being transcribed (and the current location of
		the bound polymerase molecule), and its activation
		according to the binding of resources to its bind sites.
		It also provides methods for performing a transcription
		step, and for accessing the enhancer and repressor bind
		sites hash map's of binding-resource-settings => locus.
		Direct access is allowed to the bind count/flag arrays for
		the enhancer/repressor bind sites. Methods are also
		provided for saving, loading, and clearing the gene's
		state.


MhwCell:	this class implements the EHW morphogenesis process on a
		single cell


MhwMatrix:	this class implements the EHW morphogenesis process across
		a matrix of cells


Polymerase:	this class stores the state of a RNA polymerase II molecule
		the field transloc gives the current location on the
		chromosome; geneidx gives the gene's index (Nth gene on
		chromosome) that is currently being transcribed by this
		molecule, or -1 if not bound to a gene


TFCell:		this class stores the state of TFs in a cell, and provides
		methods for creating and aging TFs, binding TFs to a locus
		on a chromosome, for querying a bound or unbound TF, and
		for saving and restoring the state of TFs in a cell


TFResources:	this class stores the TFs for each cell, and provides
		methods for accessing and manipulating the TFs in a given
		cell, such as creating and aging TFs, binding TFs to a
		locus on a chromosome, querying a bound or unbound TF,
		and for saving and restoring the state of TFs in a cell



Scripts:
-------

cytogen.pl:	cytogen.pl generates cytoplasmic determinants/morphogen TFs
 		that spread out from their source. This is used to seed and
 		guide the morphogenesis processes.

 		Note that cytoplasms are generated in a rectangular spread,
 		with axis asymmetry encoded in the distfromsrc field of TFs,
 		which have 'eternal' lifespan. The asymmetry is encoded by
		concatenating the same number of '1's as the Y axis (rows)
		distance from source with the same number of '0's as the X
		axis (cols) from source.
		
		Morphogen TFs are produced in the same manner as for those
		that are produced by genes, with spread being done by
		following the axis out to the extent indicated by the row/col
		spread, then on the cells of the quadrants between axis, the
		distance metric is filled up to the lowest distance specified
		by the two. Morphogen's distfromsrc is a random generated
		binary number with as many digits as the manhattan distance
		from the source.

		Usage: perl cytogen.pl [-v] [-gran R,C] TFsequence      \
			    maxrow,maxcol srclist row-spread col-spread	\
			    [ row-diffusion col-diffusion ]
		
		arguments:
		  -v		visual mode - produces a visual
				representation of spread
  		  -gran R,C	each coord contains RxC (rows,cols) cells
  				with copies of same cytogens. output coords
  				and matrix bounds will be scaled accordingly
		  TFsequence	the cytoplasm/morphogen sequence, to use
		  maxrow,maxcol	upper bounds of cell matrix, lower is 0,0
		  srclist	semi-colon delimited list of source cells
		  row-spread	vertical spread
		  col-spread	horizontal spread

			the presence of the following parameters
			indicate that cytoplasms are being produced

		  row-diffusion rows traversed to increase distance metric
		  col-diffusion cols traversed to increase distance metric






Examples:
--------

$ perl cytogen.pl 3223012 "10,10" "2,0;5,10" 1 1 > cyto.txt

$ perl cytogen.pl -v 3223012 "10,10" "0,5;4,1" 3 2 1 1

$ perl cytogen.pl -v -gran "1,2" 000 "7,7" "4,0;3,0" 2 4 1 1

The first example produces a file with morphogens placed around points at 2,0 
and 5,10. these would typically be input and output cells. The contents of
cyto.txt would be:

  1,0:TF,Morphogen,0,3223012
  2,0:TF,Morphogen,,3223012
  2,1:TF,Morphogen,0,3223012
  3,0:TF,Morphogen,1,3223012
  4,10:TF,Morphogen,1,3223012
  5,9:TF,Morphogen,0,3223012
  5,10:TF,Morphogen,,3223012
  6,10:TF,Morphogen,1,3223012

and a visual representation of this, using the -v option, looks like the
following, noting that the '00' and '01's indicated the length of the
distance from source field, and the 'X' indicates the source:

	   -----------------------------------
	10 | .  .  .  .  .  .  .  .  .  .  . |
	 9 | .  .  .  .  .  .  .  .  .  .  . |
	 8 | .  .  .  .  .  .  .  .  .  .  . |
	 7 | .  .  .  .  .  .  .  .  .  .  . |
	 6 | .  .  .  .  .  .  .  .  .  .  01|
	 5 | .  .  .  .  .  .  .  .  .  01 X |
	 4 | .  .  .  .  .  .  .  .  .  .  00|
	 3 | 01 .  .  .  .  .  .  .  .  .  . |
	 2 | X  01 .  .  .  .  .  .  .  .  . |
	 1 | 00 .  .  .  .  .  .  .  .  .  . |
	 0 | .  .  .  .  .  .  .  .  .  .  . |
	   -----------------------------------
	     0  1  2  3  4  5  6  7  8  9 10


The second example produces rectangular spreads of cytoplasmic determinants,
that will provide axis asymmetry. The first rectangle being centered at 0,5
and the second at 4,1; both with a vertical spread of 3, and horizontal of 2
cells. The last parameters specify that the distance metric change every
row and column. Using the -v (visual) option produces the following
output:

	   -----------------------------------
	10 | .  .  .  .  .  .  .  .  .  .  . |
	 9 | .  .  .  .  .  .  .  .  .  .  . |
	 8 | .  .  .  .  .  .  .  .  .  .  . |
	 7 |3-13-03-13-2 .  .  .  .  .  .  . |
	 6 |2-12-02-12-2 .  .  .  .  .  .  . |
	 5 |1-11-01-11-2 .  .  .  .  .  .  . |
	 4 |0-1 X 0-10-2 .  .  .  .  .  .  . |
	 3 |1-11-01-13-23-13-03-13-2 .  .  . |
	 2 |2-12-02-12-22-12-02-12-2 .  .  . |
	 1 |3-13-03-11-21-11-01-11-2 .  .  . |
	 0 | .  .  . 0-20-1 X 0-10-2 .  .  . |
	   -----------------------------------
	     0  1  2  3  4  5  6  7  8  9 10



X's indicate the source, and digits separated by a '-' indicate the row and
column distance from source. 0-1, for example, indicates row distance of 0
and a column distance of 1. Note that where there are cytogens from different
sources, the last displayed will overwrite the earlier, although this is of
course only for the visual representation.


The final example uses the granularity flag to indicate that each column
contains 2 cells, as would be the case with slice granularity cells where
each CLB contains 2 cells. Although increasing max row to 15 (ie a width of
16 cells rather than 8) provides the correct number of cells, the distance
metrics are wrong, as distances should be in terms of CLBs. Each CLB takes
up 2 columns by convention in the morhogenesis system, as a convenient
representation, but to reflect the actual inter-CLB routing and FPGA
structure we need to assign distances based on CLBs. By using the -gran
option, this can be handled properly.

In this example the maximum row and column are given in terms of CLBs, as
are the distance metrics. The results are however returned in cellular
coordinates, but with the correct CLB-distances. The output of this example
demonstrates this (note the repeats of the distance metrics in the cell pairs
that comprise the 2 slices in a CLB):

	   --------------------------------------------------
	 7 | .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  . |
	 6 |2-02-02-12-12-22-22-32-32-42-4 .  .  .  .  .  . |
	 5 |1-01-01-11-12-22-22-32-31-41-4 .  .  .  .  .  . |
	 4 | X  X 0-10-10-20-21-31-31-41-4 .  .  .  .  .  . |
	 3 | X  X 1-11-10-20-21-31-31-41-4 .  .  .  .  .  . |
	 2 |2-02-01-11-12-22-22-32-32-42-4 .  .  .  .  .  . |
	 1 |2-02-02-12-12-22-22-32-32-42-4 .  .  .  .  .  . |
	 0 | .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  . |
	   --------------------------------------------------
	     0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15




Environment/platform notes:
--------------------------

Note that the Java classes were developed using JBits 2.8 and the
Java JDK 1.2.2 + JRE, and the perl scripts were done using Active
Perl version v5.6.1 built for MSWin32-x86-multi-thread with local
patch ActivePerl Build 633,  Compiled at Jun 17 2002. These were
all developed on WinXP, with Cygwin version 1.3.10 (Note that the
Active Perl win32 dll is external to Cygwin).

Note that, for portability, there is no "#!/usr/bin/perl" interpreter
location directive at the start of perl scripts, hence scripts are
executed with "perl scriptname.pl".


