
			      README.txt

		       for Non-Developmental EHW

			last modified 2/2/2006

			      J.A. Lee



Overview:
--------

This directory contains programs for decoding an evolved chromosome
into a set of Virtex configuration instructions, and for executing
these within a bounded region of the FPGA, to produce an (evolved)
circuit.



Description:
-----------

The programs here implement a safe non-developmental approach to
evolvable hardware on the Virtex, using JBits to configure the CLBs
within the evolvable region of the FPGA.

The only approach still supported (the virtex genetic code-based circuit
construction method is depreceated) here is a set of direct encoding schemes
on a binary chromosome, with each logic element on the evolvable region of
the FPGA having a group of bits that determine how it should be configured.

For each logic element its bits on the chromosome are decoded to produce
set-resource instructions. The logic element's coordinates and resource
settings of each logic element are then able to be used to configure the
entire evolvable region of the FPGA.


Description of NDEHW files:
--------------------------

NDEHW_README.txt (this file)

BuildEhw.java			Builds cct based on decoded chrom instructions

directdecodelegrantrim.pl	Decode chrom as binary GA on slim model
directdecodelegranslim.pl	LE gran, with synch LE output, 2 out buses
directdecodelegranasynch.pl	LE gran, with asynch LE output, 8 out buses
directdecodelegransynch.pl	LE gran, with synch LE output, 8 out buses




Files:
-------


BuildEhw.java:

	takes a decoded chromosome (the output of one of the directdecode
	scripts) that being a list of slice/CLB move commands, each being
	followed by a set of Virtex resource settings. These are then used
	to move around within the evolvable region of the FPGA, and to
	configure the resources according to the settings in the decoded
	chromosome. ie it builds a cct based on a sequence of move,
	set-resources instructions.

	These instructions (one per line) are in the format:
		horizmove,vertmove,slicemove: details
	- horizmove and vertmove are one of -N,0,+N (N is an integer)
	  indicating respectively no movement, move +N CLBs, move -N CLBs
	- slicemove is "0", "1", "G", "F", "0G", "0F", "1G", "1F",
	  "-", "%", or ".". indicating respectively slice 0, 1, logic
	  element G, F, slice-and-LE S0-GY, S0-FX, S1-GY, S1-FX, swap slice,
	  swap logic element, and lastly, no move
	  alternatively, "+,+,+" may be used to indicate, move to next cell
	  (slice or LE), according to some ordering (eg W to E, S to N), and
	  "@,@,@" is used to indicate move to start corner (according to the
	  same ordering used above)
	- details is a semi-colon delimited list of elements, each in format:
   		resource-type "=" resource-details
	  where:
		resource-type is one of (we don't have TFs here):
 			jbits, LUT, LUTIncrFN, SliceRAM
		resource-details for SliceRAM is in the form:
			slice0class,bitsconst1,bitsconst2,../
			slice1class,bitsconst1,bitsconst2,..
		resource-details for jbits, LUT, LUTIncrFN is in the form of:
			bits "," val [ "/" bits "," val ]



	Usage: BuildEhw [-v] [-le] devname bitfile contentionfile \
		decodedchromfile minCLB maxCLB startCLBslice[LE] [outfile]

	  arguments:

	-v		verbose mode. outputs extra info to STDIO to
			track what the program is doing
	-le		use logic element granularity rather than slice level
	devname		the FPGA device, eg XCV50, XCV1000
	bitfile		a bitstream, produced by EHWLayout
	contentionfile	information file for preventing contentious
			resources being set at the same time
	decodedchrom	pre-processed and JBits-converted chromosome
			that contains the circuit building instructions
	minCLB		minimum CLB row and column of evolvable region
	maxCLB		maximum CLB row and column of evolvable region
	startCLBslice/	the starting location from which move and build
	  LE		instructions are applied
	outfile		name of the file to save resulting bitstream to





directdecodelegrantrim.pl:

	This is used with a standard GA using a direct encoding scheme, and
	its output is used with BuildEhw to configure each logic element
	within the evolvable region of the FPGA.

	directdecodelegrantrim takes fixed length binary chromosomes which
	indicate the settings for each logic element (LUT-flip flop pair) in
	the evolvable region. All LUT input lines are used, while only
	directly connecting singles and out bus lines (0,1,6,7) into and out
	of a CLB are used. Logic element outputs are limited to synchronous
	(registered) signals from the flip flop associated with the LUT.

	Note that each logic element has 2 of the 8 out bus lines allocated
	to it (S0G-Y uses out 0,1; S0F-X uses 2,5; S1G-Y 4,6; S1F-X out 3,7)
	and is only able to manipulate the out mux to single lines that are
	driven by its own out bus lines.

	Each logic element configuration is specified by its input lines,
	LUT truth table, out bus mux settings and out bus to single mux
	settings, in that order. These take up 17, 16, 2, 12 bits,
	respectively. Hence the chromosome is divided into 47 bit long
	sections, one for each logic element, with excess bits discarded.

	Note that although this is aimed at LE-granularity, slice level
	granularity would be exacty the same, and so this can be used for
	either.

	Usage: directdecodelegrantrim.pl [ -d ] outfilename outfext

	  arguments:

  		-d		- output extra info for debugging to STDERR
		outfilename	- the basename for the output converted chrom.
		outfext		- the extension for the output converted chrom
				  output files will have names of:
				  	outfilename_CHROMOSOME#.outfext



directdecodelegransynch.pl:

	as with directdecodelegrantrim.pl but with shared CLB outputs
	ie without 2 out bus lines allocated per LE. hence all registered
	CLB outputs are available to each out bus line


directdecodelegranasynch.pl:

	as with directdecodelegransynch.pl but with unregistered CLB outputs


directdecodelegranslim.pl:

	directdecodelegranslim is the same as as directdecodelegrantrim.pl
	except that it uses the 'slim' subset of resources, consisting of a
	single LUT input, with 4 possible LUT functions (always 0, always 1,
	pass input, invert), and only 3 or 4 connections to other CLBs.

	Each logic element configuration is specified by its single input
	line (with a choice of 3 or 4 single lines or OFF), LUT function,
	out bus mux settings, and out bus to single mux settings, in that
	order. These take up 4, 2, 2, 4 bits, respectively. Hence the
	chromosome is divided into 12 bit sections, one for each logic
	element, with excess bits discarded.

	Usage: directdecodelegranslim.pl [ -d ] outfilename outfext







Examples:
--------

The following are examples of how to use the programs in this directory,
noting that the use of the backslash at the end of the line, is simply
for cosmetic purposes - the entire command line parameters can be put
on a single line.


An example of using the script for decoding a standard GA with a direct
encoding scheme, follows:

$ cat binpop.txt | perl directdecodelegranslim.pl dout txt

$ java BuildEhw -v -le XCV50 ../XCVlayout/test.bit \
	../JBitsRes/contentionInfo.txt dout_1.txt "4,8" "11,15" "4,8,0,0" \
	junk.bit

Here, 'binpop.txt' is a population of fixed length binary chromosomes, and
the first of these is decoded to dout_1.txt which can then be used with
BuildEhw to configure each logic element within the evolvable region.




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


