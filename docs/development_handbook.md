Developer handbook
==================

Table of contents
-----------------

- [Features](#features)
- [Quick start](#quick-start)
- [Description](#description)
- [Why?](#why)
- [System Requirements](#system-requirements)
- [New Developer Setup](#new-developer-setup)
- [Step-by-step guide to installing Java on Windows](#step-by-step-guide-for-installing-java-on-windows)
- [Java on Mac](#java-on-mac)
- [HOWTO](#howto)
- [To test the web server with Telnet](#to-test-the-web-server-with-telnet)
- [To test with Chrome on localhost with invalid certificates](#to-test-with-chrome-on-localhost-with-invalid-certificates)
- [TDD](#TDD)
- [Testing](#testing)
- [Database](#database)
- [Logging](#logging)
- [Threads](#threads)
- [Routing](#routing)
- [Security](#security)
- [Templates](#templates)
- [Writing to files](#writing-to-files)
- [Authentication](#authentication)
- [Avoidance of null](#avoidance-of-null)
- [Immutability](#immutability)
- [Documentation](#documentation)
- [Feature tracking](#feature-tracking)
- [History](#history)
- [Theme](#theme)
- [ActionQueue](#actionqueue)


Features:
--------

- Secure TLS 1.3 HTTP/1.1 web server
- In-memory database with disk persistence
- Server-side templating
- Logging framework
- Testing framework
- HTML parsing

Quick start:
------------

* To test: `make test`
* To create a library jar: `make jar`
* For help: `make`

Description
------------

Minum is a framework with most of the components needed to build a web application,
including a web server and a database.  The major paradigm is to stay simple, but high-quality, and avoid
third-party dependencies whenever possible.


Why?
----

To have a working example of the programming technique I offer to colleagues and clients.  This 
was an experiment.  The hypothesis was: If I take all my [beliefs](#theme) about software development and 
apply them in the most idealistic manner possible, I think it will enable productivity far 
exceeding ordinary development practices.  

The experiment is still ongoing, but I have seen some interim benefits.

- The code is [minimalistic](size_comparisons.md) on purpose.  It does not
  handle every imaginable case, but it fosters [modifying the code](https://programmingisterrible.com/post/139222674273/write-code-that-is-easy-to-delete-not-easy-to).  There
  is nothing to prevent inclusion of extra libraries, but basics are handled.

- The [compiled binary is small](perf_data/framework_perf_comparison.md) - around 150 kilobytes, which includes the database, web server,
  templating, logging, and HTML parsing.  The [example projects](../README.md#example-projects-demonstrating-usage)
  show how to continue that pattern with the business logic.  This makes everything faster - sending to
  production takes seconds.

- In many web frameworks, there are annotation-based mechanisms for hiding complexity. These have their greatest
  value during the initial implementation phase, saving a few seconds typing.  However, we spend the majority of time in the
  maintenance phase, where such tools obscure the control flow. Since the priority here is on making the maintenance phase easiest,
  mechanisms like these were avoided.

- [Well-documented throughout](https://hackaday.com/2019/03/05/good-code-documents-itself-and-other-hilarious-jokes-you-shouldnt-tell-yourself/).
  More supportive of long-term maintenance.

- Zero dependencies.  Projects often incorporate many dependencies, which must be kept updated, leading to churn. The 
  standard library is sufficient for most needs.  Benefit from the power of an [industrial strength general-purpose programming language](https://www.teamten.com/lawrence/writings/java-for-everything.html).

- Good performance, because [performance was always a goal](https://blog.nelhage.com/post/reflections-on-performance/). See
  the [response speed test](perf_data/response_speed_test.md), the [database speed test](perf_data/database_speed_test.md), and 
  the [templating engine speed test](perf_data/templateRenderTest.md).

- Embraces the bleeding edge of Java technology, like [virtual threads](https://openjdk.org/jeps/436).
  This allows it to manage [thousands of concurrent requests](perf_data/loom_perf.md) on resource-constrained
  hardware.

- Other projects strive to support universal cases.  [Because this does not](http://josdejong.com/blog/2015/01/06/code-reuse/), there
  is less code to hide bugs.

  >I conclude that there are two ways of constructing a software design: One way is to
  >make it so simple that there are obviously no deficiencies and the other way is to
  >make it so complicated that there are no obvious deficiencies.
  >
  > Tony Hoare,  _1980 ACM Turing award lecture_


See the [theme](development_handbook.md#theme) for more philosophical underpinnings.


System requirements:
--------------------

[JDK version 20](https://jdk.java.net/20/) is _required_, since it
provides us the [virtual threads](https://openjdk.org/jeps/436) we need (and even so, virtual
threading is a preview until JDK version 21).

Developed in two environments:
* MacBook Pro with OS 12.0.1, with OpenJDK 20, GNU Make 3.81 and Rsync 2.6.9
* Windows 10 64-bit professional, on [Cygwin](https://www.cygwin.com/), OpenJDK 20, Gnu Make 4.4 and Rsync 3.2.7

Note that the build tool, _Gnu Make_, is already installed on Mac.  On Windows you can install
it through the Cygwin installer.  See [here](https://www.cygwin.com/packages/summary/make.html)


New Developer Setup
-------------------

Here's the general series of steps for a new developer:

1. Install the required JDK onto your machine if you don't already have it. Make sure to add the path to your 
   JDK to your environment as JAVA_HOME, and also add the path to the Java binaries to your PATH variable.
2. Download and install IntelliJ Community Edition (which is free of charge) if you don't already have 
   it. Find it here: https://www.jetbrains.com/idea/download/
3. Obtain this source code from Github, at https://github.com/byronka/minum.git
4. Run the tests on your computer, using the command line: make test 
5. Run the [using_minum](https://github.com/byronka/minum_usage_example_mvn) application to get a feel for the user experience: make run (and then go to http://localhost:8080)
6. Open the software with IntelliJ.  Navigate around the code a bit.
7. Read through some of this developer documentation to get a feel for some of its unique characteristics.
8. Examine some tests in the system to get a feel for how the system works and how
   test automation is done (see the src/test directory).

Optional:
* Install "Code With Me" plugin for IntelliJ - Preferences > Plugins > Code WIth me
    * Use the new "Code With Me" icon in top bar to enable "Full Access" (turn off "start voice call")
    * Share the link with your friends


Step-by-step guide for installing Java on Windows:
--------------------------------------------------

1. Download the binary by clicking [here](https://download.java.net/java/GA/jdk20.0.1/b4887098932d415489976708ad6d1a4b/9/GPL/openjdk-20.0.1_windows-x64_bin.zip).
2. Uncompress the zip file
3. Add the home directory to your path.  The home directory of Java is the one with "bin"
   and "conf" directories, among others. if you, for example, uncompressed the
   directory to C:\java\jdk-20.0.1, then in Windows you should add it to your path,
   following these instructions:

* Click the Windows start icon
* Type `env` to get the system properties window
* Click on _Environment Variables_
* Under user variables, click the _New_ button
* For the variable name, enter `JAVA_HOME`, and for the value, enter `C:\java\jdk-20.0.1`
* Edit your _Path_ variable, click _New_, and add `%JAVA_HOME%\bin`

Java on Mac:
------------

1. You will need your JAVA_HOME variable set.  Check [this stackoverflow answer](https://stackoverflow.com/a/22842806/713809)

HOWTO
-----

A series of HOWTO's for essential capabilities:

* [How to add a new endpoint](howto/add_a_new_endpoint.md)
* [How to create a new test class](howto/create_a_new_test_class.md)

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

This entire project was built using TDD, but not the dogmatic flavor you often
hear.  When I started development, I used TDD, and immediately fell in love with
the technique, and have been advancing my techniques ever since.  In particular,
a key difference is that I prefer to write as few tests as possible with as much
impact as possible.  If the test won't have much impact, then it's just costing
me in the form of slowed momentum.

Use TDD for intricate things, for helping think through ideas, for reviewing
correctness.  Use it when it makes sense, and if it doesn't feel right, don't
use it.


Testing
-------

High-quality programs require good testing.  Given that the overarching theme
of this project is choosing the simplest thing that could work, the testing framework
is almost embarrassingly basic, but gets the job done.

The tests start from a plain old-fashioned main() method, which then calls to a
tree of tests in a procedural fashion.  See main() in the Tests class.

There's really not much magic here - the trick is to follow the conventions.  The TestLogger
class has a method that we use to document each test.  For example, here's a test:

```java
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
```

Notice that the first line has a semicolon and a curly brace at the end.  The reason for
this is that IDE's will allow us to fold the code between braces, and so this just comes out
looking great:

```java
    + ---   logger.test("client / server");{...}
```
No magic, just tidy.

The assertions are similarly minimalistic.  Here's assertEquals:

```java
public static <T> void assertEquals(T left, T right) {
    if (! left.equals(right)) {
        throw new RuntimeException("Not equal! %nleft:  %s %nright: %s".formatted(showWhiteSpace(left.toString()), showWhiteSpace(right.toString())));
    }
}
```

The rest of the testing framework is about the same.  It's never simple,
but we can choose to work with fewer tools.

There is some rudimentary test reporting (see out/reports/test/tests.xml after running tests,
and see writeTestReport in TestLogger), but in most regards it is unimportant.  Reporting is not
the point of tests.  Instead, the point is to give the developer confidence that the recent changes aren't breaking anything.

Instead of using a mocking framework, we just create a
"Fake" version of a class (e.g. FakeSocketWrapper).  This is
because mocking frameworks actually compile code _at runtime_ which
slows down tests tremendously.  By using this practice, our unit tests
often take less than even 1 millisecond.

Database
--------

If your business needs are relatively lower-risk, there is a chance you can use
a database with less safety guarantees.  The database provided here is just such
a one.  It's in-memory-based, with eventual disk persistence.  This is a clear
and intentionally different choice than an ACID-compliant database, but can
be valuable in a variety of business cases.

In this paradigm, the capabilities are minimal.  Here is an example of
creating a new user in a registration process:

```java
  final var newUser = new User(0L, newUsername, hashedPassword, newSalt);
  userDiskData.write(newUser);
```

This data stays in memory and its manipulations eventually end up persisted.  The database
reads from disk just once - the first time any data is required, all data is loaded from disk.

This is the general paradigm:

_thin data_: identifiers, metadata

_fat data_: documents and other large data

The expectation is to use the database for storing thin data, and store fat data using regular
file writes.  Here is a concrete example:

Say your system receives photograph files.  When a user uploads a photograph, you may create
thin data - a new integer representing the identifier for the photograph, and a UUID for use
in representing that photograph to the outside world.  Because the system uses atomic values
for the identifiers, you are assured no photograph's identifier will conflict with any other.
Now that you have some unique identifiers (the UUID is also unique), you may use either to
name the photograph file itself.

So you see, the only data that gets stored in the in-memory database is the thin data.

Another way this plays out is audit information.  There is no need to overstuff your database.
If you need to record many audits of user actions in the system, it would be better to 
write those actions to simple files, per person maybe, rather than store it all in memory.


Logging
-------

Logging capabilities are provided by the `Logger` class.  When the system starts, it 
creates one instance of this class and then stores that in an instance of Context, which
is passed around everywhere.


Threads
-------

If you need to run parts of your program in their own threads, it is available by creating
threads and running them in an instance of the ExecutorService, which is available in the Context instance
that is passed around the system.  for an example of its use, see `LoopingSessionReviewing`
in the test folder.  In this case, it requires the thread so that it can periodically wake up 
and check whether any of the authentication sessions are old and need to be removed.

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

```java
Response getFoo(Request r) {
    ...
}
```

Security
--------

Since our system is designed to be put on the public internet without an intervening
load balancer or other reverse proxy, it faces a lot of abuse.  There are two classes
in particular designed to mitigate this.  They are called UnderInvestigation (UI) and
TheBrig (TB).  In short, UI has code for determining whether some
behavior is indicative of an attack, and TB maintains a list of clients who
have been deemed dangerous, and makes that list available to key parts of the system.

For example, if UI thinks that a client is seeking out vulnerabilities in the system,
it will indicate that to TB, who will put that client in a list for a week.  Adjacent to
the ServerSocket.accept() command, there is a check if the newly-accepted client is
in that list, and if so, drop the connection.

The offending client is designated by a combination of their ip address and a short string
representing the offense type. For example, if someone tries to force our system to use 
a version of TLS that has known vulnerabilities, we'll assume they're of no value to us 
and put them away for a week:

    "123.123.123.123_vuln_seeking", 604800000


Templates
---------

There's a few things to note when dealing with templates.

First, files you intend to use as a template should be stored under src/resources/templates.
They should then be placed inside a subdirectory there, to help organize them.  Inside
a template, wherever you intend to substitute values, use a syntax like this:

    this is some text {{template_key}} this is more text

Once your template is written, you'll need to load the file, like this (note that
the filetype of html is random - it could be any text file):

```java
String template = FileUtils.readTemplate("foo/bar.html");
```

Now that you have the string, you run a command one time to convert it to a form
that is suitable for fast processing:

```java
fooProcessor = TemplateProcessor.buildProcessor(template);
```

And now you can render a template like this:

```java
String renderedFoo = fooProcessor.renderTemplate(Map.of(
    "beep", getBeepValue(),
    "boop", 2 + 2
));
```

Writing to files
----------------

Occasionally you will need to write to files.  If you want to keep with the paradigm of
this software, you will want to keep those files in the same root directory as the database,
but in its own sub-directory.

The steps to this are:
1. Get the configured root directory for the database: `var dbDir = Path.of(FullSystem.getConfiguredProperties().getProperty("dbdir"));`
2. Create the path for a sub-directory: `photoDirectory = dbDir.resolve("photo_files");
3. Make a directory:

```java
try {
    FileUtils.makeDirectory(logger, photoDirectory);
} catch (IOException e) {
    logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
}
```

4. Then you can use that to write files, maybe like this:

```java
Path photoPath = photoDirectory.resolve(newFilename);
try {
    Files.write(photoPath, photoBytes);
} catch (IOException e) {
    logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
    return new Response(_500_INTERNAL_SERVER_ERROR, e.toString(), Map.of("Content-Type", "text/plain;charset=UTF-8"));
}
```

Authentication
--------------

Authentication is handled like follows:

```java
AuthResult authResult = auth.processAuth(request);
if (! authResult.isAuthenticated()) {
    return new Response(_401_UNAUTHORIZED);
}
```

Avoidance of null
-----------------

Throughout this application, it was attempted to avoid null.  Null is fine and all,
but it has a problem: null is too vague a description, and it's too easy to forget to
handle the situation when something comes back null.

1) if I request a "user" from the database, and I get null ... what does that mean? Does
   it mean there was no user?  That there was an error? Who knows.

2) In Java, a lot of methods return an object, and oftentimes it returns null, and it's
   just so painfully easy to forget to handle what happens when it's null.

Not saying you have to be perfect, but when you can, try to avoid null.


Immutability
------------

Immutable data structures are easier to track.  The basic problem with immutability
is that when it is possible for a certain data structure to be changed after creation,
it's just too easy to do so.  When a structure's state can easily change, and the code
has any parallelization or event-driven code, trying to be certain of the code's behavior
becomes quite a bit more difficult.

When we create a data structure, we favor doing so immutably - that is, once created, it cannot
be changed.  To make a change, we have to recreate the data structure.


Documentation
-------------

The only way we can keep the complexity tamped down is by keeping vigilant about quality.
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

This project is a Java rewrite of https://github.com/byronka/r3z, which was inspired by
work on https://github.com/7ep/demo.  So you could say this project has been in
the works since 2018.

This project was originally meant to be Automated Test Quality Analysis, an attempt
to evaluate the quality of a codebase.  In the process, I wrote what I considered as
high-quality web app code as an example to analyze.  That code lived, but the analysis code - maybe
we'll try that another day.



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

See the [parable of two programmers](parable_two_programmers.md)

>A complex system that works is invariably found to have evolved from a simple system
>that worked. The inverse proposition also appears to be true: A complex system designed
>from scratch never works and cannot be made to work. You have to start over,
>beginning with a working simple system.”
>
>-- John Gall (Gall's law)

>Simple ain’t easy
>
> -- _Thelonious Monk_

>Slow is smooth, smooth is fast
>
> -- _Navy Seals_

>It occurred to him almost instantly, with the instinctive correctness that self-preservation
>instills in the mind, that he mustn’t try to think about it, that if he did, the law of gravity
>would suddenly glance sharply in his direction and demand to know what the hell he thought
>he was doing up there, and all would suddenly be lost.
>
> -- _Douglas Adams, Life, The Universe and Everything_

>"I never knew words could be so confusing," Milo said to Tock as he bent down to scratch the
>dog's ear. "Only when you use a lot to say a little," answered Tock. Milo thought this was
>quite the wisest thing he'd heard all day.
>
> -- _Norton Juster, The Phantom Tollbooth_

>One of the most irritating things programmers do regularly is feel so good about learning a 
>hard thing that they don’t look for ways to make it easy, or even oppose things that would do so.
> 
>  -- _Peter Bhat Harkins_

>If you’re going to repair a motorcycle, an adequate supply of gumption is the first
>and most important tool. If you haven’t got that you might as well gather up all
>the other tools and put them away, because they won’t do you any good.
>
> -- _Robert Pirsig, Zen and the Art of Motorcycle Maintenance_

>If you want to build a ship, don't drum up people to collect wood and don't assign
>them tasks and work, but rather teach them to long for the endless immensity of the sea.
>
> -- _Antoine de Saint-Exupery_

>I conclude that there are two ways of constructing a software design: One way is to
>make it so simple that there are obviously no deficiencies and the other way is to
>make it so complicated that there are no obvious deficiencies. The first method is
>far more difficult. It demands the same skill, devotion, insight, and even inspiration
>as the discovery of the simple physical laws which underlie the complex phenomena of
>nature. It also requires a willingness to accept objectives which are limited by
>physical, logical, and technological constraints, and to accept a compromise when
>conflicting objectives cannot be met.
>
> -- _Tony Hoare, 1980 ACM Turing award lecture_

>Keep it simple, stupid
>
> -- _Kelly Johnson, Lockheed Skunk Works_


ActionQueue
-----------

[ActionQueue](../src/main/java/minum/utils/ActionQueue.java) lets you run thread-contended actions safely.  Let's unpack 
that sentence a bit with an example: Because this program is multithreaded, there will be times when multiple threads
want to write to the same file at the same time.

This could lead to exceptions being thrown or data being corrupted.  Bad stuff, yeah.  To avoid that outcome, we
have some options - we could mark the function as `synchronized`, which means only one thread can be running it
at a time.  I'd recommend *against* this, since as of this writing, there is some conflict between virtual threads
and using the synchronized keyword. Instead, use ReentrantLocks like this:

```java
class X {
   private final ReentrantLock lock = new ReentrantLock();
   // ...

   public void m() {
     lock.lock();  // block until condition holds
     try {
       // ... method body
     } finally {
       lock.unlock();
     }
   }
 }
```

Here, you can see that multiple threads will contend only if they really need to write data.  What will happen is
one thread will go in and write the data, the others will wait outside for their turn.

There is a performance issue with this - if there are lots of writes happening, you will block lots of threads,
even if they wouldn't have necessarily written to the same file.

With this context in mind, let's talk about ActionQueue.

With ActionQueue, if there is some file you wish to write, you can enqueue that work to be done later.  The 
enqueue call immediately returns.  For example, looking at this code, here's an example usage from DatabaseDiskPersistenceSimpler:

```Java
        actionQueue.enqueue("update data on disk", () -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update "+file+" but it doesn't exist");
            writeString(fullPath, data.serialize());
        });
```

Any time it is important to put something to be done later, ActionQueue is ready to help.  But note it has some
drawbacks.  For one, you won't get exceptions bubbling up from your call.  If you need to immediately return
a 400 error to a user based on some calculation, it wouldn't make sense to use ActionQueue there.  And like I 
mentioned, the other options - synchrnonized, Locks - are also good in many cases.
