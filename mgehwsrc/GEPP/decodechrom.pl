
# decodechrom.pl by J.A. Lee, May 2003
# 		 (fixed binding of TFs and added -notfbind arg, July 2004)
#
# Synopsis:
# [perl] decodechrom.pl [-notfbind] code-file [cyto-file] [p:promoter-list] [s:start-list] [e:stop-list] [pb=bound] [eb=bound] [rb=bound] 
#
# Args:
#    -notfbind	- don't check for TF binding,
#     code-file - A config file that contains the genetic code table, giving
#     		  a mapping from codons to resource-types, and associated
#     		  user-defined functions for resources that decode the
#     		  following codons into the attributes and settings for that
#     		  resource. This is used for both decoding genes, and for
#     		  locating binding sites on regulatory regions and their
#     		  associated binding resources.
#     cyto-file	- file containing lines of cytoplasmic determinants in format:
#	          row, col: resource,attribute,setting1[,setting2[, ..]]
#                 we only use those that have resource="TF"; attribute may be
# 		  (M|m)orphogen, (L|l)ocal, (C|y)toplasm (only first character
# 		  used); setting1 is distance from source, and is given by the
# 		  number of binary digits, so morphogens at source will have
# 		  an empty distance field, and local/cytoplasm TFs will have
# 		  this field ignored; setting2 is the TF binding sequence.
#     p:promoter-list	- comma separated list with no spaces that give the
# 			  sequence that will be interpreted as a promoter
#     s:start-list	- comma separated list with no spaces that give the
# 			  sequence that will be interpreted as a start codon
#     e:stop-list	- comma separated list with no spaces that gives the
# 			  sequence that will be interpreted as a stop codon
#     pb=bound	- limits distance upstream of gene for promoter effect
#     eb=bound	- limits distance upstream of promoter for enhancer binding
#     rb=ound	- limits distance downstream of promoter for repressor binding
#     
#
# Inputs: (stdin)
# 		- lines of chromosomes comprised of bases in the alphabet
# 		  [0-3] (equivalent to DNA [TCAG], RNA [UCAG])
#
# Outputs:
# 	    	- (stdout) lines of:
#			chromno: geneno,gstart: feat,+/-offset,length: details
#		  - chromno identifies which chromosome according to its
#		    occurence in the input.
#		  - geneno gives the number of the gene starting with 1 at
#		    the 5' end of the chromosome
#		  - gstart gives the start location of the gene's coding
#		    region on the chromosome
#		  - feat is one of "gene", "prom", "enhb", "repb"; which
#		    indicate, respectively, gene coding region, promoter,
#		    enhancer binding site, repressor binding site
#		  - offset gives the distance in bases upstream (-) or
#		    downstream (+) from the gene start
#		  - length is the coding/binding/sequence length in bases
#		  - details is a list of semi-colon delimited elements, with
#		    each element itself being comma-delimited.
#		      feature	    detail		element
#		       prom	sequence
#		       gene	elem1;elem2;...	   offset,len,resattrset
#		       enhb	elem1;elem2;...    offset,len,bindets
#		       repb	elem1;elem2;...    offset,len,bindets
#		    where resattrset is in the form:
#		    	resource,attribute,setting1,setting2, ...
# 		    and bindets in form:
# 			"TF,TF", dist, bindseq
# 		     or
# 			resource,attribute,setting1,setting2,...
# 		- (stderr)
#
# Description:
# 		decodechrom reads chromosomes in base 4 and parses and
# 		decodes the information in these, to return a list of genes
# 		with information on location, relative position of promoter
# 		and regulatory sites, and the decoded contents of the gene
# 		and the resources that bind to the regulatory sites. This
# 		information can be used to construct a working model of gene
# 		expression for the supplied chromosome.
#
# See Also:	egGeneticCode.conf for a more detailed description of
# 		the genetic code table and resources' decode functions.
#
# Note:
#     	the config file is responsible for creating '$GeneticCodeTable'
#     	which contains the top level (resource-attribute) genetic code, and
#     	for the creation of the subroutines which further decode the
#     	resource's attributes and settings.



### set the environment

# use strict;	# forces declaration of variables
#
# use vars qw( STDERR_REDIRECT CAT DEL CP );

$PID=$$;

#$STDERR="nul";	# coz Active Perl runs from win
$STDERR=(-e "/dev/null") ? "/dev/null" : "nul"; # assume DOS if not unix
$STDERR_REDIRECT="2>$STDERR";

$CAT="perl concat.pl";

$PARSECHROM="parsechrom.pl";
$EXTRACTCHROMFEAT="extractchromfeat.pl";
$DECODEGENES="decodegenes.pl";
$FINDBINDINGSITES="findbindingsites.pl";
$BINDMATCH="bindmatch.pl";

$TMPFILE="TMP0$PID" . "_ge";
$TMPFILE1="TMP1$PID" . "_ge";
$TMPFILE2="TMP2$PID" . "_ge";
$chromfile="TMPc$PID" . "_ge";

###### end ENVironment declarations



#### Global declarations


### end Global declarations


# scan commandline arguments
($notfbind,$codefile,$cytofile,$promoters,$startcodons,$stopcodons,$pbound,$ebound,$rbound)=getArgs();

