##
# Project name - used to set the jar's file name
##
PROJ_NAME := atqa

HOST_NAME := atqa.com

##
# In cygwin on Windows, if I look at the OS environment value I get "Windows_NT".
# I can use this to distinguish when I'm running there and change some values, mostly
# related to the paths.
##

# the delimiter between directories in the classpath to the Java application
# on a Windows box is a semicolon, and on a posix box it's a colon.
ifeq ($(OS),Windows_NT)
	JAVA_HOME := $(cygpath $(JAVA_HOME))
    DIR_DELIM := ;
else
    DIR_DELIM := :
endif

##
# source directory
##
SRC_DIR := src/main

##
# test source directory
##
TST_SRC_DIR := src/test

##
# overall output directory
##
OUT_DIR := out

##
# output directory for main source files
##
OUT_DIR_MAIN := $(OUT_DIR)/main

##
# output directory for test files
##
OUT_DIR_TEST := $(OUT_DIR)/test

##
# the utilities
##
UTILS := utils

##
# sources
##
SRCS := $(shell find ${SRC_DIR} -type f -name '*.java' -print)

##
# test sources
##
TST_SRCS := $(shell find ${TST_SRC_DIR} -type f -name '*.java' -print)

##
# build classpath options - the classpaths needed to build
##
BUILD_CP := "$(SRC_DIR)/"

##
# build classpath for the tests
##
TEST_BUILD_CP := "$(SRC_DIR)/$(DIR_DELIM)$(TST_SRC_DIR)/$(DIR_DELIM)$(OUT_DIR_MAIN)/"

##
# run classpath options - the classpaths needed to run the program
##
RUN_CP := "$(OUT_DIR_MAIN)"

##
# run classpath for tests
##
TST_RUN_CP := "$(OUT_DIR_MAIN)$(DIR_DELIM)$(OUT_DIR_TEST)"

##
# classes
##
CLS := $(SRCS:$(SRC_DIR)/%.java=$(OUT_DIR_MAIN)/%.class)

##
# test classes
##
TST_CLS := $(TST_SRCS:$(TST_SRC_DIR)/%.java=$(OUT_DIR_TEST)/%.class)

# If Java home is defined (either from command-line
# argument or environment variable), add /bin/ to it
# to access the proper location of the java binaries
#
# otherwise, it will just remain an empty string
ifneq ($(JAVA_HOME),)
  JAVA_HOME := $(JAVA_HOME)/bin/
endif

# the name of our Java compiler
# -g means generate all debugging info
# The following line, about enabling preview, is for using virtual threads with java 19
JC = $(JAVA_HOME)javac --release 20 --enable-preview -Xlint:all -g

# the name of the java runner
# The following line, about enabling preview, is for using virtual threads with java 19
JAVA = $(JAVA_HOME)java  --enable-preview

# the directory where we store the code coverage report
COV_DIR = out/coveragereport

##
# suffixes
##
.SUFFIXES: .java


##
# default target(s)
##
all:: classes copyresources

# note that putting an @ in front of a command in a makefile
# will cause that command not to echo out when running Make.


##
# copy to output directory resources originally located under main
# note: Java commands like FileUtils.getResources will look into any folder
# in the classpath
##
copyresources::
	 @rsync --recursive --update --perms src/resources out/main

# make empty arrays for later use
LIST:=
TEST_LIST:=

classes:: $(CLS)
	 @if [ ! -z "$(LIST)" ] ; then \
	     $(JC) -d $(OUT_DIR_MAIN)/ -cp $(BUILD_CP) $(LIST) ; \
	 fi

testclasses: $(TST_CLS)
	 @if [ ! -z "$(TEST_LIST)" ] ; then \
	     $(JC) -d $(OUT_DIR_TEST)/ -cp $(TEST_BUILD_CP) $(TEST_LIST) ; \
	 fi

# here is the target for the application code
$(CLS): $(OUT_DIR_MAIN)/%.class: $(SRC_DIR)/%.java
	 $(eval LIST+=$$<)

# here is the target for the test code
$(TST_CLS): $(OUT_DIR_TEST)/%.class: $(TST_SRC_DIR)/%.java
	 $(eval TEST_LIST+=$$<)

