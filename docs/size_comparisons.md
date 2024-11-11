Size statistics, in the form of production lines of code: 
=========================================================

<!-- TOC -->
  * [Web frameworks](#web-frameworks)
  * [Web server](#web-server)
  * [Templating](#templating)
  * [Database](#database)
  * [Logging](#logging)
  * [HTML Parser](#html-parser)
  * [Testing](#testing)
  * [Background task processing](#background-task-processing)
  * [Various helpful utilities](#various-helpful-utilities)
  * [Javascript utilities](#javascript-utilities)
  * [Javascript framework](#javascript-framework)
  * [Javascript form handling](#javascript-form-handling)
  * [Database object-relational-mapper](#database-object-relational-mapper)
  * [Database connection pooling](#database-connection-pooling)
  * [Transactions](#transactions)
  * [Appendix](#appendix)
  * [Basis for Spring Boot's size:](#basis-for-spring-boots-size)
  * [Basis for Javalin's size:](#basis-for-javalins-size)
<!-- TOC -->

Web frameworks
--------------

| Minum | Javalin | Spring Boot |
|-------|---------|-------------|
| 5,297 | 141,048 | 1,085,405   |


#### Our project is 30 times smaller than [Javalin](https://javalin.io/), which bills itself as

>"A simple web framework for Java and Kotlin"
> 
>"Lightweight - Javalin is just a few thousand lines of code on top of Jetty, and its 
> performance is equivalent to raw Jetty code. Due to its size, it's very easy to 
> reason about the source code.

We'll see that, and raise you ***a web framework with zero dependencies***

Minum does not require a separate web server, database, or HTML parsing - it is
all built-in.  It even includes a testing framework!


Web server
----------

The web server is sufficiently performant and capable for most cases.  It also makes use of
some security algorithms to protect itself in the harsh modern internet environment.  It is also
well-tested and there are examples of its use linked on the top-level README.

| Minum | Nginx   | Tomcat  |
|-------|---------|---------|
| 2586  | 163,000 | 242,000 |


Templating
----------

The templating is logic-free - the only syntax is to use a key inside
double brackets.  Any extras, such as sanitizing text for HTML, or
looping, is implemented through ordinary code, which is far easier to debug.
See an example of sophisticated use [here](https://github.com/byronka/template-benchmark/blob/utf8/src/main/java/com/mitchellbosecke/benchmark/Minum.java).

| Minum | Mustache | Pebble | Rocker | Thymeleaf |
|-------|----------|--------|--------|-----------|
| 146   | 11,346   | 16,876 | 11,996 | 43,056    |


Database
--------

The database is exceptionally simplistic but stable and minimal.  Any other database may
be used in its place - the framework does nothing to hinder that choice.

| Minum | Postgresql | MySQL     | SQLite  |
|-------|------------|-----------|---------|
| 243   | 1,300,000  | 1,500,000 | 116,000 |


Logging
-------

The logging is as simple as possible, without causing undue negative performance impacts.
Each method takes a lambda to be run later in a queue.  The lack of complexity means less
surface area for bugs, and the performance is more than acceptable in most cases. 

| Minum | log4j  | slf4j |
|-------|--------|-------|
| 374   | 70,000 | 3,400 |


HTML Parser
-----------

This is a parser for the common case in web application development - parsing one's
own HTML code, for such reasons as extracting data or testing.

| Minum | JSoup  |
|-------|--------|
| 558   | 14,337 |


Testing
-------

These are mostly basic assertion functions, adhering to tenets of minimalism.  There is
no need for an overabundance of assertion types - assertTrue and assertEquals works for 
most cases.

| Minum | JUnit4 |
|-------|--------|
| 184   | 10,834 |


Background task processing
--------------------------

This is provided by ActionQueue, in the queue package.  See the development_handbook in the docs.

| Minum | Jobrunr |
|-------|---------|
| 151   | 22,581  |


Various helpful utilities
-------------------------

These utilities were sufficient for the development of a fully-featured web application.  
Perhaps there are other utilities that would assist, but in nearly every case I encounter,
there is a basic-Java-only approach that obviates an extra dependency.

| Minum | Apache Commons IO |
|-------|-------------------|
| 632   | 18,045            |


Javascript utilities
--------------------

There are no JavaScript utilities provided.  You may add these if you wish, but 
do not underestimate the power of vanilla JavaScript.

| Minum | Various |
|-------|---------|
| 0     | Varies  |


Javascript framework
--------------------

The prevailing pattern for new web applications is to include a reactive web dependency
such as React.  However, the majority of new web applications lack any real need for
that kind of reactivity, being mostly a series of forms.  See this well-written essay
on why [server-side rendering is better in most cases](https://www.timr.co/server-side-rendering-is-a-thiel-truth/).

| Minum | React  |
|-------|--------|
| 0     | 13,000 |


Javascript form handling
------------------------

If you don't require a reactive JavaScript library, then vanilla form handling is already provided,
meaning less code.

| Minum | react-hook-forms |
|-------|------------------|
| 0     | 4,682            |


Database object-relational-mapper
---------------------------------

If you use simpler communication with the database, respecting the difference between a
tabular structure and your objects, there won't be as many leaky abstractions and giant
dependencies.  Further, if you make use of the database bundled with Minum, there is no 
need for mapping at all - the data remains a collection of objects in memory, with no
friction at all.

| Minum | Hibernate |
|-------|-----------|
| 0     | 452,000   |


Database connection pooling
---------------------------

If you don't run your database as a separate server, then you don't need to worry about
pooling your socket connections for it.  Nor do you worry about authentication.

| Minum | HikariCP |
|-------|----------|
| 0     | 5,435    |

Transactions
------------

It is occasionally necessary to ensure that certain actions take place completely, or
not at all.  This is not a difficult programming assignment - that is, it certainly
has edge cases and involves tedious work, but it is not beyond an experienced developer
to write proper code, guided by testing, that meets the business needs correctly.  

This is a perfect example of the kind of universal dependency inclusion that ends up making our
systems indecipherable and unmaintainable, despite assertions to the contrary, and fills up 
sites like StackOverflow with questions about its byzantine complexity, where a search 
for "@Transactional" yields twenty-five thousand results.

| Minum | Spring @Transactional implementation |
|-------|--------------------------------------|
| 0     | 7,132                                |


Appendix
--------

_(all measurements of lines of code are for production code - that is, non-test-code)_

used [cloc](https://github.com/AlDanial/cloc/) for lines-of-code calculations.

Basis for Spring Boot's size:
-----------------------------

| Component              | LOC       |
|------------------------|-----------|
|                        |           |
| essential dependencies |           |
| logback                | 33,654    |
| jackson-core           | 31,035    |
| jackson-databind       | 75,165    |
| micrometer             | 48,659    |
| 	log4j                 | 113,377   |
| tomcat                 | 258,583   |
| 	slf4j                 | 3,000     |
| 	spring-framework      | 365,961   |
| 	spring-boot           | 156,061   |
| 	                      |           |
| 	total                 | 1,085,405 |


Basis for Javalin's size:
-------------------------

_Note that this is a conservative measure - I'm not even counting the subdependencies, but
instead only one layer deep.  Also note that Minum contains code for automated testing and 
makes concientious decisions to provide the user-oriented functionality without requiring this
much technology - therefore, it is a valid apples-to-apples comparison_

| Component               | LOC     |
|-------------------------|---------|
|                         |         |
| Javalin                 | 5,485   |
|                         |         |
| essential dependencies  |         |
| slf4j-api               | 3,070   |
| slf4j-simple            | 423     |
| jetty-server            | 37,176  |
| jetty-webapp            | 7,126   |
| websocket-jetty-server  | 890     |
| websocket-jetty-api     | 800     |
| ***subtotal***          | 49,485  |
|                         |         |
| optional dependencies:  |         |
| jvmbrotli               | 895     |
| brotli4j                | 1,492   |
| jackson-databind        | 73,242  |
| jackson-module-kotlin   | 1,407   |
| gson                    | 9,042   |
| ***subtotal***          | 86,078  |
|                         |         |
| ***total***             | 141,048 |
