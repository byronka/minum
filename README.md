Minum Web Framework
===================

This codebase provides the facilities necessary to build a web
application, with a design that prioritizes server-side-rendering.
Basic capabilities that any web application would need, like
persistent storage or templating, are provided.  See _Features_
further down in this document.

You might find this project well-suited to your needs if your priority
is maintainability and quality, and if you are aiming to build
programs that last years with minimal upkeep.

There are several examples of projects built with this framework in
the _example projects_ below.

Here is a small Minum program (see more _code samples_ below):

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

The high level characteristics
-------------------------------

| Capability     | Rating       |
|----------------|--------------|
| Small          | `[#######-]` |
| Tested         | `[#######-]` |
| Documented     | `[######--]` |
| Performant     | `[######--]` |
| Maintainable   | `[#######-]` |
| Understandable | `[#######-]` |
| Simple         | `[######--]` |
| Capable        | `[######--]` |

* Embraces the concept of _kaizen_: small beneficial changes over time
  leading to impressive capabilities
* Has its own web server, endpoint routing, logging, templating
  engine, html parser, assertions framework, and database
* Designed with TDD (Test-Driven Development)
* It has 100% test coverage (branch and statement) that runs in 30
  seconds without any special setup (`make test_coverage`)
* Has close to 100% mutation test strength using the _PiTest_ mutation
  testing tool (`make mutation_test`)
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
* Has examples of framework use - see _Example Projects_ below

Minum has zero dependencies, and is built of ordinary and well-tested
code: hashmaps, sockets, and so on. The "minimalist" competitors range
from 400,000 to 700,000 lines when accounting for their dependencies.

Applying a minimalist approach enables easier debugging,
maintainability, and lower overall cost. Most frameworks trade faster
start-up for a higher overall cost. If you need sustainable quality,
the software must be well-tested and documented from the onset.  As an
example, this project's ability to attain such high test coverage was
greatly enabled by the minimalism paradigm. The plentiful tests and
comments help to make intent and implementation clearer.

Minum follows _semantic versioning_, and has been version 8 since
August 2024. It is intended not to make breaking changes for the
forseeable future. There is no slated end-of-life for version 8.

The design process
------------------

1) Make it work.
2) Make it right.
3) Make it fast.

This project represents thousands of hours of an experienced
practitioner experimenting with maintainability, performance, and
pragmatic simplicity.


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
    <version>8.3.2</version>
