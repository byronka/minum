Quick Start
===========

This software will enable you to create web applications in Java.  It provides
what is necessary for that task, plainly and simply.

To obtain the best value from this, it is necessary to acquaint yourself with
the basic technologies that comprise the web.  Most of the other frameworks
abstract this heavily, in the name of an improved developer experience.  Here,
HTTP, HTML, CSS, and Java code are first-class citizens.  Do you know what
a [start-line](https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line) is? 
You should, if you plan to exploit the true power of the web.

Let's get started.

Step 1 - Java
-------------

Let's make sure you can run java.  Quick test - run this in your shell:

```shell
javac -version
```

The result should be `javac 20.0.1` or higher.  If it not, check out [Step-by-step guide to installing Java on Windows](development_handbook.md#step-by-step-guide-for-installing-java-on-windows)
or [Java on Mac](development_handbook.md#java-on-mac)

***After changing environment variables, you must close and reopen your terminal to see the change***

Make sure to have the JAVA_HOME environment variable set.  Test like this:

```shell
echo $JAVA_HOME
```

The output should be the directory where Java is installed, but *not* the bin 
directory where java and javac live.  `JAVA_HOME` should look a bit like this:

```shell
Byron@byron-desktop /cygdrive/c/Users/Byron
$ cd $JAVA_HOME

Byron@byron-desktop /cygdrive/c/java/jdk-20.0.1
$ ls
bin  conf  include  jmods  legal  lib  release
```

This is why your `PATH` environment variable should include something like this:

```shell
$JAVA_HOME/bin
```

Step 2 - download the "small" example
-------------------------------------

Continuing on, we'll download a project includes everything for a running
web application, in the smallest working form.  Grab this project.  We will
call it "smaller" from here on.

https://github.com/byronka/minum_usage_example_smaller


Step 3 - run the example
------------------------

In the directory for smaller, run this command:

```shell
./mvnw compile exec:java
```

This will compile smaller and you will be able to view it at http://localhost:8080


Step 4 - modify the example
---------------------------

In the source for smaller, go to Main.java and think for a second about it. You should
be able to modify the message easily.  To see your changes, cancel the running
program and re-run `./mvnw compile exec:java`.  

* Modify the path - have it serve content from /hello

```diff
@@ -21 +21 @@ public class Main {
- "",
+ "hello",
```

* Adjust to say hello to a query string parameter

```diff
@@ -21,2 +21,5 @@ public class Main {
- "",
- request -> Response.htmlOk("<p>Hi there world!</p>"));
+ "hello",
+ request -> {
+     String name = request.startLine().queryString().get("name");
+     return Response.htmlOk(String.format("<p>Hi there %s!</p>", name));
+ });

```

Step 5 - review the larger example
-----------------------------------

Now you are ready for the [larger code example](https://github.com/byronka/minum_usage_example_mvn)

Have fun!