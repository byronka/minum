ATQA Framework
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
