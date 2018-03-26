
			      README.txt

		     for JBits Resources Interface

			last modified 2/10/2004

			      J.A. Lee



Overview:
--------

This directory contains classes for accessing (setting, querying, and
performing equivalence tests) JBits resources in a string-based format
equivalent to the integer array based format provided in the
JBits.Virtex.Bits classes, as required for manipulating FPGA resources based
on the contents of decoded chromosomes (as required by an evolvable hardware
system). This also allows a higher-level of abstraction where necessary, and
ensures that contention is avoided.

This directory also contains scripts for converting from the parsed and
intermediate form (Resource, Attribute, Setting number) of decoded
chromosomes, or cytoplasmic determinant placement files, to equivalent formats
that directly specify JBits resources.




Description:
-----------

The JBits Resources classes and scripts form the intermediate stages between
a decoding a chromosome and generating the Virtex bitstream with JBits. There
are 2 closely related stages, JBits Conversion and JBits Interface, that
perform the necessary tasks. Visually, this is shown as:

    ----------              ----------          ---------           -----
   |Chromosome|            |  JBits   |        |  JBits  |         |JBits|
   | Decoder  |----------->|Conversion|------->|Interface|-------->| API |
    ----------              ----------          ----------          -----
                 resource,               bits,             int[][],
		 attribute,              value             int[]/int
		 settings


The chromosome decoder (GEPP), extracts the information contained in the
chromosome, with the Virtex resources to be queried or set encoded in a
format of resource, attribute, settings. This format will be referred to as
resattrset format henceforth.

The resource field is used to indicate the type of evolvable hardware
resource (instantiatable in hardware or simulated cell), such as SliceIn,
for slice input multiplexors. The attribute field narrows this down further,
for example a SliceIn resource with atrribute LUT indicates that this will
be a LUT input. The meanings of setting fields varies between resources,
however, as an example, the settings field(s) for routing resources (such
as inter CLB lines) are numeric, and indicate which of the available lines
to select, while the following setting may be used to determine if line is
ON or OFF.

The resattrset format then needs to be converted to a format more suitable
for use with the EHW system. This is done by converting to a string that
contains JBits 'bits' and 'value' constant names (bitsval format). At this
point it is also possible to map from resattrset to various cell resources
according to cell granularity (eg at logic-element granularity there is only
a single LUT) or to subsets of the available resources (synchronous slice
outputs only, for example).

The bitsval format string is then used with the JBits interface that
translates from the bitsval string to a bits constant (an int[][]), defined
within the resource's class in the com.xilinx.JBits.Virtex.Bits package,
that indicates which bits in the CLB to configure, and a value constant (an
int[] or int), defined within the same class, that indicates what value to
set the bits to. For example, S0G2.S0G2 is the bits field for configuring
the slice 0 LUT G line 2 input, and S0G2.SINGLE_WEST20 is a value that the
multiplexor can be configured to, for connecting that line to the LUT input.

Using this interface ensures that evolved settings won't create any damaging
configurations. The JBits interface also provides some higher-level
constructs, specifically LUT incremental functions and Slice RAM
configuration. The resources supported by the JBits interface are elaborated
below.



JBits Resources:

JBits resources are configurable resources on the Virtex FPGA that
are accessed via the Xilinx JBits (2.8) Java API. Most resources are
manipulated directly as presented in the API. These resources are
defined here as being of type "jbits" or "LUT". Such resources are
defined within the JBts API as int[][] constants inside classes within
the com.xilinx.JBits.Virtex.Bits package. These 'bits' constants
define the resource being accessed within a CLB/IOB/BRAM location,
using the JBits get(row,col,bits) or set(row,col,bits,value) methods.
The 'value' setting to configure the resource to is given by int[]
constants defined within the same classes. For example,to configure
the line driving CLB slice 0 input BX to be a single line from the
west, the bits constant required is S0BX.S0BX, and the value constant
is S0BX.SINGLE_WEST8.


