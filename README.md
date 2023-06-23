Minum Framework
===============

This code provides all the functionality for a web application.
It is written mostly in Java.

Features:
--------

- web server
- web path routing
- database pattern - in memory w/ eventual disk persistence
- logging
- testing framework

Quick start:
------------

* To test: `make test`
* To create a library jar: `make jar`
* For help: `make help`

example projects demonstrating usage:
-------------------------------------

- [One with a few features](https://github.com/byronka/minum_usage_example) 
- [Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)

Why?
----

I wanted an opportunity to build software the way I would if allowed enough time and autonomy. It 
needed to be high quality - built using test-driven development (TDD) (in a non-dogmatic fashion). By 
following this paradigm, I could develop software with such simplicity and longevity that I would
be proud use it as an example for future work.

There are multiple practical benefits to using this framework for your web application:

- The binary is tiny - 150 kilobytes.  That includes the database code, the web server,
  the templating library, the logging utilities, the HTML parsing library.  The example
  projects show how to continue that pattern with the business logic so that the total
  remains small.  With such a small size, everything becomes faster and easier - moving
  the project onto your production server takes just a couple seconds.
- The code is minimal.  It doesn't handle every imaginable case, but there's so little
  there, you can add or modify the code easily.  By the way, there's nothing preventing
  you from adding extra libraries if you need them, but all the basics are handed to you.
  It's a much easier project to read and understand than any other web framework.
- No magic.  There's no special machinery running in the shadows, like you will often
  find with web frameworks using annotations/decorators or naming conventions.  Everywhere 
  in the code, you will find easily-navigable code.  This also provides a greater 
  signal-to-noise ratio when examining stacktraces, making the maintenance cheaper and less 
  stressful.
- Well-documented throughout, easier to maintain into the future.
- The only dependency is the Java standard library.  That's it!  The commonplace approach
  of using large frameworks with sub-dependencies, in combination with multitudes of
  incidental dependencies, leads to dependency hell.  Why would you want to be in a 
  permanent treadmill of adjusting your code for the never-ending updates? Just cut the 
  cord.  It turns out that most of what you need doesn't require a lot of code - and 
  this project is proof of that.
- The build and testing goes way faster when there's less.  **Less is more**.  When your
  test pipeline is finished in seconds, your development team's productivity keeps humming.
  In my experience, most teams are oriented to _short-term_ developer productivity. Using
  the practices exemplified here, you will attain _long-term_ productivity.
- Oh by the way, did I mention this thing _moves_? It can handle 9000 web requests per 
  second! The database can perform 1 _million_ writes per second.  The templating engine
  is pretty dang fast too.  It clocked in at 27,000 renderings per second on the same
  test where other well-known frameworks like Mustache only made it to 22,000 (and ours
  uses about a hundredth as much code, meaning you can actually understand it all and
  even improve it)

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

- src: All the source code, including production and test code
- docs: documentation for the project
- lib: essential utilities and dependencies
- .git: necessary files for Git.
- utils: useful software for our system

Root-level files:
-----------------

- app.config: a configuration file for the running app (a local / test-oriented version)
- .gitignore: files we want Git to ignore.
- Makefile: the configuration for Gnu Make, which is our build tool
- README.md: this file
