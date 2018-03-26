
			      README.txt

		for Biologically Inspired Genetic Algorithm

			initial version 17/4/2003
			last modified 16/4/2004

			      J.A. Lee



Overview:
--------

This directory contains scripts that can be used to generate, monitor,
and evolve a population of base 4 chromosomes, using a stable state GA
with tournament selection and replacement, and biologically inspired
operators - homologous crossover, mutation (transversions and transitions),
inversion, and base insertion/deletion.

genpopn.pl is the main driving script, which would generally be the one
run by the end used, however, the other scripts can also be used
independently.

Fitness scripts need to provided to generate fitness values for chromosomes
according to the specific criterior of the application. The example fitness
evaluation script, "fitest.pl", can be used as a template for writing a
proper fitness evaluation script. However, for real problems, the evaluation
would likely be done by some external process, and so the fitness evaluation
script would need to return a chromosome's fitness by querying a file or
database, or by some other means of querying the appropriate device or
process.



Description:
-----------

At each generation a breeding tournament is conducted to determine which
chromosomes will be mated. An elimination tournament is also conducted,
the losers of which are removed from the population, to be replaced with
the offspring of the winners of the breeding tournament.

Child chromosomes are produced by firstly applying crossover (with the given
probability) to the parent chromosomes, the resulting two chromosomes
(recombined or not) then have inversion applied (with given probability),
followed by mutation (at the given mutation rate) and base insertion/deletion
(at the given rate). It should be noted though that mutation and
insertion/deletion rate refer to the probability of a base on a chromosome
being modified, not to the rate of application to any particular chromosome;
all child chromosomes have both mutation and insertion/deletion operators
applied. However, the rates for crossover and inversion refer the probability
of the operation itself being applied to any particular reproducing
chromosomes.

The resulting offspring are then evaluated, and depending on the replacement
option chosen, either both are placed in the population, or solely the
fittest, while the other is discarded. In the first scheme, this requires
an even number for the replacement rate, which gives the number of parents.
While for the latter option there needs to be twice as many winners of the
breeding tournament as there are to be new offspring, and as there is both a
breeding and elimination tournament, the replacement rate can be no more than
1/4 of the population size. This restriction also applies to the first option.



Examples:
--------

the following are some examples of how to use genpopn, noting that the use
of the backslash at the end of the line, is simply for cosmetic purposes -
the entire command line parameters can be put on a single line.


$ perl genpopn.pl -g 100 40 80 pop1.txt "fitscript.pl -f" 0
$ perl genpopn.pl -v -g 100 40 80 pop1.txt "fitscript.pl -f" \
	10 4 1 0.02 0.2 0.01
$ perl genpopn.pl -v -s 0 -f pop1.txt pop2.txt "fitscript.pl -f" \
	10+10 4 1 0.02 0.02 0.01
$ perl genpopn.pl -s 5 pop2.txt pop3.txt "fitscript.pl -f" \
	20+10 4+ 1 0.02 0.02 0.01


The first example generates a population of 100 randomly generated
chromosomes, with lengths varying between 40 and 80 bases, and stores these
in the file "pop1.txt". No evolution is carried out, as specified by
numgens=0.

The second example generates a population in the same manner as the first,
but then evolves the population for 10 generations, replacing 4 members of
the population at each generation, and storing the final evolved population
in the file pop1. The '-v' flag specifies verbose mode, which will output
various information and population statistics during evolution. The remaining
parameters specify the rates of application for crossover (100% per pair of
parent chromosomes), the probability of a base being mutated in the offspring
chromosome (2% chance per base), the application rate for inversion in
offspring (20% chance per chromosome), and the probability of bases being
inserted or deleted at any particular base in the offspring chromosome (1%
per base).

The third example uses a pre-existing population file, pop1, which are evolved
for a further 10 generations, beginning at generation 10, with the same
operator and replacement parameters as the previous example, and the result is
stored in pop2.txt, along with the fitness values. Although verbose mode is
selected, the '-s 0' parameter turns population statistics generation off.

The last example, takes the population (with fitness values) from the
previous example, and then continues evolution for 10 generations, starting
at generation 20, in much the same manner as the previous examples, except
that the replacement scheme will select 2 pairs of breeders, each of which
produces 2 offspring which are placed in the population to replace 4 unfit
individuals; also, verbose mode is off, so messages aren't output during
evolution, however, '-s 5' turns population statistics reporting on, but
does it only for every fifth generation, and the initial and final
populations. The resulting population is stored without fitness values.



