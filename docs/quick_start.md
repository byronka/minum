Quick Start
===========

First off, slow down! This project breaks many conventions you might be used to, so
if you barge through these docs, you might miss crucial information.

This software will enable you to create web applications in Java.  It provides
the bare minimum of what is necessary for that task, plainly and simply.

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

Continuing on, we'll download a project includes everything for a running
web application, in the smallest working form.  Grab this project. 

https://github.com/byronka/minum_usage_example_smaller


Step 3 - run the example
------------------------

In its directory, run this command:

```shell
./mvnw compile exec:java
```

It will compile and you will be able to view it at http://localhost:8080


Step 4 - modify the example
---------------------------

In its source, go to Main.java and take a moment to review. You should
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