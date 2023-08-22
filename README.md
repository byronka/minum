Minum Framework
===============

A web framework demonstrating [_"less is more"_](http://mcfunley.com/choose-boring-technology)

> It is better to do a simple thing today and pay a little more tomorrow to change it
> if it needs it, than to do a more [complicated](docs/simplify_then_add_lightness.md) thing today 
> that may never be used anyway.
> 
> -- Kent Beck, _Extreme Programming Explained_

Minum is a framework that provides solely those components
necessary to create a web-based application, including a web server, pared-down database,
template processor, and other necessary features. It differs from a library in
that it does not merely cater for a motley assortment of needs, but instead,
covers every core need of a web application, minimalistically.

For more detail on intentions, purpose, and benefits, see the
[development handbook](docs/development_handbook.md).


## 🚀 [Quick start](docs/quick_start.md)



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

Maven
=====

These are files necessary for publishing Minum to the Maven Central repository

How to deploy to Maven Central
------------------------------

Run `make mvnprep`

There will now be a file `out/bundle.jar`, which you will use in
the process explained [here](https://central.sonatype.org/publish/publish-manual/#bundle-creation)

In case the web page is down, here's the gist of it:

1. Once bundle.jar has been produced, log into [OSSRH](https://s01.oss.sonatype.org/), and select
   Staging Upload in the Build Promotion menu on the left.
2. From the Staging Upload tab, select Artifact Bundle from the Upload Mode dropdown.
3. Then click the Select Bundle to Upload button, and select the bundle you just created.
4. Click the Upload Bundle button. If the upload is successful, a staging repository will be created, and you can proceed with [releasing](https://central.sonatype.org/publish/release/).