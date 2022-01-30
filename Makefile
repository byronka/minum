##
# source directory
##
SRC_DIR := src

##
# test source directory
##
TST_SRC_DIR := test

##
# output directory
##
OUT_DIR := out

##
# sources
##
SRCS := $(shell find ${SRC_DIR} -type f -name '*.java' -print)

##
# test sources
##
TST_SRCS := $(shell find ${TST_SRC_DIR} -type f -name '*.java' -print)


##
# classes
## 
CLS := $(SRCS:$(SRC_DIR)/%.java=$(OUT_DIR)/%.class)

##
# test classes
## 
TST_CLS := $(TST_SRCS:$(TST_SRC_DIR)/%.java=$(OUT_DIR)/%.class)

# If Java home is defined (either from command-line
# argument or environment variable), add /bin/ to it
# to access the proper location of the java binaries
#
# otherwise, it will just remain an empty string
ifneq ($(JAVA_HOME),)
  JAVA_HOME := $(JAVA_HOME)/bin/
endif

# the name of our Java compiler
JC = $(JAVA_HOME)javac

# the name of the java runner
JAVA = $(JAVA_HOME)java

# the directory where we store the code coverage report
COV_DIR = coveragereport

# the directory we store the Jacoco coverage utilities
JAC_DIR = utils/jacoco

##
# suffixes
##
.SUFFIXES: .java

##
# targets that do not produce output files
##
.PHONY: all clean run test testcov rundebug testdebug

##
# default target(s)
##
all: $(CLS)

# here is the target for the application code
$(CLS): $(OUT_DIR)/%.class: $(SRC_DIR)/%.java
	    $(JC) -Werror -g -d $(OUT_DIR)/ -cp $(SRC_DIR)/ $<

# here is the target for the test code
$(TST_CLS): $(OUT_DIR)/%.class: $(TST_SRC_DIR)/%.java
	    $(JC) -Werror -g -d $(OUT_DIR)/ -cp $(SRC_DIR)/ -cp $(TST_SRC_DIR)/ -cp $(OUT_DIR) $<


##
# clean up any output files
##
clean:
	    rm -fr $(OUT_DIR)
	    rm -fr $(COV_DIR)

# run the application
run: all
	    $(JAVA) -cp $(OUT_DIR) primary.Main

# run the application and open a port for debugging.
rundebug: all
	    $(JAVA) -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y -cp $(OUT_DIR) primary.Main

# run the tests
test: all $(TST_CLS)
	    $(JAVA) -cp $(OUT_DIR) primary.Tests

# run the tests and open a port for debugging.
testdebug: all $(TST_CLS)
	    $(JAVA) -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y -cp $(OUT_DIR) primary.Tests

# If you want to obtain code coverage from running the tests
testcov: all $(TST_CLS)
	    $(JAVA) -javaagent:$(JAC_DIR)/jacocoagent.jar=destfile=$(COV_DIR)/jacoco.exec -cp $(OUT_DIR) primary.Tests
	    $(JAVA) -jar $(JAC_DIR)/jacococli.jar report $(COV_DIR)/jacoco.exec --html ./$(COV_DIR) --classfiles $(OUT_DIR) --sourcefiles $(SRC_DIR)

# a handy debugging tool.  If you want to see the value of any
# variable in this file, run something like this from the
# command line:
#
#     make print-CLS
#
# and you'll get something like: CLS = out/logging/ILogger.class out/logging/Logger.class out/primary/Main.class out/utils/ActionQueue.class
print-%:
	    @echo $* = $($*)