JBits resources are specified as strings in the following format:

	resource-type "=" resource-details

  - resource-type is one of:
	jbits, SliceRAM, LUT, LUTIncrFN (and TF)

  - resource-details for jbits, LUT, LUTIncrFN differs slightly according
	to whether these are the output of the conversion stage, or if
	they are are the input to the JBits interface via morphogenetic
	system. In the former case they may "/" delimited such that different
	resources are to be set according to the slice or logic element that
	the cell maps to. In the case of slice granularity, the format is:
		bitslice0 "," valslice0 "/" bitslice1 "," valslice1
	for logic element granularity the same format, but with 4 items is
	used, ie. bitLE0,valLE0/bitLE1,valLE1/bitLE2,valLE2/bitLE3,valLE3.
	However, some resources may be shared across the whole CLB; these
	will then use the format:
		bitsclb "," valclb
	In the latter case, the appropriate slice/logic-element item for the
	cell will have been extracted, and is passed to the JBits interface
	as bits and value strings, or setop,boolop,lines for LUTIncrFNs

  - TF's aren't JBits resources, but are found in cytoplasmic
    determinant files, and parsed chromosomes. They are used in
    the morphogenesis process (elsewhere). resource-details for
    a TF is in the form of: TF-type "," spreadfromsrc "," bind-seq
	- TF-type is one of Local, Morphogen, Cytoplasm
	- spreadfromsrc is a possibly empty binary string
	- bind-seq is a string of base-4 digits (ie [0-3])


SliceRAM resources:

The S0RAM and S1RAM JBits classes define control bits for the Slice
RAM that allow CLB LUTs to be used in various RAM configurations.
These classes have 7 bits constants, for controlling the muxes, to
provide the desired functionality: LUT_MODE, DUAL_MODE,
F_LUT_RAM, G_LUT_RAM, F_LUT_SHIFTER, G_LUT_SHIFTER, RAM_32_X_1.
Each of which can be set to one of two possible values (ON or OFF).

However, these are not totally independent resources; to get the
desired functionality may require setting several bits in conjunction,
and there is also the possibility of illegal configurations (for
example it is not possible to put the F LUT in RAM mode and the G LUT
in Shifter mode in the same Slice). Thus we use a higher level encoding
that ensures that we set these resources sensibly, avoiding conflicting
settings. The SliceRAM resource-type provides this functionality by
combining settings in a sensible manner, as follows.

SliceRAM divides resources, according to the functionality that will be
provided by the LUTs in that slice, into LUT, RAM, and shifter
functions. LUTs can be in either single or dual mode, RAM can be either
single- or dual-ported, and there is also 32x1-bit (and 16x2-bit) RAM,
while shifters require dual mode to be off.

In the format of decoded chromosomes, the resources are encoded with
an attribute of dual mode on or off, and a setting of LUT, RAM, SHIFT,
or 32X1. This is then converted into a comma delimited format of bits
and value constants (by the perl conversion script), with all other
bits in the S0RAM/S1RAM class being set to OFF. The settings for
SliceRAM are provided as follows:
DUAL_MODE.(ON | OFF), (RAM_32_X_1.ON | F_LUT_RAM.ON, G_LUT_RAM.ON | 
F_LUT_SHIFTER.ON, G_LUT_SHIFTER.ON).

Note that as we manipulate SliceRAM at a slightly higher level which
involves setting a few resources (to ON) in conjunction, the
get(row,col,bits) method can't just return a single value constant,
instead it returns an int[] of 0's and 1's which indicates which of the
bits constants are set to ON, in the following order (same order as
the fields in javadoc): DUAL_MODE, F_LUT_RAM, F_LUT_SHIFTER, G_LUT_RAM,
G_LUT_SHIFTER, LUT_MODE, RAM_32_X_1.

Resource details for SliceRAM is in the form of:
	"S0RAM," bitsconst1 "," bitsconst2,.. "/"
	"S1RAM," bitsconst1 "," bitsconst2,..
    - bitsconst? will be one of:
	 DUAL_MODE, F_LUT_RAM, F_LUT_SHIFTER, G_LUT_RAM,
	 G_LUT_SHIFTER, LUT_MODE, RAM_32_X_1


LUT resources:

The function performed by a LUT is defined by an inverted, least
significant bit first int array of length 16. The LUT is set, and can
be queried, in exactly the same manner as other jbits resources,
except that the value array is not a predefined constant, and so
must supplied by the caller. As an example, setting the slice 1 F LUT
to an OR on all inputs would use a bits constant of LUT.SLICE1_F, and
an int[] array with a value of {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0}.
Obviously, this is not an easy space for evolution to explore and
to learn useful functions. For this reason, the LUTIncrFN resource
is provided.


