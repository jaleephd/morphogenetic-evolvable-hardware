
			      README.txt

		     for Morphogenetic EHW System

			last modified 2/2/2006

			      J.A. Lee



Overview:
--------

These directories contain all the code necessary to seed, evolve,
grow and evaluate digital circuits on Xilinx Virtex FPGAs.

There are several stages involved in this. The first is to generate
an initial population of chromosomes, and then to evolve these to a
point where they contain genes (including promoters, regulatory and
transcription regions). These gene-containing chromosomes are
preprocessed to extract all the information from them to build a gene
expression model, which in turn, is used to direct the morphogenesis
process. It is this process that is responsible for growing digital
circuits on the Virtex. Only a portion of the CLB matrix on the Virtex
is used in the growth process, and so there is a parallel stage in
which the designation of the evolvable region, and its connection to
incoming and outgoing signals, is performed. This only needs to
performed when a new circuit interface is required. The last stage of
the whole process is to evaluate the growing or grown circuits, and
assign them a fitness, after which the next round of evolution of the
population can occur, with the chromosomes of the fittest circuits
being more likely to be chosen for reproduction.


Important Note:
--------------

To run the system requires that all the compiled class files
and executables must be placed in a directory in CLASSPATH and PATH
(respectively) or along with the copies of scripts configuration files
and null bitstreams, placed in a single directory from which the system
is run. This is a current limitation of the (prototype) system.
Also, the system can be run without a complete installation of ActivePerl
only the interpreter and dll are needed in most cases, the exception to
this is cleanupEHW.pl which uses globbing, and runMG.pl which calls
cleanupEHW.pl.



Top level Files:
---------------

EHW_README.txt		(this file)
runMG.pl		performs one or more runs of the EHW system
setupEHW.pl		sets up the EHW system prior to running a single run
runEHW.pl		runs the EHW system according to params from setupEHW
cleanupEHW.pl		cleanup files after a run of runEHW.pl
EHWfitness.pl		calculates chromosome fitness


The top level contains the scripts for running the entire system, while
the sub-directories contain the various components of the system.

Brief descriptions of the top level scripts follow, along with their
synopsis and examples of  how to run them.


- runMG.pl

runMG.pl is a front end to setupEHW.pl and runEHW.pl, it enables easy setup
(without needing to remember a complex set of parameters) and multiple runs
of runEHW.pl, each generating a new population file, etc, in its own dir.

Note that the evolutionary and morphogenetic params are defined as constants
within this script.

The evolutionary params are pre-defined as:

	reprate=10	chosen as 10% of a population of 100,
			can be up to 25% of population
	xrate=0.8	probability of using Xover in offspring = 80%
	irate=0.05	prob of applying inversion in offspring = 5%
	mrate=0.02	prob of any given base being mutated = 2%
			which is approx 1 codon out of each 16
	idrate=0.001	prob of adding or deleting a base at any base = 0.1%
			which is one per 1000 (or 333 codons)
	sgen=5		generate population stats every 5 generations
	antigenebloat=1	prevent unbounded chromosome growth


The morphogenetic parameters are pre-defined as:

	numVgens=25	  number of gens to pre-evolve chroms to generate genes
			  for morphogenetic runs
	numTestIter=30    minimum number of fitness evals
	numGrowthSteps=1  how many growth steps per cct fitness eval
	transcribeRate=4  transcribe 4 bases of a gene each growth step
	polyToGene=0.3    number of polymerase enzymes per gene
	polyThreshold=0.8 probability of polymerase binding (1-0.8=0.2=20%)


