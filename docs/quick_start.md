Quick Start
===========

First off, slow down! This project breaks some conventions, so
barging through the docs might cost you understanding.

This software will enable you to create web applications in Java.  It provides
the bare minimum of what is necessary for that task, plainly and simply.

Let's get started.

Step 1 - Java
-------------

Let's make sure you can run java.  Quick test - run this in your shell:

```shell
javac -version
```

The result should be `javac 21` or higher.  If it not, check out [Step-by-step guide to installing Java on Windows](development_handbook.md#step-by-step-guide-for-installing-java-on-windows)
or [Java on Mac](development_handbook.md#java-on-mac)

***After changing environment variables, you must close and reopen your terminal to see the change***

Make sure to have the JAVA_HOME environment variable set.  Test like this:

```shell
echo $JAVA_HOME
```

The output should be the directory where Java is installed, but *not* the bin 
directory where java and javac live. Try this (this command changes directory to
JAVA_HOME and then lists the files there):

```shell
cd $JAVA_HOME
ls
```

You should see results like: `bin  conf  include  jmods  legal  lib  release`

This is why your `PATH` environment variable should include something like this:

```shell
$JAVA_HOME/bin
```

Step 2 - download the "small" example
-------------------------------------

Next, we will download a project that includes the simplest-possible
web application.  Grab this project. 

https://github.com/byronka/minum_usage_example_smaller


Step 3 - run the example
------------------------

Once you have downloaded it, run this command in its directory:

```shell
./mvnw compile exec:java
```

It will compile and you will be able to view it at http://localhost:8080


Step 4 - modify the example
---------------------------

In its source, go to Main.java and take a moment to review. Take your time
to understand what you are looking at, and then try some of the following
recommended changes, to acclimate.  To see your changes, cancel the running
program and run `./mvnw compile exec:java` to start it again.  

For ease of reference, here is the code you will see:

```java
public class Main {

    public static void main(String[] args) {
        // Start the system
        FullSystem fs = FullSystem.initialize();

        // Register some endpoints
        fs.getWebFramework().registerPath(
                StartLine.Verb.GET,
                "",
                request -> Response.htmlOk("<p>Hi there world!</p>"));

        fs.block();
    }
}
```

* Add a new path - have it serve content from /hello
* (After making this change, stop the running server and rerun the startup command)

```java
public class Main {

    public static void main(String[] args) {
        // Start the system
        FullSystem fs = FullSystem.initialize();

        // Register some endpoints
        fs.getWebFramework().registerPath(
                StartLine.Verb.GET,
                "",
                request -> Response.htmlOk("<p>Hi there world!</p>"));
        fs.getWebFramework().registerPath(
                StartLine.Verb.GET,
                "hello",
                request -> Response.htmlOk("<p>Hi there world!</p>"));

        fs.block();
    }
}
```

* Adjust to say hello to a query string parameter

```java
public class Main {

    public static void main(String[] args) {
        // Start the system
        FullSystem fs = FullSystem.initialize();

        // Register some endpoints
        fs.getWebFramework().registerPath(
                StartLine.Verb.GET,
                "",
                request -> Response.htmlOk("<p>Hi there world!</p>"));
        fs.getWebFramework().registerPath(
                StartLine.Verb.GET,
                "hello",
                request -> {
                    String name = request.startLine().queryString().get("name");
                    return Response.htmlOk(String.format("<p>Hi there %s!</p>", name));
                });

        fs.block();
    }
}
```

Step 5 - review the larger example
-----------------------------------

Now you are ready for the [larger code example](https://github.com/byronka/minum_usage_example_mvn)

Have fun!