Description of GA components:
----------------------------

The following attempts to outline the uses of the scripts. Most of the
information contained in this section is gleaned from the online help
of the scripts - which can be seen by issuing the script with a '-h', '-?',
'--help' or invalid command line arguments. Hence for more details on the
usage of the script, run the desired script with this option.

The files included in this distribution are as follows:

  README.txt		(this file)
  changebase4.pl
  chrominfo.pl
  concat.pl
  dummyfitness.pl
  fitest.pl
  fitest2.pl
  genchroms.pl
  genpopn.pl
  homxover.pl
  insdelbase4.pl
  inverop.pl
  joinlines.pl
  lcsstr.c		(compiled to lcsstr.exe)
  mutbase2.pl
  mutbase4.pl
  oneptxover.pl
  popfstats.pl
  popgstats.pl
  pophstats.pl
  poplstats.pl
  tournament.pl
  updatepopn.pl
  viablefitness.pl
  viableNDfitness.pl

Descriptions of these follow:



- main program

  genpopn.pl : the driving code for the GA

  This is the main program of the genetic algorithm, and would be the one
  that the user would run. It in turn passes tasks on to other scripts to
  perform, and although these can be used independently, it should generally
  be unneccessary to do do. As such, its synopsis and a description of its
  parameters follows the general program description below.

  genpopn generates a population of the specified number of random
  chromosomes, or uses an existing population stored in a file, and evolves
  these over the requested number of generations, with the specified number
  of unfit individuals replaced at each generation with the offspring of fit
  individuals, which are produced by genetic operators used according to their
  assigned probabilities. The result of this is the production of a population
  of chromosomes that are fit according to the fitness evaluation given by
  the specified fitness evaluation script.

  Note that the rates for the genetic operators refer to the probability that
  an operation will take place (0 for never, .5 is 50% chance, 1.0 for
  always). In crossover and inversion, this is the probability of the operator
  being used, but in mutation and base insertion/deletion, this is the
  probability of this occuring at any particular base within the chromosome
  (ie mutation and insertion/deletion are always used, but may result in no
  changes, for example, if their rate is set to 0).

  Synopsis: [perl] genpopn.pl [-v] [[-s|-sg] ngens] [-f] [-ga]
  		                   (-g popsize (len | minlen maxlen) | popfile)
				   outfile fitscript [gen+]numgens
				   [reprate[+] xrate mrate irate [idrate]]
 
  Args:
  	-v		- verbose output to STDERR, operator results,
  			  information on removed population members, and
 			  fitness, and length if '-ga' switch not given, stats
 			  every generation
 	-s ngens	- generate fitness stats, and if not a standard GA,
			  length and homology stats every 'ngens' generations
 			  (0 for off - used to turn of stats with verbose mode)
 	-sg		- as above, but also generate, gene count stats
 	-f		- save fitness with evolved chromosomes
  	-ga		- use standard GA on standard binary fixed length chrom
  			  this means that -g takes 'len' not minlen and maxlen,
  			  and the 'idrate' parameter should not be given
  	-g popsize ( len | minlen maxlen)
  			- generate a populate of size 'psize' randomly
  			  chromosomes with lengths between min and max
  			  or all of length 'len' if -ga switch was used
  	popfile		- to use a pre-existing population
  	outfile		- for storing resulting population
  	fitscript	- fitness evaluator that returns lines of:
  			  fitness '>' chromosome
  			  If command line switches are needed for the
  			  fitness script, script and switches should be
  			  enclosed in single or double quotes.
  	[gen+]numgens	- number of generations to evolve population. gen
  			  indicates what generation to start at (default 0)
  			  if numgens=0, the following arguments are optional
  	reprate[+]	- number of members to replace with new (maximum of
  			  1/4 the population size). If followed by a '+',
			  then both offspring of the 'reprate' selected
			  parents are introduced into the population (which
			  requires 'reprate' to be an even number), otherwise
			  only the fitest of the 2 x 'reprate' are introduced.
  	xrate		- rate of crossover in offspring (0.0 - 1.0)
  	mrate		- rate of mutation of bases in offspring (0.0 - 1.0)
  	irate		- rate of inversion in offspring (0.0 - 1.0)
  	idrate		- rate of base insert/delete (0.0 - 1.0) in offspring
 
  Inputs: (popfile)
  			- lines in the form: [fitness '>'] chromosome, in the
  		  	  optional population file (popfile)
 
  Returns:
  	  (stdout)	- generation number
  	  		- statistics (if -s option, verbose stats if -v)
  	  		  - population fitness stats
  	  		  - population chromosome length stats
  	  		  - population chromosome homology stats
  	  (stderr)	- various messages tracking progress (if -v)
  	  		- operator results (if -v)
  	  (outfile)	- lines of chromosomes in base 4, without fitnesses
 	  (OUTFILEBASENAME_stats.txt)
 	  		- (optional) stores statistics generated during run


 

