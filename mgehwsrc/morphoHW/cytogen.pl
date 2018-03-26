
# cytogen.pl by J.A. Lee, January 2004
#
#  Synopsis:
#  [perl] cytogen.pl [-v] [-gran R,C] TF-sequence maxrow,maxcol srclist
#  			row-spread col-spread [ row-diffusion col-diffusion ]
# 
#  Args:
#  		-v		- output a visual representation of spread
#  		-gran R,C	- each coord contains RxC (rows,cols) cells
#  				  with copies of same cytogens. output coords
#  				  and matrix bounds will be scaled accordingly
#  		TF-sequence	- a sequence of chars in the alphabet [0-3]
#  		maxrow,maxcol	- gives the boundaries of the (unscaled) cell
#  				  matrix
#  		srclist		- the source locations of cytoplasm/morphogens
#  				  a semicolon-delimited list of row,col pairs
#  				  or single range: minrow-maxrow,mincol-maxcol
#  		row-spread	- extent of spread from source in rows
#  		col-spread	- extent of spread from source in cols
#  	if the following are present, indicates generate cytoplasms, otherwise
#  	morphogens are generated:
#  		row-diffusion	- # rows traversed to increase distance metric
#  		col-diffusion	- # cols traversed to increase distance metric
#
#  Inputs: (none)
#
#  Outputs: (stdout)
#		  output is list of cell locations having cytoplasmic
#		  determinants or morphogens, in lines of:
#				cellrow, cellcol: details
#		  	with details in the form of:
#				TF-type,distfromsrc,bind-seq
#		  	and TF type is "Morphogen" or "Cytoplasm"
#		 Note that if R,C granularity is specified, then output coords
#		 will be at cell granularity, with cell ranges from 0,0 to
#		 (maxrow+1 X R)-1, (maxcol+1 X C)-1
#
#  Description:
#		cytogen.pl generates cytoplasmic determinants/morphogen TFs
#		that spread out from their source. This is used to seed and
#		guide the morphogenesis processes.
#
#		Note that cytoplasms are generated in a rectangular spread,
#		with axis asymmetry encoded in the distfromsrc field of TFs,
#		which have 'eternal' lifespan. The asymmetry is encoded by
#		concatenating the same number of '1's as the Y axis (rows)
#		distance from source with the same number of '0's as the X
#		axis (cols) from source.
#	
#		Morphogen TFs are produced in the same manner as for those
#		that are produced by genes, with spread being done by
#		following the axis out to the extent indicated by the row/col
#		spread, then on the cells of the quadrants between axis, the
#		distance metric is filled up to the lowest distance specified
#		by the two. Morphogen's distfromsrc is a random generated
#		binary number with as many digits as the manhattan distance
#		from the source.
#
#  See Also:



($visual,$rgran,$cgran,$MAXROW,$MAXCOL,$tftype,$seq,$src,$rspread,$cspread,$rdiff,$cdiff)=scanArgs();

if (index($src,"-")>-1) { # its a range
  ($min,$max)=split(/-/,$src);
  ($minr,$minc)=split(/,/,$min);
  ($maxr,$maxc)=split(/,/,$max);
  if ($minr eq "" || $minc eq "" || $maxr eq "" || $maxc eq "") {
    die("Invalid source range argument ($src). Must be in format: minr,minc-maxr,maxc");
  }
  # generate all the coords within this range
  for ($i=$minr; $i<=$maxr; $i++) {
    for ($j=$minc; $j<=$maxc; $j++) {
      push(@srclist,"$i,$j");
    }
  }
} else { # its a list of semicolon-delimited coords
  @srclist=split(/;/,$src);
}

@srclist=uniq(@srclist);
#printA("uniq srclist",@SRCLIST);

# get the spread of cells from each cell in the source list
foreach $cell (@srclist) {
  ($row,$col)=split(/,/,$cell);
  # returns a list of cells, each in row,col:distfromsrc format
  # where dist from src is "" for cytoplasm types
  push(@cells,genCytoList($row,$col,$rspread,$cspread,$rdiff,$cdiff));
#printA("cells",@cells);
}

# get rid of duplicates
@cells=uniq(@cells);

#printA("uniq cells",@cells);

