Minum Framework
===============

A web framework demonstrating _"less is more"_.

> It is better to do a simple thing today and pay a little more tomorrow to change it
> if it needs it, than to do a more complicated thing today that may never be used anyway.
> 
> -- Kent Beck, _Extreme Programming Explained_

Features:
--------

- secure HTTP/1.1 web server
- in-memory database with disk persistence
- efficient server-side templating
- defensive security capabilities
- logging
- testing
- authentication


Quick start:
------------

* To test: `make test`
* To create a library jar: `make jar`
* For help: `make help`

See [Dependencies](#system-requirements-)

Example projects demonstrating usage:
-------------------------------------

See the following links for sample projects that use this framework.

- [One with a few features](https://github.com/byronka/minum_usage_example) 
- [Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)


Why?
----

To build software like if I was allowed enough time and autonomy. It 
must be quality - built using TDD - [test-driven development](https://www.google.com/books/edition/Test_driven_Development/CUlsAQAAQBAJ?hl=en). Something 
I would be proud to use for future work. 

I benefit from several aspects:

- The binary is tiny - 150 kilobytes.  That includes the database code, the web server,
  the templating library, the logging utilities, the HTML parsing library.  The [example projects](#example-projects-demonstrating-usage-)
  show how to continue that pattern with the business logic so that the total
  remains small.  With such a small size, everything becomes faster - moving
  the project onto your production server takes just a couple seconds.  That's part of
  how I am able to type a command and have new code running on the production server just
  seconds later.
- The code is minimal (See [size comparison](docs/size_comparison_to_javalin.md)).  It doesn't handle every imaginable case, but there's so little
  there, I can easily add or modify the code.  There's no technical reason why I couldn't
  include extra libraries if I needed, but I pretty much have all the basics handled. 
- It's a much less difficult project to read and understand than any other web framework.
- No magic.  There's no special machinery running in the shadows, like you will often
  find with web frameworks using annotations/decorators or naming conventions.  Everywhere 
  is plain navigable code.  All the functionality is supported 
  through direct method calls. It is a higher signal-to-noise ratio when 
  examining stacktraces, making the maintenance cheaper and less stressful.
- Well-documented throughout, more supportive of long-term maintenance. I love being reminded
  of the context I am within by concise documentation, without having to read code.
- The only dependency is the Java standard library.  Using large frameworks 
  combined with many incidental dependencies leads to dependency hell.  The commonplace
  conundrum is how to stay on top of the ensuing treadmill of updates. While it may be 
  that way on most projects, it doesn't need to be that way
  here. The pendulum for avoiding programming has swung too far out for many teams. Let's
  not just be "configurers" - let's be programmers.  There's a lot we can reasonably 
  undertake - just look at this project for a sense of how little is actually needed
  for functionality when you work with a general-purpose programming language.
- My typical wait time for automated testing is several seconds. When your
  test pipeline is finished in seconds, your development team's productivity keeps humming.
  What enables this is a focus on keeping the tests fast and minimal in number but high
  in value.  If you haven't seen this approach in practice, definitely take a moment
  to read through it here.
- It is decently performant, because performance has been a target from inception. For example, 
  it can handle 9000 web requests per second. The [database](docs/perf_data/database_speed_test.txt) can perform 2 _million_ writes per 
  second.  The [templating engine](docs/perf_data/templateRenderTest.txt) crunched 27,000 renderings per second.
- It can run on a low-performance machine.  This saves me a lot of money, since I can 
  run on the free tier for cloud providers, and only need one computer.
- Embraces the bleeding edge of Java technology, like virtual threads.
  This allows it to manage, for example, ten thousand concurrent requests using hundreds of
  megabytes rather than many gigabytes.
- Other projects strive to support universal cases.  This doesn't, so there is less code here
  to hide bugs.  

  >I conclude that there are two ways of constructing a software design: One way is to
  >make it so simple that there are obviously no deficiencies and the other way is to
  >make it so complicated that there are no obvious deficiencies.
  > 
  > Tony Hoare,  _1980 ACM Turing award lecture_ 


See the [theme](docs/development_handbook.md#theme) section in
the development handbook.


System requirements: 
--------------------

[JDK version 20](https://jdk.java.net/20/) is _required_.

Developed in two environments:
* MacBook Pro with OS 12.0.1, with OpenJDK 20, GNU Make 3.81 and Rsync 2.6.9
* Windows 10 64-bit professional, on [Cygwin](https://www.cygwin.com/), OpenJDK 20, Gnu Make 4.4 and Rsync 3.2.7

Note that the build tool, _Gnu Make_, is already installed on Mac.  On Windows you can install
it through the Cygwin installer.  See [here](https://www.cygwin.com/packages/summary/make.html)


Step-by-step guide for installing Java on Windows:
--------------------------------------------------

1. Download the binary by clicking [here](https://download.java.net/java/GA/jdk20.0.1/b4887098932d415489976708ad6d1a4b/9/GPL/openjdk-20.0.1_windows-x64_bin.zip).
2. Uncompress the zip file
3. Add the home directory to your path.  The home directory of Java is the one with "bin"
   and "conf" directories, among others. if you, for example, uncompressed the
   directory to C:\java\jdk-20.0.1, then in Windows you should add it to your path,
   following these instructions:

  * Click the Windows start icon
  * Type env to get the system properties window
  * Click on Environment Variables
  * Under user variables, click the New button
  * For the variable name, enter JAVA_HOME, and for the value, enter C:\java\jdk-20.0.1
  * Edit your Path variable, click New, and add %JAVA_HOME%\bin


Developer documentation:
------------------------

See [The development handbook](docs/development_handbook.md)


Directories:
------------

- src: All the source code
- docs: documentation for the project
- .git: necessary files for Git source-code management.
- utils: testing utilities


Root-level files:
-----------------

- app.config: a configuration file for the running app (a local / test-oriented version)
- .gitignore: files we want Git to ignore.
- Makefile: the configuration for Gnu Make, which is our build tool
- README.md: this file

Appendix
--------

* [The Javadoc](https://byronka.github.io/javadoc/)
* [Test coverage](https://byronka.github.io/coveragereport/)
* [Tests](https://byronka.github.io/minum_tests.html)
