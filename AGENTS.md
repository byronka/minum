# AGENTS

Minum is a minimal Java framework to build a web applications.

Minum has zero dependencies, and is built of ordinary and well-tested
code: hashmaps, sockets, and so on.

- **DO NOT** add any dependencies. Use only Java 21+.

## Philosophy

* Embraces the concept of _kaizen_: small beneficial changes over time
  leading to impressive capabilities
* Designed with TDD (Test-Driven Development)
* Relies on no dependencies other than the Java 21 (and up) SDK - i.e.
  no Netty, Jetty, Tomcat, Log4j, Hibernate, MySql, etc.
* Is thoroughly documented throughout, anticipating to benefit
  developers' comprehension
* No reflection - The framework's method calls are all
  scope-respecting, and navigation of the code base is plain
* No annotations - unlike certain other frameworks, the design
  principle is to solely use ordinary Java method calls
  for all behavior, and configuration is minimal and relegated to a
  properties file
* No magic - nothing unusual behind the scenes, no byte-code runtime
  surprises.  Just ordinary Java


## Tests

Write a test before writing any other code. 

Run the tests after any changes.

- `make test_coverage`
- `make mutation_test`

Test coverage should be at 100%. Always look to improve the quality and quantity of tests.