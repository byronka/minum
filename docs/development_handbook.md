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
- [Versioning](#versioning)
- [Mutation Testing](#mutation-testing)
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
- [On minimalism](#on-minimalism)
- [Theme](#theme)
- [ActionQueue](#actionqueue)
- [Dependency Injections](#dependency-injection)
- [Sending larger and streaming data](#sending-larger-and-streaming-data)


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

[Quick start](quick_start.md)

Description
------------

[How Minum is different](https://renomad.com/blogposts/minum2.html)

Minum is a framework with most of the components needed to build a web application,
including a web server and a database.  The major paradigm is to stay simple and high-quality, avoiding
third-party dependencies whenever possible.

When you want to write a web application that is fast and maintainable
and (big-picture) less expensive.  For when you prefer a stable
foundation with minimal dependencies and not a [teetering tower](https://xkcd.com/2347/).

If these speak to you:

* https://renegadeotter.com/2023/09/10/death-by-a-thousand-microservices.html
* http://mcfunley.com/choose-boring-technology
* https://www.teamten.com/lawrence/writings/java-for-everything.html
* https://www.timr.co/server-side-rendering-is-a-thiel-truth/

If you realize there is no silver bullet, there are no shortcuts, that
hard work is the only way to create value, then you might share my
mindset about what makes this web framework valuable.

Why?
----

To have a working example of the programming techniques I offer to colleagues and clients.  My hypothesis 
was if I take all my [beliefs](#theme) about software development and 
apply them in the most idealistic manner possible, I think it will enable incredible productivity.

The experiment is still ongoing, but I have seen some interim benefits.

- The code is [minimalistic](size_comparisons.md) on purpose.  It does not
  handle every imaginable case, but it fosters [modifying the code](https://programmingisterrible.com/post/139222674273/write-code-that-is-easy-to-delete-not-easy-to).  There
  is nothing to prevent inclusion of extra libraries, but basics are handled.

- The [compiled binary is small](perf_data/framework_perf_comparison.md) - around 200 kilobytes, which includes the database, web server,
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
  test_Templating_Performance in the [templating tests](../src/test/java/com/renomad/minum/templating/TemplatingTests.java).
  Please understand - this does not make it the fastest in the world, but
  its ratio of speed to code size fares well.

- Embraces the bleeding edge of Java technology, like [virtual threads](https://openjdk.org/jeps/444).
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

[JDK version 21](https://jdk.java.net/21/) is _required_, since it
provides us the [virtual threads](https://openjdk.org/jeps/444) we need.

Developed in two environments:
* MacBook Pro with OS 12.0.1, with OpenJDK 21, GNU Make 3.81 and Rsync 2.6.9
* Windows 10 64-bit professional, on [Cygwin](https://www.cygwin.com/), OpenJDK 21, Gnu Make 4.4 and Rsync 3.2.7

Note that the build tool, _Gnu Make_, is already installed on Mac.  On Windows you can install
it through the Cygwin installer.  See [here](https://www.cygwin.com/packages/summary/make.html)


New Developer Setup
-------------------

Development for this project was done on a Windows machine using Cygwin and on a Mac OS machine. It is
recommended your development environment is Cygwin, Mac, or Linux.  

Some of the tests are particular about text file contents, and developers have encountered issues
when cloning the project with Windows Git, which may load text files with CRLF (carriage-return-line-feed)
line endings.  The tests have succeeded on Mac, Windows, and Linux machines.

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

1. Download the binary by clicking [here](https://jdk.java.net/21/) and selecting the Windows/x64 zip.
2. Uncompress the zip file
3. Add the home directory to your path.  The home directory of Java is the one with "bin"
   and "conf" directories, among others. if you, for example, uncompressed the
   directory to C:\java\jdk-21, then in Windows you should add it to your path,
   following these instructions:

* Click the Windows start icon
* Type `env` to get the system properties window
* Click on _Environment Variables_
* Under user variables, click the _New_ button
* For the variable name, enter `JAVA_HOME`, and for the value, enter `C:\java\jdk-21`
* Edit your _Path_ variable, click _New_, and add `%JAVA_HOME%\bin`

Java on Mac:
------------

1. You will need your JAVA_HOME variable set.  Check [this stackoverflow answer](https://stackoverflow.com/a/22842806/713809)

HOWTO
-----

A series of HOWTO's for essential capabilities:

* [How to add a new endpoint](howto/add_a_new_endpoint.md)


Versioning
----------

The version of the project is set in the Makefile, with a property name of VERSION.
We have followed semantic versioning.  The versions are numbered like 3.1.0, with the
first number being the major number, the second being the feature number, and the last
being the patch number.  If the major number is increased, that means it will cause
known breaking changes - it will be necessary to update the code in systems with a
dependency on Minum.  The feature number increasing means that there will be new
capabilities in the system, but nothing should be broken or needing adjustment. The
last number means corrections or improvements were applied - bug fixes, refactorings,
documentation.

Mutation Testing
----------------

Be patient - this takes about 10 minutes.

```shell
make mutation_test
```

alternately:
```shell
mvn test-compile org.pitest:pitest-maven:mutationCoverage
```

To test the web server with Telnet
----------------------------------
It is possible to use the "telnet" tool to have a more immediate conversation with the 
web server.  Try this:

    telnet localhost 8080

    GET / HTTP/1.1
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

The assertions are minimalistic.  Here's assertEquals:

```java
  public static <T> void assertEquals(List<T> left, List<T> right, String failureMessage) {
    if (left.size() != right.size()) {
        throw new TestFailureException(
                String.format("different sizes: left was %d, right was %d. %s", left.size(), right.size(), failureMessage));
    }
    for (int i = 0; i < left.size(); i++) {
        if (!left.get(i).equals(right.get(i))) {
            throw new TestFailureException(
                    String.format("different values - left: \"%s\" right: \"%s\". %s", showWhiteSpace(left.get(i).toString()), showWhiteSpace(right.get(i).toString()), failureMessage));
        }
    }
  }
```

The rest of the testing framework is about the same.  It's never simple,
but we can choose to work with fewer tools.

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

Logging capabilities are provided by the `Logger` class, with an interface defined in `ILogger`.  When 
the system starts, it creates one instance of this class and then stores that in an instance of Context, which
is passed around everywhere, making access widely available.

The Logger class has a constructor which is specialized to enable creation of descendant logging classes which
share a `LoggingActionQueue`, enabling smooth output with customized logging.  See LoggerTests.testUsingDescendantLogger
to see an example of this.

Threads
-------

If you need to run parts of your program in their own threads, it is available by creating
threads and running them in an instance of the ExecutorService, which is available in the Context instance
that is passed around the system.  for an example of its use, see `LoopingSessionReviewing`
in the test folder.  In this case, it requires the thread so that it can periodically wake up 
and check whether any of the authentication sessions are old and need to be removed.

Our project uses virtual threads, a very recent addition to the Java language.  Also known
as "Project Loom", it enables our server to handle many concurrent requests with minimal
resource needs.

Routing
-------

See `TheRegister.java` in the tests folder for an example of how routing works.  When you
first start the program, you are given a WebFramework class instance, which has a method
called `registerPath()`.  

Most verbs, like GET, PUT and POST are available.  The function you provide must be
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
and put them away for a while:

    "123.123.123.123_vuln_seeking", 604800000


Templates
---------

There's a few things to note when dealing with templates.

Template files can really be stored anywhere, but there are some conventional locations in the 
Java ecosystem.  Templates meant to be used in a webapp are commonly stored under src/main/webapp/templates.
They should then be placed inside a subdirectory there, to help organize them. It will be
necessary to read the file using plain old Java file reading - see [an example](https://github.com/byronka/minum_usage_example_mvn/blob/03a34f32e9c79fdc4a00f16d85d62eb5b8173ae6/src/main/java/com/renomad/sampledomain/SampleDomain.java#L31C27-L31C27)

In order to read those files during local dev testing, it is recommended to keep things simple - 
you can just read the file from its location in the directory structure.  Once you graduate to
running a system in production as well, you may find that the location differs between your
development environment and the production system.  In that case, I have taken to writing a
helper method that uses a value set in a property file.  See the [Memoria project config file](https://github.com/byronka/memoria_project/blob/a3e5e2d299dfa4f770f65fa902a30ac707f059ad/memoria.config#L12)
and its [template reader in a helper method](https://github.com/byronka/memoria_project/blob/a3e5e2d299dfa4f770f65fa902a30ac707f059ad/src/main/java/com/renomad/inmra/utils/FileUtils.java#L29)

Inside a template, wherever you intend to substitute values, use a syntax like this:

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

Occasionally you will need to write to files.  You may want to keep those files in the same 
root directory as the database, but in its own sub-directory, but the choice is up to you.

The steps to this are:
1. Get the configured root directory for the database: `var dbDir = Path.of(FullSystem.getConfiguredProperties().getProperty("dbdir"));`
2. Create the path for a sub-directory: `photoDirectory = dbDir.resolve("photo_files");`
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

Here is an example of how I chose to implement authentication in some of my projects running Minum.
This is arbitrary - the way you develop this is up to you, but maybe this example will help.

See [the authentication code](https://github.com/byronka/memoria_project/blob/a3e5e2d299dfa4f770f65fa902a30ac707f059ad/src/main/java/com/renomad/inmra/auth/AuthUtils.java#L64)
and [an example of applying it](https://github.com/byronka/memoria_project/blob/a3e5e2d299dfa4f770f65fa902a30ac707f059ad/src/main/java/com/renomad/inmra/featurelogic/persons/PersonCreateEndpoints.java#L67)

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


On minimalism:
--------------

* Aim for success in providing value, not in seeming complex.
* It is less desirable to support multitudes of textual data formats: e.g. XML, JSON, YAML.  Fewer is better.
* The HTML specification is gigantic.  Fully proper parsing is often unnecessary - many (most?) times, we are parsing our own HTML. This is
  how it is possible to utilize a simplified parsing engine, which is marginally easier to debug and maintain.
* There is no need for large varieties of test assertion types.  "assertTrue" and "assertEquals" account for most situations.
* logic-free templates are easier to debug.
* Annotations and magic method names ("getFooById") save little time at the onset, and add time later in the form of confusion and searching documentation.
* Monoliths are easier to maintain than microservices, and thus better for most projects.
* Server-side rendering is simpler and easier to maintain than having a separate front-end and back-end project.
* Starting out simply is better than planning for massive scale.
* Especially at the onset, a single database without replicas is sufficient.
* A database built into the application is better than needing a separate machine and network connection.
* Running a single binary is better than a distributed application for most scenarios.
* Minimal JavaScript, as an optional usability improvement, is good.  More is unsustainable.
* Substantially less code enables better confidence and enables you to understand what is really happening without resorting to experts.
* Good automated testing and documentation is a necessity for professional work.
* System-thinking is a key ingredient.  Assembling a hodgepodge of "best-of-class" components that have different
  mental models and qualities does not necessarily result in the best system.  Relatedly, building just enough for what you need enables flexibility.
* It is not possible to build a simple program, but you can and should aim for simplicity.
* Thrift is a underestimated merit in our field, contributing to long-term success.
* Your work does not need to focus on making money.
* Demand that your system be understandable.  Too often, complex systems are treated like biology or voodoo.  Countering
  this requires better testing and documentation.
* Good work requires time to consider alternatives and to avoid stress.
* Get over your fascination with the new and shiny.  That is not how professional work is done. On the other hand, keep
  tabs on what is new.
* Many of our choices are based on fear.  Understanding this can aid our critical thinking.
* Patience and persistence are the foundations of excellence.  Minimalism is nothing more than these in action.


Theme
-----

Software is often written in a rush.  It feels like we're expected to get as many
capabilities added as fast as possible.  But what would happen if we weren't so
pressured for time?  We could explore different avenues.  If we wrote in a quality
fashion, and included good tests, we could refactor fearlessly.

* What would happen if you built software in the simplest possible way from scratch?
* What if our team held quality sacred?
* What if we spent all the necessary time to think things through?
* What if we incorporated diverse perspectives?
* What if testing drove the design?
* If we understand that our software is a reflection of our culture, should we not focus on improving that first?


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


>It is better to do a simple thing today and pay a little more tomorrow to change it
>if it needs it, than to do a more [complicated](simplify_then_add_lightness.md) thing today
>that may never be used anyway.
>
> -- _Kent Beck Extreme Programming Explained_

> My experience is that the weird tests you end up having to write just to cause some 
> obscure branch to go one way or another end up finding problems in totally unrelated 
> parts of the system. One of the chief benefits of 100% MC/DC is not so much that every 
> branch is tested, but rather that you have to write so many tests, and such strange, 
> weird, convoluted, and stressful tests, that you randomly stumble across (and fix) 
> lots of problems you would have never thought about otherwise.
> 
>  -- Dwayne Richard Hipp

>Keep it simple, stupid
>
> -- _Kelly Johnson, Lockheed Skunk Works_


ActionQueue
-----------

[ActionQueue](../src/main/java/com/renomad/minum/queue/ActionQueue.java) lets you run thread-contended actions safely.  Let's unpack 
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
enqueue call immediately returns.  For example, looking at this code, here's an example usage from `Db.java`:

```Java
        actionQueue.enqueue("update data on disk", () -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update "+file+" but it doesn't exist");
            writeString(fullPath, data.serialize());
        });
```

To instantiate an actionQueue, code something like this:

```Java
actionQueue = new ActionQueue("a unique name here for your actionqueue", context).initialize();
```

Any time it is important to put something to be done later, ActionQueue is ready to help.  But note it has some
drawbacks.  For one, you won't get exceptions bubbling up from your call.  If you need to immediately return
a 400 error to a user based on some calculation, it wouldn't make sense to use ActionQueue there.  And like I 
mentioned, the other options - `synchronized`, Locks - are also good in many cases.


Dependency Injection
--------------------

Dependency injection has multiple meanings.  It can mean a general practice of having methods
receive objects or functions it requires instead of building them itself.  This term can also 
refer to the automated ways in which the framework makes instances of that class available.

Some of the most popular Java web frameworks have this functionality as their core feature.
Minum does not provide this, opting for simpler non-automated approaches, exemplified
in its own programming and projects like [Memoria](https://github.com/byronka/memoria_project)

See [this blogpost](https://renomad.com/blogposts/minum_ioc.html) for further explanation.


Sending larger and streaming data
---------------------------------

Minum provides the capability to transmit large data, such as large files or streaming
content.  This feature was built with a primary use case in mind - sending videos as
part of web pages.

There are a few ways to do this:

1) Any data in the static files directory that is larger than a million bytes will
   be sent by streaming.
2) In a web handler, when returning a Response object, there is a factory method
   expecting a file path.  Using this will send the file's data as a stream, and
   is thread safe.  See `Response.buildLargeFileResponse`
3) There is also a factory method for sending a custom stream.  See `Response.buildStreamingResponse`
