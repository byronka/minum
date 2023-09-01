##
# default target(s)
##
all:: help

#: test the whole system
test::
	 mvn test

#: run mutation testing using pitest
mutation_test::
	 @echo "be patient - this can take 10 minutes, for real"
	 mvn test-compile org.pitest:pitest-maven:mutationCoverage

# a handy debugging tool.  If you want to see the value of any
# variable in this file, run something like this from the
# command line:
#
#     make print-CLS
#
# and you'll get something like: CLS = out/inmra.logging/ILogger.class out/inmra.logging/Logger.class out/inmra.testing/Main.class out/inmra.utils/ActionQueue.class
print-%::
	    @echo $* = $($*)

# This is a handy helper.  This prints a menu of items
# from this file - just put hash+colon over a target and type
# the description of that target.  Run this from the command
# line with "make help"
help::
	 @echo
	 @echo Help
	 @echo ----
	 @echo
	 @grep -B1 -E "^[a-zA-Z0-9_-]+:([^\=]|$$)" Makefile \
     | grep -v -- -- \
     | sed 'N;s/\n/###/' \
     | sed -n 's/^#: \(.*\)###\(.*\):.*/\2###\1/p' \
     | column -t  -s '###'