</dependency>
```


Features:
--------

<details><summary>Secure TLS 1.3 HTTP/1.1 web server</summary>
<p>
A web server is the program that enables sending your data over the internet.
</p>
</details>
<details><summary>In-memory database with disk persistence</summary>
<p>
A database is necessary to store data for later use, such as user accounts.  Our database stores
all its data in memory, but writes it to the disk as well.  The only time data is read from disk
is at database startup.  There are benefits and risks to this approach.
</p>
</details>
<details><summary>Server-side templating</summary>
<p>
A template is just a string with areas you can replace, and the template processor 
renders these quickly.  For example, here is a template: "Hello {{ name }}".  If we
provide the template processor with that template and say that "name" should be replaced
with "world", it will do so.
</p>
</details>
<details><summary>Logging</summary>
<p>
Logs are text that the program outputs while running.  There can be thousands of lines
output while the program runs.
</p>
</details>
<details><summary>Testing utilities</summary>
<p>
The test utilities are mostly ways to confirm expectations, and throw an error if unmet.
</p>
</details>
<details><summary>HTML parsing</summary>
<p>
Parsing means to interpret the syntax and convert it to meaningful data for later analysis.
Because web applications often have to deal with HTML, it is a valuable feature
in a minimalist framework like this one.
</p>
</details>
<details><summary>Background queue processor</summary>
<p>
Across the majority of the codebase, the only time code runs is when a request comes in.  The
background queue processor, however, can continue running programs in parallel.
</p>
</details>


Size Comparison:
----------------

Compiled size: 209 kilobytes.

_Lines of production code (including required dependencies)_

| Minum | Javalin | Spring Boot |
|-------|---------|-------------|
| 6,410 | 141,048 | 1,085,405   |

See [a size comparison in finer detail](docs/size_comparisons.md)


Performance:
------------

Performance is a feature. On your own applications, collect
performance metrics at milestones, so that trends and missteps are
made apparent.

One of the benefits of minimalism combined with test-driven
development is that finding the bottlenecks and making changes is
easier, faster, and safer.

* Web request handling: Plenty fast, depending on network and server
  configuration.  [details here](docs/perf_data/response_speed_test.md)
* Database updates/reads: 2,000,000 per second. See "test_Performance"
  in DbTests.java. O(1) reads (does not increase in time as database
  size increases) by use of indexing feature.
* Template processing:
  * 12,000,000 per second for tiny templates
  * 335,000 per second for large complex templates. 
  * See a [comparison benchmark](https://github.com/byronka/template-benchmark?tab=readme-ov-file#original-string-output-test), with
    Minum's code represented [here](https://github.com/byronka/template-benchmark/blob/utf8/src/main/java/com/mitchellbosecke/benchmark/Minum.java).

See a [Minum versus Spring performance comparison](docs/perf_data/framework_perf_comparison.md)


Documentation:
--------------

* [Development handbook](docs/development_handbook.md)
* [Javadocs](https://renomad.com/javadoc/)
* [Code coverage](https://renomad.com/site/jacoco/index.html) 
* [Mutation test report](https://renomad.com/pit-reports)
* [Test run report](https://renomad.com/site/surefire-report.html)
* [Project site report](https://renomad.com/site/project-info.html)


Example projects demonstrating usage:
-------------------------------------

See the following links for sample projects that use this framework.

[Smallest-possible](https://github.com/byronka/minum_usage_example_smaller)

This project is valuable to see the minimal-possible application that
can be made.  This might be a good starting point for use of Minum on
a new project.

<hr>

[Example](https://github.com/byronka/minum_usage_example_mvn) 

This is a good example to see a basic project with various
functionality. It shows many of the typical use cases of the Minum
framework.

<hr>

[Memoria project](https://github.com/byronka/memoria_project)

This is a family-tree project.  It demonstrates the kind of approach
this framework is meant to foster.

<hr>

[Restaurants](https://github.com/byronka/restaurants)

Restaurants is a prototype project to provide a customizable ranked
list of restaurants.

<hr>

[Alternate database](https://github.com/byronka/minum_using_alternate_database)

This is a project which uses a SQL database called
[H2](https://www.h2database.com/html/main.html), and which shows how a
user might go about including a different database than the one
built-in.


Code samples
============

The following code samples help provide an introduction to the features.

* [Database](#database)
* [Logging](#logging)
* [HTML Parser](#html-parser)
* [Endpoints](#endpoints)
* [Templates](#templates)
* [User Input](#user-input)
* [Testing](#testing)
* [Output](#output)
* [Helpful utilities](#helpful-utilities)


Database
--------

_Instantiating a new database_:

There are two database options - the older simpler database, or the
new DbEngine2 database.  They have nearly identical interfaces and
external behaviors, but the DbEngine2 database reads and writes to
disk _magnitudes_ faster than its sibling Db. It is the recommended
database to use going forward.

Here is how you instantiate the faster database:

```java
AbstractDb<Foo> db = new DbEngine2<>(foosDirectory, context, new Foo());
```

Here is the simpler database:

```java
AbstractDb<Foo> db = new Db<>(foosDirectory, context, new Foo());
```

The Minum database keeps its data and processing primarily in memory
but persists to the disk.  Data is only _read_ from disk at startup.

There are pros and cons to this design choice: on the upside, it's
very fast and the data stays strongly typed.  On the downside, it does
not make the same guarantees as ACID-compliant databases. Also, an
ill-considered application design could end up using too much memory.

On the [Memoria project](https://github.com/byronka/memoria_project/),
the risks and benefits were carefully considered, and so far it has
worked well.

Users are free to pick any other database they desire (See "Alternate
database" project above for an example project using a third-party
database). 

<hr>

_Adding a new object to a database_:

```java
// instantiate an object that implements DbData. Note that because
// this is a new object, we set the index to 0.
Foo myFoo = new Foo(0L, 42, "blue");

