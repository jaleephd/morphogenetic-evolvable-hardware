# cleanupEHW.pl by J.A. Lee, Sept. 2004
#
#  Synopsis:
#  [perl] cleanupEHW.pl [-v] [numgens]
#
#  Args:
#  		-v		Verbose mode - generate info to STDERR
#  		numgens		the number of generations that evolution was
#  				specified to run for - so there should be a
#  				pop_NUMGENS_stats.txt and pop_NUMGENS.parm.txt
#				in the DATADIR specified in the EHW_config.txt
#  Inputs:
#  	   EHW_config.txt	holds the parameters used by the system
#				(needed if numgens param supplied)
#	   TMPpop$$$$_info.txt	checkpoint information file, '$$$$' is the PID
#
#  Outputs:
#  	   (STDERR)		  informative messages
#
#  Description:
#		performs a cleanup of temporary population and chromosome
#		files in the main directory, and if numgens is specified,
#		the chechkpoint population file is copied to the DATADIR
#		pop_NUMGENS.txt file, and the associated population stats
#		and parameter files are renamed. Note that temporary files
#		from earlier runs are not touched, and neither are any
#		temporary (decoded) chromosome files in the DATADIR.
#		NB: if numgens is not specified, the temp and checkpoint
#		files from the most recent run will just be deleted.


$VERBOSE=0;

if ($#ARGV>-1 && $ARGV[0] =~/^-/) {
  shift;
  $VERBOSE=1;
}

if ($#ARGV>-1 && $ARGV[0]=~/^-/) {
  printUsage();
  exit(1);
}

$NUMGENS=shift;


