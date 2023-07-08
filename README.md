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

To demonstrate [the results](https://hasen.substack.com/p/the-stupid-programmer-manifesto) of [following](docs/parable_two_programmers.md) the [programming](https://en.wikipedia.org/wiki/Extreme_programming) [technique](https://web.stanford.edu/~ouster/cgi-bin/book.php) I offer to colleagues and clients. 
Built using [test-driven development](http://wiki.c2.com/?TestDrivenDevelopment).

Developers benefit from several aspects:

- The code is [minimalistic](docs/size_comparisons.md) on purpose.  It does not 
  handle every imaginable case, but it empowers developers to [modify the code](https://programmingisterrible.com/post/139222674273/write-code-that-is-easy-to-delete-not-easy-to).  There 
  is nothing preventing a developer from including extra libraries as needed, but the basics are handled. 

- The compiled binary is small - around 150 kilobytes, which includes the database, web server,
  templating, logging, and HTML parsing.  The [example projects](#example-projects-demonstrating-usage)
  show how to continue that pattern with the business logic.  This makes everything faster - sending to
  production takes seconds.

- It's a less difficult project to read and understand than major web frameworks.

- [No magic](https://blog.codinghorror.com/the-magpie-developer/).  There is no surprising 
  behavior.  Plain method calls help make the maintenance cheaper and less stressful.

- [Well-documented throughout](https://hackaday.com/2019/03/05/good-code-documents-itself-and-other-hilarious-jokes-you-shouldnt-tell-yourself/). 
  More supportive of long-term maintenance.

- Zero dependencies (unless you include the Java standard library).  Projects often
  incorporate many dependencies, which must be kept updated. With fewer dependencies,
  the churn is minimized. Benefit from the power of an [industrial strength general-purpose programming language](https://www.teamten.com/lawrence/writings/java-for-everything.html).

- Wait time for automated testing is seconds. When the
  test pipeline finishes that quickly, the team's productivity hums.

- Good performance, because [performance was always a goal](https://blog.nelhage.com/post/reflections-on-performance/). As an example, 
  it can respond to [19,500 web requests per second](docs/perf_data/response_speed_test.md). The [database can perform 2 _million_ writes](docs/perf_data/database_speed_test.md) per 
  second.  The [templating engine renders 27,000 times per second](docs/perf_data/templateRenderTest.md).

- It is capable of running on minimal resources.  The free-tier on cloud providers should suit it well.

- Embraces the bleeding edge of Java technology, like [virtual threads](https://openjdk.org/jeps/436).
  This allows it to manage [thousands of concurrent requests](docs/perf_data/loom_perf.md) on resource-constrained
  hardware.

- Other projects strive to support universal cases.  [Because this does not](http://josdejong.com/blog/2015/01/06/code-reuse/), there 
  is less code to hide bugs.  

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