# read chromosomes from STDIN, parse chroms and store results in chromfile
$chromno=storeParseChromsFromStdin($chromfile);


# for each chromosome,
for ($ci=1; $ci<=$chromno; ++$ci) {

  # put the parsed info for this chromosome into an array of strings in the
  # comma-delimited format: region-type, start-loc-on-chrom, length, sequence
  @chrominfo=getParsedChromInfo($chromfile,$ci);

  # now create an array of genes associated with their promoters and
  # regulatory regions. as strings in the comma-separated format:
  # gene, promoter, enhancer, repressor; where each is an index into the
  # @chrominfo array, or -1 for no entry
  @geneinfo=assocByGene($pbound,@chrominfo);

  # now we decode each gene sequence, using the data in @chrominfo
  # to give an array of resources (@decodedgenes) whose index is that of
  # geneinfo's, and each element contains a string with the list of decoded
  # resources and their locations on the coding region, in the form of:
  # decodedres1 ";" decodedres2 ";" ...
  # each decodesres is in form: offset "," length "," encoded-resource
  @decodedgenes=decodeGenes(\@geneinfo,\@chrominfo,$codefile);
#printA("decodedgenes",@decodedgenes);

  # create hash of binding sites within regulators, to be used for binding
  # with TFs and resources: key is gene-index-in-@geneinfo, the value is
  # a string containing a list of binding sites and their locations on the
  # regulator, in the form of: bindsite1 ";" bindsite2 ";" ...
  # with each bindsite in the form: offset "," length "," sequence

  %enhbinds=getBindingSites("enh",$ebound,$codefile,\@chrominfo,\@geneinfo);
  %repbinds=getBindingSites("rep",$rbound,$codefile,\@chrominfo,\@geneinfo);
#printA("enhbinds",%enhbinds);
#printA("repbinds",%repbinds);

  if ($notfbind eq "true") {
    %TFs=();
  } else {
    # next we extract from the decoded genes, all the TFs. these will be
    # used to determine what TFs bind on regulatory regions.
    %TFs=extractTFsFromGenes(@decodedgenes);

#printA("TFs extracted from genes",%TFs);

    # get the cytoplasmic determinants from file and merge with the others
    if ($cytofile ne "") {
      addTFsFromCytoplasmFile($cytofile,\%TFs);
    }
  }

  # TFs are stored in a hash in format: key = seq, value = a string of
  # comma delimited dist-from-src, some of these distances may be empty
  # fields and some duplicates, so we remove duplicates - one empty field
  # is OK though - it indicates local TF or morphogen at its source

  foreach $tf (keys %TFs) {
    %hashdists=();
    # each of these may have several comma-delimited dist-from-src entries
    @maybedupsdists = split(/,/,$TFs{$tf});
    # some may be duplicates, so remove duplicates by converting to a hash
    @hashdists{@maybedupsdists} = ();
    @dists = keys %hashdists;
    # and now store as a comma-delimited string list
    $TFs{$tf}=join(",",@dists);
  }

#printA("all extracted TFs to check for binding",%TFs);
#printA("TFs",%TFs);

  # now can determine binding of TFs and resources to regulatory regions,
  # returns hash of binding elements, with key gi.bsn, where gi is the
  # gene-index-in-@geneinfo, and bsn is that regulator's bind site number.
  # value is  in format: bindelem1;bindelem2; ...
  # each bindelem is in format: offset-in-site "," length ',' binding-details
  # with binding-details in form:
  # 	dist,bindsequence or resource,attribute,setting1,setting2,...

#print STDERR "test binding of resources & TFs to enhancer bind sites\n";
  %enhbindingelems=bindMatch($codefile,\%TFs,%enhbinds);
#print STDERR "test binding of resources & TFs to repressor bind sites\n";
  %repbindingelems=bindMatch($codefile,\%TFs,%repbinds);
#printA("enhbindingelems",%enhbindingelems);
#printA("repbindingelems",%repbindingelems);

  # need to maintain track of the number of deleted genes,
  # so output gene index is correct, rather than having missing gene indexes
  # in the output (of the deleted genes)
  $dg=0;
  for ($gi=0;$gi<$#geneinfo+1;$gi++) {
    # gi gives index on @geneinfo, @decodedgenes, and key on
    # %enhbinds and %repbinds, and the first part of the key on
    # %enhbindingelems and %repbindingelems
    ($geneidx,$promidx,$enhidx,$repidx)=split(/,/,$geneinfo[$gi]);
    # geneidx,promidx,enhidx,repidx are indexes on @chrominfo

    # get the start and length of the gene in the chromosome
    ($type,$gstart,$glength,$gseq)=split(/,/,$chrominfo[$geneidx]);
    # get the decoded gene
    $gdetails=$decodedgenes[$gi];

    # if there is an empty coding region, then discard gene
    if ($gdetails eq "") {
#print STDERR "empty gene ($gi)\n";
      $dg++;				# increment counter of deleted genes
      next;
    }
 
    # output the gene's info:
    # 	gene location on chromosome, length, coded resources
    printGeneDetails($ci,$gi-$dg,$gstart,"gene",$gstart,$glength,$gdetails);

    # if there's a promoter get its start, length & sequence on the chromosome
    if ($promidx>-1) {
      ($type,$pstart,$plength,$pdetails)=split(/,/,$chrominfo[$promidx]);
      # output promoter location (relative to gene start) and length
      printGeneDetails($ci,$gi-$dg,$gstart,"prom",$pstart,$plength,$pdetails);
    }


    # get regulator binding sites and their binding elements if any
    # each bindsite is treated as an independent feature to be output,
    # its details will be its binding element list, which is already in a
    # string list format of bindelem1;bindelem2; ...
    # each bindelem is in format: offset-in-site "," length ',' bind details

    # if there's an enhancer then output its binding sites and elements
    if ($enhidx>-1) {
      # get the start of the enhancer in the chromosome (ignore other stuff)
      ($type,$estart,$elength,$eseq)=split(/,/,$chrominfo[$enhidx]);
      (@ebindsites)=split(/;/,$enhbinds{$gi});
#print STDERR "estart=$estart,elength=$elength,eseq=$eseq\n";
#print STDERR "enhbinds {$gi} =" . $enhbinds{$gi} . "\n";
      # for each bind site (1..N) get its location, and binding elements
      for ($i=1;$i<=$#ebindsites+1;$i++) {
        # get binding site offset within regulator and length
        ($bsoffset,$bslen,$seq)=split(/,/,$ebindsites[$i-1]);
        # get bind site's actual location on chromosome
        $bstart=$estart+$bsoffset;
        # get this binding site's list of semi-colon delimited elements
        $enhbelems=$enhbindingelems{"$gi.$i"};
#print STDERR "i=$i,bsoffset=$bsoffset,bslen=$bslen,seq=$seq, enhbelems=$enhbelems\n";
	# skip empty bind sites
        if ($enhbelems eq "") {
#print STDERR "empty enhancer bind site ($i) site on ($gi)\n";
          next;
	}
        # output bind site (relative) location, length, and binding resources
        printGeneDetails($ci,$gi-$dg,$gstart,"enhb",$bstart,$bslen,$enhbelems);
      }
    }

    # if there's a repressor then output its binding sites and elements
    if ($repidx>-1) {
      # get the start of the repressor in the chromosome (ignore other stuff)
      ($type,$rstart,$rlength,$rseq)=split(/,/,$chrominfo[$repidx]);
      (@rbindsites)=split(/;/,$repbinds{$gi});
#print STDERR "rstart=$rstart,rlength=$rlength,rseq=$rseq\n";
#print STDERR "repbinds {$gi} =" . $repbinds{$gi} . "\n";
#printA("rbindsites",@rbindsites);
      # for each bind site (1..N) get its location, and binding elements
      for ($i=1;$i<=$#rbindsites+1;$i++) {
        # get binding site offset within regulator and length
        ($bsoffset,$bslen,$seq)=split(/,/,$rbindsites[$i-1]);
        # get bind site's actual location on chromosome
        $bstart=$rstart+$bsoffset;
        # get this binding site's list of semi-colon delimited elements
        $repbelems=$repbindingelems{"$gi.$i"};
#print STDERR "i=$i,bsoffset=$bsoffset,bslen=$bslen,seq=$seq, repbelems=$repbelems\n";
	# skip empty bind sites
        if ($repbelems eq "") {
#print STDERR "empty repressor bind site ($i) site on ($gi)\n";
          next;
	}
        # output bind site (relative) location, length, and binding resources
        printGeneDetails($ci,$gi-$dg,$gstart,"repb",$bstart,$bslen,$repbelems);
      }       # next bind site
    }	    # end if
  }	  # next gene
}	# next chromosome
 

