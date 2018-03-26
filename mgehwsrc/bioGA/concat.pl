# concat.pl's sole purpose is to avoid the need to specify different
# concatenation programs on Windows (type) and Unix (cat).
# Concat.pl reads in all the files specified in the command line,
# concatenates them and outputs their contents to STDOUT

while (<>) { print $_; }