// write it to the database.  The returned value is the object we wrote
// with its index set by the database.
Foo myResultingFoo = db.write(myFoo);    
```

_Updating an object in a database_:

```java
myResultingFoo.setColor("orange");
db.write(myResultingFoo);    
```

_Deleting from a database_:

```java
db.delete(myResultingFoo);    
```

_Getting a bit more advanced - indexing_:

If there is a chance there might be a large amount of data and search
speed is important, it may make sense to register indexes.  This can
be best explained with a couple examples.

1) If every item has a unique identifier, like a UUID, then this is how you would register the index
   for much-increased performance in getting a particular item (compared to a worst-case full table scan)

In the following example, we will have a database of "Foo" items, each of which has a UUID identifier.
```java
// The first parameter is the name of the index, which we will refer to elsewhere when 
// asking for our data, and the second parameter is the function used to generate the key
// for the internal map.
db.registerIndex("id", x -> x.getId().toString())

// later to get the data, it will look like this, and will return
// a collection of data.  If the identifier is truly unique, then we can 
// prefer to use the findExactlyOne method
db.findExactlyOne("id", "87cfcbc1-5dad-4dcd-b4dc-7d8da9552ffc");
```

2) alternately, instead of having one-to-one unique values, we might be partitioning the data
    in some way. For example, the data may be categorized by color.

```java
// note that the key generator (the second parameter here) must always return a string
db.registerIndex("colors", x -> x.getColor().toString())

// later to get the data, it will look like this, and will return
// a collection of data.  If the identifier is truly unique, then we can 
// prefer to use the findExactlyOne method
Collection<Foo> blueFoos = db.getIndexedData("colors", "blue");
```

Logging
-------

_Writing a log statement_:

```java
// useful info for comprehending what the system is doing, non-spammy
logger.logDebug(() -> "Initializing main server loop"); 
```

The logs are output to "standard out" during runtime.  This means, if
you run a Minum application from the command line, it will output its
logs to the console.  This is a typical pattern for servers.

The logs are all expecting their inputs as closures - the pattern is
`() -> "hello world"`.  This keeps the text from being processed until
it needs to be.  An over-abundance of log statements could impact the
performance of the system.  By using this design pattern, log
statements will only be run if necessary, which is a great performance
improvement for trace-level logging and log statements which include
further processing (e.g. `_____ has requested to _____ at _____`).

Other levels of logs use similar methods:

```java
// useful like "debug", but spammy (for example, code in an inner loop)
logger.logTrace(() -> "Socket was closed");

// logs related to business situations, like a user authenticating
logger.logAudit(() -> "user \"foo\" has logged in");

// when something has broken, unexpectedly
logger.logAsyncError(() -> "IOException: error while reading file");
```

It is also possible to programmatically adjust what levels are being
output by using the `getActiveLogLevels` method.  For example:

```java
// disable all logging
for (var key : logger.getActiveLogLevels().keySet()) {
    logger.getActiveLogLevels().put(key, false);
}

// enable one particular log level
logger.getActiveLogLevels().put(LoggingLevel.TRACE, true);
logger.logTrace(() -> "Now you can see trace-level logs");
```

HTML Parser
-----------

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

Endpoints
---------

When a user visits a page, such as "hello" in
`http://mydomain.com/hello`, the web server provides data in response.

Originally, that data was a file in a directory. If we were providing
HTML, `hello` would have really been `hello.html` like
`http://mydomain.com/hello.html`.  

Dynamically-generated pages became more prevalent, and patterns
changed.  In Minum, it is possible to host files in that original way,
by placing them in the directory configured for "static" files - the
`minum.config` file includes a configuration for where this static
directory is located, `STATIC_FILES_DIRECTORY`.  In web applications,
it is still useful to follow this pattern for files that don't change,
such as JavaScript or images, but also very powerful to provide paths
which return the results of programs, as follows:

