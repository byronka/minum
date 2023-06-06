ATQA Framework
===============

This code provides all the functionality for a low-risk web application.
It has very few dependencies, and is written mostly in Java. This software
is meant as a basis for a variety of useful domains.

Note that the database is non-ACID compliant.  It stores all data to
memory and puts the data into a queue to be eventually (often < 1
millisecond later) written to disk.

Benefits of this software is the intention of simplicity and
documentation.  It should be easier to modify and maintain than most
frameworks. _Please note_: I am not saying it's **simple**, I'm saying that I _value
simplicity_ and have put my efforts towards that as a goal.


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
* To create a library jar: `make publish`
* For help: `make help`

There are also sample projects using this framework:

- [One with a few features](https://github.com/byronka/atqa_usage_example) 
- [Smallest-possible](https://github.com/byronka/atqa_usage_example_smaller)

System requirements: 
--------------------

JDK version 20 is _required_

Developed in two environments:
* MacBook Pro with OS 12.0.1, with OpenJDK 20, GNU Make 3.81 and Rsync 2.6.9
* Windows 10 64-bit professional, on Cygwin, OpenJDK 20, Gnu Make 4.4 and Rsync 3.2.7

Developer documentation:
------------------------

See `docs/development_handbook.md`

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