unlink($chromfile) || die ("Unable to remove parsed chromosomes file '$chromfile'\n");



### end main()


sub printGeneDetails {
  my ($cno,$gno,$gstart,$feat,$featstart,$length,$details) = @_;
  my $offset;
  # add 1 to gene number so genes start from 1 not 0, as with chromosome num
  $gno++;
  # offsets are 0 or +/-N
  $offset=($featstart-$gstart==0) ? "0" : sprintf("%+d",$featstart-$gstart);
  print "$cno\: $gno,$gstart\: $feat,$offset,$length\: $details\n";
}




# get cytoplasmic determinants from file, and add to existing TFs hash
# having format: format: key = bind-sequence, value = a string of comma
# delimited dist-from-src, some of which may be empty fields
# cytoplasmic determinants are stored on file in the format:
#	xcoord, ycoord: jbits=resource,attribute,setting1[,setting2[, ...]]
#		        or TF=Local/Morphogen/Cytoplasm,dist,bindseq
# we only use those that have resource="TF"

sub addTFsFromCytoplasmFile {
  my ($cytofile,$TFhashref) = @_;
  my ($coord,$details,$type,$tftype,$dist,$seq);

#print STDERR "entering addTFsFromCytoplasmFile($cytofile)\n";
  open(CTFILE,"<$cytofile") || die ("Unable to read cytoplasm TFs from file $cytofile\n");
  while(<CTFILE>) {
    chop;
    next if (/^\s*#/);			# skip line comments
    next if (/^\s*$/);			# skip blank lines
    # split into coord & resource fields, remove leading space from resource
    ($coord,$details)=split(/:\s*/);
    ($type,$dets)=split(/=/,$details);
    if ($type eq "TF") {
      # TF's 1st field is type/attribute, followed by dist, then binding seq
      ($tftype,$dist,$seq)=split(/,/,$dets);
#print STDERR "read TF: (type=$tftype,dist=$dist,seq=$seq)\n";
      # if its a local TF then get rid of its distance field
      $dist="" if ($tftype=~/^[Ll]/);
      if (exists $TFhashref->{$seq}) {
	$TFhashref->{$seq} .= "," . $dist;
#print STDERR "appended dist=$dist to tfhasref($seq)\n";
      } else {
	$TFhashref->{$seq} = $dist;
#print STDERR "created tfhashref($seq) with dist=$dist\n";
      }
    }
  }
  close(CTFILE);
}



# extract all TF sequences from the decoded genes - these will be used
# to determine what TFs bind on regulatory regions.
# decodedgenes entries are semicolon delimited lists of products in form:
# 	offset "," length "," encoded-resource
# return a hash in format: format: key = seq, value = a string of comma
# delimited dist-from-src (for morphogens spread is indicated by '?') some
# of which may be empty fields

sub extractTFsFromGenes {
  my (@decodedgenes) = @_;
  my ($gene,$off,$len,$offlenresattrset,$res,$attr,$dist,$seq);
  my %TFs=();

#print STDERR "entering extractTFsFromGenes()\n";
  foreach $gene (@decodedgenes) {
    # decoded genes are stored as a semicolon-delimited list of resources:
    foreach $offlenresattrset (split(/;/,$gene)) {
      # encoded resources are stored as:
      # 	offset,length,resource,attribute,setting1,setting2,..
      # we are looking for encoded "TF" resources, and in these setting1
      # is the dist-from-src, and setting2 is the sequence
      ($off,$len,$res,$attr,$dist,$seq)=split(/,/,$offlenresattrset);
#print STDERR "read $res,$attr from gene\n";
      # this will result in a hash of strings, each of which is can be split
      # but which will contain empty entries - those of locals for a start
      if ($res eq "TF" && $seq ne "") {	# ensure its a valid TF
        # get rid of the "-" for locals.. it was needed to ensure gene product
        # had no empty setting fields which would indicate unprocessed resource
        if ($dist eq "-") { $dist=""; }
	# for morphogens discard spread from source and replace with dist='?'
	# as all possible dist-from-source will need to be tested for binding
        if ($attr=~/^[Mm]/) { $dist="?"; }
#print STDERR "added TF from gene: (type=$attr,dist='$dist',seq=$seq)\n";
        if ($TFs{$seq}) {
	  $TFs{$seq} .= "," . $dist;
	} else {
	  $TFs{$seq} = $dist;
	}
      }
    }
  }

  %TFs;
}




# determine binding of TFs and resources to regulatory regions
# args:
# 	codefile  - the name of the genetic code file,
# 	TFhashref - a reference to a hash containing TFs in format:
# 		key = seq, value = a string of comma delimited dist-from-src
# 		(some of these distances may be empty)
#	%regulators - hash containing the binding sites of each gene's
#		regulator (one of enhancer or repressor), with the key being
#	  	gene-index-in-@geneinfo, and value is string containing a
#	  	list of binding sites and their locations on the regulator,
#	  	in the form of: bindsite1 ";" bindsite2 ";" ...
#	        with each bindsite in the form: offset "," length "," sequence
#
# returns:
# 	a hash whose key is the gene-idx "." regulator-site-num (1..N)
# 	and values are strings in the format:
# 		offset-elem1,len-elem1,details-elem1;offset-elem2, ...
# 	with details in form:
# 	    dist,bindsequence or resource,attribute,setting1,setting2,...
# 	noting that dist may be an empty field

sub bindMatch {
  my ($codefile,$TFhashref,%regulators) = @_;
  my ($tf,$dist,$i,$k,$tftype);
  my ($oldbindnum,$bindnum,$offset,$len,$tfdets,$loc);
  my (@sitelems,@sites,@bs);
  my %bindingelems;

#print STDERR "entering bindMatch ...\n";
#printA("regulators",%regulators);
  # write TFs into a file to pass to bindmatch
  open(TFILE,">$TMPFILE1") || die ("Unable to create '$TMPFILE1'\n");
  # get each binding sequence
  foreach $tf (keys %{$TFhashref}) {
#print STDERR "tf=$tf, extracting dist's from '$TFhashref->{$tf}'\n";

    # TF may have no distance entries, in which case the 
    # "foreach $dist split(/,/, .." code block will never be entered
    # so deal with special case here
    if ($TFhashref->{$tf} eq "") {
      print TFILE "Local,,$tf\n";
#print STDERR "output to bindmatch tfs-file type=Local,dist=,tf=$tf\n";
    } else {
      # each TF may have several comma-delimited dist-from-src entries
      foreach $dist (split(/,/,$TFhashref->{$tf})) {
        # we haven't stored TF's type in hash (local/morphogen/cytoplasm),
	# but we have ensured that local's have an empty dist field, and
	# morphogens at their source are equivalent to local TFs, we treat
	# all TFs with empty dist fields as local. for the others, if their
	# dist field is '?' then they are morphogens, otherwise cytoplasm
	if ($dist eq "") {
	  $tftype="Local";
	} elsif ($dist eq "?") {
	  $tftype="Morphogen";
	} else {
	  $tftype="Cytoplasm";
	}
        print TFILE "$tftype,$dist,$tf\n";
#print STDERR "output to bindmatch tfs-file type=$tftype,dist=$dist,tf=$tf\n";
      }
    }
  }
  close(TFILE);

  # get info on binding of Resources and TFs on the binding sites of
  # regulator sequences
  open(BINDS, "| perl $BINDMATCH $codefile $TMPFILE1 > $TMPFILE2") || die("Unable to do matching of elements to binding regions! Exiting..\n");

#  open(BINDS, "| perl $BINDMATCH $codefile $TMPFILE1 > $TMPFILE2 $STDERR_REDIRECT") || die("Unable to do matching of elements to binding regions! Exiting..\n");

  # for each regulator, iterate through each of its bindsites
  # and find which elements bind on them. note that key is the gene-index
  foreach $k (sort keys %regulators) {
    # regulators are in format: bindsite1 ";" bindsite2 ";" ...
    @sites=split(";",$regulators{$k});
#printA("sites",@sites);
    for ($i=0; $i<$#sites+1;++$i) {
      # bindsites are in format: offset "," length "," sequence
      ($offset,$len,$seq)=split(",",$sites[$i]);
      print BINDS "$seq\n";
#print STDERR "btype=$btype,regulator=$k;sitenum=$i,seq=$seq\n";
      # store who the owner (gene , site) of the Nth site is
      push(@bs,join(".",$k,$i+1));
    }
  }
  close(BINDS);

  # cleanup temporary TF file
  unlink($TMPFILE1) || die ("Unable to remove temp TFs file '$TMPFILE1'\n");

  # results are returned as lines of:
  # 	bindsite-number ':' (spaces)
  # 	offset-in-site (spaces) length of binding match ':' (spaces)
  # 	    TF-dist-from-source (binary) ',' TF-binding region (base 4)
  # 	or
  #         binding resource, attribute, [ setting1, [ setting2, ... ] ]
  # note that TF-dist-from-source field will be empty for TFs at their source

#print STDERR "reading bindmatch results ..\n";
  $oldbindnum=1;
  open(BINDS,"<$TMPFILE2") || die ("Unable to read binding results file '$TMPFILE2'\n");
  while(<BINDS>) {
    chop;
    s/^\s+//;						# strip leading spaces
    ($bindnum,$loc,$tfdets)=split(/\s*:\s*/);		# split & strip spaces
    ($offset,$len)=split(/\s+/,$loc);
#print STDERR "bindnum=$bindnum,offset=$offset,len=$len,tfdets=$tfdets\n";
    # if new bindsite then store @sitelems in %bindingelems in format:
    #	offset-elem1,len-elem1,details-elem1;offset-elem2, ...
    # key is gene-idx "." regulator-site-num (1..N)
    if ($bindnum!=$oldbindnum) {
      $bindingelems{$bs[$oldbindnum-1]}=join(";",@sitelems);
#printA("storing bindelems from sitelems",@sitelems);
      @sitelems=();
    }
    # store into @sitelems in format:
    # 	offset-in-bindsite "," length ',' bind-details
    push(@sitelems,join(",",$offset,$len,$tfdets));
    $oldbindnum=$bindnum;
  }
  close(BINDS);

  # add the binding elements from the end of the file
  if (@sitelems) {
    $bindingelems{$bs[$oldbindnum-1]}=join(";",@sitelems);
#printA("storing bindelems from sitelems",@sitelems);
    @sitelems=();
  }

  # cleanup TEMP results file
  unlink($TMPFILE2) || die ("Unable to remove temp file '$TMPFILE2'\n");

  %bindingelems;
}




# create hash of binding sites within regulators, to be used for binding
# with TFs and resources. key is gene-index-in-@geneinfo, and the value is
# a string containing a list of binding sites and their locations on the
# regulator, in the form of: bindsite1 ";" bindsite2 ";" ...
# with each bindsite in the form: offset "," length "," sequence

sub getBindingSites {
  my ($regtype,$bound,$codefile,$chrominforef,$geneinforef) = @_;
  my ($oldregionum,$regnum,$loc,$cinfoidx,$ginfoidx);
  my @regidxes;
  my @regbindsites;
  my %bindingsites;

#print STDERR "entering getBindingSites() ...\n";
  # execute the script that finds the binding sites on regulators
  @regidxes=findBindingSites($regtype,$bound,$codefile,$chrominforef,$geneinforef);
#printA("regidxes ($regtype)",@regidxes);

  # now read the binding sites results in from the temp files, stored as:
  # (spaces) region # ':' (spaces) start (spaces) length ':' (spaces) sequence

  open(TFILE,"<$TMPFILE") || die ("Unable to read binding sites from $TMPFILE\n");
  $oldregnum=1;
  while(<TFILE>) {
#print STDERR;
    chop;
    s/^\s+//;						# strip leading spaces
    next if ($_ eq "");					# skip if empty
    ($regnum,$loc,$seq)=split(/\s*:\s*/);		# split & strip spaces
    ($offset,$len)=split(/\s+/,$loc);
#print STDERR "(regnum=$regnum,offset=$offset,len=$len,seq=$seq)\n";
    # note that regnum will correspond to the Nth enh/rep sequence in
    # @geneinfo and regidxes[N-1], so can get chrominfo & owning gene index
    ($cinfoidx,$ginfoidx)=split(/,/,$regidxes[$regnum-1]);
#print STDERR "cinfoidx=$cinfoidx, ginfoidx=$ginfoidx; regnum=$regnum, oldregnum=$oldregnum\n";
    # if new regulator then store @regbindsites in %bindingsites
    if ($regnum!=$oldregnum) {
#print STDERR "storing regbindsites into bindingsites\n";
      $bindingsites{$oldginfoidx} = join(";",@regbindsites);
      @regbindsites=();
    }
    # now store into @regbindsites in format:
    # 	offset-in-regulator "," length ',' sequence
    push(@regbindsites,join(",",$offset,$len,$seq));
    $oldginfoidx=$ginfoidx;
  }
  close(TFILE);

  # add the binding sites from the end of the file
  if (@regbindsites)  { $bindingsites{$oldginfoidx}=join(";",@regbindsites); }

  # cleanup TEMP file
  unlink($TMPFILE) || die ("Unable to remove temp file '$TMPFILE'\n");

#printA("bindingsites ($regtype)",%bindingsites);
  %bindingsites;

}


# execute the script that locates the binding sites
# return an array of the regulator's index in chrominfo "," index in geneinfo
# such that the Nth line in the results file corresponds to regidxes[N-1]
# the binding sites results are stored in $TMPFILE as:
# (spaces) region # ':' (spaces) start (spaces) length ':' (spaces) sequence

sub findBindingSites {
  my ($regtype,$bound,$codefile,$chrominforef,$geneinforef) = @_;
  my ($i,$gi,$pi,$ei,$ri,$feat,$start,$len,$seq);
  my @regidxes;
  
#print STDERR "entering findBindingSites() ...\n";
  # this script finds the binding sites on supplied enhancer/repressor sequences
#print STDERR "perl $FINDBINDINGSITES -f $codefile $bound > $TMPFILE $STDERR_REDIRECT\n";
  open(BINDSITES, "| perl $FINDBINDINGSITES -f $codefile $bound > $TMPFILE $STDERR_REDIRECT") || die("Unable to find regulator bind site details on chromosome! Exiting..\n");

  # scan through all the geneinfo entries to extract regulatory regions
  for ($i=0;$i<$#{$geneinforef}+1;$i++) {
    # extract all the info for this gene, in the form of indexes in @chrominfo
    ($gi,$pi,$ei,$ri)=split(/,/,$geneinforef->[$i]);

    # if there's an enhancer (and we're looking for enhancers)
    if ($ei>-1 && $regtype eq "enh") {
      # get its info out of @chrominfo
      ($feat,$start,$len,$seq)=split(/,/,$chrominforef->[$ei]);
      # and output for decoding
      print BINDSITES "$seq\n";
#print STDERR "findbindingsites for ei=$ei,i=$i;seq=$seq\n";
      # store enhancer's index in chrominfo "," index in geneinfo
      push(@regidxes,"$ei,$i");
    }

    # if there's a repressor (and we're looking for repressors)
    if ($ri>-1 && $regtype eq "rep") {
      # get its info out of @chrominfo
      ($feat,$start,$len,$seq)=split(/,/,$chrominforef->[$ri]);
      # and output for decoding
      print BINDSITES "$seq\n";
#print STDERR "findbindingsites for ri=$ri,i=$i;seq=$seq\n";
      # store repressor's index in chrominfo "," index in geneinfo
      push(@regidxes,"$ri,$i");
    }

  }
  close(BINDSITES);

  # TEMP file is not cleaned up here - it's passed used in the calling fn

  @regidxes;
}


# decode all the gene coding regions on the chromosome, using the genetic
# code supplied in codefile. @geneinfo specifies the coding regions as
# pointers into @chrominfo which stores the chromosome sequences and other
# details
# returns: an array of decoded genes, whose index is that of geneinfo's,
# containing a string of the list of decoded resources and their locations on
# coding region, in the form of: decodedres1 ";" decodedres2 ";" ...
# each decodesres is in form: offset "," length "," encoded-resource

sub decodeGenes {
  my ($geneinforef,$chrominforef,$codefile) = @_;
  my ($i,$gi,$pi,$ei,$ri);
  my ($feat,$start,$len,$seq);
  my ($genenum,$loc,$decoded,$offset,$length);
  my @decodedgn;
  my @decodedgenes;

  # first we need to decode all the genes in geneinfo

#print STDERR "entering decodeGenes() ...\n";
  open(DECGENES,"| perl $DECODEGENES -f $codefile > $TMPFILE $STDERR_REDIRECT") || die("Unable to decode genes! Exiting..\n");
  # scan through all the geneinfo entries
  for ($i=0;$i<$#{$geneinforef}+1;$i++) {
    # extract all the info for this gene, in the form of indexes in @chrominfo
    ($gi,$pi,$ei,$ri)=split(/,/,$geneinforef->[$i]);
    # and now get the gene's info out of @chrominfo
    ($feat,$start,$len,$seq)=split(/,/,$chrominforef->[$gi]);
    # and output for decoding
    print DECGENES "$seq\n";
#print STDERR "i=$i\:seq=$seq\n";
  }
  close(DECGENES);

  # decoded genes are in file with format:
  # (spaces) genenum ':' (spaces) offset (spaces) length ': ' res-attr-set 

  # now, read them into @decodegenes
  open(TFILE,"<$TMPFILE") || die ("Unable to read decoded $genes from $TMPFILE\n");
  $oldgenenum=1;
  while(<TFILE>) {
    chop;
    s/^\s+//;						# strip leading spaces
    ($genenum,$loc,$decoded)=split(/\s*:\s*/);		# split & strip spaces
    ($offset,$length)=split(/\s+/,$loc);
    if ($genenum!=$oldgenenum) {
      # add decoded gene to %decodedgenes, noting that oldgenenum will
      # correspond to the Nth geneseq in genesfile and to geneinfo[N-1]
      push(@decodedgenes,join(";",@decodedgn));
      @decodedgn=();
      $oldgenenum=$genenum;
    }
#print STDERR "genenum=$genenum;loc=$loc;offset=$offset;length=$length;decoded=$decoded\n";
    push(@decodedgn,join(",",$offset,$length,$decoded));
  }
  close(TFILE);

  # add the decoded genes from the end of the file
  if (@decodedgn)  { push(@decodedgenes,join(";",@decodedgn)); }

  # cleanup TEMP file
  unlink($TMPFILE) || die ("Unable to remove temp file '$TMPFILE'\n");

  @decodedgenes;

}



# create an array of genes associated with their promoters and
# regulatory regions. as strings in the comma-separated format:
# gene, promoter, enhancer, repressor; where each is an index into the
# @chrominfo array, or -1 for no entry
#
# note that promoters that are too far upstream from their associated gene
# cause the promoter and regulatory regions to be invalidated, the gene
# becomes an unregulated gene
# 
# the FSM is as follows:
# 	junk: can only occur between genes without promoters,
# 	      or as the initial or final intergenic area
# 		Action -> reset state
# 	promoter:
# 	    - if preceded by junk,
# 	        Action -> ignore
# 	    - else, can assume that a repressor and gene, or repressor
# 	      will follow
# 	      	Action -> set P = current promoter
# 	enhancer: implies that a promoter follows
# 	      	Action -> set E = current enhancer
# 	repressor: implies that a gene follows
# 	      	Action -> set R = current repressor
# 	gene:
# 		Action -> associate gene with E,P,R which indicate the
# 			  enhancer,promoter, and repressor associated
# 			  with this gene

sub assocByGene {
  my ($pbound,@chrominfo) = @_;
  my ($i,$g,$p,$e,$r,$j);
  my ($feat,$loc,$len,$seq,$chromentry,$ploc);
  my @geneinfo;

#print STDERR "entering assocByGene() ...\n";
#printA("chrominfo",@chrominfo);

  $g=$p=$e=$r=$j=$ploc=-1;
  for ($i=0; $i<$#chrominfo+1; $i++) {
    ($feat,$loc,$len,$seq)=split(/,/,$chrominfo[$i]);
#print STDERR "($feat,$loc,$len,$seq)\n";
    if ($feat eq "junk") {
      $g=$p=$e=$r=-1; $j=$i;				# reset FSM
    }
    elsif ($feat eq "promoter") {
      if ($j<0) { $p=$i; $ploc=$loc; }			# if no preceding junk
    }
    elsif ($feat eq "enhancer") {
      $j=-1;
      $e=$i;
    }
    elsif ($feat eq "repressor") {
      $j=-1;
      $r=$i;
    }
    elsif ($feat eq "gene") {
      $g=$i;						# for clarity
      # if promoter is too far upstream from its gene then ignore it
      if ($p>-1 && $pbound ne "" && $loc-$ploc>$pbound) { $p=$e=$r=-1; }
      push(@geneinfo,join(",",$g,$p,$e,$r));		# store
      $g=$p=$e=$r=$j=$ploc=-1;				# and reset
    }
    else {
      die("Unknown chromosome feature type ($feat).. exiting!\n");
    }
#print STDERR "$i>g=$g,p=$p,e=$e,r=$r,j=$j\n";
  }

#printA("geneinfo",@geneinfo);
  @geneinfo;

}



# extract the parsed info for this chromosome, and store in an array of
# strings in the comma-delimited format:
# 	region-type, start-loc-on-chrom, length, sequence
# region-type is one of: junk, promoter, enhancer, repressor, gene

sub getParsedChromInfo {
  my ($parsedchromfile,$chromnum) = @_;
  my ($cnum,$type,$start,$seq);
  my @chrominfo;

#print STDERR "entering getParsedChromInfo() ...\n";
#print STDERR "$CAT $parsedchromfile | perl $EXTRACTCHROMFEAT -f $chromnum ? > $TMPFILE $STDERR_REDIRECT\n";
  system("$CAT $parsedchromfile | perl $EXTRACTCHROMFEAT -f $chromnum ? > $TMPFILE $STDERR_REDIRECT") && die("Unable to extract genes from chromosome $chromnum! Exiting..\n");

  open(TFILE,"<$TMPFILE") || die ("Unable to read parsed chomosome $chromnum from $TMPFILE\n");
  while(<TFILE>) {
    chop;
    ($cnum,$type,$start,$seq)=split(/\s+/);
#print STDERR "$type,$start,$seq\n";
    push(@chrominfo,join(",",$type,$start,length($seq),$seq));
  }
  close(TFILE);

  # remove temp file
  unlink($TMPFILE) || die ("Unable to remove temp file '$TMPFILE'\n");

  @chrominfo;
}



sub storeParseChromsFromStdin {
  my ($chromfile) = @_;
  my $chromno;

#print STDERR "entering storeParseChromsFromStdin() ...\n";
  if ($chromfile eq "") {
    die("Invalid filename for storing parsed chromosomes\n");
  }
  # store chromosomes in file for passing to chromosome parser
  $chromno=0;
  open(CFILE,">$TMPFILE") || die ("Unable to create '$TMPFILE'\n");
  while (<STDIN>) { chop; if ($_) { ++$chromno; print CFILE "$_\n"; } }
  close(CFILE);

#print STDERR "stored chroms from stdin to '$TMPFILE' ...\n";
#print STDERR "$CAT $TMPFILE | perl $PARSECHROM -f $promoters $startcodons $stopcodons > $chromfile $STDERR_REDIRECT\n";

  # parse chromosomes to create a list (file) in the space-separated format:
  # chrom #, region type, region start index, region sequence
  system("$CAT $TMPFILE | perl $PARSECHROM -f $promoters $startcodons $stopcodons > $chromfile $STDERR_REDIRECT") && die ("Unable to parse chromosome(s)! Exiting..\n");
#print STDERR "parsed chroms from '$TMPFILE' into '$chromfile' ...\n";
  # remove temp file containing chromosomes - don't need it any more, we use
  # the parsed chromosome(s) from here on
  unlink($TMPFILE) || die ("Unable to remove temp chromosome file '$TMPFILE'\n");

#print STDERR "removed '$TMPFILE'...\n";
  $chromno;
}



# get the commandline arguments
# synopsis: [perl] decodechrom.pl code-file [cyto-file] [p:promoter-list] [s:start-list] [e:stop-list] [pb=bound] [eb=bound] [rb=bound] 

sub getArgs {

  my ($notfbind,$codefile,$cytofile,$promoters,$startcodons,$stopcodons,$pbound,$ebound,$rbound);
  my $argc;

  if ($ARGV[0] eq "-notfbind") { 
    $notfbind="true"; shift @ARGV;
  } else {
    $notfbind="false";
  }

  $argc=$#ARGV+1;
  $codefile=$cytofile=$promoters=$startcodons=$stopcodons=$pbound=$ebound=$rbound="";

  # get the genetic code specification file
  if ($argc>0 && !($ARGV[0]=~/^-/)) {
    $codefile=$ARGV[0];
    shift @ARGV; $argc--;
  } else {
    printUsage();
    exit(1);
  }

  # check if there's a cytoplasmic determinants file
  if ($argc>0 && !($ARGV[0]=~/^-/) && !($ARGV[0]=~/^[pse]:/)
                                   && !($ARGV[0]=~/^[per]b=/)) {
    $cytofile=$ARGV[0];
    shift @ARGV; $argc--;
  }

  while ($argc>0) {
    if (($ARGV[0]=~/^p:/) && $promoters eq "") { $promoters=$ARGV[0]; }
    elsif (($ARGV[0]=~/^s:/) && $startcodons eq "") { $startcodons=$ARGV[0]; }
    elsif (($ARGV[0]=~/^e:/) && $stopcodons eq "") { $stopcodons=$ARGV[0]; }
    elsif (($ARGV[0]=~/^pb=([0-9]+)/) && $pbound eq "") { $pbound=$1; }
    elsif (($ARGV[0]=~/^eb=([0-9]+)/) && $ebound eq "") { $ebound="-$1"; }
    elsif (($ARGV[0]=~/^rb=([0-9]+)/) && $rbound eq "") { $rbound="+$1"; }
    else { printUsage(); exit(1); }
    shift @ARGV; $argc--;
  }

  ($notfbind,$codefile,$cytofile,$promoters,$startcodons,$stopcodons,$pbound,$ebound,$rbound);
}



sub printA {                            # for debugging to print arrays
  my ($str,@a) = @_;
  my $i;

  print STDERR "$str: ";
  for ($i=0; $i<$#a+1; ++$i) {
    print STDERR "($a[$i])";
  }
  print STDERR "\n";
}



sub printUsage {

  print STDERR <<EOF;
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
  			"TF,TF", dist, bindseq
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
EOF

}



