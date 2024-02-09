Minum Web Framework
===================

When you want to write a web application that is fast and maintainable and (big-picture) less expensive.  For 
when you prefer a stable foundation with minimal dependencies and not a [teetering tower](https://xkcd.com/2347/).

If these speak to you:

* https://renegadeotter.com/2023/09/10/death-by-a-thousand-microservices.html
* http://mcfunley.com/choose-boring-technology
* https://www.teamten.com/lawrence/writings/java-for-everything.html
* https://www.timr.co/server-side-rendering-is-a-thiel-truth/

If you realize there is no silver bullet, there are no shortcuts, that hard work is the only way to create 
value, then you might share my mindset about what makes this web framework valuable.

For more detail on intentions, purpose, and benefits, see the
[development handbook](docs/development_handbook.md).


Getting Started
---------------

There is a 🚀 [Quick start](docs/quick_start.md), or if you have
a bit more time, consider trying the [tutorial](docs/getting_started/getting_started.md)


Maven
-----

Published at [Maven Central Repository](https://central.sonatype.com/artifact/com.renomad/minum)


Features:
--------

- [Secure TLS 1.3 HTTP/1.1 web server](src/main/java/com/renomad/minum/web)
- [In-memory database with disk persistence](src/main/java/com/renomad/minum/database)
- [Server-side templating](src/main/java/com/renomad/minum/templating)
- [Logging framework](src/main/java/com/renomad/minum/logging)
- [Testing framework](src/main/java/com/renomad/minum/testing)
- [HTML parsing](src/main/java/com/renomad/minum/htmlparsing) 


Size Comparison:
----------------

Compiled size: 150 kilobytes.

_Lines of production code (including required dependencies)_

| Minum | Javalin | Spring Boot |
|-------|---------|-------------|
| 3,870 | 141,048 | 1,085,405   |

See [details](docs/size_comparisons.md)


Performance:
------------

* 19,000 http responses per second by web server. [detail](docs/perf_data/response_speed_test.md)
* 2,000,000 updates per second to database. [detail](docs/perf_data/database_speed_test.md)
* 27,000 templates per second rendered. [detail](docs/perf_data/templateRenderTest.md)

See [framework performance comparison](docs/perf_data/framework_perf_comparison.md)


Documentation:
--------------

* [Development handbook](docs/development_handbook.md)
* [Javadocs](https://renomad.com/javadoc/)
* [Various reports](https://renomad.com/site/project-reports.html) 
* [Mutation test report](https://renomad.com/pit-reports)


Example projects demonstrating usage:
-------------------------------------

See the following links for sample projects that use this framework.

- [Example](https://github.com/byronka/minum_usage_example_mvn) 

This is a good example to see a basic project with various functionality. It
shows many of the typical use cases of the Minum framework.

- [Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)

This project is valuable to see the minimal-possible application that can
be made.  This might be a good starting point for use of Minum on a new project.