LUTIncrFN resources:

A LUTIncrFN is a way of combining a simple boolean function with the
existing function encoded in the LUT to produce a more complex LUT
function. The manner of combining the new function with the old can
be one of SET,AND,OR,XOR. The first just sets the new LUT function to
be the simple boolean function provided, while the others do a bitwise
AND/OR/XOR of the new function (as a truth table) and existing LUT to
produce the updated LUT truth table values.

The boolean functions that may be used for the new function are:
AND, OR, XOR, NAND, NOR, XNOR, MJR0, MJR1. The latter 2 operations are
majority functions, with tie-breaks on 0 or 1, respectively. The new
truth table is made by applying the function to the specified lines,
supplied as a 4 character template string of '-','0','1's, one for
each LUT input line, with 0 indicating invert input, 1 direct input,
and '-' indicating ignore input.

When performing a get(row,col,bits) on a LUTIncrFN resource, the value
returned is the LUT's contents, the same as for a LUT resource, as the
resources are the same. LUTIncrFN simply provides a higher-level
abstraction for setting, and comparing these same LUTs. When comparing
LUT contents with LUTIncrFNs (for binding in morphogenesis system),
the meaning of the set operation changes, here SET requires all entries
in the LUT to match, AND requires at least 1 entry from each group to
match, OR requires at least 1 entry from 1 group to match, and XOR
requires at least 1 group not to have any matches. A group is defined
as being one of the 2^(number-of-active lines) combinations of active
lines.

a LUTIncrFN expression is in the form of: setop "," boolop "," lines
      - setop: how to combine the boolean expression with the existing
	       LUT. setop is one of SET, AND, OR, XOR
      - boolop: specifies which boolean function to perform on the
		input lines. boolop is one of AND, OR, XOR, NAND, NOR,
		XNOR, MJR0, MJR1. The latter 2 ops are majority
		functions, with tie-breaks on 0 or 1, respectively.
      - lines:	4 char template string that specifies how the 4 input
		lines are used by the boolean operation to generate a
		truth table. lines is comprised of '-','0','1'
		characters, one for each input, with 0 indicating
		invert input, 1 direct input, and '-' indicating
		ignore input. note that the inputs are in order 4,3,2,1


LUTActiveFN resources:

To aid the exploration of function space, it could be useful to eliminate
unconnected inputs (that are held high) and undriven input lines (held low)
from dominating LUT configurations. To achieve this another LUT type,
LUTActiveFN, is introduced, which provides the same basic functions as
LUTIncrFN (AND, OR, XOR, NAND, NOR, XNOR, MJR0, MJR1) but without incremental
applications of LUT function (ie just does a SET). Where it really differs is
that it checks the existing LUT input configuration and only applies the
basic function to 'active' input lines, that being inputs that have a line
connected that is driven by a CLB output.

For the line to be considered active the connected line must be connected to
a changing signal, originating either in the circuit inputs, or in a feedback
loop, which may produce an oscillating signal (clock divider).

This scheme is based on the idea that cell functionality should only be based
on inputs that are used. In nature this would probably be the case (for eg
neurons). Cell functionality would not be crippled by a large state space of
useless functions, particularly when there are a large number of possible
inputs. Here, however, FPGA LUTs are designed for human or automated design
software, such that they allow as much flexibility within the constraints of
an FPGA.

a LUTActiveFN expression is in the form of: boolop "," invlines
      - setop: how to combine the boolean expression with the existing
	       LUT. setop is one of SET, AND, OR, XOR
      - lines: 4 character string, indicating which lines are inverted
	       0's indicate inverted line, 1's - input line uninverted
	       for active lines (inactive lines are ignored)
	       note that the inputs are in order 4,3,2,1



Description of JBitsRes files:
-----------------------------

