
			      README.txt

		     	 for Virtex EHW layout

			last modified 2/2/2006

			      J.A. Lee



Overview:
--------

This directory contains classes and programs for mapping from Nets and
Busses on a Virtex package to IOBs in JBits, and also for determining
the layout of the evolvable region on a Virtex FPGA, and for routing
input and output busses into and out of this region.



Description:
-----------

For an evolved, or growing circuit, to be evaluated or usefully applied
to performing a task, it is necessary for the circuit to be connected
to input and output busses. Both the evolutionary and morphogenetic
processes only manipulate CLB resources within a user-defined region of
the CLB matrix; IOBs and BRAM are not accessible to this process. Thus
it is necessary for input and ouput busses to be routed to entry and
exit points at the boundaries of the evolvable region. It is also
necessary for these IOBs to be connected to the package pins associated
with the input and ouput busses for a given board.

All this functionality is provided by the classes and programs within
this directory.


Description of XCVlayout files:
------------------------------

XCVlayout_README.txt (this file)

EHWLayout.java			This performs layout and routing for EHW

LEcoord.java			logic element granularity coordinate class
RaptorXCV1000Board.java		defines abstract board for an XCV1000
RaptorXCV1000.ucf		defines mapping of pins to nets for XCV1000
XCV50bg256Board.java		defines abstract board for an XCV50
XCV50bg256.ucf			maps pins to nets for simulated XCV50

GetIobSite.java			utility to generate Iob's corresponding nets





Files:
-------

