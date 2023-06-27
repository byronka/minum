Minum Framework
===============

This code provides all the functionality for a Java web application.


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


Example projects demonstrating usage:
-------------------------------------

See the following links for sample projects that use this framework.

- [One with a few features](https://github.com/byronka/minum_usage_example) 
- [Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)


Why?
----

I wanted an opportunity to build software the way I would if allowed enough time and autonomy. It 
needed to be high quality - built using test-driven development (TDD) (in a non-dogmatic fashion). By 
following this paradigm, I could develop software with such simplicity and longevity that I would
be proud to use it as an example for future work. (Also see the [theme](docs/development_handbook.md#theme) section in the development
handbook)

There are multiple practical benefits to using this framework for your web application:

- The binary is tiny - 150 kilobytes.  That includes the database code, the web server,
  the templating library, the logging utilities, the HTML parsing library.  The example
  projects show how to continue that pattern with the business logic so that the total
  remains small.  With such a small size, everything becomes faster - moving
  the project onto your production server takes just a couple seconds.
- The code is minimal.  It doesn't handle every imaginable case, but there's so little
  there, you can add or modify the code.  By the way, there's nothing preventing
  you from adding extra libraries if you need them, but all the basics are handed to you.
  It's a much less difficult project to read and understand than any other web framework.
- No magic.  There's no special machinery running in the shadows, like you will often
  find with web frameworks using annotations/decorators or naming conventions.  Everywhere 
  in the code, you will find navigable code.  All the functionality is supported 
  through direct method calls. This also provides a greater signal-to-noise ratio when 
  examining stacktraces, making the maintenance cheaper and less stressful.
- Well-documented throughout, more supportive of long-term maintenance.
- The only dependency is the Java standard library.  Using large frameworks 
  combined with many incidental dependencies leads to dependency hell.  The commonplace
  conundrum is how to stay on top of the ensuing treadmill of updates. While it may be 
  that way on most projects, it doesn't need to be that way
  here. The pendulum for avoiding programming has swung too far out for many teams. Let's
  not just be "configurers" - let's be programmers.  There's a lot we can reasonably 
  undertake - just look at this project for a sense of how little is actually needed
  for functionality when you work with a general-purpose programming language.
- The build and testing goes way faster when there's less.  **Less is more**.  When your
  test pipeline is finished in seconds, your development team's productivity keeps humming.
  In my experience, most teams are oriented to _short-term_ developer productivity. Using
  the practices exemplified here, you will attain _long-term_ productivity.
- It _moves_. It can handle 9000 web requests per 
  second! The database can perform 1 _million_ writes per second.  The templating engine
  is pretty dang fast too.  It clocked in at 27,000 renderings per second on the same
  test where other well-known frameworks like Mustache only made it to 22,000 (and ours
  uses about a hundredth as much code, meaning you can actually understand it all and
  even improve it)
- It can run on a low-performance machine.  On the lower bounds of business capability it only
  needs about 10 megabytes of memory to run.
- Embraces the bleeding edge of Java technology, like virtual threads.
  This allows it to manage, for example, ten thousand concurrent requests using hundreds of
  megabytes rather than many gigabytes.
- Other projects strive to support universal cases.  Because we don't, there is less here
  to hide bugs.  

  >I conclude that there are two ways of constructing a software design: One way is to
  >make it so simple that there are obviously no deficiencies and the other way is to
  >make it so complicated that there are no obvious deficiencies.
  > 
  > Tony Hoare,  _1980 ACM Turing award lecture_ 

System requirements: 
--------------------

JDK version 20 is _required_

Developed in two environments:
* MacBook Pro with OS 12.0.1, with OpenJDK 20, GNU Make 3.81 and Rsync 2.6.9
* Windows 10 64-bit professional, on Cygwin, OpenJDK 20, Gnu Make 4.4 and Rsync 3.2.7


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
