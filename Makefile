##
# Project name - used to set the jar's file name
##
PROJ_NAME := minum
HOST_NAME := minum.com
VERSION=0.1.0

##
# In cygwin on Windows, if I look at the OS environment value I get "Windows_NT".
# I can use this to distinguish when I'm running there and change some values, mostly
# related to the paths.
##

# the delimiter between directories in the classpath to the Java application
# on a Windows box is a semicolon, and on a posix box it's a colon.
ifeq ($(OS),Windows_NT)
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
	ifeq ($(OS),Windows_NT)
		JAVA_HOME_BIN := $(cygpath $(JAVA_HOME))
	else
		JAVA_HOME_BIN := $(JAVA_HOME)/bin/
	endif
endif

# the name of our Java compiler
# -g means generate all debugging info
# The following line, about enabling preview, is for using virtual threads with java 19
JC = "$(JAVA_HOME_BIN)javac" --release 20 --enable-preview -Xlint:all -g

# the name of the java runner
# The following line, about enabling preview, is for using virtual threads with java 19
JAVA = "$(JAVA_HOME_BIN)java"  --enable-preview

# the directory where we store the code coverage report
COV_DIR = out/coveragereport

##
# suffixes
##
.SUFFIXES: .java


##
# default target(s)
##
all:: help

# note that putting an @ in front of a command in a makefile
# will cause that command not to echo out when running Make.


##
# copy to output directory resources originally located under main
# note: Java commands like FileUtils.getResources will look into any folder
# in the classpath
##
copyresources::
	 @rsync --recursive --update --perms src/resources/ out/main/

copytestresources::
	 @rsync --recursive --update --perms src/testresources/ out/

##
# copy the source code to the output directory.  This is used before we
# built a jar file for publishing
##
copysources::
	 @rsync --recursive --update --perms src/main/ out/main

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

#: Build a jar of the project for use as a library
jar:: test
	 # remove the static content from static and templates, won't need it
	 rm -fr $(OUT_DIR_MAIN)/static/* $(OUT_DIR_MAIN)/templates/*
	 mkdir -p $(OUT_DIR_MAIN)/META-INF/
	 $(eval GIT_BRANCH=$(shell git rev-parse --short HEAD))
	 version=$(VERSION)_$(GIT_BRANCH) utils/build_manifest.sh > $(OUT_DIR_MAIN)/META-INF/MANIFEST.MF
	 cd $(OUT_DIR_MAIN) && jar --create --manifest META-INF/MANIFEST.MF --file $(PROJ_NAME)-$(VERSION).jar * && mv $(PROJ_NAME)-$(VERSION).jar ../$(PROJ_NAME)-$(VERSION).jar
	 @echo
	 @echo "*** Your new jar file is at out/$(PROJ_NAME)-$(VERSION).jar ***"

# this is used to bundle the source code into a jar, to prepare for Maven publishing
jar_sources::
	 mkdir -p $(OUT_DIR)
	 cd src/main && jar --create --file $(PROJ_NAME)-$(VERSION)-sources.jar * && mv $(PROJ_NAME)-$(VERSION)-sources.jar ../../$(OUT_DIR)/$(PROJ_NAME)-$(VERSION)-sources.jar

# this is used to bundle the javadocs into a jar, to prepare for Maven publishing
jar_javadoc:: javadoc
	 cd $(OUT_DIR)/javadoc && jar --create --file $(PROJ_NAME)-$(VERSION)-javadoc.jar * && mv $(PROJ_NAME)-$(VERSION)-javadoc.jar ../$(PROJ_NAME)-$(VERSION)-javadoc.jar

#: run the tests
test:: classes testclasses copyresources copytestresources
	 $(JAVA) $(JMX_PROPERTIES) -cp $(TST_RUN_CP) $(PROJ_NAME).Tests

JMX_PROPERTIES=-Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false
DEBUG_PROPERTIES=-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y

#: run the tests and open a port for debugging.
testdebug:: classes testclasses copyresources copytestresources
	 $(JAVA) $(JMX_PROPERTIES) $(DEBUG_PROPERTIES) -cp $(TST_RUN_CP) $(PROJ_NAME).Tests

#: If you want to obtain code coverage from running the tests. output at out/coveragereport
testcov:: classes testclasses copyresources copytestresources
	 $(JAVA) -javaagent:$(UTILS)/jacocoagent.jar=destfile=$(COV_DIR)/jacoco.exec -cp $(TST_RUN_CP) $(PROJ_NAME).Tests
	 $(JAVA) -jar $(UTILS)/jacococli.jar report $(COV_DIR)/jacoco.exec --html ./$(COV_DIR) --classfiles $(OUT_DIR_MAIN) --sourcefiles $(SRC_DIR)
	 @echo
	 @echo "*** The coverage report is at out/coveragereport ***"

#: build the javadoc documentation in the out/javadoc directory
javadoc::
	 mkdir -p $(OUT_DIR)
	 javadoc -Xdoclint:none --source-path src/main -d out/javadoc -subpackages $(PROJ_NAME)

#  see https://maven.apache.org/plugins/maven-deploy-plugin/examples/deploying-sources-javadoc.html
#: add to the local Maven repository
mvnlocalrepo:: clean set_pom_version jar jar_sources jar_javadoc
	 mvn org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file -Dfile=out/$(PROJ_NAME)-$(VERSION).jar         -DpomFile=out/pom.xml
	 mvn org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file -Dfile=out/$(PROJ_NAME)-$(VERSION)-sources.jar -DpomFile=out/pom.xml -Dclassifier=sources
	 mvn org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file -Dfile=out/$(PROJ_NAME)-$(VERSION)-javadoc.jar -DpomFile=out/pom.xml -Dclassifier=javadoc

#: prepares the jars for sending to Maven central
mvnprep:: clean set_pom_version jar jar_sources jar_javadoc
	 for i in $$(ls out/minum-*); do gpg -ab $$i; done
	 cd out && jar -cvf bundle.jar minum-*

# copies the pom.xml to the output directory, adjusting its version in the process.  VERSION is set in the Makefile
set_pom_version::
	 mkdir -p out
	 sed 's/VERSION/${VERSION}/g' docs/maven/pom.xml > out/$(PROJ_NAME)-$(VERSION).pom

# a handy debugging tool.  If you want to see the value of any
# variable in this file, run something like this from the
# command line:
#
#     make print-CLS
#
# and you'll get something like: CLS = out/minum.logging/ILogger.class out/minum.logging/Logger.class out/minum.testing/Main.class out/minum.utils/ActionQueue.class
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