Synopsis: runMG.pl runMG.pl [-v | -t] testname basedir maxgens [[startrun#]numruns]

   Args:
   	 	-v		Verbose mode - generate info to STDIO
		-t		Test mode - outputs commands used to run sys
		testname	the EHW problem for which a solution is to
				be found; can be one of:
				    slim5x5
				    slim5x5GA
				    slim5x5tfs
				    slim8x8
				    slim8x8GA
				    slim8x8tfs
				    adder1[(fixed|preset)(mux|6200)]
				    adder1bit[GA]([fixed|preset)(mux|6200|lut)]
				    adder1incr[(fixed|preset)(mux|6200|lut)]
				    adder1active[(fixed|preset)(mux|6200|lut)]
				    adder2[bit|incr|active]
				    slim:ROWSxCOLS:IN-OUT
				    inout:ROWSxCOLS:IN-OUT
				    inoutao:ROWSxCOLS:IN-OUT
				    fnseq:ROWSxCOLS:IN-OUT:INSEQ:OUTSEQ
				    fnseqao:ROWSxCOLS:IN-OUT:INSEQ:OUTSEQ
				ROWS and COLS give the size of the CLB matrix
				IN and OUT give the no. of inputs and outputs
				INSEQ and OUTSEQ give the function truthtable
				in little-endian (input/output) order comma
				delimited binary (there will be 2^I x I input
				signals and 2^I x O output signals).
				signals are: 0,1 = digital signals; also on
				outputs: -1 = Z (unconnected), 2=X (don't care)
				eg. for inout; inout:5x5:1-1
				eg. for a 1bit adder with fnseq;
				fnseq:3x3:3-2:
				0,0,0,1,0,0,0,1,0,1,1,0,0,0,1,1,0,1,0,1,1,1,1,1
				:0,0,1,0,1,0,0,1,1,0,0,1,0,1,1,1
		basedir		the base directory for storing populations,
				etc. if numruns is specified, then each run
				will be stored in subdirectory (run#) under
				the basedir, otherwise basedir will be used.
  	 	maxgens		the maximum number of generations to evolve
  	 			the population
  	 	startrun	initial run number (default is 1)
		numruns		runs this numruns times (default is 1)


  Inputs: (none)

  Outputs:
  	   (STDERR)		  informative messages
  	   basedir/run#		  all the generated files:
  	     pop_#gens.txt	  evolved population of chromosomes, eg if
  	   			  completed 100 gens, pop_100.txt
  	     pop_#gens.parm.txt   parameters used to evolve population
	     pop_#gens_stats.txt  popn statistics (if -v or -s ngen > 0)
  	     fittest_details.txt  details of fittest population members:
  	     				fitness \@iter : rand_seed
  	     				fittest gene count
  	     				generation fittest occured
  	     				prevbest details ..
  	     bestchrom.txt	  chromosome of fittest population member
  	     bestchrom.(fitness).txt   "	prev fittest popn member
  	     pop.txt		  initial generated population after pre-
  	     			  evolution for 'viable' chromosomes, ie
  	     			  containing genes
  	     pop.parm.txt	  parameters used to evolve initial population
  	     pop_stats.txt	  stats (incl viability fitness) for init popn
  	     EHW_config.txt	  EHW configuration file that is placed in
  	     			  EHW bin directory when running
  	     layout.bit		  EHW initial layout bitstream
  	     cytobits.txt	  cytoplasms (empty if no TFs used)




- setupEHW:

This script is responsible for setting up the configuration for running
the EHW system, as specified in the generated configuration file
(EHW_config.txt), for generating the FPGA layout and the initial random
population, and also, if performing morphogenesis, a cytoplasmic
determinants placement file.

This script needs to be run prior to running runEHW.pl. The generated
configuration file can be modified by hand to change any settings as
desired to change the behaviour of runEHW.pl.

setupEHW generates an FPGA layout (layout.bit) for the given device
according to the specified area of CLB matrix to be accessible to
evolution for circuit construction, and routes the input and output
buses to logic elements (LUT/FF pairs) borderering the boundaries of the
evolvable region. Cytoplasmic determinants are placed around the
locations of the input and output cells in the evolvable region, if
using morphogenesis.

All generated files (except EHW_config.txt) are placed in the specified
(sub) directory. EHW_config.txt needs to be in the same directory as
runEHW.pl and EHWfitness.pl. All generated/evolved population files,
from setupEHW.pl and runEHW.pl, have an associated population parameter
file (eg pop.txt has pop.parm.txt).


Synopsis: setupEHW.pl [-v] [-s] [-agb] [-notfs] [-code confile]              \
   				[-exitonsuccess] [-exitonstagnate sg]	     \
   		      		[-noexitoncctfail]
				device rows cols inbuswidth outbuswidth      \
  				testclass popsize [chromsize] datadir [      \
  				numVgens reprate[+] xrate mrate irate idrate \
  				numTestIter numGrowthSteps [ transcribeRate  \
  				polyToGene polyThreshold ] [mgseed] ]

   Args:
  		-v		Verbose mode - generate info to STDIO during
  				setup
  		-s		save best bitstream option - during evolution
  				the bitsream of the highest fitness is kept
		-agb		anti-gene-bloat for use with morphogenesis,
  				factors in ratio of chromosome's gene count
  				to fittest (to-date) chrom's gene count when
  				evaluating fitness. If chrom has more genes,
  				and is not fitter, then fitness is scaled down
  				by the gene count ratio.
		-notfs		don't use TFs, incl. morphogens and cytoplasms
   		-code confile	overides the default genetic code file
  		-exitonsuccess	when get a 100% solution, finish generation
  				and then exits. checkpoint population file is
			        the successful generation and can be examined.
	    -exitonstagnate sg  if maximum fitness (in population) has not
				increased in 'sg' generations, then exit. The
				checkpoint population file can be examined or
				have evolution continued from.
	      -noexitoncctfail	don't abort if a circuit evaluation fails,
	      			just give genotype a fitness of 0 and continue
   		device		FPGA device & resource subset (model). Must be
   				one of the following, ordered with increasing
   				resource limitations:

		      XCV[1000|50] - slice granularity almost full model, use
		      		  XCV50 with VirtexDS, and XCV1000 with Raptor
		      XCV50sync - slice gran, lacks only asynch & Ctrl outputs
		      XCV50nFF  - slice gran lacks unregistered & Ctrl outputs
		      XCV50LE	- LE gran lacks SliceRAM & Ctrl inputs/outputs
		      XCV50LEsync - LE gran also lacks unregistered outputs
		      XCV50GAsync - as above but standard GA and LUTBitFNs
		      XCV50LEnFF - same as sync but lacks registered outputs
		      XCV50GAnFF - as above but standard GA and LUTBitFNs
		      XCV50-	- LE gran uses 'trim' model with 2 outbus lines
		      		  allocated per LE, synch slice out, LUTBitFNs
		      XCV50GA-	- standard GA with 'trim' model
		      XCV50--	- LE gran 'slim' model (1 LUT input, 4 LUT fn,
		      		  2 OUT bus lines, 3-4 singles per LE)
		      XCV50GA--	- standard GA with 'slim' model
		      XCV50slim	- slice gran, 'slim' model
  				note: a 'GA' suffix indicates that a standard
				GA using a binary fixed length chromosome with
  				a direct mapping to the FPGA is used.
  		rows		number of CLB rows in evolvable region
  		cols		number of CLB cols in evolvable region
  		inbuswidth	width of input (DUT_in) bus
  		outbuswidth	width of output (DUT_out) bus
  		testclass	the name of the java classfile to run to (grow
  				and) test evolved circuit. The output of this
  				must be: fitness [@ iteration [; numgenes]]
  		popsize		size of population to be generated
  		chromsize	median size of chromosomes to generate, with
  				sizes ranging from 0.5 - 1.5 x chromsize
  				Note this parameter should be ommitted if
  				XCV50GA device is specified
  		datadir		where all the data files (eg population,
  				bitstream) will be kept

			... the following are not used if using standard GA

  		numVgens	the number of generations to evolve initial
  				population, to generate chromosomes containing
  				genes for morphogenesis (including promoters,
  				regulatory and transcription regions), or
  				multiple chromosome sections for starting
  				circuit construction at each of the IO cells
  		reprate[+]	number of members to replace with new (maximum
  				of 1/4 the population size). If followed by a
  				'+', then both offspring of the 'reprate'
  				selected parents are introduced into the
  				population (which requires 'reprate' to be an
  				even number), otherwise only the fitest of the
  				2 x 'reprate' are introduced.
 		xrate		crossover rate in offspring (0.0 - 1.0)
 		mrate		rate of mutation of bases (0.0 - 1.0)
 		irate		rate of inversion in offspring (0.0 - 1.0)
 		idrate		rate of base insert/delete in offspring
 				(0.0 - 1.0)
  		numTestIter	number of times to continue growth and test
  				fitness when running morphogenesis
  		numGrowthSteps	number of growth steps per fitness test
  				when running morphogenesis

		transcribeRate	number of bases transcribed each growth step
				(default is 4)
		polyToGene	RNA polymerase II molecules per gene, this
				limits the number of genes that can be
				active at any time. use a value < 1.0 to
				indicate a ratio, or >1 to set a fixed number
				(default is 0.3)
		polyThreshold	theshold to be exceeded for a free polymerase
				molecule to bind to a gene (default is 0.8
				which means binding occurs 20% of the time)

  		mgseed		optional random seed, to ensure the steps
  				taken by morphogenesis process is reproducible

   Inputs: (none)
   Outputs:
  	   (STDERR)		informative messages
  	   POPBASENAME.txt	generated population of chromosomes
  	   POPBASENAME.parm.txt parameters used to generate population
  	   LAYOUTBIT		bitstream connected to GCLK, with input and
  	   			output buses routed to border of evolvable
  	   			region, a region of null CLBs
  	   CYTOBITS.txt		generated file of cytoplasmic determinants
  	   CONFIGFILE		configuration file for EHW system



- runEHW

runEHW.pl is the interface to running evolution of a population of
chromosomes that configure a region of the CLB matrix on a Virtex FPGA
bitstream (layout.bit) using JBits. The parameters to this script are
solely for controlling evolution; EHW_config.txt (which is generated
by setupEHW.pl, and must reside in the current directory) defines the
parameters required for converting genotype (chromosome) to phenotype
(circuit), and for evaluating the fitness of the phenotype.


Synopsis: runEHW.pl [-v] [-f] [-s ngen] [gen+]numgens reprate[+]      \
			      		xrate mrate irate [ idrate ]

   Args:
   		-v		Verbose mode - generate info to STDIO
  		-f		save fitness with evolved chromosomes
 		-s ngen		generate fitness, length, and homology stats
 			  	every 'ngen' generations (0 for off - used to
  				turn of stats when using -v switch).
  		[gen+]numgens	the number of generations to evolve popn
  				if preceded by gen (an integer) '+' then it
  				will look for POPBASENAME_gen.txt and evolve
  				it for a further numgens generations to give
  				a resulting population in the file
  				POPBASENAME_(gen+ngen).txt 
   		reprate[+]	number of members to replace with new (maximum
   				of 1/4 the population size). If followed by a
   				'+', then both offspring of the 'reprate'
   				selected parents are introduced into the
   				population (which requires 'reprate' to be an
   				even number), otherwise only the fitest of the
   				2 x 'reprate' are introduced.
  		xrate		crossover rate in offspring (0.0 - 1.0)
  		mrate		rate of mutation of bases (0.0 - 1.0)
  		irate		rate of inversion in offspring (0.0 - 1.0)
 			.. the following parameter should only supplied if
 			   using variable length chromosomes (not standard GA)
  		idrate		rate of base insert/delete in offspring
  				(0.0 - 1.0)
 
   Inputs:
   	   EHW_config.txt	holds the parameters used by the system
 
   Outputs:
   	   (STDERR)		  informative messages
  	   POPBASENAME_#gens.txt  updated population of chromosomes, eg if
  	   			  ngens=100, pop_100.txt
  	    "  " _#gens.parm.txt  parameters used to evolve population
	    "  " _#gens_stats.txt popn statistics (if -v or -s ngen > 0)

	   FITTESTDETS		 for the fittest circuit:
	   			   fitness [@ growth step : seed ; numgenes]
	   			   # fittest occurred at generation GEN
 
   	   ... the following are produced if SAVEBEST=true in EHW_config.txt
 
	   BESTCHROM		 chromosome that produced fittest circuit
	   BESTBIT		 bitstream of fittest circuit


- cleanupEHW

cleanupEHW performs a cleanup of temporary population and chromosome
files in the main directory, and if numgens is specified, the chechkpoint
population file is copied to the DATADIR pop_NUMGENS.txt file, and the
associated population stats and parameter files are renamed. Note that
temporary files from earlier runs are not touched, and neither are any
temporary (decoded) chromosome files in the DATADIR.
NB: if numgens is not specified, the temp and checkpoint files from the most
recent run will just be deleted.

Synopsis: cleanupEHW.pl [-v] [numgens]

  Args:
  		-v		Verbose mode - generate info to STDERR
  		numgens		the number of generations that evolution was
  				specified to run for - so there should be a
  				pop_NUMGENS_stats.txt and pop_NUMGENS.parm.txt
				in the DATADIR specified in the EHW_config.txt
  Inputs:
  	   EHW_config.txt	holds the parameters used by the system
				(needed if numgens param supplied)
	   TMPpop$$$$_info.txt	checkpoint information file, '$$$$' is the PID

  Outputs:
  	   (STDERR)		  informative messages




- EHWfitness

EHWfitness.pl takes chromosomes and calculates their fitness, by
constructing and testing the encoded circuit. Optionally, the bitstream
of the fittest circuit may be kept. It uses EHW_config.txt (which again
must be located in the current directory) to supply the parameters used
to convert the chromosome to a circuit, and for evaluating the fitness
of the circuit.

This script is actually responsible for executing the chromosome
decoding components (GEPP or NDEHW) of the EHW system, and also for
executing the circuit construction and testing program (in TestEhw).
The circuit construction and testing program may either simply build
a circuit and test it, or actually repeatedly perform grow and test
cycles. In either case, it returns the evaluated fitness to EHWfitness.

This script would not generally be run directly by the user, it is
executed by genpopn.pl, the core script that runs the evolutionary
process.

Synopsis: EHWfitness.pl [-v] [ -l | -f ] [-gen gen#]

  Args:
 	-v	- output (to STDERR) some extra information
 	-l	- output (to STDERR) chromosomes's line number
 	-f	- output (to STDOUT) fitness and chromosome together
	-gen gen# the current generation, so that this can be stored with
		  the fittest's details

  Inputs: (stdin)
 		- lines of chromosomes in base 4
 	  (EHW_config.txt)
 	  	- holds the parameters used by the system

  Returns:
  		lines of output
 	    	- (stdout) chromosomes' fitness ( '>' chromosome )
 		- (stderr) ( line count ': ' ) ( extra info '; ' )

  		optional (if SAVEBEST=true in EHW_config.txt)
		- (BESTBIT) bitstream of fittest circuit
		- (BESTCHROM) chromosome of fittest circuit
		- (FITTESTDETS) fitness [@ growth step : seed ; numgenes] 
		  of best cct



Examples:
--------

The system should generally be run using runMG.pl, which takes care of
supplying the majority of parameters to the rest of the system.
However, it is possible to run the system from several levels.

These first examples show how to run the system from the top level.

$ perl runMG.pl -v slim5x5 runMGdata 3000 10
$ perl runMG.pl -v slim5x5tfs runMGdata 3000
$ perl runMG.pl -v adder1active runMGdata 5000 10
$ perl runMG.pl -v adder1active runMGdata 5000 11#10

All runs here create data directories under the runMGdata subdirectory
(which is created if it doesn't already exist).

The first example runs the 'slim' model connectivity test on a 5x5
CLB matrix. This is run 10 times, for up to 3000 generations or until
success or stagnation, with each run's data being placed in runMGdata/RUN#
sub directories (ie runMGdata/1 runMGdata/2 ..)

The second example performs a single run of the same test, but using TFs,
and the resulting data files are placed directly in the runMGdata dir.

The third example runs the 1-bit full adder test set, using ActiveLUTFns.
This is run 10 times (data placed in subdirs runMGdata/1 runMGdata/2 ..)
for up to 5000 generations, or until success or stagnation.

The last example continues this set of experiments for another 10 runs
(runs 11-20).


Example of continuing a crashed run (eg if power outage), initially
started with:

	perl runMG.pl -v adder1bit runMGdata/adder1bit 5000

continuing crashed run (from gen 231, to complete all 5000 gens)
[generally only needed for adder runs due to their long run times]
noting the use of cleanupEHW after the crash, and again at the end
    of the run (necessary!)
and re-aligning stats generation to modulus 5 gens (optional)

	perl cleanupEHW.pl -v 5000
	perl runEHW.pl -v -s 5 -f 231+4 10 0.8 0.02 0.05 0.001; \
	perl runEHW.pl -v -s 5 -f 235+4765 10 0.8 0.02 0.05 0.001; \
	perl cleanupEHW.pl -v 5000



Examples of how to run the system manually, setting parameters as desired
follow, noting that the use of the backslash at the end of the line,
is simply for cosmetic purposes - the entire command line parameters
should be put on a single line.

There are two phases involved in running the system. The first is
the setup, which creates the FPGA layout, and generates the initial
population, and the second is to evolve the population until a,
hopefully, optimal solution is found.


Setup:

$ perl setupEHW.pl -v -s XCV50GA-- 8 8 4 4 TestInToOutRecurse 100 data

$ perl setupEHW.pl -v -s XCV50 8 8 4 4 TestInToOut0101s 100 8000 data \
	50 10 1 0.02 0.1 0.001 15 1

In both examples, the -v (verbose) flag is used to generate progress and
information messages to the screen while the EHW setup is being performed.
The save flag is specified, indicating that the best generated bitstream
and associated chromosome should be kept (best.bit and bestchrom.txt), as
should its fitness, and when using morphogenesis, which growth iteration
it occured at and what seed was used for the random number generator in
the morphogenesis system.

The population size is 100 in all the above examples, and all use an evolvable
region consisting of an 8x8 matrix of CLBs, which have with 4 input and 4
output cells on opposite sides of the evolvable region, and are routed to the
input and output busses.

The generated population file (pop.txt), with associated parameter file
(pop.param.txt) are stored in the specified 'data' directory, which will
also be used as a directory for storing evolved population files
(pop_GEN.txt), statistics (pop_GEN_stats.txt), saved best chromosome and
bitstream, layout bitstream, and other generated data files.

The first example sets up evolution for using a standard GA with a fixed
length binary chromosome that uses a direct encoding as in traditional EHW.
The chromosome length doesn't need to be supplied, as it is determined by
the size of the evolvable region; and no pre-run viability evolution needs
to be performed here.

The second example uses the non-developmental circuit construction method
with a codon-based encoding on a variable length chromosome. The initial
population consists of randomly generated chromosomes of average length 800
bases (lengths will vary from 400-1200, that being from 0.5 to 1.5 the
supplied length). This population is then pre-evolved for 50 generations for
viability, where viability is measured as the number of chromosome sections
as a fraction of the number of input and output cells from where to start
circuit construction. As in the first example, a slimmed down set of EHW
resources is used logic element granularity (indicated by XCV50--), and
evolved chromosomes will be evaluated using TestInToOutRecurse, which traces
connections from the inputs to determine their progress towards connecting
the outputs.

In the final example, a nearly-complete set of virtex resources is available
for the morphogenetic process to manipulate. The only limitation being that
asynchronous slice outputs are not used, and neither are direct inputs into
a slice from the same CLB, due to the limitations of the VirtexDS, which is
assumed to be used if the XCV50 device is specified. Evolved chromosomes
will be tested using TestInToOut0101s, which tests for connectedness by
passing an oscillating signal through the circuit and probes at various
points to determine if the signals change, indicating that there is a
connection to the inputs.

In this example initial chromosomes with an average length of 8000 bases,
and range from 4000-12000 bases (being 0.5 to 1.5 the supplied length) are
generated. The initial population is pre-evolved for for viability before
being ready for use with the EHW system. This is to generate chromosomes
that will be viable for use with morphogenesis, driven by gene expression,
which requires the chromosomes to contain multiple genes, including
promoters, regulatory and transcription regions. In this example, the
initial random population is evolved for 50 generations with a replace
rate of 10, probability of crossover is 100%, probability of any given base
being mutated is 2%, probability of an offspring's chromosome having a
section inverted is 10%, and the probability of a base insertion or deletion
occuring at any given base being 0.1%.

The final two parameters in the final example are used to indicate the number
of tests to apply to the growing circuit, and the number of growth
(morphogenesis) steps per test, when morphogenesis is used to construct a
circuit. Here 15 evaluations are done, each after a single growth step.

Evolving population:

$ perl runEHW.pl -v -s 10 -f 100 2 1 0.02 0.1 0.001

$ perl runEHW.pl -v 100+50 2 1 0.02 0.1 0.001


Both examples show how to run the evolutionary process to generate a
(hopefully) useful FPGA configuration. In the first case, the initial
generated population is evolved for 100 generations, and stores the
resulting population (pop_100.txt) along with its members' fitness
values.

The second example takes the population from generation 100 (as
produced by the first example), and evolves it for a further 50
generations (and stores the result in pop_150.txt).

In both cases verbose mode is set, resulting in a lot of information
being displayed to the screen, but in the first example population
statistics are limited to being generated only once every 5 generations.
Both examples use the same evolutionary parameters as was used to
evolve the 'viable' population in the 2nd example from setupEHW, that
being a replace rate of 2, with 100% probability of crossover,
2% probability of any given base being mutated, 10% probability of an
offspring's chromosome having a section inverted, 0.1% probability of
a base insertion or deletion occuring at any given base.

Due to the save-best flag having been set in setupEHW, the fittest
chromosome, along with its generated bitstream, will be saved to
bestchrom.txt and best.bit, and the details of these will be stored
in fittest_details.txt. All of these, along with the evolved population
files (pop.txt, pop_100.txt, pop_150.txt) and their parameter files
(pop.param.txt, pop_100.param.txt, pop_150.param.txt) will be stored in
the data directory, as specified when setupEHW was run.


cleanup:

When runEHW.pl is run, if a solution is found or if stagnation occurs
before evolution has finished the number of generation specified on the
command line, then the final population will be in a checkpoint file in
the running directory, rather than in a population file in the data dir,
and the stats and run params files will have the wrong suffix (ie the
wrong number of generations). This can all be fixed manually, or can be
done using the cleanupEHW.pl script. This can also be used to cleanup
the temp/checkpoint files in the run directory. Examples follow:

$ perl cleanupEHW.pl -v 3000
$ perl cleanupEHW.pl -v

In the first example, an evolutionary run was specified for 3000 gens,
but exited prior to this (on success or stagnation). The most recent
checkpoint population file is located and copied to the data directory
specified in EHW_config.txt, as pop_321.txt (where 321 is the number
of generations actually completed), the associated pop_3000_stats.txt
and pop_3000.parm.txt are renamed to pop_321_stats.txt and
pop_321.parm.txt), other temp files from this run are deleted from the
running directory, and setup_EHW.txt is moved to the data directory.

In the second example, we just cleanup all the temp and checkpoint files
from the most recent evolutionary run are deleted. EHW_config.txt is
not used (to find data directory), and is not moved or deleted.






Description of EHW system components:
------------------------------------

The various (source) components of the EHW system are separated into
directories, as follows


bioGA/		Biologically Inspired Genetic Algorithm
GEPP/		Gene Expression Chromosome Preprocessor
JBitsRes/	JBits Resources Interface
morphoHW/	Morpho-genetic EHW
NDEHW/		Non-Developmental EHW
TestEHW/	Evolvable Hardware Tests
XCVlayout/	Virtex EHW layout


Also, in the top level directory are the scripts used to run the
whole system: setupEHW.pl, runEHW.pl, runMG.pl, and EHWfitness.pl



The entire morphogenetic EHW system is comprised of several groups of
components, each group performs a specific stage of the entire complex
process. These processes are connected in the following cycles:

  - evolution with morphogenesis

				morphHW
			       (cytogen)
				   |
				   V
	  start	  ---> 	 bioGA -> GEPP -> JBitsRes
		 	   ^		    ^ |
		 	   |		    | V
	  XCVlayout --> TestEHW  <---->   morphoHW



  - evolution without morphogenesis


	  start	  --->	 bioGA _    JBitsRes
			   ^	\      ^ |
			   |	 `---> | V
	   	        TestEHW <---> NDEHW <-- XCVlayout


In both cases we start with bioGA, a biologically inspired genetic
algorithm, to generate an initial random population of chromosomes.
In the case of the morphogenetic approach, we also need to go through
an initial evolutionary process which breeds viable chromosomes, in
the sense of having numerous genes with promoters, regulatory and
transcription regions.

The population (or new members of it) are then passed to either GEPP,
the gene expression chromosome preprocessor, in the morphogenetic
approach, or to NDEHW if not using morphogenesis.

GEPP takes one or more chromosomes (minus fitness attribute) and
parses this into genes with their associated promoter, regulatory
regions, bind sites, and transcription region. The transcription
regions are decoded into lists of Virtex resource, attribute, settings,
and lists of the same are associated with bind sites, to indicate
which resources bind there. The genetic code (file) is responsible for
determining these translations and binding associations. If cytoplasmic
determinants and morphogens are used, then their placement file,
generated by cytogen.pl in the morphoHW directory, should be provided
to the GEPP process.

NDEHW, the non-developmental EHW approach, originally covered two completely
separate approaches, the first involving a set of simple move-set resources
instructions that are decoded from a variable length base-4 chromosome; while
the latter using a direct encoding on a fixed length binary chromosome,
wherby each resource that can be manipulated on the FPGA is directly encoded
on the chromosome. The use of the first approach has been depreceated,
leaving only the direct encoding method for use with a standard GA.

The approach of directly encoding on a binary chromosome, maps each
consecutive N bits to a specific logic element (slice or CLB) on the
evolvable region of the FPGA, the each bit or group of bits is used to
represent the configuration for a given resource. The decoding is done by
directdecodelegranslim.pl, to produce set resources instructions for each
configurable cell (logic element here), in JBits compatible format. Lastly,
the resulting instructions are then passed to BuildEhw via the TestEhw
interface, to construct the specified circuit within the evolvable region
of the FPGA.

JBitsRes, the JBits resources interface, is used in two manners,
firstly (using resattrset2bitsval.pl with convToJBits.conf) to convert
decoded chromosomes from the intermediate form of Virtex-resource,
attribute, settings to JBits 'bits' and 'value' constants. These
converted chromosomes are used by NDEHW and morphoHW to configure and
query the Virtex bitstream, via JBitsRes (using the JBitsResources
class) which provides a safe, string-based interface, with some higher
level constructs, to the underlying device.

MorphoHW performs the morphogenesis (growth) process on the Virtex
bitstream, guided by a gene expression model that was extracted from
the chromosome by the GEPP chromosome preprocessor, and driven by the
states of the resources on the Virtex, and transcription factors
(which includes pre-placed cytoplasmic determinants and morphogens)
in the cell.

TestEHW is the final stage of the evolutionary cycle, in which the
built or grown circuit is tested to determine its ability at
performing the desired task. The circuit's fitness is then assigned
to its chromosome for determining its chances of being chosen for
reproduction or removal from the population.

In both morphogenetic and non-developmental approaches, XCVlayout
supplies the layout for the evolved circuits to be constructed
within, routing of global clock, and input and output buses on the
FPGA. This is supplied in the form of a bitstream, to TestEHW for
morphogenesis or non-developmental circuit construction.



Examples of Running individual components of EHW System
-------------------------------------------------------

Examples of running the individual components of the EHW system
follow, from creating the initial population, FPGA layout and
optional cytoplasm placement file, to constructing and evaluating
the circuit encoded by a chromosome, and evolving the population
further.


  Setting Up Initial Population, FPGA layout and Cytoplasms
  ---------------------------------------------------------

  Inside the system initialisation and seeding, the following steps
  are performed: setup FPGA, generate initial population, and
  cytoplasmic determinant file if required. Detailed examples follow:


a) define an 8x8 CLB area of an XCV50 for evolution, and connect to
   input and output buses. the resulting bitstream is used with both
   morphogenetic and non-developmental EHW approaches.

$ java EHWLayout XCV50 8 8 XCV50bg256.ucf DUT_in 4 DUT_out 4 \
       null50GCLK1.bit layout.bit


b) randomly generate an initial population of 100 chromosomes, with
lengths varying between 1000 and 8000 bases, and stores these in the
file "pop1.txt". No evolution is carried out, as specified by
numgens=0. Note that the fitness function is ignored here.

$ perl genpopn.pl -g 100 1000 8000 pop1.txt "dummyfitness.pl -f" 0


The following 2 steps are only required for the morphogenetic approach:

c) evolve this population for 1000 generations to generate, hopefully
   viable chromosomes for gene expression, 4 members of the population
   are replaced at each generation, and the final evolved population
   is stored in the file pop1v.txt. The last parameters specify the
   rates of application for crossover (100% per pair of parent
   chromosomes), the probability of a base being mutated in the
   offspring chromosome (2% chance per base), the application rate for
   inversion in offspring (10% chance per chromosome), and the
   probability of bases being inserted or deleted at any particular
   base in the offspring chromosome (0.1% per base).

$ perl genpopn.pl pop1.txt pop1v.txt "viablefitness.pl -f" \
	1000 4 1 0.02 0.1 0.001


d) create a cytoplasm and morphogen placement file, and then convert
   to JBits compatible format.

$ perl cytogen.pl 3223012 "10,10" "2,0;5,10" 1 1 > cytofile.txt
$ perl cytogen.pl 3223012 "10,10" "0,3;4,1" 3 3 1 1 >> cytofile.txt
$ cat cytofile.txt | perl resattrset2bitsval.pl -c convToJBits.conf \
                          cytobits txt



  Chromosome Decoding and Fitness Evaluation
  ------------------------------------------

  Inside the fitness evaluation, the following steps are performed:


  Stage 1. Decode chromosome(s)
  -------

a) decode the chromosomes in the population

For morphogenetic approach using the gene expression preprocessor,
and utilising cytoplasmic determinants and morphogens generated
earlier:

$ cat pop1v.txt | perl decodechrom.pl VirtexGeneticCode.conf \
      cytobits.txt > dpop1.txt


b) Convert the preprocessed chromosomes to JBits suitable format, and
   store in new text files, one for each chromosome
   (dpop1bits[1..100].txt)

$ cat dpop1.txt | perl resattrset2bitsval.pl convToJBits.conf \
      dpop1bits txt


For the standard GA non-developmental approach, each chromosome is decoded
and converted to JBits suitable format in one step, with the results stored
in new text files, one for each chromosome (as above).

$ cat binpop.txt | perl directdecodelegransynch.pl dpop1bits txt



  Stage 2. Construct and evaluate population of circuits
  -------

For the morphogenetic approach, circuit construction and evaluation are
combined, so that the fitness at each stage of development can be
ascertained. The chromosome's fitness is the highest fitness attained
during the growth process.

a)   Grow and evaluate the (first member of the population's) circuit
     100 times, with 5 growth cycles per evaluation. This needs to be
     done for each (new) member of the population.

$ java TestInToOut0101s layout.bit			\
       "LEFT,11,1;LEFT,11,2;LEFT,10,2;LEFT,10,3"	\
       "RIGHT,6,1;RIGHT,5,1;RIGHT,4,1;RIGHT,4,2"	\
       "7,8,S0G-Y;7,8,S1F-X;8,8,S0G-Y;8,8,S1F-X"	\
       "4,15,S0G-Y;6,15,S1F-X;9,15,S0G-Y;11,15,S1F-X"	\
       "4,8" "11,15" contentionInfo.txt			\
       dpop1bits_1.txt cytotestbits.txt 100 5 | 	\
       last | awk '{ print $1 }' >> pop1fitness.txt


For the non-developmental approach, circuit construction and
evaluation may be combined as with the morphogenesis approach (note the
use of the -le flag to indicate that decoded chromosome specifies settings
at logic element granularity), for example evaluating a single decoded
chromosome looks like:

$ java TestInToOut0101s -le layout.bit			\
       "LEFT,11,1;LEFT,11,2;LEFT,10,2;LEFT,10,3"	\
       "RIGHT,6,1;RIGHT,5,1;RIGHT,4,1;RIGHT,4,2"	\
       "7,8,S0G-Y;7,8,S1F-X;8,8,S0G-Y;8,8,S1F-X"	\
       "4,15,S0G-Y;6,15,S1F-X;9,15,S0G-Y;11,15,S1F-X"	\
       "4,8" "11,15" contentionInfo.txt			\
       dpop1bits_1.txt | last | awk '{ print $1 }' >> pop1fitness.txt



b) after all circuits have been evaluated the fitnesses need to be
   assigned to their associated chromosomes:

$ perl joinlines.pl pop1fitness.txt pop1v.txt ">" > pop1.txt




  Evolving Population
  -------------------

The following will evolve the population for 10 generations, giving
population statistics every 5 generations. The genetic operator
parameters are the same as in the earlier example. This example
performs morphogenesis, to use the non-developmental approach,
give EHWfitness.pl a second parameter, '-n'. Note the use of the '-f'
switch, which indicates to save the population members along with
their fitness values. This allows evolution to be continued, without
requiring their fitness values to be re-calculated (a potentially
very expensive exercise).


$ perl genpopn.pl -s 5 -f pop1.txt pop2.txt "EHWfitness.pl -f" \
	10 4 1 0.02 0.1 0.001



See the README files within the component directories for more examples




Environment and Installation Notes:
----------------------------------

Note that the Java classes were developed using JBits 2.8 and the
Java JDK 1.2.2 + JRE, and the perl scripts were done using Active
Perl version v5.6.1 built for MSWin32-x86-multi-thread with local
patch ActivePerl Build 633,  Compiled at Jun 17 2002. These were
all developed on WinXP, with Cygwin version 1.3.10 (Note that the
Active Perl win32 dll is external to Cygwin). These all need to be
installed to run the EHW system. ALthough it should be possible to
run without Cygwin under a windows command window, or on Linux,
these have not been tested.

Note that, due to the use of Active Perl, there is no "#!/usr/bin/perl"
interpreter location directive at the start of perl scripts, hence
scripts are executed with "perl scriptname.pl". This also means that
Active Perl needs to be earlier in the PATH than Cygwin's /usr/bin/perl

For JDK 1.2.2 + JRE, which is the version required for JBits 2.8, the
directory containing javac.exe, java.exe, etc needs to be added to the
PATH, in windows through
	control panel/system/advanced/environment variables
to C:\jdk1.2.2\bin\ for example; in Cygwin the following is an example
of what is required in one of the login scripts (or can be run at the
command prompt)
	export PATH=$PATH:/cygdrive/c/jdk1.2.2/bin
the actual directory should be changed to match wherever java was
installed. Note that under cygwin, drives are accessed as /cygdrive/c
for C:, etc.

JBits 2.8 requires JDK 1.2.2 to have been installed already, and to
use JBits classes requires that Java's CLASSPATH environment variable
to have the the directory containing JBits (the one containing the "com"
subdirectory) added, for example (if installed JBits2.8 into C:\JBits):
	C:\jdk1.2.2\lib;C:\JBits;.



