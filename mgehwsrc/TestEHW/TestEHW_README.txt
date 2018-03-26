
			      README.txt

		     for Evolvable Hardware Tests

			last modified 2/2/2006

			      J.A. Lee



Overview:
--------

This directory contains files for testing evolved, and growing,
Virtex bitstreams, and assigning them a fitness value based on
their behaviour.



Description:
-----------

Once an evolved chromosome has been preprocessed and converted to
JBits format, then it can be used to specify a circuit, either
directly (the non-developmental approach) or by directing a growth
process (morphogenesis). The resulting circuit needs to be
evaluated, according to some criterion, so that evolution can be
guided towards circuits that produce the desired behaviour.

The tests contained within this directory are used to evaluate the
fitness of evolved circuits. This can also be coupled with the
morphogenesis process, so that at each stage of development the
developing circuit is evaluated.


Description of TestEHW files:
----------------------------

  TestEHW_README.txt (this file)

  TestFnInToOut.java		test circuit for function & connectivity
  TestInToOutRecurse.java	test for connectivity without simulator

  Adder2x2Fixed.pl		wrapper to TestFnInToOut class allowing
				a subset of CLB resources to be fixed
				for 1-bit adder tests on a 2x2 CLB region

  adder2x2fixedActiveLUTsettings.txt: pre-sets the ActiveLUTFNs for the
				adder2x2fixedlut tests

  DummyMGTest			for testing other parts of the MG system



Files:
-------

- DummyMGTest.java

  this class just outputs a random fitness, iteration and number of genes
  for testing other parts of the morphogenetic EHW system



- TestInToOutRecurse.java:

  this program tests or attempts to grow a connected circuit that will reach
  the outputs. It checks for connectedness by tracing connections from the
  inputs, until all connected LEs have been reached. Note that, even though
  slice granularity may be used, it uses the LE granularity subset of
  resources defined in legranDSconvToJBits.conf, whereby each LE is
  allocated 2 of the 8 out bus lines: S0G-Y uses out 0,1; S0F-X out 2,5;
  S1G-Y uses out 4,6; S1F-X out 3,7. All LUT inputs are used for logic
  element input connections, but Ctrl inputs are ignored, as they belong to
  a slice rather than a single logic element.

  Fitness is based on how close to the final outputs the generated circuit
  is able to connect. The evolvable region is divided into layers
  (perpendicular to the axis connecting inputs to outputs on opposite ends
  of the region), fitness is calculated by counting how many layers,
  starting from the inputs, have been connected, and how many elements (up
  to the rounded up average of in and out bus sizes) in the layer are
  connected after factoring in how many regions in the layer contain
  connected cells, and the progress of each connected logic element at
  connecting from the previous layer to the next layer.

  As we want to encourage spread orthoganal to the direction of signal
  transmission, to help evolution find connections to all of the outputs,
  layers are divided into output-bus-width regions, and the number of
  regions that have connected cells are counted, which is then used to
  scale the layer's fitness.

  100% fitness also requires that all input lines are connected to input
  cells (within the evolvable region), and all output cells are connected,
  via other cells, to these inputs, and finally all output cells are
  connected to the output bus'es lines.

  A logic element's connectivity state is given by adding the following:
	- LUT input(s) connected to neighbour output(s)		=> 1
	- LUT function uses a connected input			=> 2
	- LUT output connected to any OUT lines			=> 4
	- OutToSingle for one of the out lines set to on has
	  a single line connected to LUT input in neighbour	=> 8
  giving each LE a value from 0 .. F hex
  
  Usage: TestInToOutRecurse [-v|-d] [-le] [-forcespread] [-sharedouts] \
	 [-ndo] [-sil] [(-s|-sr) outbit]  XCV50CLK1.bit inIOBs outIOBs \
	 inCLBsliceLEs outCLBsliceLEs minCLB maxCLB contentionFile     \
	 [ dchromfilelist | dchromfile cytofile numTestIter            \
	   nGrowthStepsPerTest [ transcriptionRate npoly2gene          \
	   polyThreshold ] [ seed ] ]

  All arguments, except the '-forcespread', '-sharedouts' and '-ndo'
  switches, are the same as for TestInToOut0101s, although here the
 'inIOBs' and 'outIOBs' args aren't actually used. They are kept solely
  so that the same params are used for all EHW fitness evaluation programs.
  See TestInToOut0101s for further details on the parameters.

  The '-forcespread' switch is used to specify that for 100% fitness, not
  only do all inputs and outputs need to be connected, with a route from
  one or more of the inputs to all of the outputs, but each middle layer
  must have a "sufficient" degree of spread. Spread is used to aid in the
  search for connecting to all outputs, but once this is achieved, it is
  not necessary, however, it may be desired that this degree of spread is
  maintained for a 100% solution. This would possibly smooth the fitness
  function, and also generate circuit structures that are more resilient
  to further modifications for attaining the desired function, due to the
  guaranteed higher degree of connectivity.

  The '-sharedouts' switch indicates that all CLB outputs share the out
  bus lines, otherwise each LE is allocated 2 of the 8 out bus lines.

  The '-ndo' switch indicates not to trace direct connections from Out bus
  lines 0,1,6,7 to the neighbouring CLBs.



