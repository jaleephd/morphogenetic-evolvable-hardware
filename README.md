# Morphogenetic Evolvable Hardware

Generating gate-level electronic circuits on FPGAs through gene expression and morphogenesis closely tied to hardware configuration.

## Brief background

Evolvable hardware (EHW) uses simulated evolution to generate an electronic circuit with specific characteristics, and is generally implemented on Field Programmable Gate Arrays (FPGAs).
EHW has proven to be successful at producing small novel circuits for applications such as
robot control and image processing, however, traditional approaches,
in which the FPGA configuration is directly encoded on the chromosome,
have not scaled well with increases in problem and FPGA architecture complexity.

One of the methods proposed to overcome this is the incorporation of a growth process,
known as morphogenesis, into the evolutionary process. However, existing approaches have tended to
abstract away the underlying architectural details, either to present a simpler virtual FPGA architecture,
or a biochemical model that hides the relationship between the cellular state and the underlying hardware.
By abstracting away the underlying architectural details, EHW has moved away from one of its key strengths,
that being to allow evolution to discover novel solutions free of designer bias.
Also, by separating the biological model from the target FPGA architecture, too many assumptions
and arbitrary decisions need to be made, which are liable to lead to the growth process failing to produce
the desired results.

This code shows a new approach to applying morphogenesis to gate-level FPGA-based EHW,
whereby circuit growth is closely tied to the underlying gate-level architecture,
with circuit growth being driven largely by the interaction between evolved genes and the
state of gate-level resources of the FPGA.

## Directory Structure

The complete source code tree is found under `mgehwsrc`, with EHW_README.txt giving an
overview of each sub directory and the top level scripts for running the system.
Each sub directory contains a README further documenting the source code contained in it.

## Environment and Requirements

### JBits Gate-level Virtex FPGA Interface

This project was developed between 2001 and 2006, for use with the Xilinx Vertex FPGA,
which allowed gate level configuration via the proprietary Xilinx JBits Java library.
The FPGA layouts were intended originally for the Raptor Board developed at the
University of Paderborn, Germany. In practice simulated boards were largely used (via JBits).

The code requires the JBits (2.8) library to run, which was available on request from Xilinx,
though it appears to be no longer supported,
see: <https://forums.xilinx.com/t5/Virtex-Family-FPGAs/Where-can-I-find-JBits/td-p/746550>.
The JBits library required Java JDK 1.2.2 and JRE.

### Operating System Platform, Language and Library Versions

The software was developed on Windows 2000 / XP, with Cygwin version 1.3.10 using:
* Bash 2
* Active Perl v5.6.1 built for MSWin32-x86-multi-thread
  with local patch ActivePerl Build 633, Compiled at Jun 17 2002.
	* win32 dll is external to Cygwin.
	* need to ensure Active Perl is earlier in the `PATH` than Cygwin's `/usr/bin/perl`

* Java JDK 1.2.2 + JRE
    * need directory containing javac.exe, java.exe, etc added to path:
	* in windows through control panel/system/advanced/environment variables: `C:\jdk1.2.2\bin\`
	* in cygwin: `export PATH=C:/jdk1.2.2/bin/`

* Xilinx JBits 2.8
    * requires JDK 1.2.2
    * need java's CLASSPATH environment variable set to the directory containing JBits
      (the one containing the "com" subdirectory): `C:\jdk1.2.2\lib;D:\JBits;.`
	* (optional) for accessing mappings from IOB CoreTemplate pins to package pin name:
      unpack IobXCVpackage_v2.zip and place the class and javadoc files in their respective locations
      under: `com/xilinx/JBits/Virtex/RTPCore/Iob`

While the Java dependencies are fixed due to JBits requirements, the rest of the system
should be able run on later versions of Perl (Active Perl should no longer be required
for more recent Perl installations), as external libraries were avoided to ensure
ease of installation across multiple machines. Additionally, white it was developed under
Cygwin 1.3.10, there is no requirement for that version, more recent versions should be
compatible.

The code should be agnostic to base operating system, only requiring a Bash-like shell (>= version 2).
That is, it should be able to run directly on Linux (not tested) through the Bash shell,
on any compatible version of Windows via Cygwin, and possibly on MacOS or Windows 10
through Bash.

### Setup for Running the MGEHW System

Given that the above prerequisites are fulfilled, to run the system requires
that all the compiled class files and executables must be placed in a directory
in `CLASSPATH` and `PATH` (respectively) or along with the copies of scripts
configuration files and null bitstreams, placed in a single directory
from which the system is run. This is a current limitation of the (prototype) system.

The system can be run without a complete installation of ActivePerl (more modern
Cygwin installations and Linux should be able to use the system Perl)
only the Perl interpreter and dll are needed in most cases, the exception to
this is cleanupEHW.pl which uses globbing, and runMG.pl which calls
cleanupEHW.pl.

### Running the MGEHW System

See `mgehwscr/EHW_README.txt` for details.


## For more information

This code comprised a large component of the Ph.D. work outlined in the thesis
[Morphogenetic Evolvable Hardware](https://eprints.qut.edu.au/16231/ "QUT ePrints"), 
Justin A. Lee, Queensland University of Technology, 2006.

For more information, design, results, etc. see the above reference.
