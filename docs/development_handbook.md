Developer handbook
==================

Table of contents
-----------------

- [Description](#description)
- [New Developer Setup](#new-developer-setup)
- [JDK notes](#jdk-notes)
- [To test the web server with Telnet](#to-test-the-web-server-with-telnet)
- [To test with Chrome on localhost with invalid certificates](#to-test-with-chrome-on-localhost-with-invalid-certificates)


Description
------------

ATQA is a multi-threaded web application with its own web server and its
own eventually-consistent database.  It builds to a binary that is
runnable on any Java virtual machine version 13 or up, using a commandline like

    java -jar atqa.jar

It follows some unusual conventions that new developers should be aware of.

For example, we don't have many dependencies in the system.
Storytime: As we have developed, we would occasionally bring in a
dependency to handle a need.  Since we operated with sufficient time
to reflect on things, it often occurred to us that a particular
dependency was providing relatively little value.  We then could easily
replace it with just a little custom code. As that continued, we
generally found that the only outside code worth its salt happened to
already exist in the JDK standard library.  As of this writing, we have no
dependencies outside the standard library (which is just incredible -
thanks Java library developers!)

Instead of using a mocking framework, we just create a
"Fake" version of a class (e.g. FakeSocketWrapper).  This is
because mocking frameworks actually compile code _at runtime_ which
slows down tests tremendously.  By using this practice, our unit tests
often take less than even 1 millisecond.

Another example: the data structures in the database are built on a
thread-safe data structure (ConcurrentHashMap), so that we don't need
to synchronize a great deal of the time.  

New Developer Setup
-------------------

Here's the general series of steps for a new developer:

1. Install the latest JDK onto your machine if you don't already have it. Make sure to add the path to your 
   JDK to your environment as JAVA_HOME, and also add the path to the Java binaries to your PATH variable.
2. Download and install IntelliJ Community Edition (which is free of charge) if you don't already have 
   it. Find it here: https://www.jetbrains.com/idea/download/
3. Obtain this source code from Github, at https://github.com/byronka/atqa.git
4. Run the tests on your computer, using the command line: make test 
5. Run the application to get a feel for the user experience: make run (and then go to http://localhost:8080)
6. Open the software with IntelliJ.  Navigate around the code a bit.
7. Read through some of this developer documentation to get a feel for some of its unique characteristics.
8. Examine some tests in the system to get a feel for how the system works and how
   test automation is done (see the src/test directory).

Optional:
* Install "Code With Me" plugin for IntelliJ - Preferences > Plugins > Code WIth me
    * Use the new "Code With Me" icon in top bar to enable "Full Access" (turn off "start voice call")
    * Share the link with your friends
    

JDK notes
---------

We need a minimum of Java version 13 to get the latest version of TLS, which is 1.3, and also because
there was a bug in TLS 1.3 that was fixed in Java 13.


To test the web server with Telnet
----------------------------------
It is possible to use the "telnet" tool to have a more immediate conversation with the 
web server.  Try this:

    telnet localhost 8080

    GET /formentry HTTP/1.1
    HOST: localhost
    connection: keep-alive


To test with Chrome on localhost with invalid certificates
----------------------------------------------------------

When testing this application, the secure (SSL) server will look for
a keystore and a keystore-password (see WebEngine checkSystemPropertiesForKeystore())

If it cannot find those values in the system properties, it will revert
back to using its own self-signed certificate.  Browsers like Chrome will
complain about this and balk at making a connection.  In order to calm
it down and let us run locally on SSL, do this:

In Chrome, go to chrome://flags/#allow-insecure-localhost
Set this option to enabled

TDD
---

A nice example of setting up some broken code in the TDD process is available.

In this case, we want to write the simplest eloquent concise code.  This particular example is pretty good for showing
what that initial attempt might look like.

The initial code is at commit 79325280441b561c5b2a1b401c2c1125c5ddabd6
The followup is at a589244cbba806912a08702584de0b78266bab0e