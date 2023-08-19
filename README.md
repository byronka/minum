Minum Framework
===============

A web framework demonstrating [_"less is more"_](http://mcfunley.com/choose-boring-technology)

> It is better to do a simple thing today and pay a little more tomorrow to change it
> if it needs it, than to do a more [complicated](docs/simplify_then_add_lightness.md) thing today 
> that may never be used anyway.
> 
> -- Kent Beck, _Extreme Programming Explained_

This software provides web application
capability in a minimalist way.  Consider it an augmented
"hello world" web server, combined with a pared-down database,
template processor, and other necessary features.

This may be valuable to understand how a web server works, past the typical
echo server demonstration.  For more detail on the intentions, see the
[development handbook](docs/development_handbook.md).

Features:
--------

- Secure TLS 1.3 HTTP/1.1 web server
- In-memory database with disk persistence
- Server-side templating
- Logging framework
- Testing framework
- HTML parsing

Size Comparison:
----------------

_lines of production code (including required dependencies)_

| Minum | Javalin | Spring Boot |
|-------|---------|-------------|
| 3,757 | 255,384 | 1,085,405   |


Documentation:
--------------

* [Quick start](docs/quick_start.md)
* [Development handbook](docs/development_handbook.md)
* [Javadocs](https://byronka.github.io/javadoc/)
* [Test coverage](https://byronka.github.io/coveragereport/)
* [Test summary](https://byronka.github.io/minum_tests.html)


Example projects demonstrating usage:
-------------------------------------

See the following links for sample projects that use this framework.

- [Example](https://github.com/byronka/minum_usage_example_mvn) 

This is a good example to see a basic project with various functionality. It
shows many of the typical use cases of the Minum framework.

- [Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)

This project is valuable to see the minimal-possible application that can
be made.  This might be a good starting point for use of Minum on a new project.
