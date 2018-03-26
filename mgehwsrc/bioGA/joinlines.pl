# joinlines.pl written 1/2/2004, J.A. Lee
#
#	Usage: perl joinlines file1 file2 delimiter
#
#	joinlines is a simple utility for joining together the
#	corresponding lines from 2 different files using the
#	supplied delimiter, and outputting the result to STDOUT


$argc=$#ARGV+1;

if ($argc!=3) {
  printUsage();
  exit(1);
}

$file1=shift;
$file2=shift;
$delim=shift;

open(FILE1,"<$file1") || die ("Unable to read '$file1'\n");
while (<FILE1>) { chop; push(@fileone, $_); }
close(FILE1);

open(FILE2,"<$file2") || die ("Unable to read '$file2'\n");
while (<FILE2>) { chop; push(@filetwo, $_); }
close(FILE2);

while(@fileone || @filetwo) {
  $f1=shift(@fileone);
  $f2=shift(@filetwo);
  $f3=($f1 ne "" && $f2 ne "") ? join($delim,$f1,$f2) : $f1 . $f2;
  push(@merged,$f3);
}

#printA("fileone",@fileone);
#printA("filetwo",@filetwo);

foreach $str (@merged) { print $str . "\n"; }


######### end main()


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

  print STDERR <<EOH

	Usage: perl joinlines file1 file2 delimiter

	joinlines is a simple utility for joining together the
	corresponding lines from 2 different files using the
	supplied delimiter, and outputting the result to STDOUT

EOH
}


