##
# Project name - used to set the jar's file name
##
PROJ_NAME := minum
HOST_NAME := minum.com
VERSION=2.2.1

MAVEN := ./mvnw

##
# overall output directory
##
OUT_DIR := out

##
# output directory for main source files
##
OUT_DIR_MAIN := $(OUT_DIR)/main

##
# default target(s)
##
all:: help

#: clean up any output files
clean::
	 @rm -fr $(OUT_DIR)
	 @rm -fr target

#: test the whole system
test::
	 @${MAVEN} test

#: create the site report
site::
	 @${MAVEN} site

#: run tests, and build a coverage report
test_coverage::
	 @${MAVEN} jacoco:prepare-agent test jacoco:report

#: run mutation testing using pitest
mutation_test::
	 @echo "be patient - this can take 10 minutes, for real"
	 @${MAVEN} test-compile org.pitest:pitest-maven:mutationCoverage

#: build the javadoc documentation in the out/javadoc directory
javadoc::
	 @mkdir -p $(OUT_DIR)
	 @javadoc -Xdoclint:none --source-path src/main/java -d out/javadoc -subpackages com.renomad.minum


# this is used to bundle the javadocs into a jar, to prepare for Maven publishing
jar_javadoc:: javadoc
	 @cd $(OUT_DIR)/javadoc && \
 		jar --create --file $(PROJ_NAME)-$(VERSION)-javadoc.jar * && \
 		mv $(PROJ_NAME)-$(VERSION)-javadoc.jar ../$(PROJ_NAME)-$(VERSION)-javadoc.jar

# this is used to bundle the source code into a jar, to prepare for Maven publishing
jar_sources::
	 @mkdir -p $(OUT_DIR)
	 @cd src/main/java && jar --create --file $(PROJ_NAME)-$(VERSION)-sources.jar * && \
 		mv $(PROJ_NAME)-$(VERSION)-sources.jar ../../../$(OUT_DIR)/$(PROJ_NAME)-$(VERSION)-sources.jar

#: Build a jar of the project for use as a library
jar:: test
	 @mkdir -p $(OUT_DIR_MAIN)/META-INF/
	 @cp -r target/classes/* $(OUT_DIR_MAIN)
	 @$(eval GIT_SHA=$(shell git rev-parse --short HEAD))
	 @version=$(VERSION)_$(GIT_SHA) utils/build_manifest.sh > $(OUT_DIR_MAIN)/META-INF/MANIFEST.MF
	 @cd $(OUT_DIR_MAIN) && \
	 	jar --create --manifest META-INF/MANIFEST.MF --file $(PROJ_NAME)-$(VERSION).jar * && \
	 	mv $(PROJ_NAME)-$(VERSION).jar ../$(PROJ_NAME)-$(VERSION).jar
	 @echo
	 @echo "*** Your new jar file is at out/$(PROJ_NAME)-$(VERSION).jar ***"

#  see https://maven.apache.org/plugins/maven-deploy-plugin/examples/deploying-sources-javadoc.html
#: add to the local Maven repository
mvnlocalrepo:: clean set_primary_pom_version set_version_of_published_pom jar jar_sources jar_javadoc
	 ./mvnw org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file -Dfile=out/$(PROJ_NAME)-$(VERSION).jar         -DpomFile=out/$(PROJ_NAME)-$(VERSION).pom
	 ./mvnw org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file -Dfile=out/$(PROJ_NAME)-$(VERSION)-sources.jar -DpomFile=out/$(PROJ_NAME)-$(VERSION).pom -Dclassifier=sources
	 ./mvnw org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file -Dfile=out/$(PROJ_NAME)-$(VERSION)-javadoc.jar -DpomFile=out/$(PROJ_NAME)-$(VERSION).pom -Dclassifier=javadoc

#: prepares the jars for sending to Maven central
mvnprep:: clean set_primary_pom_version set_version_of_published_pom jar jar_sources jar_javadoc
	 for i in $$(ls out/minum-*); do gpg -ab $$i; done
	 cd out && jar -cvf bundle.jar minum-*

# copies the pom.xml to the output directory, adjusting its version in the process.  VERSION is set in the Makefile
set_version_of_published_pom::
	 mkdir -p out
	 sed 's/{{VERSION}}/${VERSION}/g' docs/maven/pom.xml > out/$(PROJ_NAME)-$(VERSION).pom

# this will adjust the version in the primary pom.xml file to match what we have set here.
# after running this, the changed pom.xml should be committed to source control.
set_primary_pom_version::
	sed -i '0,/<version>.*<\/version>/s/<version>.*<\/version>/<version>${VERSION}<\/version>/' pom.xml

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