The following files comprise the JBitsRes component of the morphogenetic EHW
system:

  JBitsRes_README.txt (this file)

  JBitsResources.java		The Jbits Interface class
  JBitsLookup.java		Resolves bits,value String to JBits const
  ContentionLookup.java		Checks for contention
  ContentionEntry.java		A single entry from contention database
  contentionInfo.txt		The contention database file
  LUTIncrFN.java		Configure/query LUT according to an incr expr
  ActiveInputLEs.java		config/query LUT on active inputs w/ basic FN

  StringSplitter.java		Split utility for use with JDK 1.2.2

  resattrset2bitsval.pl		Converts from resattrset format to bitsval
  convToJBits.conf		Conversion functions for each resource
  VirtexDSconvToJBits.conf	Same, but only uses synchronous slice outputs
  NFFconvToJBits.conf		Same, but only uses unregistered slice outputs
  slimconvToJBits.conf		Same, but uses a subset of available resources
  legranslimconvToJBits.conf	Same as prev, but at logic element granularity
  legrantrimconvToJBits.conf	as above but with most LUT-only functionality
  legranDSconvToJBits.conf	as above but with shared out lines & LUTIncrFN
  legranconvToJBits.conf	as above but also unregistered slice outputs
  legranNFFconvToJBits.conf	as above but only unregistered slice outputs
  test_conv2jbits_X.pl		Template for inserting conversion code to test


The following attempts to outline the uses of the classes and scripts.
For more information, the source code should be examined.


Classes:
-------

JBitsResources: this class acts as an interface to all the JBits resources,
		taking care of contention checking (via ContentionLookup),
		translation of higher-level constructs to JBits settings
		(via this class and LUTIncrFN), and conversion of strings
		to actual JBits parameters (int[][] and int[] via
		JBitsLookup). Note that attempting to set a resource that
		would cause contention to occur, simply results in no
		action occuring. It provides operations for setting,
		querying and testing equivalence of resource settings.

		Usage: JBitsResources devname bitfile contentionfile     \
		(load setfile [ outbit ]) |                              \
		(row col (get type=bits | set type=bits,val [ outbit ] | \ 
			  isEquiv bits type=bits,equivVal))

		- devname is a valid Virtex device name, such as XCV50
		- bitfile is a valid bitstream file for that device
		- contentionfile is a text file containing information
		  on contentious resources (see contentionInfo.txt)
		- load setfile [outbit] - loads recorded JBitsResources
		  set instructions, one per line, from setfile, and
		  executes them, the resulting bitstream may be saved to
		  outbit. The format of the instructions is:
		  	set: row, col, type=bits,val
		  eg:
			set: 4,13: jbits=OUT6.OUT6,OUT6.S1_YQ
		- row col are JBits CLB row and column addresses
		  type is the resource type, which must be one of:
		  	LUT, LUTIncrFN, SliceRAM, JBits
		  bits is a JBitsResources 'bits' field (eg S0BX.S0BX)
		  val is an associated 'value' field (eg S0BX.SINGLE_EAST19)
		  or, for a LUT, an inverted truth table configuration,
		  or, setop,boolop,lines for a LUTIncrFN
		  or, S[01]RAM, bitsconst1,bitsconst2,.. for SliceRAM
		- get type=bits	- gets the value for the bits resource
		- set type=bits,val [ outbit ] - sets the value for the
			resource, and optionally saves the result to file
		- isEquiv bits type=bits,equivVal - tests if the current
			value for the bits resource is the same as the
			specified equivVal

	 	Current Limitation Note:
		  The JBitsResources class doesn't handle cases with subclasses,
		  as it expects to be in the form of classname.constname which
		  means it can't deal with classname.subclassname.constname as
		  is required for some of the slice control muxes, eg:
		  S0Control.YDin.YDin



JBitsLookup:	implements a lookup for JBits resources, to return the
		constants required for configuring and querying the
		Virtex, when supplied with the String representation of
		the resource, or to do a reverse lookup to return the name
		of the resource associated with a given bit pattern for a
		given JBits bits class


LUTIncrFN:	implements all the required functionality for setting and
		comparing LUTs using incremental function expressions, or
		16 character strings of 0's and 1's. Incremental
		expressions are in the format of setop,boolop,lines
		Note that LUT arrays should be supplied (and are returned)
		as stored on the FPGA, that is, inverted and least
		significant bit first

		Usage: LUTIncrFN LUTarray setop boolop lines

		- LUTarray is a list of comma-delimited 1's and 0's and
		  should be supplied (and are returned) as stored on the
		  FPGA, that is, inverted and least significant bit first
  		- setop specifies how to compare the existing LUT with the
		  table produced by the boolean operation (boolop). setop
		  is one of:
			SET - all entries must match,
			AND - at least 1 entry from each group must match,
			OR - at least 1 entry from 1 group must match, and
			XOR - at least 1 group must not have any matches
 		- boolop specifies which boolean function to perform on
		  the input lines, one of: AND, OR, XOR, NAND, NOR, XNOR,
		  MJR0, MJR1. The latter 2 ops are majority functions, with
		  tie-breaks on 0 or 1, respectively.
 		- lines	is 4 character template string that specifies how
		  the 4 input lines are used by the boolean operation to
		  generate a truth table. lines is comprised of '-','0','1'
		  characters, one for each input, with 0 indicating invert
		  input, 1 direct input, and a '-' indicating ignore input