#: clean up any output files
clean::
	 rm -fr $(OUT_DIR)

#: jar up the application (See Java's jar command)
jar:: classes copyresources
	 cd $(OUT_DIR_MAIN) && jar --create --file $(PROJ_NAME).jar -e $(PROJ_NAME).Main * && \
      # move the jar up one directory \
      mv $(PROJ_NAME).jar ../$(PROJ_NAME).jar

#: Build a jar of the project for use as a library
publish:: classes copyresources
	 rm -fr $(OUT_DIR_MAIN)/atqa/sampledomain
	 rm -fr $(OUT_DIR_MAIN)/atqa/resources
	 rm $(OUT_DIR_MAIN)/atqa/Main.class
	 rm $(OUT_DIR_MAIN)/atqa/TheRegister.class
	 vendor=renomad.com version=1.0.0 utils/build_manifest.sh > $(OUT_DIR_MAIN)/META-INF/MANIFEST.MF
	 cd $(OUT_DIR_MAIN) && jar --create --file $(PROJ_NAME).jar -e $(PROJ_NAME).Main * && \
      # move the jar up one directory \
      mv $(PROJ_NAME).jar ../$(PROJ_NAME).jar

JMX_PROPERTIES=-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false
DEBUG_PROPERTIES=-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y

# This command includes several system properties so that we can connect to the
# running Java Virtual Machine with Java Mission Control (JMC)
#: run the application
run:: all
	 $(JAVA) $(JMX_PROPERTIES) -cp $(RUN_CP) $(PROJ_NAME).Main

#: run the application using the jar
runjar:: jar
	 $(JAVA) $(JMX_PROPERTIES) -jar $(OUT_DIR)/$(PROJ_NAME).jar

#: run the application using the jar, debugging
runjardebug:: jar
	 $(JAVA) $(JMX_PROPERTIES) $(DEBUG_PROPERTIES)  -jar $(OUT_DIR)/$(PROJ_NAME).jar

#: run the application and open a port for debugging.
rundebug:: all
	 $(JAVA) $(JMX_PROPERTIES) $(DEBUG_PROPERTIES) -cp $(RUN_CP) $(PROJ_NAME).Main

#: run the tests
test:: all testclasses
	 $(JAVA) $(JMX_PROPERTIES) -cp $(TST_RUN_CP) $(PROJ_NAME).testing.Tests

#: run the tests and open a port for debugging.
testdebug:: all testclasses
	 $(JAVA) $(JMX_PROPERTIES) $(DEBUG_PROPERTIES) -cp $(TST_RUN_CP) $(PROJ_NAME).testing.Tests

#: If you want to obtain code coverage from running the tests. output at out/coveragereport
testcov:: all testclasses
	 $(JAVA) -javaagent:$(UTILS)/jacocoagent.jar=destfile=$(COV_DIR)/jacoco.exec -cp $(TST_RUN_CP) $(PROJ_NAME).testing.Tests
	 $(JAVA) -jar $(UTILS)/jacococli.jar report $(COV_DIR)/jacoco.exec --html ./$(COV_DIR) --classfiles $(OUT_DIR_MAIN) --sourcefiles $(SRC_DIR)

#: build the javadoc documentation in the out/javadoc directory
javadoc::
	 javadoc --source-path src/main -d out/javadoc -subpackages $(PROJ_NAME)

# a handy debugging tool.  If you want to see the value of any
# variable in this file, run something like this from the
# command line:
#
#     make print-CLS
#
# and you'll get something like: CLS = out/atqa.logging/ILogger.class out/atqa.logging/Logger.class out/atqa.testing/Main.class out/atqa.utils/ActionQueue.class
print-%::
	 @echo $* = $($*)

# This is a handy helper.  This prints a menu of items
# from this file - just put hash+colon over a target and type
# the description of that target.  Run this from the command
# line with "make help"
help::
	 @grep -B1 -E "^[a-zA-Z0-9_-]+\:([^\=]|$$)" Makefile \
     | grep -v -- -- \
     | sed 'N;s/\n/###/' \
     | sed -n 's/^#: \(.*\)###\(.*\):.*/\2###\1/p' \
     | column -t  -s '###'
