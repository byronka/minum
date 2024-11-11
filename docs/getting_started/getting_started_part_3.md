Getting Started, Part 3
=======================

This document is the third part of the getting started 
tutorial, after [part 2](getting_started_part_2.md).

Step 1 - Adding a Test
----------------------

Creating a test is a bit of a doozy.

Not only because there are extra technical details, but also because it requires
a testing mindset, one infrequently promoted in our industry.  Often, we believe
that our software should work flawlessly or else we have failed.
However, all software (of realistic size) has bugs, no matter how hard we
try to avoid them. Thus, it is valuable to flip your perspective when you are 
testing. Novice testers focus too much effort on extreme edge cases - but you 
should expect to find bugs in ordinary workflows, and finding them should be a celebration. 

I highly recommend taking a look at a great book on this subject, _The
Art of Software Testing_, (TAOST) by Glenford Myers.

Create a new directory for our test files:

```shell
mkdir -p src/test/java/org/example/myproject/
```

Add Junit as a dependency in the file at `pom.xml`:

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
        <!-- The Minum web framework -->
        <dependency>
            <groupId>com.renomad</groupId>
            <artifactId>minum</artifactId>
            <version>8.0.4</version>
        </dependency>

        <!-- JUnit is a testing framework -->
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.13.2</version>
            <scope>test</scope>
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

Create a new file at `src/test/java/org/example/myproject/MainTests.java`, with 
this content.  Hold your breath, here we go:

```java
package org.example.myproject;

import com.renomad.minum.state.Context;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.web.FullSystem;
import com.renomad.minum.web.FunctionalTesting;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_200_OK;

public class MainTests {

    private static Context context;
    private static FunctionalTesting ft;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("_integration_test");
        FullSystem fullSystem = new FullSystem(context).start();
        new Endpoints(fullSystem).registerEndpoints();
        ft = new FunctionalTesting(context);
    }

    @AfterClass
    public static void cleanup() {
        var fs = context.getFullSystem();
        fs.shutdown();
        context.getLogger().stop();
        context.getExecutorService().shutdownNow();
    }


    /**
     * A user should see "hi there world" when running
     * the program.
     */
    @Test
    public void testFullSystem() {
        // send a GET request to the server
        var testResponse = ft.get("");

        // check that we got a 200 OK status in response
        assertEquals(testResponse.statusLine().status(), CODE_200_OK);

        // Confirm that the response body, parsed as HTML, yields a paragraph with the expected content
        assertEquals(testResponse.searchOne(TagName.P, Map.of()).innerText(), "Hi there world!");
    }

}
```

For the time being, feel free to ignore all the code in this file *except* the 
test code, which is `testFullSystem`.  I sadly acknowledge it is complex,
but you have to pick your battles.  Dealing with complexity is like a balloon,
you compress in one place and it expands out somewhere else.  

I would rather have
the complexity out in the light than have it hidden and obscured.  Maybe it is a 
conversation for another time, and I am losing some people at
this point.  Still, I have taken a deliberate and methodical approach to all this, 
and perhaps it is just a bias.  It is all a journey.  There is no one right way.

That digression aside, here is how to run your test:

From the command line:

```shell
mvn test
```

From your IDE (I like Jetbrains Intellij), just left-click the arrow showing up next to 
the name of the test, and click "run testFullSystem()".  You can also run it with
code coverage, give that a try.

Next steps
----------

Now we have a test, so we can keep a firm grip on expected behavior as we refactor and
redesign.  Our next activity will be incorporating templates and a database, and modifying
the behavior of our application.  Move on to [part four of getting started](getting_started_part_4.md)