EHWLayout.java:

	This program performs the tasks of determining the layout for
	morphogenesis, or evolution, on a Virtex FPGA. It decides the
	boundaries of the CLB region to be used, allocates input and
	output CLBs, for which single lines are available for connecting
	to the external input and output busses. These busses are routed
	to the logic elements (LUT/FF pairs) next to these input/output
	CLBs, from which the single lines carrying IO signals are driven.

	Input CLBs (slices actually) are placed next to each other, in
	the center of the evolvable region's edge (on the input side),
	with inputs being to the G-LUT if slice 0, F-LUT if slice 1. ie
	the inputs into the evolvable region are always S0G-Y and S1F-X.
	This is to ensure that the routing can work with the 'slim' model
	(see JBitsRes_README.txt) where S1G1 has no West single line
	inputs (so S1G-Y logic element is avoided).

	Output CLBs are spread evenly across the output edge, but with
	only S0G-Y and S1F-X logic elements used, to ensure that the
	layout can always work with the 'slim' model where S0_X/XQ has no
	West single outputs, and  S1_Y/YQ has no South single outputs, to
	drive the associated output bus line.

	Input CLBs can connect to one line of the input bus via any of
	the many singles driven from the appropriate direction, by the
	input routing slice neighbouring it on the external boundary of
	the evolvable region. This slice passes the input signal to its
	outputs, which are fanned out to all the singles in the direction
	of the evolvable region from the 4 CLB out bus lines assigned to
	the slice (slice 0 uses out lines 0-3, slice 1 uses 4-7).

	The single lines available to be driven by each out line are
	given here (their direction will be reversed on the inputs to the
	input CLBs - eg E2 becomes W2, etc):

		OUT0		E2  	N0  N1  S1  S3  W7
		OUT1		E3  E5  N2  	S0  	W4  W5
		OUT2		E6  	N6  N8  S5  S7  W9
		OUT3		E8  E11 N9  	S10 	W10 W11
		OUT4		E14 	N12 N13 S13 S15 W19
		OUT5		E15 E17 N14 	S12 	W16 W17
		OUT6		E18 	N18 N20 S17 S19 W21
		OUT7		E20 E23 N21 	S22 	W22 W23

	This gives the following input lines available to LUTs in the
	input CLBs:

 		SOF1 S0G1 S1F4 S1G4	OUT_WEST1 OFF
			.		N15 N19 N22
			.		S6  S12 S13 S21
			.		W5  W17 W18

		S0F2 S0G2 S1F3 S1G3	OUT_WEST0 OFF 
			.		E11 E23
			.		N12 N17
			.		S1
			.		W2 W20

		S0F3 S0G3 S1F2 S1G2	OUT_EAST7 OFF 
			.		E5  E7  E9  E10 E21
			.		N13
			.		S0  S14 S20
			.		W6  W8  W15 W23

		S0F4 S0G4 S1F1 S1G1	OUT_EAST6 OFF 
		  	.		E4 E16 E17 E19 E22
		  	.		N0 N1 N3 N5 N7 N10
		  	.		S2 S8 S9 S18
		  	.		W3 W11 W14


	Although, the single lines are driven into the evolvable region,
	they are not explicitly connected to the inputs of the input
	CLBs; this is left up to the evolution/morphogenesis process, as
	as in/out CLBs are no different from other CLBs in the evolvable
	region of the FPGA. This is also the case for the output CLBs,
	where the OUT bus lines and out mux to single multiplexors are
	not explicitly connected to the output routing CLBs on the outside
	border of the evolvable region.

	Unlike input CLBs, where there are many copies of each input
	signal, output CLBs must drive specific single lines to produce
	outputs on the external output bus. The reason for this is due to
	the fact that undriven lines that are connected to LUT inputs are
	held high, therefore if _any_ of the lines aren't driven, then the
	LUT, to which they are connected, will always produce a high
	output. For this reason, single input pass-through LUTs are used
	on the output routing CLBs, externally bordering the evolvable
	region.

	To ensure that the same layout can be used with various approaches
	the output routing LUT inputs were chosen from those that can be
	driven by the 'slim' subset of resources (see JBitsRes_README.txt)
	as this is a subset of all other models that we use. However, this
	requires evolution to learn to use the appropriate OUT lines on
	the output CLBs (as we have limited out cells to be S0G-Y and
	S1F-X, which use out lines 0,1 and 3,7 respectively in the logic
	element granularity models), and further to learn to drive the
	appropriate single lines.

	The single lines available to the output cells, S0G-Y and S1F-X,
	are: E3,N2,S3,W4 (which become W3,S2,N3,E4 LUT inputs) and
	E11,N9,S10,W22 (which become W11,S9,N10,E22 LUT inputs),
	respectively. These are used as the inputs to the pass through LUTs
	on S0G-Y and S1F-X. For LUTs on the currently unused (for passing
	output signals) S0F-X and S1G-Y LUTs, singles lines driven by the
	other CLB out bus lines (2,4,5,6) were chosen (basically use the
	other 'slim' model output singles, plus, in the case of east
	outputs, where there are only 3 west singles available as inputs
	to the S0G4/S0F4/S1G1/S1F1 LUTs, W14 - driven by out 4 to E14 - is
	used for both S0F4 and S1G1, since they are not likely to be used
	anyway). Specifically the S0F-X outputs E14,N8,S7,W16 (which become
	W14,S8,N7,E16) and S1G-Y outputs E14,N18,S5,W19 (which become
	W14,S18,N5,E19) are used.

	This gives the following pass through LUT inputs, with only S0G4
	and S1F1 LUT inputs currently used:

	    LUT-input		lines from each direction

	     S0G4		E4  N3  S2  W3
	     S1F1		E16 N7  S8  W11
	     (S0F4		E22 N10 S9  W14)
	     (S1G1		E19 N5  S18 W14)

	Thus, to drive the external output bus lines, these LUT inputs
	must be driven by the corresponding lines (in the appropriate
	direction):

	   Output LE	      CLB out bus	   single
	     S0G-Y		1,1,0,1		E3,N2,S3,W4
	     S1F-X		3,3,3,7		E11,N9,S10,W22

	For example, if inputs are on the west and outputs on the east
	(sides of the CLB region), and 6,15,S1F-X is an output cell.
	Then to pass the output signal from the evolvable region, then CLB
	6,15 needs out bus 1 driven by the output of LUT F in slice 0
	(actually it wouldn't matter if it was driven by another LUT
	output), which is done by setting OUT1.OUT1 to OUT1.S0_XQ (or S0_X),
	and then connecting this to the appropriate single to the west,
	which is West4, which is done by setting
	OutMuxToSingle.OUT1_TO_SINGLE_WEST4 to OutMuxToSingle.ON.

	On an input CLB, such as 8,8,S0G-Y, all that would be required to
	receive an input signal from the external input bus, is for the LUT
	to connect to any of the single lines  from the west, for example
	by setting S0G4.S0G4 to be S0G4.SINGLE_WEST11.




	Usage: EHWLayout [-v|-d] (XCV50 | XCV1000) rows cols ucf-file \
	       inbus inbuswidth outbus outbuswidth [inbitfile outbitfile]

	  arguments:

	-v		verbose mode - outputs extra information to stdio
	-d
	XCV(50|1000)	this determines which FPGA device to use, either
			a XCV50 (for use with simulator) or XCV1000 (for
			use with the Raptor board)
	rows		the number of CLB rows to use in EHW 
	cols		the number of CLB cols to use in EHW 
	ucf-file	configuration file describing mapping from net
			and bus names to package pins
	inbus		name of input bus (in ucf-file). note that bus
			lines have netnames in the form of busname with
			line index (0 .. N-1) appended
	inbuswidth	width of bus
	outbus		name of output bus (in ucf-file)
	outbuswidth	width of bus

	... optional parameters

	inbitfile	the (null) bitstream to connect up
	outbitfile	the filename to save connected bitstream to



