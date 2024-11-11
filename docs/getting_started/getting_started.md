Minum, minimally
================

Given my preference for simplicity, naturally I would want a tutorial for Minum that starts
from first principles.  Here it is.

We will build a web application from the ground up, in the simplest manner.  I will accompany steps with examples based on my own computer, to help give some context to
the instructions.  Adjust based on your own situation.

These instructions presume you have [Java 21](https://jdk.java.net/21/) and [Maven](https://maven.apache.org/download.cgi) installed.
(if you need some help, see the guide to 
installing [Java for Windows](../development_handbook.md#step-by-step-guide-for-installing-java-on-windows) 
or [for Mac](../development_handbook.md#java-on-mac) in the development handbook)

Step 1 - Create a Directory
---------------------------

Create a directory for your software:

```shell
# make the directory
mkdir ~/playground
# change your current working directory to the new directory, "playground"
cd playground
```

Step 2 - Add a Maven Configuration File
---------------------------------------

We need a build tool for our project.  Maven is a conservative approach.
Add a configuration file for Maven, `pom.xml`, in the directory we just made:

Contents:
```xml
<project>
    <!-- Basic stuff that is required for a Maven project -->
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.example</groupId>
    <artifactId>myproject</artifactId>
    <version>1.0</version>

    <properties>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
      <maven.compiler.source>21</maven.compiler.source>
      <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <!-- Software our project needs to run -->
    <dependencies>
      <dependency>
          <groupId>com.renomad</groupId>
          <artifactId>minum</artifactId>
          <version>8.0.4</version>
      </dependency>
    </dependencies>

    <build>
      <plugins>
       <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>exec-maven-plugin</artifactId>
          <version>3.1.1</version>
          <configuration>
            <mainClass>org.example.myproject.Main</mainClass>
          </configuration>
        </plugin>
      </plugins>
    </build>

</project>
```

Step 3 - Add Source Code
------------------------

If you were to run the system at this point, it would complain because there is
no source code yet.  Let's add that. We will name our project "myproject".  

Create a directory for your source code:

```shell
mkdir -p src/main/java/org/example/myproject
```

Let us create a little program.

Put the following into `src/main/java/org/example/myproject/Main.java`:

```java
package org.example.myproject;

import com.renomad.minum.web.*;
                                                                                                                       
public class Main {

    public static void main(String[] args) {
        // Start the system
        FullSystem fs = FullSystem.initialize();

        // Register some endpoints
        fs.getWebFramework().registerPath(
                RequestLine.Method.GET,
                "",
                request -> Response.htmlOk("<p>Hi there world!</p>"));

        fs.block();
    }
}

```

Run this command from the top level of the `playground` directory:

```shell
mvn compile exec:java
```

This should succeed, and you should be able to see the running application telling
you "Hi there world!" when you hit http://localhost:8080/

Go ahead and shutdown the running server using the key combination: control + c

Taking a breath
---------------

Let's assess our situation.  We have a running web application, though it is limited in
functionality.  Note that we are sending HTML text directly to the browser.  This is
how traditional web servers worked, back when most applications used
server-rendered html.  Just because something has a little age does not make it useless.
In my opinion this method is still innovative and performant, and allows creating sites
and applications that have the same 
surface [chrome](https://stackoverflow.com/questions/5071905/what-does-chrome-mean) of 
the other technologies, despite having fallen out of favor.

Enough of my opinions, let us get back on track.

We are going to adjust our program for some extra capability.  Instead of defining 
all our routes in the Main class, we will extract that to its own class.  The route registration will
become more fleshed out - it will use an HTML template instead of a string literal.  We 
will also make use of the database that is part of the Minum framework.

There are other ways this can be done.  See what works for you.

[Continue to part 2](getting_started_part_2.md)