ActiveInputLEs:	used to determine which LEs within the evolvable region of
		a FPGA's CLB matrix are active, and which inputs to their
		LUTs are active. this is so that inactive lines can be
		removed from LUT functions by LUTActiveFNs

ContentionLookup: implements a lookup for contentious JBits resources
		  from a table that is loaded from a file containing the
		  necessary contention information (see contentionInfo.txt
		  entry for details on file format). This class is used by
		  JBitsResources to check for contention.

ContentionEntry: a structure used by ContentionLookup for storing
		 information on a Virtex resource that will cause
		 contention problems with another resource.


StringSplitter:	implements a String split utility, with additional
		functions for splitting and trimming whitespace, and for
		splitting and parsing fields into an array of int or long.
		this splitter class is needed as JBits uses jdk 1.2.2 and
		String.split() isn't supported before jdk 1.4! This class
		is used throughout the java portions of the EHW system.


Scripts:
-------

resattrset2bitsval.pl:

		takes a cytoplasmic determinants file, or a decoded
		set of chromosomes, and outputs a file (or one for each
		chromosome) which has basically the same format but with
		the resource, attribute, settings elements replaced with
		JBits bits and value constants, for use with
		JBitsResources class as used within a java/JBits
		evolvable hardware generation program.

  Synopsis:
  perl resattrset2jbits.pl [-d] [-c | -n] resToJBitsConf outfname outfext
 
  Args:
  		-d		- output extra info for debugging to STDERR
  		-c		- input/output is cytoplasmic determinants
  		-n		- input/output is a non-developmental
				  chromosome
		conv2JBitsConf	- configuration file that contains the
      				  user-defined functions for converting from
      				  resources with attributes and settings,
				  into JBits bits and value constants where
				  possible
		outfname	- the basename for the output
		outfext		- the extension for the output
				  output files will have names of:
				  	outfilename_CHROMOSOME#.outfext


		The resource, attribute, settings format in the supplied
		chromosome or cytoplasmic determinants file consists of
		- resource type:
			TF, SliceIn, SliceToOut, OutToSingleDir
		  	OutToSingleBus, SliceRAM, LUTBitFN, LUTIncrFN.
		- the resource's attribute:
		  	TF: Local, Morphogen, Cytoplasm;
		  	LUTBitFN, LUTIncrFN: F, G;
		  	SliceRAM: DUAL_OFF, DUAL_ON;
		  	SliceIn: F1-F4, G1-G4, BY, BX, SR, CE, CLK;
		  	OutToSingleDir: E, N, S, W;
		  	OutToSingleBus: 0-7
		- a comma separated list of settings for that resource


