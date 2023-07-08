Minum Framework
===============

A web framework demonstrating [_"less is more"_](http://mcfunley.com/choose-boring-technology)

> It is better to do a simple thing today and pay a little more tomorrow to change it
> if it needs it, than to do a more [complicated](https://byronka.github.io/external_author_docs/simplify_then_add_lightness.txt) thing today 
> that may never be used anyway.
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
* For help: `make`

See [dependencies](#system-requirements)

Example projects demonstrating usage:
-------------------------------------

See the following links for sample projects that use this framework.

- [One with a few features](https://github.com/byronka/minum_usage_example) 
- [Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)


Why?
----

To demonstrate the results of following the programming technique I offer to colleagues and clients. 
Built using [test-driven development](https://www.google.com/books/edition/Test_driven_Development/CUlsAQAAQBAJ?hl=en).

I benefit from several aspects:

- The binary is tiny - 150 kilobytes.  That includes the database, web server,
  templating, logging, and HTML parsing.  The [example projects](#example-projects-demonstrating-usage)
  show how to continue that pattern with the business logic so that the total
  remains small.  With such a small size, everything becomes faster - moving
  the project onto your production server takes just a couple seconds.
- The code is minimal (see [size comparison](docs/size_comparisons.md)).  It doesn't 
  handle every imaginable case, but 
  there's so little there, I can easily add or [modify the code](https://programmingisterrible.com/post/139222674273/write-code-that-is-easy-to-delete-not-easy-to) .  There's no technical reason why I couldn't
  include extra libraries if I needed, but I pretty much have all the basics handled. 
- It's a less difficult project to read and understand than major web frameworks.
- [No magic](https://blog.codinghorror.com/the-magpie-developer/).  There's no special 
  machinery running in the shadows, like is typical with web frameworks using annotations
  or naming conventions.  Everywhere is plain method calls, making the maintenance cheaper and less stressful.
- [Well-documented throughout](https://hackaday.com/2019/03/05/good-code-documents-itself-and-other-hilarious-jokes-you-shouldnt-tell-yourself/), more 
  supportive of long-term maintenance. I love being reminded
  of the context I am within by concise documentation, without having to read code.
- Zero dependencies (unless you include the Java standard library).  Most projects
  end up incorporating many dependencies, which you must then keep updated.  Nope to that.
  Benefit from the power of an [industrial strengh general-purpose programming language](https://www.teamten.com/lawrence/writings/java-for-everything.html).
- My ordinary wait time for automated testing is seconds. When your
  test pipeline is finished that fast, your team's productivity hums.
  Keep the tests fast and few but high value.  If you haven't seen this before, take a moment
  to read through it here.
- It is performant, because [performance was always a goal](https://blog.nelhage.com/post/reflections-on-performance/). For example, 
  it can respond to [19,500 web requests per second](docs/perf_data/response_speed_test.md). The [database can perform 2 _million_ writes](docs/perf_data/database_speed_test.md) per 
  second.  The [templating engine renders 27,000 times per second](docs/perf_data/templateRenderTest.md).
- It can run on a low-performance machine.  This saves me a lot of money, since that lets me use 
  the free tier on cloud providers, requiring only one computer.
- Benefits from the bleeding edge of Java technology, like [virtual threads](https://openjdk.org/jeps/436).
  This allows it to manage, for example, [ten thousand concurrent requests](docs/perf_data/loom_perf.md) using hundreds of
  megabytes rather than many gigabytes.
- Other projects strive to support universal cases.  [This doesn't](http://josdejong.com/blog/2015/01/06/code-reuse/), so there is less code here
  to hide bugs.  

  >I conclude that there are two ways of constructing a software design: One way is to
  >make it so simple that there are obviously no deficiencies and the other way is to
  >make it so complicated that there are no obvious deficiencies.
  > 
  > Tony Hoare,  _1980 ACM Turing award lecture_ 


See the [theme](docs/development_handbook.md#theme) section in
the development handbook for more philosophical underpinnings.


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


Developer documentation:
------------------------

See [The development handbook](docs/development_handbook.md), in particular
the [HOWTO guide section](docs/development_handbook.md#howto).


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