GetIobSite.java:

	This program is used to find out what IOBs are connected to
	which Nets (and hence package pins). Its functionality is also
	contained within EHWLayout, but here can be used as a stand
	alone application.

	Usage: GetIobSite UCF-file Bus-NetName [BusWidth]

	  arguments:

	ucf-file	configuration file describing mapping from net
			and bus names to package pins
	Bus-NetName	name of Net or Bus (in ucf-file)
	BusWidth	if netname is a Bus, then this gives the Width
			of the bus. bus line netnames are assumed to be
			in the form of busname with line index (0 .. N-1)
			appended



LEcoord:	this class stores a logic element (LE)'s coordinates and
		provides constructors that accepts coordinates as either
		4 ints, a string of 4 comma-delimited ints, or as a string
		in the same format as its string representation (minus
		the enclosing brackets). It also provides an equality test



RaptorXCV1000Board.java

	This defines an abstract board class for an XCV1000 in a BG560
	package as used by the Virtex XCV1000 chip in the Raptor Board


XCV50bg256Board.java

	This defines an abstract board class for an XCV50 in a BG256
	package. This is for use with the VirtexDS simulator.


RaptorXCV1000.ucf

	This file defines the mapping of bus and nets to package pins
	on the Raptor board.


XCV50bg256.ucf

	This file defines the mapping of bus and nets to package pins
	on the virtual XCV50 board used with the VirtexDS simulator.





Examples:
--------

The following are examples of how to use the programs in this directory,
noting that the use of the backslash at the end of the line, is simply
for cosmetic purposes - the entire command line parameters can be put
on a single line.


$ java EHWLayout -v XCV50 8 8 XCV50bg256.ucf DUT_in 4 DUT_out 4

$ java EHWLayout -d XCV50 8 8 XCV50bg256.ucf DUT_in 4 DUT_out 4 \
		 datafiles/null50GCLK1.bit test.bit


The first example is the format used for just finding the necessary
parameters for passing to the EHW system. It returns the bounds of
the EHW region, the CLB (slice and logic element) input and output
locations, and the sides of the CLB matrix that the input and
output CLBs are on - all input CLBs are allocated to one side, and
all output CLBs are allocated to a different side (of the evolvable
region). The "-v" verrbose flag will also produce information on the
mapping of bus lines to IOB sites.

The second example also routes from the IOBs to the evolvable region,
although the input and output CLBs are explicitly connected, their
neighbours (outside the evolvable region) are, and from these single
lines are driven towards input CLBs, or singles connected from output
CLB's direction. The lack of explicit connections to the input/output
CLBs is due to the fact that evolution can modify any connections
within this region, so if any non-single lines are used to route to
them, then there will be no way to connect to these, leaving the
evolved region separated from the FPGA inputs and outputs.

Though this is the case, it is sometimes necessary to verify that
signals are making it onto and off of the FPGA, and so the "-d" debug
circuit option is provided. It routes all the way from the first line
of the input bus to the first line of the output bus. This results in
a circuit that outputs input bus line 0's signal on output bus line 0.
The "-d" option also sets the verbose mode, which prints to the screen
all the routing information.



Environment/platform notes:
--------------------------

Note that the Java classes were developed using JBits 2.8 and the
Java JDK 1.2.2 + JRE, on WinXP, with Cygwin version 1.3.10.



