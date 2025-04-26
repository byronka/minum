Minum Web Framework
===================

This project was built by hand, and demonstrates what is 
possible by craft and minimalism.

It prioritizes the concept of high-quality
monolithic server-side-rendered web applications.  There are several examples
of this in the [example projects](#example-projects-demonstrating-usage).

Here is a small Minum program (see more [code samples](#code-samples) below):

```Java
public class Main {
    public static void main(String[] args) {
        var minum = FullSystem.initialize();
        var wf = minum.getWebFramework();
        wf.registerPath(GET, "",
                r -> Response.htmlOk("<p>Hi there world!</p>"));
        minum.block();
    }
}
```

What is this?
--------------

This web framework, "Minum", provides a full-powered minimalist foundation for a web 
application. Built from scratch by TDD (Test-Driven Development).

* Embraces the concept of _kaizen_: small beneficial changes over time leading to impressive capabilities
* Has its own web server, endpoint routing, logging, templating engine, html parser, assertions framework, and database
* 100% test coverage (branch and statement) that runs in 30 seconds without any special setup (`make test_coverage`)
* Nearly 100% mutation test strength using the [PiTest](https://pitest.org/) tool. (`make mutation_test`)
* Relies on no dependencies other than the Java 21 SDK - i.e. no Netty, Jetty, Tomcat, Log4j, Hibernate, MySql, etc.
* Well-documented
* No reflection
* No annotations
* No magic
* Has [examples of framework use](#example-projects-demonstrating-usage)


Minum is five thousand lines of ordinary and well-tested code: hashmaps, sockets, etc. The "minimalist" competitors 
range from 400,000 to 700,000 lines when accounting for their dependencies.

Applying a minimalist approach enables easier debugging, maintainability, and lower overall cost. Most 
frameworks trade faster start-up for a higher overall cost. If you need sustainable quality, the software 
must be well-tested and documented from the onset.  As an example, this project's ability to attain such
high test coverage was greatly enabled by the minimalism paradigm.

Minum follows [semantic versioning](https://semver.org/)


Getting Started
---------------

There is a 🚀 [Quick start](docs/quick_start.md), or if you have
a bit more time, consider trying the [tutorial](docs/getting_started/getting_started.md)


Maven
-----

[Maven central repository](https://central.sonatype.com/artifact/com.renomad/minum)

```xml
<dependency>
    <groupId>com.renomad</groupId>
    <artifactId>minum</artifactId>
    <version>8.1.1</version>
</dependency>
```


Features:
--------

- Secure TLS 1.3 HTTP/1.1 web server
- In-memory database with disk persistence
- Server-side templating
- Logging 
- Testing utilities
- HTML parsing
- Background queue processor


Size Comparison:
----------------

Compiled size: 200 kilobytes.

_Lines of production code (including required dependencies)_

| Minum | Javalin | Spring Boot |
|-------|---------|-------------|
| 5,373 | 141,048 | 1,085,405   |

See [a size comparison in finer detail](docs/size_comparisons.md)


Performance:
------------

Performance is a feature. On your own applications, collect 
performance metrics at milestones, so that trends and missteps are made apparent.


* 19,000 http web server responses per second. [details here](docs/perf_data/response_speed_test.md)
* 2,000,000 database updates per second. [details here](docs/perf_data/database_speed_test.md)
* 31,717 templates rendered per second. See "test_Templating_Performance" [here](src/test/java/com/renomad/minum/templating/TemplatingTests.java).
  Also, see this [comparison benchmark](https://github.com/byronka/template-benchmark?tab=readme-ov-file#original-string-output-test), with Minum's
  code represented [here](https://github.com/byronka/template-benchmark/blob/utf8/src/main/java/com/mitchellbosecke/benchmark/Minum.java).

See a [Minum versus Spring performance comparison](docs/perf_data/framework_perf_comparison.md)


Documentation:
--------------

* [Development handbook](docs/development_handbook.md)
* [Javadocs](https://renomad.com/javadoc/)
* [Code coverage](https://renomad.com/site/jacoco/index.html) 
* [Mutation test report](https://renomad.com/pit-reports)
* [Test run report](https://renomad.com/site/surefire-report.html) 


Example projects demonstrating usage:
-------------------------------------

See the following links for sample projects that use this framework.

[Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)

This project is valuable to see the minimal-possible application that can
be made.  This might be a good starting point for use of Minum on a new project.

<hr>

[Example](https://github.com/byronka/minum_usage_example_mvn) 

This is a good example to see a basic project with various functionality. It
shows many of the typical use cases of the Minum framework.

<hr>

[Memoria project](https://github.com/byronka/memoria_project)

This is a family-tree project.  It demonstrates the kind of
approach this framework is meant to foster.

<hr>

[Restaurants](https://github.com/byronka/restaurants)

Restaurants is a prototype project to provide a customizable ranked list of restaurants.

<hr>

[Alternate database](https://github.com/byronka/minum_using_alternate_database)

This is a project which uses a SQL database called [H2](https://www.h2database.com/html/main.html),
and which shows how a user might go about including a different database than the one built-in.


Code samples
------------

_Instantiating a new database_:

```java
var db = new Db<>(foosDirectory, context, new Foo());
```

The Minum database keeps its data and processing primarily in memory but persists to the disk.
There are pros and cons to this design choice: on the upside, it's very fast and the data
stays strongly typed.  On the downside, if you're not careful you could end up using
a lot of memory.  For certain designs, this is a suitable design constraint.  On the [Memoria
project](https://github.com/byronka/memoria_project/), the only data stored in the database is the "lean" information - user tables,
sessions, primary data.  Anything beyond the basics is stored in files and read from the disk
as needed, with some caching to improve performance.

Obviously this won't work for all situations, and users are free to pick any other database 
they desire (See "Alternate database" project above for an example project using a third-party 
database). That said, the aforementioned will work for many common situations and for prototypes, 
particularly if expectations are adjusted for what to store in the database.

<hr>

_Adding a new object to a database_:

```java
var foo = new Foo(0L, 42, "blue");
db.write(foo);    
```

_Updating an object in a database_:

```java
foo.setColor("orange");
db.write(foo);    
```

_Deleting from a database_:

```java
db.delete(foo);    
```

_Writing a log statement_:

```java
logger.logDebug(() -> "hello");
```

The logs are output to "standard out" during runtime.  This means, if you run a Minum application
from the command line, it will output its logs to the console.  This is a typical pattern for servers.

The logs are all expecting their inputs as closures - the pattern is `() -> "hello world"`.  This keeps
the text from being processed until it needs to be.  A profusion of log statements
could impact the performance of the system.  By using this design pattern, those statements will only be
run if necessary, which is valuable for trace-level logging and those log statements which include
further processing (e.g. `_____ has requested to _____ at _____`).

<hr>

_Parsing an HTML document_:

```java
List<HtmlParseNode> results = new HtmlParser().parse("<p></p>");
```

Minum includes a simple HTML parser.  While not as fully-featured as its big brothers, it is well suited
for its minimal purposes, and provides capabilities like examining returned HTML data or for use in functional tests.
It is used heavily in the [Memoria tests](https://github.com/byronka/memoria_project/blob/c474040aac46b52bc48341b5972c8d9d1c438da8/src/test/java/com/renomad/inmra/featurelogic/FunctionalTests.java#L165)
and the [FamilyGraph class](https://github.com/byronka/memoria_project/blob/master/src/main/java/com/renomad/inmra/featurelogic/persons/FamilyGraph.java) which
handles building a graph of the family members.
<hr>

_Searching for an element in the parsed graph_:

```java
HtmlParseNode node;
List<HtmlParseNode> results = node.search(TagName.P, Map.of());
```

_Creating a new web handler (a function that handles an HTTP request and
returns a response)_:
```java
public Response myHandler(Request r) {
  return Response.htmlOk("<p>Hi world!</p>");
}
```

The term "web handler" refers to the bread-and-butter of what Minum provides - programs that receive
HTTP requests and return HTTP responses.  This example demonstrates returning an HTML message, ignoring
the request data.  A less contrived example would examine the "query string" or the "body" of
the request for its data, and then returning an appropriate response based on that.

For example, there is [sample code in Minum](https://github.com/byronka/minum/blob/17bc2b5759727e97987092187844a0cbfc90a7bd/src/test/java/com/renomad/minum/sampledomain/SampleDomain.java#L50)
which checks the authentication and returns values as HTML.  There are other example endpoints in that
class, and you may see these endpoints in operation by running `make run_sampledomain` from the command line, presuming you
have installed Java and GNU Make already, and then by visiting http://localhost:8080.

<hr>

_Registering an endpoint_:

```java
webFramework.registerPath(GET, "formentry", sd::formEntry);
```

The expected pattern is to have a file where all the endpoints are registered.  See [Memoria's endpoint registration page](https://github.com/byronka/memoria_project/blob/c474040aac46b52bc48341b5972c8d9d1c438da8/src/main/java/com/renomad/inmra/TheRegister.java#L56)

_Building and rendering a template_:

```java
TemplateProcessor myTemplate = TemplateProcessor.buildProcessor("hello {{ name }}");
String renderedTemplate = myTemplate.renderTemplate(Map.of("name", "world"));
```

The Minum framework is driven by a paradigm of server-rendered HTML.  Is is performant and
works on all devices.  In contrast, the industry's current predominant approach is single
page apps, whose overall system complexity is greater.  Complexity is a dragon we must fight daily.

The templates can be any string, but the design was driven concerned with rendering HTML templates.  Here is [an example of a simple template](https://github.com/byronka/minum/blob/master/src/test/webapp/templates/sampledomain/name_entry.html),
which is rendered with dynamic data in [this class](https://github.com/byronka/minum/blob/master/src/test/java/com/renomad/minum/sampledomain/SampleDomain.java) 

HTML templates must consider the concept of _HTML sanitization_, to prevent XSS, when the data is coming
from a user.  To sanitize content of an HTML tag (e.g. `<p>{{user_data}}</p>`), sanitize the data 
with `StringUtils.safeHtml(userData)`, and for attributes (e.g. `class="{{user_data}}"`), 
use `StringUtils.safeAttr(userData)`.  Putting this all together:

_template_

```html
<div>{{user_name}}</div>

<div data-name="{{user_name_attr}}">hello</div>
```

_code_

```java
String renderedTemplate = myTemplate.renderTemplate(
  Map.of(
    "user_name", StringUtils.safeHtml(username),
    "user_name_attr", StringUtils.safeAttr(username)
  ));
```

<hr>

It is a common pattern to get user data from requests by query string or body.
The following examples show this:

_Getting a query parameter from a request_:

```java
String id = r.requestLine().queryString().get("id");
```

_Getting a body parameter from a request, as a string_:

```java
String personId = request.body().asString("person_id");
```

_Get a path parameter from a request as a string_:

```java
Pattern requestRegex = Pattern.compile(".well-known/acme-challenge/(?<challengeValue>.*$)");
final var challengeMatcher = requestRegex.matcher(request.requestLine().getPathDetails().isolatedPath());
// When the find command is run, it changes state so we can search by matching group
if (! challengeMatcher.find()) {
    return new Response(StatusLine.StatusCode.CODE_400_BAD_REQUEST);
}
String tokenFileName = challengeMatcher.group("challengeValue");
```

This more complicated scenario shows handling a request from the LetsEncrypt ACME challenge 
for renewing certificates.  Because the incoming request comes as a "path parameter", we
have to extract the data using a regular expression.

In this example, if we don't find a match, we return a 400 error HTTP status code, and 
otherwise get the data by a named matching group in our regular expression.

To register an endpoint that allows "path parameters", we register a partial path, like the 
following, which will match if the provided string is contained anywhere in an incoming URL. There 
are some complications to matching this way, so it is recommended to use this approach
as little as possible.  In the Memoria project, this is only used for 
LetsEncrypt, which requires it.  All other endpoints get their user data from query 
strings, headers, and bodies.

```java
webFramework.registerPartialPath(GET, ".well-known/acme-challenge", letsEncrypt::challengeResponse);
```

<hr>

_Getting a body parameter from a request, as a byte array_:

```java
byte[] photoBytes = body.asBytes("image_uploads");
```

The photo bytes example is seen in the [UploadPhoto class](https://github.com/byronka/minum/blob/master/src/test/java/com/renomad/minum/sampledomain/UploadPhoto.java)

_Checking for a log message during tests_:

```java
assertTrue(logger.doesMessageExist("Bad path requested at readFile: ../testingreadfile.txt"));
```

The Minum application was built using test-driven development (TDD) from the ground up.  The testing
mindset affected every aspect of its construction.  One element that can sometimes trip up 
developers is when they are testing that something happened elsewhere in the system as a result of 
an action.  If that separate action has logging, then a test can examine the logs for a correct
output.