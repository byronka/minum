Developer handbook
==================

Table of contents
-----------------

- [Description](#description)
- [New Developer Setup](#new-developer-setup)
- [To test the web server with Telnet](#to-test-the-web-server-with-telnet)
- [To test with Chrome on localhost with invalid certificates](#to-test-with-chrome-on-localhost-with-invalid-certificates)
- [TDD](#TDD)
- [Testing](#testing)
- [Database](#database)
- [Logging](#logging)
- [Threads](#threads)
- [Routing](#routing)
- [Documentation](#documentation)
- [Feature tracking](#feature-tracking)
- [History](#history)
- [Theme](#theme)


Description
------------

Atqa is a web library with all the components needed to build a web application,
include a web server and a database.  

It follows some unusual conventions that new developers should be aware of.

For example, we don't have many dependencies in the system.

As we have developed, we would occasionally bring in a
dependency to handle a need.  Since we operated with sufficient time
to reflect on things, it often occurred to us that a particular
dependency was providing relatively little value.  We then could easily
replace it with just a little custom code. As that continued, we
generally found that the only outside code worth its salt happened to
already exist in the JDK standard library.  As of this writing, we have no
dependencies outside the standard library (which is just incredible -
thanks Java library developers!)

New Developer Setup
-------------------

Here's the general series of steps for a new developer:

1. Install the required JDK onto your machine if you don't already have it. Make sure to add the path to your 
   JDK to your environment as JAVA_HOME, and also add the path to the Java binaries to your PATH variable.
2. Download and install IntelliJ Community Edition (which is free of charge) if you don't already have 
   it. Find it here: https://www.jetbrains.com/idea/download/
3. Obtain this source code from Github, at https://github.com/byronka/atqa.git
4. Run the tests on your computer, using the command line: make test 
5. Run the [using_atqa](https://github.com/byronka/using_atqa) application to get a feel for the user experience: make run (and then go to http://localhost:8080)
6. Open the software with IntelliJ.  Navigate around the code a bit.
7. Read through some of this developer documentation to get a feel for some of its unique characteristics.
8. Examine some tests in the system to get a feel for how the system works and how
   test automation is done (see the src/test directory).

Optional:
* Install "Code With Me" plugin for IntelliJ - Preferences > Plugins > Code WIth me
    * Use the new "Code With Me" icon in top bar to enable "Full Access" (turn off "start voice call")
    * Share the link with your friends
    

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


Testing
-------

High-quality programs require good testing.  Given that the overarching theme
of this project is choosing the simplest thing that could work, the testing framework
is almost embarrassingly basic, but gets the job done.

The tests start from a plain old-fashioned main() method, which then calls to a
tree of tests in a procedural fashion.  See main() in the Tests class.

There's really not much magic here - the trick is to follow the conventions.  The TestLogger
class has a method that we use to document each test.  For example, here's a test:

        logger.test("client / server");{
            try (Server primaryServer = webEngine.startServer(es)) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {
                    try (SocketWrapper server = primaryServer.getServer(client)) {
                        InputStream is = server.getInputStream();

                        client.send("hello foo!\n");
                        String result = readLine(is);
                        assertEquals("hello foo!", result);
                    }
                }
            }
        }

Notice that the first line has a semicolon and a curly brace at the end.  The reason for
this is that IDE's will allow us to fold the code between braces, and so this just comes out
looking great:

    + ---   logger.test("client / server");{...}

No magic, just tidy.

The assertions are similarly minimalistic.  Here's assertEquals:

    public static <T> void assertEquals(T left, T right) {
        if (! left.equals(right)) {
            throw new RuntimeException("Not equal! %nleft:  %s %nright: %s".formatted(showWhiteSpace(left.toString()), showWhiteSpace(right.toString())));
        }
    }

The rest of the testing framework is about the same.  It's never simple,
but we can choose to work with fewer tools.

There aren't any reporting tools (other than the code coverage, for which we
use Jacoco), but in some regards that doesn't diverge from the paradigm too much: Reporting is
not really the point of tests.  Instead, the point is to give the developer confidence that the
recent changes aren't breaking anything.  Until there's a compelling developer-oriented reason
for a report, it will remain a lower priority.

Instead of using a mocking framework, we just create a
"Fake" version of a class (e.g. FakeSocketWrapper).  This is
because mocking frameworks actually compile code _at runtime_ which
slows down tests tremendously.  By using this practice, our unit tests
often take less than even 1 millisecond.

Database
--------

developers are accustomed to using sophisticated databases. We're talking relational or NoSQL
databases here - there's lots of mechanisms for the CRUD and ACID-compliance, high-availability,
awesome performance, and so on.

But what if you started with much less?

In the paradigm here, you are given a scarce minimum of abilities.  Here's an example where we
create a new user in a registration process:

```
    private final List<User> users;
    
    ...

    var newUser = new User(foo, bar);
    users.add(newUser);
    userDiskData.persistToDisk(newUser);
```

You can see here how it works - in this example we're creating a new user and storing it in a 
list.  Immediately after, we persist that data to disk.

The only time we read this data is at system startup, where we run a command `readAndDeserialize()`
to load all the data from the disk into a list, and from there into any structure we wish.


Logging
-------

Logging capabilities are provided by the `Logger` class.  When the system starts, it 
creates one instance of this class and then passes it down the call tree.  It is 
stored and available for your use in the WebFramework class.


Threads
-------

If you need to run parts of your program in their own threads, it's available.  Your 
class will need an instance of the ExecutorService class - for example, see `LoopingSessionReviewing`
in the test folder, which shows some samples of framework use.  In this case, it requires
the thread so that it can periodically wake up and check whether any of the authentication
sessions are old and need to be removed.

Our project uses virtual threads, a very recent addition to the Java language.  Also known
as "Project Loom", it enables our server to handle many concurrent requests with minimal
resource needs.  See also loom/README.md

Routing
-------

See `TheRegister.java` in the tests folder for an example of how routing works.  When you
first start the program, you are given a WebFramework class instance, which has a method
called `registerPath()`.  

The commonly used verbs GET and POST are available.  The function you provide must be
similar to this:

    Response getFoo(Request r) {
        ...
    }


Documentation
-------------

THe only way we can keep the complexity tamped down is by keeping vigilant about quality.
This takes many forms, and one of these is keeping good documentation.  Consider whether
a helpful explanation would get someone running sooner.  Please keep your words concise.
Brevity is the soul of wit.


Feature tracking
----------------

Following the pattern of using the simplest thing that works, the features are tracked
by writing them up and storing them in docs/todo/feature.  When they are finished, they
move to docs/todo/done


History
-------

This project is a Java rewrite of https://github.com/7ep/r3z, which was inspired by
work on https://github.com/7ep/demo.  So you could say this project has been in
the works since 2018.


Theme
-----

Software is often written in a rush.  It feels like we're expected to get as many
capabilities added as fast as possible.  But what would happen if we weren't so
pressured for time?  We could explore different avenues.  If we wrote in a quality
fashion, and included good tests, we could refactor fearlessly.

What would happen if you built software in the simplest possible way from scratch?

What if our team held quality sacred?

What if we spent all the necessary time to think things through?

What if we incorporated diverse perspectives?

What if testing drove the design?

If we understand that our software is a reflection of our culture, should we not focus on improving that first?

>A complex system that works is invariably found to have evolved from a simple system
>that worked. The inverse proposition also appears to be true: A complex system designed
>from scratch never works and cannot be made to work. You have to start over,
>beginning with a working simple system.”
>
>-- John Gall (Gall's law)

>If you want to build a ship, don't drum up people to collect wood and don't assign
>them tasks and work, but rather teach them to long for the endless immensity of the sea.
>
>-- Antoine de Saint-Exupery