- TestFnInToOut.java:

  this program uses the VirtexDS simulator, and optionally TestInToOutRecurse
  to test for (or attempt to create) a fully connected circuit that performs
  the desired function. Once outputs are connected, it iterates through each
  of the possible input signal combinations, and tests the outputs to see
  how closely they match the required output. If no function is specified
  the default function is to pass inputs to outputs with the output signals
  ignored in fitness evaluation - ie it bases fitness solely on connectivity
  rather than function.

  We base fitness on how much progress is made in connecting inputs and
  outputs, and how closely the outputs match that of the desired function.
  The circuit fitness is given by scaling the connectivity and functional
  fitnesses by 1/2 and then adding them together

  Circuit connectivity is measured by how many layers have changing signal
  values, and how many elements in the layer change (up to the rounded down
  average of in and out bus sizes), except in input and output layers, where
  only use the input and output LEs. connectivity can be tested using either
  TestInToOutRecurse's evaluation or using the simulator with probes at the
  logic element's registered outputs (YQ/XQ) with tests for signal changes
  at these probes.

  Function equivalence is measured according to how close the delayed output
  matches the truth table's output signals, based on the hamming distance
  between the outputs (that _change_, unchanging outputs are assumed to be
  unconnected to the inputs, and for each of these 1 is added to the hamming
  distance) and the specified outputs, for delays of M .. M+N, where N is the
  maximum additional delay on top of M, the minimum propogation delay from
  the first input to first output, and choosing the delay with the smallest
  Hamming distance to base the fitness on. Fitness is then based on the
  proportion of matching signals, calculated as fitness=(L-H)/L, where L is
  the length of the input signal, and H is the Hamming distance.
  Note that for combinatorial (stateless) functions (eg 1- and 2-bit adders),
  only unregistered slice outputs should be used, and a maxdelay of 0 should
  be used.


  Usage: TestFnInToOut [-v | -d] [-le] [-sil] [-norecurse] [-synchonly]     /
	 [-activelutfnsonly] [-maxdelay D] [ -inout | -onebitadder |        /
	  -twobitadder | -fnseq iii.. ooo.. ] [ -s outbit | -sr outbit ]    /
	 XCV50CLK1.bit inIOB outIOB inCLBslice outCLBslice minCLB maxCLB    /
	 contentionFile [ dchromfilelist | dchromfile cytofile numTestIter  /
	 nGrowthStepsPerTest [ transcriptionRate npoly2gene polyThreshold ] /
	  [ seed ] ]

  arguments:

	-d		debug mode - debugging info from whole system!
	-v		verbose mode - outputs extra information to stderr
	-le		logic element granularity; indicates that cells map
			to logic elements (LUT-FF pairs) rather than slices
  	-sil		use a strict (test) iteration limit for morphogenesis
			Without this, test iterations will continue past
  			numTestIter while maximum fitness has increased in
			the last num iter/2 iterations or while fitness is
			still increasing
	-norecurse	don't use recursive connectivity test, pass signals
			through inputs every growth step and test for changes
			on (registered) LUT ouputs to determine connectivity.
			This is slow and has less connectivity information!
	-activelutfnsonly this guarantees that all LUTs only use inputs that
			are 'active'; ie connected to inputs or changing signal
			this means that connectivity can always be accurately
			evaluated based solely on recursive connectivity test
			(fast! if used with recursive connectivity test)
	-synchonly	indicates that only registered slice outputs are used
			(some asynch configs may hang on download to board,
			 in which case program is terminated after 30 seconds)
	-maxdelay D	max delay on top of minimum propogation delay to test
			outputs (default is 0) . Actual delay is minpropdelay+D
			(in clock cycles).
	-inout		test is to pass inputs to outputs with output signals
			ignored (ie testing for connection only)
	-onebitadder	test circuit for one bit full adder function
	-twobitadder	test circuit for two bit full adder function
	-fnseq i.. o..	user defined function truth table, with inputs and
			outputs defined in comma delimited, interleaved,
			little endian binary format, but with -1 = Z
			(unconnected) and 2=X (don't care) values allowed on
			output specification only. There would normally be
			2^I x I entries in the input sequence and 2^I x O
			entries in the output sequence. The length of seqs,
			Si and So, when divided by the number of inputs and
			outputs, I and O, should be equal; ie Si/I = So/O
			format for a 3 input 2 ouput function is:
			IA0,IB0,IC0,IA1,IB1,IC1,..,IC(2^n)-1
			OA0,OB0,OA1,OB1,..,OB(2^n)-1
			eg for a full one bit adder with input signals
			x,y,Cin, and outputs Sum,Cout (spaces/nl for clarity):
			0,0,0, 1,0,0, 0,1,0, 1,1,0, 0,0,1, 1,0,1, 0,1,1, 1,1,1
			0,0,   1,0,   1,0,   0,1,  1,0,   0,1,   0,1,   1,1
	-s outbit	save best bitstream - for use with morphogenesis,
			at each iteration of growth, the latest circuit
			is evaluated (not just with -s option), and if
			a better circuit is discovered, then it is saved
			to a file of the form: outbit.iter# where iter#
			is the current iteration of growth
	-sr outbit	saves best bitstream, and also records all set
			instructions to OUTBIT.record.txt
	XCV50CLK1.bit	a bitstream for the XCV50 device, and with the
			clock lines tied to GCLK1 (for using with the
			VirtexDS simulator). The input and output IOBs
			should have been routed to CLBs neighbouring the
			evolvable area, using the EHWLayout program.
	inIOB		semicolon-delimited list of the input bus IOBs,
			each IOB is in the format SIDE,index,site#.
			SIDE is one of LEFT, RIGHT, TOP, BOTTOM. 
	outIOB		the output bus IOBs in the same format as inIOB
	inCLBslice	the entry points for input signals within the
			evolvable region of the FPGA. These are a
			semicolon-delimited list with each input in the
			format of row,col,slice,LE or row,col,sliceLE
			where slice is 0 or 1, and LE is 0 or 1 (for
			which LUT, G or F, is used), alternatively the
			equivalent sliceLE format S#(G-Y | F-X) can be
			used.
	outCLBslice	the exit points for output signals within the
			evolvable region of the FPGA. It is in the same
			format as for outCLB
	minCLB		minimum CLB row and column of evolvable region
	maxCLB		maximum CLB row and column of evolvable region

	contentionFile	information file for preventing contentious
			resources being set at the same time

	... the following is for non-developmental EHW

	dchromfilelist	semi-colon delimited list of split, decoded,
			and JBits-converted chromosomes for building cct
			can use "-" or empty "" elements in the list to
			indicate no build instructions starting at that
			in/out CLB slice-Logic Element

	... the following are for performing morphogenesis

	dchromfile	pre-processed and JBits-converted chromosome
			for directing the morphogenesis system	
	cytofile	JBits converted cytoplasmic determinants
			placement file
	numTestIter	the number of circuit evaluations to perform
	nGrowthSteps	the number of growth steps per evaluation

	... the following are optional

	transcribeRate	number of bases transcribed each growth step
			(default is 4)
	polyToGene	RNA polymerase II molecules per gene, this limits
			the number of genes that can be active at any time.
			use a value < 1.0 to indicate a ratio, or >1 to set
			a fixed number of polymerase molecules
			(default is 0.3)
	polyThreshold	theshold to be exceeded for a free polymerase
			molecule to bind to any free gene at each time step
			(default is 0.8 -> 20% probability of binding)

	seed		optional random seed for morphogenesis system


- Adder2x2Fixed.pl

  also utilises file: adder2x2fixedActiveLUTsettings.txt:

	which pre-sets the ActiveLUTFNs for the adder2x2fixedlut tests
	it will allow the LUT function to be reconfigured according
	to changes to the routing lines (muxes) connecting to that LUT

  This script simply sits between the GA and the test class, and
  removes any entries from the decoded converted chromosome
  before passing it on to the test class.

  Synopsis:
  [perl] Adder2x2Fixed.pl (-mux | -6200 | -lut) java javaoptions \
	 testclass paramlist
 
  Args:
		-mux		uses fixed routing (muxes), only LUTs can
				be modified
		-6200		emulates a 6200-like logic element architecture
				with fixed (output) routing, and only LUTs
				function and input muxes can be modified.
		-lut		uses fixed LUT (functions), only muxes can
				be modified
		java		the call to the java interpreter
		javaoptions	and its command line options
		testclass	the java class to construct and test cct
				based on modified chromosome
		paramlist	the parameters for the java program

  Inputs: (none)

  Outputs: (stdout)
  		- the output from the test class





Examples:
--------

The following are examples of how to use the TestFnInToOut program,
noting that the use of the backslash at the end of the line,is simply
for cosmetic purposes - the entire command line parameters can be put
on a single line.



$ java TestFnInToOut -norecurse test.bit		\
       "LEFT,11,1;LEFT,11,2;LEFT,10,2;LEFT,10,3"	\
       "RIGHT,6,1;RIGHT,5,1;RIGHT,4,1;RIGHT,4,2"	\
       "7,8,S0G-Y;7,8,S1F-X;8,8,S0G-Y;8,8,S1F-X"	\
       "4,15,S0G-Y;6,15,S1F-X;9,15,S0G-Y;11,15,S1F-X"	\
       "4,8" "11,15" contentionInfo.txt

$ java TestFnInToOut -sr test.bit layout.bit		\
       "LEFT,11,1;LEFT,11,2;LEFT,10,2;LEFT,10,3"	\
       "RIGHT,6,1;RIGHT,5,1;RIGHT,4,1;RIGHT,4,2"	\
       "7,8,S0G-Y;7,8,S1F-X;8,8,S0G-Y;8,8,S1F-X"	\
       "4,15,S0G-Y;6,15,S1F-X;9,15,S0G-Y;11,15,S1F-X"	\
       "4,8" "11,15" contentionInfo.txt			\
       "dchrbits_1.txt;-;dchrbits_3.txt;dchrbits_4.txt"

$ java TestFnInToOut -v -onebitadder -activelutfnsonly	\
       -synchonly -s test.bit layout.bit		\
       "LEFT,11,1;LEFT,11,2;LEFT,10,2;LEFT,10,3"	\
       "RIGHT,6,1;RIGHT,5,1;RIGHT,4,1;RIGHT,4,2"	\
       "7,8,S0G-Y;7,8,S1F-X;8,8,S0G-Y;8,8,S1F-X"	\
       "4,15,S0G-Y;6,15,S1F-X;9,15,S0G-Y;11,15,S1F-X"	\
       "4,8" "11,15" contentionInfo.txt			\
       dpopbitsout_4.txt cytotestbits.txt 30 1 4 0.3 0.8


All of these examples take a pre-routed XCV50 bitstream with GCLK1
connected, which, along with the input and output bus IOB locations,
entry and exit CLBs, and minimum and maximum CLB boundaries are
generated using the EHWLayout program. These examples also use the
same contention information file, provided in the JBitsRes package.

The first example will test the fitness of the circuit configured by
the supplied bitstream, by applying alternating sets of signals to the
inputs, and testing for changes in the outputs (and at intermediate
locations) to determine the extent of connectivity from inputs to
outputs. Each set of stimuli and responses are output, along with
the calculated fitness. The maximum fitness attained is output at the
end of the run (there may be some preceding lines from VirtexDS, for
eg: "speedGrade: -6").

The second example constructs a circuit from the build instructions
in the dchrbits_?.txt files. Each one is used in turn, to build on
the existing circuit, by starting at the associated Nth input/output
CLB (starting with input CLBs, then when all the input CLBs have been
started at, then using the output CLBs). As it uses the -sr test.bit
(save andrecord) switch, it will save the resulting bitstream to
test.bit, and a record of all the JBitsResources set() instructions
executed to produce the bitstream. The circuit is tested for
connectivity from inputs to outputs using both the recursive test
(which gives fine grained information), and by passing oscillating
signals - which allows accurate testing of LUT functionality.

The last example combines the morphogenesis process with testing of
the circuit, in the same manner as above, at each stage of development
(ie each iteration of growth), and by specifying the '-s' option, each
time a fitter circuit is encountered it will be save (for example if
a fitter circuit is found at growth iteration 5, then this will be
saved to test.bit.5). The actions that occur in the first example, are
here done at each growth iteration, and furthermore, by using the -v
(verbose) switch, the initial signal states throughout the evolvable
area and at the output bus are displayed, and for each (grown) circuit
the locations where changes in signal have been detected are displayed.
Specifying "synchonly" means that no speed information is used by the
simulator, and there is less chance of VirtexDS hanging on downloading
bitstream, but requires that there actually is no asynchronous slice
outputs specified in the gene coding regions of the decoded chromosome!
By specifying "activelutfnsonly", we can avoid passing signals through
the circuit to test connectivity - this means that when a configuration
results in no connections to the outputs, we don't need to use the
simulator, thus speeding the process up significantly, especially since
we will run this test (on the newly configured circuit) at least 30 times!
The fitness will be based on connecting inputs to outputs (50%) plus
how closely the outputs match a one-bit adder function (50%).






The following is an example, with outputs, of using TestInToOutRecurse,
with a hand crafted solution created with:

$ java JBitsResources XCV50 layout.bit contentionInfo.txt \
       		      load record.txt test.bit



$ java TestInToOutRecurse -v -le -ndo test.bit	 \
  "LEFT,11,1;LEFT,11,2;LEFT,10,2;LEFT,10,3"	 \
  "RIGHT,6,1;RIGHT,5,1;RIGHT,4,1;RIGHT,4,2"	 \
  "7,8,S0G-Y;7,8,S1F-X;8,8,S0G-Y;8,8,S1F-X"	 \
  "4,15,S0G-Y;6,15,S1F-X;9,15,S0G-Y;11,15,S1F-X" \
  "4,8" "11,15" contentionInfo.txt

The output from this example follows:

	creating circuit test...

	initial fitness=100.0
	connection states are...


	     0 1  0 1  0 1  0 1  0 1  0 1  0 1  0 1
	    ----------------------------------------
	   | . .  . .  . .  . .  . .  . .  . F  . FoO X
	 7 | . .  . .  . .  . .  . .  . .  . 7  7 7 | Y
	   | . .  . .  . .  . .  . F  . F  F F  F 7 | X
	 6 | . .  . .  . .  . .  . 7  F 7  F 7  F 7 | Y
	   | . F  . F  7 F  7 F  F F  F F  F F  F F | X
	 5 | . .  F 7  F .  F 7  F .  F .  F 7  Fo. O Y
	   I . Fi F F  F F  F F  F F  F F  F F  F F | X
	 4 I Fi.  F 7  F 7  F .  F .  F .  F 7  F . | Y
	   I . Fi F .  . .  . 7  7 F  F F  F F  F F | X
	 3 I Fi.  F .  F F  F 7  F 7  F F  F F  F F | Y
	   | F .  . .  . .  . .  . .  F F  F F  . FoO X
	 2 | . 7  . .  . .  . .  . .  F 7  F 7  7 7 | Y
	   | . 7  . .  . .  . .  . .  F F  F F  . F | X
	 1 | 7 .  . .  . .  . .  . .  F 7  F F  . . | Y
	   | . .  . .  . .  . .  . .  7 7  7 F  . 7 | X
	 0 | . .  . .  . .  . .  . .  7 7  7 F  Fo7 O Y
	    ----------------------------------------
	      0    1    2    3    4    5    6    7

	Highest fitness=100.0
	100.0

	Done. exiting...


  The connection states above have the following meaning:

  logic element state is given by adding the following:
	- LUT input connected to neighbour output		=> 1
	- LUT function passes or inverts input			=> 2
	- LUT output connected to 1 or 2 OUT lines		=> 4
	- OutToSingle for one of the out lines set to on has
	  a single line connected to LUT input in neighbour	=> 8

   giving the LE possible values of:
	0 - not connected to input IO (represented as a '.')
	1 - input connected
	3 - input connected and LUT fn passing/inverting input
	7 - input connected, LUT fn valid & LUT result connected to OUT bus
	F - input and output fully connected with valid LUT fn

   note that the 'i's and 'o's to the right of cell states indicate
   that these cells are input and output cells, respectively.





Environment/platform notes:
--------------------------

Note that the Java classes were developed using JBits 2.8 and the
Java JDK 1.2.2 + JRE, on WinXP, with Cygwin version 1.3.10.



