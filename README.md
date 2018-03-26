# Morphogenetic Evolvable Hardware

Generating gate-level electronic circuits on FPGAs through gene expression and morphogenesis closely tied to hardware configuration.

## Brief background

Evolvable hardware (EHW) uses simulated evolution to generate an electronic circuit with specific characteristics,
and is generally implemented on Field Programmable Gate Arrays (FPGAs).
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

This code shows new approach to applying morphogenesis to gate-level FPGA- based EHW,
whereby circuit growth is closely tied to the underlying gate-level architecture,
with circuit growth being driven largely by the state of gate-level resources of the FPGA.

## For more information

This code comprised a large component of the Ph.D. work outlined in the thesis
[Morphogenetic Evolvable Hardware](https://eprints.qut.edu.au/16231/ "QUT ePrints"), 
Justin A. Lee, Queensland University of Technology, 2006.

For more information, design, results, etc. see the above reference.
