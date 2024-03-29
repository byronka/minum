Minum Web Framework
===================

_When you need the fewest moving parts_

The simplest Minum program (see more [code samples](#code-samples) below):

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

Getting Started
---------------

There is a 🚀 [Quick start](docs/quick_start.md), or if you have
a bit more time, consider trying the [tutorial](docs/getting_started/getting_started.md)


Maven
-----

```xml
<dependency>
    <groupId>com.renomad</groupId>
    <artifactId>minum</artifactId>
    <version>3.0.0</version>
</dependency>
```


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
| 4,396 | 141,048 | 1,085,405   |

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
* [Code coverage](https://renomad.com/site/jacoco/index.html) 
* [Mutation test report](https://renomad.com/pit-reports)
* [Various reports](https://renomad.com/site/project-reports.html) 


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


Code samples
------------

Instantiating a new database:

```java
var db = new Db<>(foosDirectory, context, new Foo());
```

Adding a new object to a database:

```java
var foo = new Foo(0L, 42, "blue");
db.write(foo);    
```

Updating an object in a database:

```java
foo.setColor("orange");
db.write(foo);    
```

Deleting from a database:

```java
db.delete(foo);    
```

Writing a log statement:

```java
logger.logDebug(() -> "hello");
```

Parsing an HTML document:

```java
List<HtmlParseNode> results = new HtmlParser().parse("<p></p>");
```

Searching for an element in the parsed graph:

```java
HtmlParseNode node;
List<HtmlParseNode> results = node.search(TagName.P, Map.of());
```

Creating a new web handler (a function that handles an HTTP request and
returns a response):
```java
public Response myHandler(Request r) {
  return Response.htmlOk("<p>Hi world!</p>");
}
```

Registering that endpoint:

```java
webFramework.registerPath(GET, "formentry", sd::formEntry);
```

Building and rendering a template:

```java
TemplateProcessor foo = TemplateProcessor.buildProcessor("hello {{ name }}");
String rendered = foo.renderTemplate(Map.of("name", "world"));
```

Getting a query parameter from a request:

```java
String id = r.requestLine().queryString().get("id");
```

Getting a body parameter from a request, as a string:

```java
String personId = request.body().asString("person_id");
```

Getting a body parameter from a request, as a byte array:

```java
byte[] photoBytes = body.asBytes("image_uploads");
```