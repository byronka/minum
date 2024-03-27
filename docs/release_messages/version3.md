Release of Minum v3.0.0
-----------------------

- [Minum on GitHub](https://github.com/byronka/minum)
- [Migration guide to v3](../migration_to_v3.md)

A minimalist web framework written in Java, developed over five years by a TDD practitioner of fourteen years
experience. Provides an all-in-one foundation for a highly-maintainable single binary web application. For TDD, by TDD.

- Has its own web server, endpoint routing, logging, templating engine, html parser, assertions framework, and database
- Has nearly 100% test coverage that runs in 30 seconds and does not require any special setup (`make test_coverage`)
- Has nearly 100% mutation test coverage, using the PiTest tool. (`make mutation_test`)
- Relies on no dependencies other than the Java 21 SDK - No Netty, Jetty, Tomcat, Log4j, Hibernate, MySql, etc.
- Written from scratch
- Well-documented throughout
- Cleverness avoided[^1] where possible
- No reflection
- No annotations
- Uses no mocking framework - integration tests preferred, occasional use of simple interface mocks.
- Has examples of framework use:
    * [a tiny project](https://github.com/byronka/minum_usage_example_smaller), as the basis to get started
    * [a small project](https://github.com/byronka/minum_usage_example_mvn), showing some minimal use cases
    * [a full application](https://github.com/byronka/memoria_project) demonstrating realistic usage

This framework enables high-maintainability through minimalism and simplicity. Developers using this framework will
achieve the most benefit if they are of a similar mindset. Frameworks like Spring, which provide capabilities as large
blocks with high guardrails, offer a different paradigm.

FAQ:
----

* Are you saying we shouldn't be using Spring?

> No, I am not saying that. However, there is need for a minimalist web framework. You can build a fully-functioning web
> application with high sophistication with either Spring or Minum, but Minum is five thousand lines of code and Spring
> is a million. For quality-oriented long-view practitioners, the benefits of minimalism outweigh its drawbacks.

* Yet another minimalistic framework? Why??

> This is a misunderstanding - Minum is five thousand lines of code - the "minimalist" competitors range from 400,000 to
> 700,000 lines when accounting for their dependencies. I have not found a similar project.

* What is the point of minimalism?

> Easier debugging, maintainability, lower overall cost. Most frameworks trade faster start-up for a higher overall
> cost. If you need sustainable quality, the software must be well-tested and documented from the onset.

* Why not just use Java's built-in httpserver?

>* httpserver is larger and more complex, consisting of 6000 lines versus Minum's 1800.
>* There are no easily-available fast and thorough tests on httpserver, ruling out fearless refactoring.
>* There are examples of sophisticated web applications in Minum.
>* All the components in Minum work in tandem to support a web application.

* In your templating engine, where is the logic (e.g. looping, escaping, etc)?

> It does not exist - logic-free templating was intentionally chosen. Any logic must be written explicitly as code,
> which makes maintenance easier.

* Does it provide HTTP v2? Web sockets?

> It currently does not. Those features may be built in the future. The choice was made to minimize scope for now.

[^1]: Due to the necessities of structuring a web server for test-driven development, some parts of the socket / http
handling code had to be written a bit more cleverly than preferred.
