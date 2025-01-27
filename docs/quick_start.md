Quick Start
===========

This software will enable you to create web applications in Java.  It provides
the bare minimum of what is necessary for that task, plainly and simply.

Step 1 - Install Java
---------------------

Try this in your shell:

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

Step 2 - Download the "small" example
-------------------------------------

Next, we will download a project that includes the simplest-possible
web application.  Grab this project. 

https://github.com/byronka/minum_usage_example_smaller


Step 3 - run the example
------------------------

Run this command in its directory:

```shell
./mvnw compile exec:java
```

It will compile and you will be able to view it at http://localhost:8080


Step 4 - Think about the example
--------------------------------

Let's look at the code:

<img src="simple_minum_program.jpg" alt="An annotated view of the main method">


Step 5 - modify the example
---------------------------

* Stop the server and restart by running `./mvnw compile exec:java`
* Change the path - have it serve content from /hi instead of /hello

Next steps
----------

Now you are ready to go further.  If you want a step-by-step tutorial on building a
project with Minum from the ground up, check out the [getting started tutorial](getting_started/getting_started.md).

Or, you may want to pore through a [larger example](https://github.com/byronka/minum_usage_example_mvn)

Have fun!