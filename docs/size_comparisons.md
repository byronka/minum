Size statistics: 
===============

Web frameworks
--------------

| Minum | Javalin | Spring Boot |
|-------|---------|-------------|
| 3,786 | 141,048 | 1,085,405   |


#### Our project is 37 times smaller than [Javalin](https://javalin.io/), which bills itself as

>"A simple web framework for Java and Kotlin"
> 
>"Lightweight - Javalin is just a few thousand lines of code on top of Jetty, and its 
> performance is equivalent to raw Jetty code. Due to its size, it's very easy to 
> reason about the source code.

We'll see that, and raise you ***a web framework with zero dependencies*** 


Templating
----------

| Minum | Mustache | Pebble | Rocker |
|-------|----------|--------|--------|
| 73    | 11,346   | 16,876 | 11,996 |



Database
--------

| Minum | Postgresql | MySQL     | SQLite  |
|-------|------------|-----------|---------|
| 152   | 1,300,000  | 1,500,000 | 116,000 |


Logging
-------

| Minum | log4j  | slf4j |
|-------|--------|-------|
| 94    | 70,000 | 3,400 |


Web server
----------

| Minum | Nginx   | Tomcat  |
|-------|---------|---------|
| 1456  | 163,000 | 242,000 |


HTML Parser
-----------

| Minum | JSoup  |
|-------|--------|
| 304   | 14,337 |


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