- Population Operations components

  Population operations either produce, or act on an existing population of
  chromosomes. Except in the case of genchroms, which generates a population
  of chromosomes to stdout, these scripts act on chromosome fitness pairs
  stored in a population file, on separate lines with the following format: 
  	fitness '>' chromosome



  genchroms.pl : generates a population of random chromosomes

	genchroms generates a population of the specified number of
	chromosomes. Each chromosome is comprised of randomly generated
	bases, lying with the specified range, and is of a random length that
	lies between the minimum and maximum length specified. The resulting
	population is output to stdout.

	The base, specifies the alphabet, and may be 2-10. Base 2 is binary;
 	base 4 gives chromosomes with bases of numbers from 0-3; base 10 is
	decimal (0-9); etc.

	Note, it is unfortunate that the word "base" is used with 2 different
	meanings in this set of scripts, and supporting documentation. The
	first meaning refers to the number system / alphabet used - eg. base
	2 is binary, base 4 is 0-3 (or DNA or RNA), base 10 is decimal. The
	second meaning is the genetic meaning, that of a character (or base)
	in a chromosome's sequence.


  updatepopn.pl : does a one generation update of the population

	updatepopn is the core of a stable state genetic algorithm, providing
	tournament selection and replacement; it does one update of the
	population - choosing 2 x 'reprate' tournament winners for breeding,
	and replacing 'reprate' tournament losers with the best of each winner
	couple's offspring.

	Note that updatepopn.pl creates several temporary files for passing
	information between scripts. updatepopn itself creates files with
	extensions 000-004 and .chl; and calls tournament.pl, which creates
	files with extensions .wnr, .lsr, .otr, .rst, for its results.



  tournament.pl : performs a tournament between population members

	tournament takes a file of fitness-chromosome pairs, separated by a
	'>', and then conducts the specified number of tournaments, in which
	participants are randomly chosen from the general population, and a
	winner and loser are chosen. At the end of the tournaments, the
	population will have been divided into 4 groups: winners, losers,
	those that were neither winners or losers (for tsize>2).  and the rest
	(those who didn't compete). These groups are stored in 4 different
	files, having the same filename as the original population file, but
	different file extensions, and the number of members of each is
	printed to STDOUT.

	Note that tournament.pl stores its results in the files with
	extensions .wnr, .lsr, .otr, and .rst



- Population Statistics components

  Population statistic scripts all take a population filename as an argument,
  and collect the relevant statistics from the specified population file. The
  -v switch produces labeled output.

  Population files are comprised of lines of:
  	[fitness '>'] chromosome
  where the fitness component is optional for chromosome length and homology
  statistics, but are required for the fitness statistics (obviously).

  Note that pophstats samples the population for homology by dividing the
  population into two groups, and randomly pairing each member of one group
  with a member of the other. For this reason, homology statistics may vary
  from one sample to another of the same population.

  Also note that, length, gene count and homology stats are examining integer
  data, and when binning these values, the integer values form the centre
  values for the bins, meaning that when bin ranges are reported, they may be
  floating point numbers lying between integer values.



  popfstats.pl : generates population fitness statistics for the specified
		 population file.

  popgstats.pl : generates population gene count statistics for the
  		 specified population file.

  pophstats.pl : generates population chromosome homology statistics for the
  		 specified population file.


  poplstats.pl : generates population chromosome length statistics for the
  		 specified population file.



- Genetic Operator components

  Genetic operators all take lines (2 lines for crossover, any number for
  other operations) of chromosomes from stdin, and return the modified
  chromosomes to stdout, with additional outputs to stderr if specified by
  the -a command line switch.

  Chromosomes are comprised of bases specified in an alphabet of [0-3],
  corresponding to the DNA alphabet of [TCAG].



  homxover.pl : homologous crossover (recombination) operator

	homxover takes 2 chromosomes from stdin and generates 2 new
	chromosomes using homologous crossover, the 2 parent chomosomes are
	aligned on a randomly chosen common substring of sufficient length,
	and then ends are exchanged to give the 2 offspring.

	homxover uses lcsstr.exe to find a homologous region of the parents'
	chromosomes. Homology is measured by the length of common substrings
	rather than the more commonly used, and biologically plausible,
	measure of difference between base sequences given by longest common
	subsequence; which allows sequences to be compared where there have
	been bases inserted, deleted or mutated.

	To have a longest common subsequence based homologous crossover only
	requires providing an executable that can find this, and changing the
	$LCSSTR variable declaration at the start of homxover.pl to point to
	the executable.



  lcsstr.c / lcsstr.exe : finds longest (or random) common substring

	lcsstr.c should be compiled to lcsstr.exe, with:
			gcc lcsstr.c -o lcsstr.exe

	lcsstr takes 2 strings from stdin and returns to stdout the indexes
	and length of the longest common substring or a randomly chosen common
	substring, with longer substrings having a higher probability of being
	chosen.

	lcsstr is used to find homologous regions of the parent chromosomes
	for the crossover operator, it is called with the -a switch to return
	the indexes and lengths of longest and random common strings, and the
	count and sum of all potential common strings.

	It contains suffix tree code adapted from the C code in file 'st.c'
	that forms the core of the Perl String::Ediff-0.01 module, by Bo Zou
	(boxzou@yahoo.com), released 25 Jan 2003 at www.cpan.org

  oneptxover.pl: a 1 point crossover operator for fixed length chromosomes.

	oneptxover takes 2 chromosomes (of the same length) and generates
	2 new chromosomes using one point crossover. This is done by randomly
	choosing a point on one chromosome, and exhanging the ends of the
	chromosomes at that point to produce the 2 offspring chromosomes.


  inverop.pl : an inversion operator

  	inverop.pl is an inversion operator that takes chromosomes from stdin
	and inverts a randomly chosen sub-sequence of the supplied chromosome.
	The length of the inversion is limited by the command line arguments,
	either specifying absolute lengths or percentages of the chromosome's
	length. The resulting chromosome(s) are output to stdout.


  mutbase2.pl : a (bit flip) mutation operator for binary chromosomes.


  mutbase4.pl : a mutation operator giving transversions and transitions

  	mutbase4 is a mutation operator that takes chromosomes from stdin,
	comprised of bases specified as [0-3] (equivalent to DNA [TCAG])
	and generates new chromosomes by randomly mutating bases, with the
	probability of mutation being given by the commandline argument. The
	resulting chromosome(s) are output to stdout.

	Mutations may be either transversions, or transitions, which are
	described as follows:
    	- transversion 0<-->2, 1<-->3 (in DNA: T<-->A, C<-->G);
    	- transition   0<-->1, 2<-->3 (in DNA: T<-->C, A<-->G).



  insdelbase4.pl : a base insertion/deletion operator

	insdelbase4 is an operator that randomly inserts or deletes bases
	from the supplied chromosomes (read from stdin), with the
	probability of this occuring being given by the command line
	arguments. The resulting chromosome(s) are output to stdout.



- Fitness Evaluation Scripts

  Fitness evaluation scripts take a population of chromosomes from stdin
  and output their fitnesses to stdout. It is important to note that, these
  scripts are also required to be able to output fitness chromosome pairs,
  in the format:
  	fitness '>' chromosome

  In all fitness evaluation scripts, this is accomplished by using the
  command line switch '-f' .

  Also, all fitness scripts are required to accept the -gen generation#
  parameter, which indicates what the current generation is. They are not
  required to use this, however.


  dummyfitness.pl : a non-evaluating fitness script for use with genpopn.pl

	dummyFitness is used when generating an initial random population
	of chromosomes with genpopn.pl, which requires a fitness script,
	but when don't with to evolve the population yet. Hence, a this
	script just returns 0 for the fitness of each chromosome, saving
	computational expense when not required.


  fitest.pl : a template / test fitness script for debugging other components

	fitest is a simple fitness evaluator for testing purposes, it takes
	chromosomes (from stdin), and calculates their fitness based on the
	number of promoter sequences (0202 - equivalent to TATA) found in the
	first 100 bases, and the length of the chromosome (up to 100 bases).


  fitest2.pl: another test fitness script, for use with binary chromosomes

	fitest2 is a simple fitness evaluator for testing purposes, it takes
	chromosomes (from stdin), comprised of binary digits and calculates
	their fitness based on the longest sequence of alternating digits
	(ie ..01010..) as a fraction of the length of the chromosome.


  viablefitness.pl : fitness measure of chromosomes viability for development

	viablefitness takes chromosomes (from stdin) and calculates their
	fitness based on the number of genes and the percentage of the
	chromosome that is 'useful'.

	The aim of this fitness evaluator is to aid evolution in the process
	of seeding a population with chromosomes that may have functional
	gene expression (hence # genes), while both trying to eliminate too
	much or too little junk regions (hence percentage 'useful').

	Genes are specified as:
		promoter ... start-codon ... gene-coding-region  stop-codon
		  TATA   ...    AUG             (XXX)+           UAA/UGA/UAG
	          0202   ...    203             (XXX)+           022/032/023

	The percent useful of a chromosome is calculated as follows:
	- the length of a gene (G) = gene end - gene start
	- the length of suppressor region (S) = gene start - promoter
	- the length of enhancer region (E) = promoter - prev gene end
	hence the useful length of a gene (UGL) is given by:
	G + E (where E<=TFmax, else TFmax) + S (where S<=TFmax+4, else
	TFmax+4)
	(note: +4 in the suppressor region for the promoter length)

	The fitness is dependent on 2 functions, the latter applied as a
	scaling factor on the first:
	- a gaussian function applied to the number of genes, which gives a
	  fast rise from 20-70 genes, and plateaus around 100 (by default,
	  but can optionally be scaled by supplying a gene ceiling);
	- a gaussian function applied to the % of the chromosome that is
	  useful, giving a rise from 0-30%, plateau 30-70%, and fall 70-100%

  viableNDfitness.pl: gives measure of chromosomes viability for construction

	fitness is calculated based on the number of viable chromosome
	sections, whereby viability is indicated by being at least long
	enough to encode something useful, up to the specified number of
	sections.

	The aim of this is to aid evolution in the process of seeding a
	population with chromosomes that have as many sections as there are
	input and outputs, to aid in growing circuits that have inputs and
	outputs connected in some manner.



- Other Scripts

  changebase4.pl : change between different base 4 alphabets (DNA/RNA/decimal)

	changebase4 takes chromosomes (from stdin), comprised of bases
        specified in decimal [0-3], DNA [TCAGtcag], or RNA [UCAGucag]
	alphabets, and converts chromosomes to the equivalent in a different
	base 4 alphabet: upper or lower case DNA/RNA, or decimal.


  chrominfo.pl: gives information on chrom length and number of genes, etc


  joinlines.pl: joins corresponding lines in 2 files (eg fitness + chroms)

	joinlines is a simple utility for joining together the
	corresponding lines from 2 different files using the
	supplied delimiter, and outputting the result to STDOUT


  concat.pl: a platform independent concatenation utility
	concat.pl replaces the need for changing between "cat" and "type"
	unix and DOS commands, when switching between platforms.


Environment/platform notes:
--------------------------

Note that this has been developed on Win2000, with Cygwin version 1.3.10
using:
	- gcc 2.95.3-5 on Cygwin (to compile lcsstr.c)
	- Perl version v5.6.1 built for MSWin32-x86-multi-thread
	  with local patch ActivePerl Build 633,  Compiled at Jun 17 2002,
	  for all perl scripts. Note: win32 dll is external to Cygwin.

Note that, for portability, there is no "#!/usr/bin/perl" interpreter
location directive at the start of scripts, hence scripts are executed
with "perl scriptname.pl". Also, note the use of "perl concat.pl" rather
than the unix "cat" or DOS "type" commands, to aid portability. Calls to
the shell have been made as generic as possible to avoid the need to
change them if the platform is changed. Also, the setup of stderr redirect
involves checking for the existence of /dev/null, and if present using it,
otherwise it assumed that the script is being run from DOS/Windows and
stderr is redirected to "nul".




Design decisions:
----------------

When dealing with populations of chromosomes, only the bare minimum
chromosomes, those that are currently being used, are kept in memory, with
the rest kept on disk. Although memory is not usually an issue, it was an
early design decision to take this approach as the length of chromosomes
(in particular for the target application) and population size (possibly for
other applications) could not be given bounds. Thus, it may be possible to
optimise the code by storing the population in memory. However, as it is
likely that in most cases, the bottleneck will lie in the (fitness)
evaluation of the generated chromosomes, this mitigates the loss of speed
involved in storing the population on disk.