convToJBits.conf:

		configuration file, containing scripts for conversion from
		Virtex-Resource, Atrribute, Setting number format (as
		generated from decoding a chromosome according to the
		Virtex genetic code) to JBits bits and value constants.
		Each resource type should have an associated
		conv2jbits_RESOURCENAME function defined in this file, that
		is called to convert resources of that type, and which
		takes parameters: attr (a string), settings (an array of
		strings). The 'attr' and 'settings' parameters are the
		attribute and settings that are returned from their
		associated genetic table's decode_RESOURCENAME function.
		An empty string in settings[0] should be taken as
		indicating no change to the current settings for that
		resource (ie don't generate any code) an empty string in
		settings[1..] can indicate to use the default setting, or
		ignore

		each function returns either an empty string, if its
		parameters are not complete (as may often be the case at
		the boundaries of bind sites or gene coding regions), or
		a valid JBits Resource format string (as elaborated near
		he start of this README)


VirtexDSconvToJBits.conf:

		This is a variant of the above (convToJBits.conf) that is
		specifically for use with the VirtexDS. It avoids using
		resources that cause problems when using the VirtexDS
		simulator, specifically direct inputs from the same CLB
		S[01]_[YX] and other non-synchronous outputs from other
		CLBs, and removes the non-GCLK1 global clock buffers.
		conv2jbits_SliceIn has had S[01]_[YX] inputs removed
		(replaced with OFF).
		conv2jbits_SliceToOut has had X,XB and Y,YB outputs
		replaced with XQ/YQ


NFFconvToJBits.conf:

		this is the same as VitrexDSconvToJBits.conf, but only
		uses S0_Y, S0_X, S1_Y, S1_X slice outputs, along with
		OFF and NOP (not used, reserved for later use by tristate)


legranDSconvToJBits.conf:

		This is a logic element granularity equivalent of
		VirtexDSconvToJBits.conf, minus the slice-wide Ctrl lines
		and SliceRAM functionality. Logic element outputs are
		limited to synchronous (registered) signals from the flip
		flop associated with the LUT.

		The logic element granularity synchronous model is
		constructed from the following:

		    LUT-input			direct connect line

		S0G1/S0F1/S1G4/S1F4:
			oFF N15 S6 W5 N19 S12 W17 N22 S13 W18 S21 OUT_WEST1

		S0G2/S0F2/S1G3/S1F3:
			OFF E11 N12 S1 W2 E23 N17 W20 OUT_WEST0

		S0G3/S0F3/S1G2/S1F2:
			OFF E5 N13 S0 W6 E7 S14 W8 E9 S20 W15 E10 W23 E21
			    OUT_EAST7

		S0G4/S0F4/S1G1/S1F1:
			OFF E4 N0 S2 W3 E16 N1 S8 W11 E17 N3 S9 W14 E19
			    N5 S18 E22 N7 N10 OUT_EAST6


		LUT FN's: 16 bits (full 4-input truth table)


		CLB outputs to output bus

		OFF		OUT0 OUT1 OUT2 OUT3 OUT4 OUT5 OUT6 OUT7
		S0_YQ		OUT0 OUT1 OUT2 OUT3 OUT4 OUT5 OUT6 OUT7
		S0_XQ		OUT0 OUT1 OUT2 OUT3 OUT4 OUT5 OUT6 OUT7
		S1_YQ		OUT0 OUT1 OUT2 OUT3 OUT4 OUT5 OUT6 OUT7
		S1_XQ		OUT0 OUT1 OUT2 OUT3 OUT4 OUT5 OUT6 OUT7


		Output Bus		to direct Singles

		OUT0		E2  	N0  N1  S1  S3  W7
		OUT1		E3  E5  N2  	S0  	W4  W5
		OUT2		E6  	N6  N8  S5  S7  W9
		OUT3		E8  E11 N9  	S10 	W10 W11
		OUT4		E14 	N12 N13 S13 S15 W19
		OUT5		E15 E17 N14 	S12 	W16 W17
		OUT6		E18 	N18 N20 S17 S19 W21
		OUT7		E20 E23 N21 	S22 	W22 W23

		to CLB offset:	(0,+1)  (+1,0)	(0,-1)	(-1,0)


legranconvToJBits.conf:

		this is the same as legranDSconvToJBits.conf, but also
		uses S0_Y, S0_X, S1_Y, S1_X. Note that it is biased
		towards use of registered outputs, 2 out of 8 of the
		encodings used for each registered output in a slice
		(YQ/XQ), and only 1 out of 8 for each unregistered (Y/X)
		the other 2 being OFF and NOP (not used, reserved for
		later use by tristate)


leNFFgranconvToJBits.conf:

		this is the same as legranconvToJBits.conf, but only
		uses S0_Y, S0_X, S1_Y, S1_X slice outputs, along with
		OFF and NOP (not used, reserved for later use by tristate)


legrantrimconvToJBits.conf:


		This is a trimmed down version of legranDSconvToJBits.conf,
		lacking LUTIncrFN's, so that the morphogenesis process can
		be compared against an equivalent standard GA approach, and
		each logic element has 2 out bus lines assigned to it, and
		is also only able to access the out mux to single lines that
		are driven by its own out bus lines. S0G-Y uses out 0,1;
		S0F-X uses 2,5; S1G-Y 4,6; S1F-X out 3,7. All single lines
		directly connect to a LUT input in its neighbour. Direct
		connections from outbus lines 0,1,6,7 to neighbouring CLBs
		are also disallowed. This model, differs from the previous
		largely in the connections out of each logic element, as
		shown below.


		CLB outputs to output bus

		OFF		OUT0 OUT1 OUT2 OUT3 OUT4 OUT5 OUT6 OUT7
		S0_YQ		OUT0 OUT1
		S0_XQ		          OUT2           OUT5
		S1_YQ		                    OUT4      OUT6
		S1_XQ		               OUT3                OUT7


		LE     Out Bus	    to direct Singles

		S0G-Y	OUT0	E2  	N0  N1  S1  S3  W7
			OUT1	E3  E5  N2  	S0  	W4  W5

		S0F-X	OUT2	E6  	N6  N8  S5  S7  W9
			OUT5	E15 E17 N14 	S12 	W16 W17

		S1G-Y	OUT4	E14 	N12 N13 S13 S15 W19
			OUT6	E18 	N18 N20 S17 S19 W21

		S1F-X	OUT3	E8  E11 N9  	S10 	W10 W11
			OUT7	E20 E23 N21 	S22 	W22 W23

		to CLB offset:	(0,+1)  (+1,0)	(0,-1)	(-1,0)



legranslimconvToJBits.conf:

		This is the same as above, but uses a _very_ slimmed down
		set of resources: 1 input line per LUT, 4 possible LUT fns
		(always 0, always 1, pass input, invert), and each out bus
		line connects to 1 single in each of the 4 directions
		(S1G-Y only connects to 1 single in each of 3 directions),
		that directly connect to a used LUT input in its neighbour.

		A graphical representation of the connectivity of this
		model is as follows:

	        E4  -> S0G4 -> S0_YQ -> OUT0 -> S3  -> (-1,0) N3  -> S0F4
		N7		        OUT1 -> E3  -> (0,+1) W3  -> S0F4
		S18			        N2  -> (+1,0) S2  -> S1F1
		W11			        W4  -> (0,-1) E4  -> S0G4

	        E19 -> S0F4 -> S0_XQ -> OUT2 -> N8  -> (+1,0) S8  -> S1G1
		N3		        	S5  -> (-1,0) N5  -> S1F1
		S9			        S7  -> (-1,0) N7  -> S0G4
		W3			OUT5 -> W16 -> (0,-1) E16 -> S1F1

	        E22 -> S1G1 -> S1_YQ -> OUT4 -> E14 -> (0,+1) W14 -> S1F1
		N10		        	W19 -> (0,-1) E19 -> S0F4
		S8			OUT6 -> N18 -> (+1,0) S18 -> S0G4

	        E16 -> S1F1 -> S1_XQ -> OUT3 -> N9  -> (+1,0) S9  -> S0F4
		N5		        	S10 -> (-1,0) N10 -> S1G1
		S2			        E11 -> (0,+1) W11 -> S0G4
		W14			OUT7 -> W22 -> (0,-1) E22 -> S1G1


slimconvToJBits.conf:

		This is a variant of legranslimconvToJBits.conf, but at
		slice granularity.



test_conv2jbits_X.pl

		This is a template for inserting conversion code to test
		before putting into the conversion configuration scripts.


Other Files:
-----------

contentionInfo.txt:

	this file contains necessary information for detecting
	contention on single lines in the Virtex FPGA. The contention
	table is in the format ( "\" indicates continuing line) of:

	contentious-bits-const "," contentious-value-const <newline>
	<tab> competing-bits-const "," competing-value-const "," \
	      competing row offset "," competing col offset <newline>
	<tab> another competing-bits-const ...

	The bits- and value-const's are the constants defined in the
	com.xilinx.JBits.Virtex.Bits package, and as such may require
	the enclosing class name, along with a period, to proceed the
	constant. For example:
	    OutMuxToSingle.OUT4_TO_SINGLE_NORTH12, OutMuxToSingle.ON
	note that the contentious virtex bits constant must start at
	the start of the line, and all lines that follow with a
	leading tab define competing virtex resources. Also, the
	offsets are in units of CLBs, and are integers, typically
	-1,0,+1 for single lines.



Examples:
--------

The resattrset2bitsval.pl script is the only program in the
directory that is run independently within the EHW system, the others
are supporting classes for EHW and configuration files, although some
of these can be usefully run from the commandline (JBitsResources
and LUTIncrFN).

The following are examples of how to use the resattrset2bitsval.pl
script, noting that the use of the backslash at the end of the line,
is simply for cosmetic purposes - the entire command line parameters
can be put on a single line.


$ cat decodedpop.txt |
	perl resattrset2bitsval.pl convToJBits.conf dpopbitsout txt

$ cat cytotest.txt |
	perl resattrset2bitsval.pl -c convToJBits.conf cytotestbits txt

$ cat ndout.txt |
	perl resattrset2bitsval.pl -d -n convToJBits.conf ndtest txt


The first example converts a set of decoded chromosomes contained in a
single text file (dpopbitsout.txt) that is the output from the Gene
Expression Pre-Processor. The configuration file, convToJBits.conf,
contains the perl subroutines for converting from resource, attribute,
setting format to the equivalent JBits format. The converted chromosomes
are stored in new text files, one for each chromosome
(dpopbitsout[1..N].txt).

The second example uses the same configuration file, but converts a
cytoplasmic determinant placement file (cytotest.txt), for use with
the EHW morphogenesis process, and stores the converted file (to
cytotestbits.txt). The "-c" option indicates that the input is a
cytoplasmic determinant placement file.

The last example shows the use of the "-d" option, which provides
debugging information to stderr. It also uses the "-n" option, which
indicates that the input to be converted is a set of non-developmental
decoded chromosomes (output from the non-developmental chromosome
pre-processor). The converted output is again written to text files
(ndtest[1..N].txt).


The following are examples of how to run JBitsResources from the
command line:


$ java JBitsResources XCV50 null50GCLK1.bit contentionInfo.txt 5 8 \
       isEquiv LUTIncrFN=LUT.SLICE0_G LUTIncrFN=LUT.SLICE0_G,AND,MJR1,-100

$ java JBitsResources XCV50 layout.bit contentionInfo.txt 12 9 \
       get jbits=S0BY.S0BY

$ java JBitsResources XCV50 layout.bit contentionInfo.txt \
       load test.bit.record.txt test2.bit


The first example tests to see if the supplied LUTIncrFN expression is
equivalent to (a subset of) the function within the slice 0 G LUT in CLB
row 5, column 8 on a null-configured FPGA.

The second example returns the current value of the S0BY resource in the
CLB at row 12, column 9 after the FPGA has been configured according to
the supplied bitstream (layout.bit).

The final example loads a record of set instructions, and executes them
on the configuration supplied by layout.bit, saving the resulting
configuration to test2.bit. This approach can also be used to manually
create a bitstream configuration, using a file containing lines of:
	set: row,col: type=bits,val

For example, to configure a logic element to take an input from the north
and pass it to the east (note that the LUT's truth table is inverted):
  set: 7,15: jbits=S1F1.S1F1,S1F1.SINGLE_NORTH5
  set: 7,15: LUT=LUT.SLICE1_F,0101010101010101
  set: 7,15: jbits=OUT3.OUT3,OUT3.S1_XQ
  set: 7,15: jbits=OutMuxToSingle.OUT3_TO_SINGLE_EAST11,OutMuxToSingle.ON

The following is an example of how to use LUTIncrFN from the command
line. It generates a new LUT truth table (un-inverted), and also tests
to see if the supplied expression is an equivalent subset of the
supplied (un-inverted) truthtable. Note that the order of lines (1-4) in
the lines template is from right to left (ie F4 F3 F2 F1).

$ java LUTIncrFN "0,0,1,1,0,0,1,1,1,1,1,1,1,1,1,1" XOR XNOR 1-11


The result from running this is:

	the new LUT is: 1,0,1,0,1,0,1,0,1,0,0,1,1,0,0,1
	the result of equivalence test is: true


The following is 2 examples of how to use JBitsLookup from the command
line, the first to lookup the constants required to configure or query
the FPGA when supplied with the String representation of the resource,
and the second is an example of how to do a reverse lookup, when the
class name and the value constant array (a binary string) are known
(as returned by JBitsResources for example)

$ java JBitsLookup S0F1 S0F1 SINGLE_EAST12

returns:
	bits=[10][10][11][10][10][01][01][00][01]
	value=010000010

$ java JBitsLookup S0F1 010000010

returns:
	S0F1.SINGLE_EAST12




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





