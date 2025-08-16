Minum Web Framework
===================

This project was built by hand, and demonstrates what is 
possible by craft and minimalism.  Its source code is brightly illuminated
with tests and documentation, making its intent and implementation clearer.

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

The high level characteristics
-------------------------------

| Capability     | Rating   |
|----------------|----------|
| Small          | ▓▓▓▓▓▓▓▒ |
| Tested         | ▓▓▓▓▓▓▓▒ |
| Documented     | ▓▓▓▓▓▓▒▒ |
| Performant     | ▓▓▓▓▓▓▒▒ |
| Maintainable   | ▓▓▓▓▓▓▓▒ |
| Understandable | ▓▓▓▓▓▓▓▒ |
| Simple         | ▓▓▓▓▓▓▒▒ |
| Capable        | ▓▓▓▓▓▓▒▒ |

The design process
------------------

1) Make it work.  
2) Make it right.  
3) Make it fast.

This project represents thousands of hours of an experienced practitioner experimenting with 
maintainability, performance, and pragmatic simplicity.

Just what exactly is this?
--------------------------

Minum is a web framework.  A web framework provides the programs necessary to build a "web application", 
which is at the foundation just a website, except that instead of solely hosting static files (e.g. 
static HTML, images, text, PDF, etc.), pages can also be rendered dynamically.  This could be anything 
programmable - your imagination is the only limit.

Minum provides a solid minimalist foundation. Basic capabilities that any web application would need, like
persistent storage or templating, are provided.  Everything else is up to you.  See the [features](#features) below.

It was designed from the very beginning with TDD (Test-Driven Development).

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


Minum has zero dependencies, and is built of ordinary and well-tested code: hashmaps, sockets, and
so on. The "minimalist" competitors range from 400,000 to 700,000 lines when accounting for their dependencies.

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
    <version>8.2.0</version>
</dependency>
```


Features:
--------

<details><summary>Secure TLS 1.3 HTTP/1.1 web server</summary>
A web server is the program that enables sending your data over the internet..
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
| 5,675 | 141,048 | 1,085,405   |

See [a size comparison in finer detail](docs/size_comparisons.md)


Performance:
------------

Performance is a feature. On your own applications, collect 
performance metrics at milestones, so that trends and missteps are made apparent.

One of the benefits of minimalism combined with test-driven development is that
finding the bottlenecks and making changes is easier, faster, and safer.

* Web request handling: Plenty fast, depending on network and server configuration.  [details here](docs/perf_data/response_speed_test.md)
* Database updates/reads: 2,000,000 per second. See "test_Performance" in DbTests.java. O(1) reads (does not increase in time as database size increases) by use of indexing feature.
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
============

The following code samples help provide an introduction to the features.

Database
--------

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

_Getting a bit more advanced - indexing_:

If there is a chance there might be a large amount of data and search speed is important, it may
make sense to register indexes.  This can be best explained with a couple examples.

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
logger.logDebug(() -> "hello");
```

The logs are output to "standard out" during runtime.  This means, if you run a Minum application
from the command line, it will output its logs to the console.  This is a typical pattern for servers.

The logs are all expecting their inputs as closures - the pattern is `() -> "hello world"`.  This keeps
the text from being processed until it needs to be.  A profusion of log statements
could impact the performance of the system.  By using this design pattern, those statements will only be
run if necessary, which is valuable for trace-level logging and those log statements which include
further processing (e.g. `_____ has requested to _____ at _____`).

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

When a user visits a page, such as "hello" in `http://mydomain.com/hello`, the web server provides data in response.

Originally, that data was a file in a directory. If we were providing HTML, `hello` would have really been `hello.html` 
like `http://mydomain.com/hello.html`.  

Dynamically-generated pages became more prevalent, and patterns changed.  In Minum, it is possible to host files
in that original way, by placing them in the directory configured for "static" files - the `minum.config` file
includes a configuration for where this static directory is located, `STATIC_FILES_DIRECTORY`.  In web applications, 
it is still useful to follow this pattern for files that don't change, such as JavaScript or images, but also very
powerful to provide paths which return the results of programs, as follows:

```java
webFramework.registerPath(GET, "hello", sd::helloName);
```

With that, there would be a path "hello" registered, so that users visiting `http://mydomain.com/hello` would receive
the result of running a program at `helloName`.  Here is simplistic example of what code could exist there:

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

The [Quick start guide](docs/quick_start.md) walks through this.

Templates
---------

```java
TemplateProcessor myTemplate = TemplateProcessor.buildProcessor("hello {{ name }}");
String renderedTemplate = myTemplate.renderTemplate(Map.of("name", "world"));
```

The Minum framework is driven by a paradigm of server-rendered HTML, which is performant and
works on all browsers.

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

_Checking for a log message during tests_:

```java
assertTrue(logger.doesMessageExist("Bad path requested at readFile: ../testingreadfile.txt"));
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
which sends HTML data with a proper content type.  A customized response is seen
in the `grabPhoto()` method of the `ListPhotos.java` class of the "SampleDomain" test 
project, for sending photos.  Here is a simplified version of that method:

```java
 public IResponse grabPhoto(IRequest r) {
        String filename = r.getRequestLine().queryString().get("name");
        logger.logAudit(() -> r.getRemoteRequester() + " is looking for a photo named " + filename);
        
        // more code here ...  see the ListPhotos.java file for complete detail

        return readStaticFile(photoPath.toString(), "image/jpeg", r.getHeaders());
    }
```

A more advanced capability is sending large files, like streaming videos. Minum supports streaming
data output.  See [createOkResponseForLargeStaticFiles](https://github.com/byronka/minum/blob/7e1d83fb4927e5ca2f1d3231a21284c8de58f0f4/src/test/java/com/renomad/minum/sampledomain/ListPhotos.java#L237)
in the `ListPhotos.java` file of the tests.

Helpful utilities
-----------------

- [Intellij Idea](https://www.jetbrains.com/idea/), an integrated development environment (IDE)
- [JProfiler](https://www.ej-technologies.com/jprofiler), a Java profiler