if ($rgran>1 || $cgran>1) {
  # update coords to be at cell granularity,
  # and create rgran X cgran copies of each cell
  @srclist=granulateCells($rgran,$cgran,@srclist);
  @cells=granulateCells($rgran,$cgran,@cells);
  # also need to update the MAXROW & MAXCOL to be at cell granularity
  $MAXROW=(($MAXROW+1)*$rgran)-1;
  $MAXCOL=(($MAXCOL+1)*$cgran)-1;
#printA("granulated cells",@cells);
}

# now output the results
if ($visual) {
  printCellMatrix($tftype,\@srclist,@cells);
} else {
  #sort cells by row and column
  @cells=sortCells(@cells);
  printCellList(@cells);
}





#################  end main()   ####################


# update coords to be at cell granularity, and create rgran X cgran copies
# of each cell.
# takes a list of cells, each in row,col:distfromsrc format
sub granulateCells {
  my ($rgran,$cgran,@cells) = @_;
  my ($cell,$coord,$details,$r,$c,$nucell);
  my @gcells=();

  foreach $cell (@cells) {
    ($coord,$details)=split(/\s*:\s*/,$cell);
    ($row,$col)=split(/\s*,\s*/,$coord);
    for ($r=0; $r<$rgran; $r++) {
      for ($c=0; $c<$cgran; $c++) {
	$nucell=(($row*$rgran)+$r) . "," . (($col*$cgran)+$c) . ":" . $details;
	push(@gcells,$nucell);
      }
    }
  }

  @gcells;
}


# removes duplicates from a list, returning a list of unique elements
sub uniq {
  my (@maybedupslist) = @_;
  my %hashlist;
  my @uniqlist;

  # make list elements unique by converting to a hash
  @hashlist{@maybedupslist} = ();
  # convert back to an array
  @uniqlist = keys %hashlist;

  @uniqlist;
}