if ($NUMGENS ne "") {
  # read in EHW_config file - which should contain the DATADIR constant defn

  if ($VERBOSE) { print STDERR "reading in EHW configuration file... "; }
  open(CFILE,"EHW_config.txt") || die "Unable to read EHW configuration file";
  while(<CFILE>) {
    next if (/^\s*#/ || /^\s*$/);	# skip empty and comment lines
    next unless (/DATADIR/); # this is all we need!
    unless (/=/) { die("Invalid line in EHW configuration file: $_"); }
    chop;
    ($const,$DATADIR)=split(/=/);
    if ($VERBOSE) { print STDERR "DATADIR=$DATADIR .. "; }
    last; # that was all we needed
  }
  close(CFILE);
  if ($VERBOSE) { print STDERR "done\n\n"; }


  # move EHW_config.txt to DATADIR
  # remove old copy if one already there
  if (-e "$DATADIR/EHW_config.txt") {
    if ($VERBOSE) { print STDERR "removing old EHW configuration file..\n"; }
    unlink("$DATADIR/EHW_config.txt");
  }
  if ($VERBOSE) { print STDERR "moving EHW configuration file.. "; }
  rename("EHW_config.txt","$DATADIR/EHW_config.txt") || die("Unable to move EHW_config.txt to $DATADIR");
  if ($VERBOSE) { print STDERR "done\n\n"; }
}

# now look for checkpoint files, in case exited from evolution, before
# reaching the number of specified gens, due to success or stagnation, etc
# we only want the last one, if more than one, as this would be from the
# last evolutionary run, for which the param and stats files would have been
# generated

$omtime=0; # last modify time in seconds since the epoch - 0 is oldest possible
$checkpointfile="";

while ($chkfile=<TMPpop*_info.txt>) {
  if ($VERBOSE) { print STDERR "stat-ing $chkfile...\n"; }
  ($dev,$ino,$mode,$nlink,$uid,$gid,$rdev,$size,$atime,$mtime,$ctime,$blksize,$blocks)=stat($chkfile);
  if ($mtime>$omtime) { # this checkpointfile is newer
    if ($VERBOSE && $checkpointfile ne "") { print STDERR "$chkfile is most recent!\n"; }
    $omtime=$mtime;
    $checkpointfile=$chkfile;
  }
}


# evolution was halted, so we use the information in the checkpoint file
# to rename the checkpoint file to a resulting population file, and
# rename the parameter and pop stats files to match the number of generations
# actually completed
if ($checkpointfile ne "") {
  if ($VERBOSE) { print STDERR "reading in checkpoint info file $checkpointfile...\n"; }
  open(KFILE,"$checkpointfile") || die "Unable to read checkpoint info file";
  while(<KFILE>) {
    next if (/^\s*#/ || /^\s*$/);	# skip empty and comment lines
    # last successfully completed generation was GENS
    if (/completed generation was (\d+)/) {
      $gens=$1;
      if ($VERBOSE) { print STDERR "population completed $gens gens..\n"; }
    }
    # TMPpopPID.gwf is the evaluated and updated population file
    if (/TMPpop(\d+).gwf.* population file/) {
      $evopid=$1;
      $popchkfile="TMPpop" . $evopid . ".gwf";
      if ($VERBOSE) { print STDERR "evolution run PID was $evopid..\n"; }
    }
  }
  close(KFILE);
  if ($VERBOSE) { print STDERR "done\n\n"; }

  unless ($evopid ne "" && $gens ne "") {
    die("unable to extract necessary gens and checkpoint PID info from checkpoint file!\nexiting...");
  }

  if ($NUMGENS ne "") {
    # copy TMPpop(\d+).gwf to DATADIR/pop_GEN.txt
    if ($VERBOSE) { print STDERR "moving and renaming checkpoint popn file...\n"; }
    # remove old copy if one already there
    if (-e "$DATADIR/pop_$gens.txt") {
      if ($VERBOSE) { print STDERR "removing old $DATADIR/pop_$gens.txt file...\n"; }
      unlink("$DATADIR/pop_$gens.txt");
    }
    rename($popchkfile,"$DATADIR/pop_$gens.txt") || die ("Unable to move $popchkfile to $DATADIR/pop_$gens.txt");
    if ($VERBOSE) { print STDERR "done\n\n"; }

    # rename DATADIR/pop_NUMGENS_stats.txt to pop_GEN_stats.txt
    if ($VERBOSE) { print STDERR "renaming popn stats file...\n"; }
    rename("$DATADIR/pop_$NUMGENS"."_stats.txt","$DATADIR/pop_$gens"."_stats.txt") || print STDERR "$DATADIR/pop_$NUMGENS"."_stats.txt not found! skipping...\n";
    # rename DATADIR/pop_NUMGENS_parm.txt to pop_GEN_parm.txt
    if ($VERBOSE) { print STDERR "renaming popn param file...\n"; }
    rename("$DATADIR/pop_$NUMGENS.parm.txt","$DATADIR/pop_$gens.parm.txt") || print STDERR "$DATADIR/pop_$NUMGENS.parm.txt not found! skipping...\n";
    if ($VERBOSE) { print STDERR "done\n\n"; }
  }

  # now get rid of all those remaining temp files:
  if ($VERBOSE) { print STDERR "deleting temp evolution files...\n"; }
  # delete TMPpop*.*
  eval "unlink <TMPpop$evopid*.*>";
  if ($VERBOSE) { print STDERR "done\n"; }

}


if ($VERBOSE) { print STDERR "\nEHW cleanup complete!\n"; }



sub printUsage {

  print STDERR <<EOH
  Synopsis:
  [perl] cleanupEHW.pl [-v] [numgens]

  Args:
  		-v		Verbose mode - generate info to STDERR
  		numgens		the number of generations that evolution was
  				specified to run for - so there should be a
  				pop_NUMGENS_stats.txt and pop_NUMGENS.parm.txt
				in the DATADIR specified in the EHW_config.txt
  Inputs:
  	   EHW_config.txt	holds the parameters used by the system
				(needed if numgens param supplied)
	   TMPpop\$\$\$\$_info.txt	checkpoint information file, '\$\$\$\$' is the PID

  Outputs:
  	   (STDERR)		  informative messages

  Description:
		performs a cleanup of temporary population and chromosome
		files in the main directory, and if numgens is specified,
		the chechkpoint population file is copied to the DATADIR
		pop_NUMGENS.txt file, and the associated population stats
		and parameter files are renamed. Note that temporary files
		from earlier runs are not touched, and neither are any
		temporary (decoded) chromosome files in the DATADIR.
		NB: if numgens is not specified, the temp and checkpoint
		files from the most recent run will just be deleted.
EOH
}