```java
webFramework.registerPath(GET, "hello", sd::helloName);
```

With that, there would be a path "hello" registered, so that users
visiting `http://mydomain.com/hello` would receive the result of
running a program at `helloName`.  Here is simplistic example of what
code could exist there:

```java
    /**
     * a GET request, at /hello?name=foo
     * <p>
     *     Replies "hello foo"
     * </p>
     */
    public IResponse helloName(IRequest request) {
        String name = request.getRequestLine().queryString().get("name");
        return Response.htmlOk("hello " + name);
    }
```

One user had a good question about the difference between the patterns
in more conventional annotation-based frameworks and this one.  See
that question [here](https://github.com/byronka/minum/discussions/19)

The [Quick start guide](docs/quick_start.md) walks through all this in
a bit more detail.

Templates
---------

```java
TemplateProcessor myTemplate = TemplateProcessor.buildProcessor("hello {{ name }}");
String renderedTemplate = myTemplate.renderTemplate(Map.of("name", "world"));
```

The Minum framework is driven by a paradigm of server-rendered HTML,
which is performant and works on all browsers.

The templates can be any string, but the design was driven concerned with rendering HTML 
templates.  Here is [an example of a simple template](https://github.com/byronka/minum/blob/master/src/test/webapp/templates/sampledomain/name_entry.html),
which is rendered with dynamic data in [this class](https://github.com/byronka/minum/blob/master/src/test/java/com/renomad/minum/sampledomain/SampleDomain.java) 

HTML templates must consider the concept of _HTML sanitization_, to prevent cross-site 
scripting (XSS), when the data is coming from a user.  To sanitize content of an HTML 
tag (e.g. `<p>{{user_data}}</p>`), sanitize the data with `StringUtils.safeHtml(userData)`, and 
for attributes (e.g. `class="{{user_data}}"`), use `StringUtils.safeAttr(userData)`.  Putting this all together:

_template:_

```html
<div>{{user_name}}</div>

<div data-name="{{user_name_attr}}">hello</div>
```

_code:_

```java
String renderedTemplate = myTemplate.renderTemplate(
  Map.of(
    "user_name", StringUtils.safeHtml(username),
    "user_name_attr", StringUtils.safeAttr(username)
  ));
```

A bit more advanced usage: instead of providing a `Map` as the parameter, you can provide
a `List` of `Map`, like follows.  This will render the template multiple times, once for
each map, and then render them all to one string:

```java
List<Map<String,String>> data = figureOutSomeData();

String renderedTemplate = myTemplate.renderTemplate(data);
```

Even more advanced, it is possible to register internal templates!  This might be useful to
push the boundaries of performance in some cases, and a more realistic case is demonstrated in
the `test_Templating_LargeComplex_Performance` test [here](src/test/java/com/renomad/minum/templating/TemplatingTests.java).

An example follows:

```java
    public void test_EdgeCase_DeeplyNested_withData() {
    TemplateProcessor aTemplate = buildProcessor("A template. {{ key1 }} {{ key2 }} {{ b_template }}");
    TemplateProcessor bTemplate = buildProcessor("B template.  {{ key1 }} {{ key2 }} {{ c_template }}");
    TemplateProcessor cTemplate = buildProcessor("C template.  {{ key1 }} {{ key2 }}");

    List<Map<String, String>> data = List.of(Map.of("key1", "foo",
            "key2", "bar"));

    var newBTemplate = aTemplate.registerInnerTemplate("b_template", bTemplate);
    var newCTemplate = newBTemplate.registerInnerTemplate("c_template", cTemplate);
    aTemplate.registerData(data);
    newBTemplate.registerData(data);
    newCTemplate.registerData(data);

    assertEquals("A template. foo bar B template.  foo bar C template.  foo bar", aTemplate.renderTemplate());
    assertEquals("B template.  foo bar C template.  foo bar", newBTemplate.renderTemplate());
    assertEquals("C template.  foo bar", newCTemplate.renderTemplate());
}
```

User Input
----------

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

Testing
-------

Automated testing is a big topic, but here I will just show some examples of Minum's code.

_Asserting true_:

Check that a value is true.  This is useful for testing predicates, and also for more
complex tests.  It is useful and important to include messages that will explain the assertion
and provide clarity when tests fail.  For example:

```java
int a = 2;
int b = 3;
boolean bIsGreater = b > a;
TestFramework.assertTrue(bIsGreater, "b should be greater than a");
```

Or, perhaps, an example might be confirming a substring is 
contained in a larger string:

```java
String result = "a man, a plan, a canal, panama!";

TestFramework.assertTrue(result.contains("a plan"));
```

_Asserting equal_:

The other assertion that is widely used is `TestFramework.assertEquals`.  If code is built carefully
(and especially if using test-driven development) the result of a method can often be verified by
confirming it is identical to an expected value. 

One interesting differentiator between this and other Java assertion frameworks is that when `assertEquals` fails, it just shows
the value of left and right.  It does not distinguish between which was "expected" and "actual". This
provides a clarity benefit in many cases - sometimes you just want to confirm two things are equal.

```java
import com.renomad.minum.testing.TestFramework;

int a = 2;
int b = 3;
int c = a + b;
TestFramework.assertEquals(c, 3);
```

_Checking for a log message during tests_:

A handy feature of the tests is the `TestLogger` class which extends `Logger`.  If you review its
code you will see that it stores the logs in a data structure, and makes access available to recent log
messages.  Sometimes, you need to test something that causes an action far away, where it is hard
to directly assert something about a result - in that situation, you can use the following `doesMessageExist`
method on the `TestLogger` class to confirm a particular log message was output.

```java
TestFramework.assertTrue(logger.doesMessageExist("Bad path requested at readFile: ../testingreadfile.txt"));
```

_Initializing context_:

On that same note, nearly all tests with Minum will need an instance of the `Context` class.  The 
expected standard way is to run `TestFramework.buildTestingContext` before the test, and then
run `TestFramework.shutdownTestingContext` afterwards to close down resources cleanly.  Check
those methods more closely to see for yourself.

Here is a very typical example:

```java
    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }
```

Output
------

How data is sent to the user is also an important detail.  In many cases, the system will
send an HTML response.  Other times, a JPEG image or an .mp3 audio or .mp4 video.  These are
all supported by the system.

When sending data, it is important to configure the type of data, so that the browser
knows how to handle it.  This kind of information is provided in the
headers part of the response message, specifically the "Content-Type" header.  The standard for
file types is called "MIME", and some examples are `text/html` for HTML documents
and `image/png` for .png image files.

The `Response.java` class includes helper methods for sending typical data, like the `htmlOk()` method
which sends HTML data with a proper content type.  Here is a method from the `AuthUtils.java` test class
that uses a couple methods from `Response`:

```java
public IResponse registerUser(IRequest request) {
    final var authResult = processAuth(request);
    if (authResult.isAuthenticated()) {
        return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of("Location","index"));
    }

    final var username = request.getBody().asString("username");
    final var password = request.getBody().asString("password");
    final var registrationResult = registerUser(username, password);

    if (registrationResult.status() == RegisterResultStatus.ALREADY_EXISTING_USER) {
        return Response.buildResponse(CODE_401_UNAUTHORIZED, Map.of("content-type", "text/html"), "<p>This user is already registered</p><p><a href=\"index.html\">Index</a></p>");
    }
    return Response.buildLeanResponse(CODE_303_SEE_OTHER, Map.of("Location","login"));
}
```

A more advanced capability is sending large files, like streaming videos. Minum supports streaming
data output.  See [createOkResponseForLargeStaticFiles](https://github.com/byronka/minum/blob/7e1d83fb4927e5ca2f1d3231a21284c8de58f0f4/src/test/java/com/renomad/minum/sampledomain/ListPhotos.java#L237)
in the `ListPhotos.java` file of the tests.

Helpful utilities
-----------------

- [Intellij Idea](https://www.jetbrains.com/idea/), an integrated development environment (IDE)
- [JProfiler](https://www.ej-technologies.com/jprofiler), a Java profiler