# sort cells according to row, then column
sub sortCells {
  my (@cells) = @_;
  my (@rows,@cols);
  my @sortedcells;

  @rows=@cols=();
  for (@cells) { push @rows, /^(\d+)/; push @cols, /,(\d+)/ }
  @sortedcells = @cells[ sort {
    			$rows[$a] <=> $rows[$b]
                               	 ||
                       	$cols[$a] <=> $cols[$b]
      			} 0..$#cells ];
  @sortedcells;
}



# spread out from cell and if a morphogen (indicated by non empty rdiff/cdiff)
# then calculate distance metric
# returns a list of cells in format: row,col:distfromsrc ("" if not morphogen)

sub genCytoList {
  my ($row,$col,$rspread,$cspread,$rdiff,$cdiff) = @_;
  my ($morpho,$cell,$r,$c,$dist,@cells);
  my @cytolist=();

#print STDERR "rdiff=$rdiff, cdiff=$cdiff\n";
  $morpho=($rdiff eq "" || $cdiff eq "") ? 1 : 0;
  
  @cells=($morpho==1) ? getMorphogenSpread($row,$col,$rspread,$cspread) :
                        getCytoplasmSpread($row,$col,$rspread,$cspread);
  $dist="";

  # for each cell calulate its distance from source (or empty if cytoplasm)
  foreach $cell (@cells) {
    ($r,$c)=split(/,/,$cell);
    if ($morpho==1) {
      # provides TFs with distfromsrc same as those genetically produced
      $dist=calcDistFromSrc($row,$col,$cell);
    } else {
      # provides TFs that will have some axis information in the dist field
      $dist=calcAxisAsymDistFromSrc($row,$col,$cell,$rdiff,$cdiff);
    }
    push(@cytolist,"$r,$c" . ":" . $dist);
  }
 
  @cytolist;
}


# cytoplasm spread is rectangular

sub getCytoplasmSpread {
  my ($row,$col,$rspread,$cspread) = @_;
  my ($x,$y,$rown,$rowp,$coln,$colp);
  my (@cells);

#print STDERR "row=$row, col=$col, rspread=$rspread, cspread=$cspread\n";
  @cells=();
  for ($x=0;$x<=$cspread;$x++) {
#print STDERR "x=$x: ";
    $colp=$col+$x;
    $coln=$col-$x;
    for ($y=0;$y<=$rspread;$y++) {
#print STDERR " y=$y ";
      $rowp=$row+$y;
      $rown=$row-$y;
#print STDERR "(rneg=$rown/rpos=$rowp,cneg=$coln/cpos=$colp)";
      # check that cells are within cell area range, and add to list if so
      if (validCoord($rown,$coln)) { push (@cells,"$rown,$coln"); }
      if (validCoord($rown,$colp)) { push (@cells,"$rown,$colp"); }
      if (validCoord($rowp,$coln)) { push (@cells,"$rowp,$coln"); }
      if (validCoord($rowp,$colp)) { push (@cells,"$rowp,$colp"); }
    }
#print STDERR "\n";
  }
#print STDERR "got spread\n";

  @cells;
}


# placement is done by following the axis out to the extent indicated by
# the row/col spread. then on the cells of the qudrants between axis, the
# distance metric is filled up to the lowest distance specified by the two,
# for example, with rspread of 2, and cspread of 3:
#
# 7 . . . . . . . . . .
# 6 . . . . . . . . . .
# 5 . . . . . 2 . . . .
# 4 . . . . 2 1 2 . . .
# 3 . 4 3 2 1 X 1 2 3 4
# 2 . . . . 2 1 2 . . .
# 1 . . . . . 2 . . . .
# 0 . . . . . . . . . .
#   0 1 2 3 4 5 6 7 8 9

sub getMorphogenSpread {
  my ($row,$col,$rspread,$cspread) = @_;
  my ($x,$y,$xoff,$yoff,$rown,$rowp,$coln,$colp);
  my ($quad1mn,$quad2mn,$quad3mn,$quad4mn);
  my (@cells);

  @cells=();

  # add cells along row & col axis (so long as within valid cell range)

  for ($y=0;$y<=$rspread;$y++) {
    $rowp=$row+$y;
    $rown=$row-$y;
    if (validCoord($rowp,$col)) { push (@cells,"$rowp,$col"); }
    if (validCoord($rown,$col)) { push (@cells,"$rown,$col"); }
  }

  for ($x=0;$x<=$cspread;$x++) {
    $colp=$col+$x;
    $coln=$col-$x;
    if (validCoord($row,$coln)) { push (@cells,"$row,$coln"); }
    if (validCoord($row,$colp)) { push (@cells,"$row,$colp"); }
  }

  # now do the quadrants ..

  $quad1mn=min($rspread,$cspread);
  $quad2mn=min($rspread,$cspread);
  $quad3mn=min($rspread,$cspread);
  $quad4mn=min($rspread,$cspread);

  # quad1: row-1,col-1 down to manhattan dist quad1mn
  for ($yoff=1;$yoff<=$quad1mn;$yoff++) {
    $rown=$row-$yoff;
    # decrease spread on 2nd axis as move further from center on 1st axis 
    for ($xoff=1;$xoff<=$quad1mn-$yoff;$xoff++) {
      $coln=$col-$xoff;
      if (validCoord($rown,$coln)) { push (@cells,"$rown,$coln"); }
    }
  }
  # quad2: row+1,col-1 up/down to manhattan dist quad2mn
  for ($yoff=1;$yoff<=$quad2mn;$yoff++) {
    $rowp=$row+$yoff;
    # decrease spread on 2nd axis as move further from center on 1st axis 
    for ($xoff=1;$xoff<=$quad2mn-$yoff;$xoff++) {
      $coln=$col-$xoff;
      if (validCoord($rowp,$coln)) { push (@cells,"$rowp,$coln"); }
    }
  }
  # quad3: row-1,col+1 down/up to manhattan dist quad3mn
  for ($yoff=1;$yoff<=$quad3mn;$yoff++) {
    $rown=$row-$yoff;
    # decrease spread on 2nd axis as move further from center on 1st axis 
    for ($xoff=1;$xoff<=$quad3mn-$yoff;$xoff++) {
      $colp=$col+$xoff;
      if (validCoord($rown,$colp)) { push (@cells,"$rown,$colp"); }
    }
  }
  # quad4: row+1,col+1 up to manhattan dist quad4mn
  for ($yoff=1;$yoff<=$quad4mn;$yoff++) {
    $rowp=$row+$yoff;
    # decrease spread on 2nd axis as move further from center on 1st axis 
    for ($xoff=1;$xoff<=$quad4mn-$yoff;$xoff++) {
      $colp=$col+$xoff;
      if (validCoord($rowp,$colp)) { push (@cells,"$rowp,$colp"); }
    }
  }

  @cells;
}


# check if cell is within the valid cell area range
sub validCoord {
  my ($row,$col) = @_;
  return ($row>=0 && $row<=$MAXROW && $col>=0 && $col<=$MAXCOL) ? 1 : 0;
}


sub min {
  my ($a,$b) = @_;
  ($a<$b) ? $a : $b;
}


# calculate the distance of a morphogen from its source in Y and X axis
# add the result together to give the manhattan distance from source,
# denoted Dsm. then generate a random number from 0 .. 2^Dsm -1, and
# convert to binary to give the TF dist-from-src

sub calcDistFromSrc {
  my ($row,$col,$morph) = @_;
  my ($ydist,$xdist,$Dsm,$x,$y,$r);
  my $dist;

  ($y,$x)=split(/,/,$morph);
  #$Dsm=int(abs($row-$y)/$rdiff)+int(abs($col-$x)/$cdiff);# depreceated
  $Dsm=abs($row-$y)+abs($col-$x);
  $r=int(rand(2**$Dsm));
  $dist=toBinary($r,$Dsm); # keep all 'Dsm' binary digits, ie leading zeros
}



# calculate the distance of a cytogen from its source in Y and X axis
# and divide this by the difusion metric, and finally form a TF dist-from-src
# field by concatenating the row dist-from-src with the col dist-from-src
# this will provide some axis asymetry info

sub calcAxisAsymDistFromSrc {
  my ($row,$col,$cyto,$rdiff,$cdiff) = @_;
  my ($ydist,$xdist,$ycnt,$xcnt,$x,$y);
  my $dist;

  ($y,$x)=split(/,/,$cyto);
  $ydist=abs($row-$y);
  $xdist=abs($col-$x);

  $ycnt=int($ydist/$rdiff);
  $xcnt=int($xdist/$cdiff);

  $dist=("1" x $ycnt) . ("0" x $xcnt);
}



sub toBinary {
  my ($x,$ndigits) = @_;
  my $b;

#print STDERR "x=$x, ndigits=$ndigits; ";
  while ($x>0) {
    $b = ($x % 2) . $b;
    $x = int($x / 2);		# equiv to $x = ($x - ($x % 2)) / 2
  }

  if (length($b)<$ndigits) {
    $b = ("0" x ($ndigits-length($b))) . $b; # prepend leading zeros
  }
#print STDERR "b=$b\n";
  
  $b;
}



# returns a decimal number from the binary parameter
sub toDecimal {
  my ($b) = @_;
  my $pwr;
  my $d;

  $pwr=1;
  while ($b ne "") {
    $d+=substr($b,-1,1) * $pwr;
    $pwr*=2;
    chop($b);
  }

  $d;
}



sub printCellList {
  my (@cells)=@_;

  foreach $cell (@cells) {
    ($coord,$dist)=split(/:/,$cell);
    print $coord . ":TF," . $tftype . "," . $dist . "," . $seq . "\n";
  }
}



sub printCellMatrix {
  my ($tftype,$srclistref,@cells)=@_;
  my ($row,$col,$cell,$coord,$dist,$rowdist,$coldist,$y,$line,$i);
  my (@matrix,$mrow);

  # create a 2D array, into which we put spaces for nothing there
  # X for morphogens or cytoplasms at source, for morphogens give
  # dist-from-source in decimal format, and for cytosplamic determinants
  # give dist-from-source in rowdist.coldist (decimal) format

  $mrow=" . " x ($MAXCOL+1);		# create a row of spaces
  for ($i=0;$i<=$MAXROW;$i++) {
    $matrix[$i]=$mrow;			# create row's of copies of above
  }

  # mark each morphogen/cytoplasm location
  # note that where there are overlaps of these, the latter occuring will
  # overwrite the previous
  foreach $cell (@cells) {
    ($coord,$dist)=split(/:/,$cell);
    ($row,$col)=split(/,/,$coord);
    if ($tftype eq "Cytoplasm") {
      $rowdist=($dist=~tr/[1]//); # count the number of row-distfromsrc markers
      $coldist=($dist=~tr/[0]//); # count the number of col-distfromsrc markers
      # insert the distance metric into the row's string
      substr($matrix[$row],$col*3,3)=sprintf("%*d-%*d",1,$rowdist,1,$coldist);
    } else {
      substr($matrix[$row],$col*3,3)=sprintf(" %02d",toDecimal($dist));
    }
  }

  # mark each source location (overwrites above)
  # note: @{$arrayref} dereferences array
  foreach $coord (@{$srclistref}) {
    ($row,$col)=split(/,/,$coord);
    substr($matrix[$row],$col*3,3)=" X ";
  }

  # print top boundary
  print "\n   -" . ("---" x ($MAXCOL+1)) . "-\n";
  # now print cell matrix with cell row 0 at bottom (JBits format)
  for ($i=$MAXROW;$i>=0;$i--) {
    $y=sprintf("%*d",2,$i);
    $line=join("",$matrix[$i]);
    print "$y |" . $line . "|\n";
  }

  # print bottom axis
  print "   -" . ("---" x ($MAXCOL+1)) . "-\n   ";
  for ($i=0;$i<=$MAXCOL;$i++) { printf(" %*d",2,$i); }
  print "\n";

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



#  arguments: [-d] TF-sequence maxrow,maxcol srclist
#  			row-spread col-spread [ row-diffusion col-diffusion ]

sub scanArgs {
  my ($argc);
  my ($visual,$rgran,$cgran,$maxrow,$maxcol,$type,$seq,$srclist,$rspread,$cspread,$rdiff,$cdiff);

  $argc=$#ARGV+1;
  $debug=0;
  $rgran=$cgran=1;

  while ($argc>0 && $ARGV[0]=~/^-[vg]/) {
    if ($ARGV[0] eq "-v") {
      $visual=1; shift @ARGV; $argc--;
    } elsif ($ARGV[0] eq "-gran") {
      if ($argc>1) {
	($rgran,$cgran)=split(/,/,$ARGV[1]);
      } else {
        $rgran=$cgran="";
      }
      if ($rgran eq "" || $cgran eq "" || $rgran<=0 || $cgran<=0) {
        print STDERR "invalid (empty or non-positive) rgran or cgran argument (".$ARGV[1].")!";
        printUsage();
        exit(1);
      }
      shift @ARGV; $argc--;
      shift @ARGV; $argc--;
    } else {
      print STDERR "unknown switch '". $ARGV[0] . "'.";
      printUsage();
      exit(1);
    }
  }

  unless (!($ARGV[0]=~/^-/) && ($argc==5 || $argc==7)) {
    printUsage();
    exit(1);
  }

  ($maxrow,$maxcol)=split(/,/,$ARGV[1]);
  if ($maxrow eq "" || $maxcol eq "" || $maxrow<0 || $maxcol<0) {
    print STDERR "invalid (empty or negative) maxrow or maxcol argument!";
    printUsage();
    exit(1);
  }

  if ($argc==7) {	# its a cytoplasm
    $rdiff=$ARGV[5];
    $cdiff=$ARGV[6];
    $type="Cytoplasm";
  } else {
    $type="Morphogen";
  }

  $seq=$ARGV[0];
  $srclist=$ARGV[2];
  $rspread=$ARGV[3];
  $cspread=$ARGV[4];

  ($visual,$rgran,$cgran,$maxrow,$maxcol,$type,$seq,$srclist,$rspread,$cspread,$rdiff,$cdiff);
}



sub printUsage {

  print STDERR <<EOF;
  Synopsis:
  [perl] cytogen.pl [-v] [-gran R,C] TF-sequence maxrow,maxcol srclist
  			row-spread col-spread [ row-diffusion col-diffusion ]
 
  Args:
  		-v		- output a visual representation of spread
  		-gran R,C	- each coord contains RxC (rows,cols) cells
  				  with copies of same cytogens. output coords
  				  and matrix bounds will be scaled accordingly
  		TF-sequence	- a sequence of chars in the alphabet [0-3]
  		maxrow,maxcol	- gives the boundaries of the (unscaled) cell
				  matrix
  		srclist		- the source locations of cytoplasm/morphogens
  				  a semicolon-delimited list of row,col pairs
  				  or single range: minrow-maxrow,mincol-maxcol
  		row-spread	- extent of spread from source in rows
  		col-spread	- extent of spread from source in cols
  	if the following are present, indicates generate cytoplasms, otherwise
  	morphogens are generated:
  		row-diffusion	- # rows traversed to increase distance metric
  		col-diffusion	- # cols traversed to increase distance metric

  Inputs: (none)

  Outputs: (stdout)
		  output is list of cell locations having cytoplasmic
		  determinants or morphogens, in lines of:
				cellrow, cellcol: details
		  	with details in the form of:
				TF-type,distfromsrc,bind-seq
		  	and TF type is "Morphogen" or "Cytoplasm"
		 Note that if R,C granularity is specified, then coords will
		 be at cell granularity, with cell ranges from 0,0 to
		 (maxrow+1 X R)-1, (maxcol+1 X C)-1

  Description:
 		cytogen.pl generates cytoplasmic determinants/morphogen TFs
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

EOF

}



