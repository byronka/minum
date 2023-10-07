Getting Started, Part 2
=======================

This document is part 2 of a tutorial.  Part 1 is [here](getting_started.md).

We will increase the capability and internal quality of our application, incorporating
templates, a database, better organization, and tests.  We will try to make these
adjustments gradually, to avoid losing our way.

Step 1 - Extract to Separate Class
----------------------------------

Let us start with extracting our code out to its own file.  

In the `Main` class, there is a section of code with the comment `// Register some endpoints`.
We will cut that code, and paste it into a new spot. Create
a new file at `src/main/java/org/example/myproject/Endpoints.java`, with 
the following content:

```java
package org.example.myproject;

import com.renomad.minum.web.FullSystem;
import com.renomad.minum.web.RequestLine;
import com.renomad.minum.web.Response;

public class Endpoints {

    private final FullSystem fullSystem;

    public Endpoints(FullSystem fullSystem) {
        this.fullSystem = fullSystem;
    }
    
    public void registerEndpoints() {
        fullSystem.getWebFramework().registerPath(
                RequestLine.Method.GET,
                "",
                request -> Response.htmlOk("<p>Hi there world!</p>"));
    }
}
```

With that done, revise the Main class at `src/main/java/org/example/myproject/Main.java` to look like this:

```java
package org.example.myproject;

import com.renomad.minum.web.*;
                                                                                                                       
public class Main {

    public static void main(String[] args) {
        // Start the system
        FullSystem fs = FullSystem.initialize();

        new Endpoints(fs).registerEndpoints();

        fs.block();
    }
}
```

Go ahead and run the application:

```shell
mvn compile exec:java
```

It should compile and run successfully, and you should be able to see it in
the browser at http://localhost:8080/

When you are done running the application, stop it: control + c

Step 2 - Commit Our Work
------------------------

We should make use of a source control tool, like Git, to
keep track of our work. 

Create a file in the root directory of playground, called `.gitignore`, to
specify which files should not be recorded:

```gitignore
# ignore Intellij's configurations, if you need to
.idea/
# ignore Maven's build directory, as it gets rebuilt during compilation
target/

# the database directory
db/

# a file that is created when the system is running, and deleted
# when the system has stopped
SYSTEM_RUNNING
```

Now we can initialize a repo and commit our work.

```shell
git init
git status
git add .
git commit -m "initial commit"
git status
```

Here is what it looks like on my terminal:


    $ git init
    Initialized empty Git repository
    
    $ git status
    On branch master
    
    No commits yet
    
    Untracked files:
      (use "git add <file>..." to include in what will be committed)
            .gitignore
            pom.xml
            src/
    
    nothing added to commit but untracked files present (use "git add" to track)
    
    $ git add .
    
    $ git commit -m "initial commit"
    [master (root-commit) bd7a6e0] initial commit
     4 files changed, 76 insertions(+)
     create mode 100755 .gitignore
     create mode 100644 pom.xml
     create mode 100755 src/main/java/org/example/myproject/Endpoints.java
     create mode 100644 src/main/java/org/example/myproject/Main.java

    $ git status
    On branch master
    nothing to commit, working tree clean


Next steps
----------

The upcoming step is a doozy.  We are going to write an 
integration test that will give us confidence in system behavior.  This one test will
assert correctness from end-to-end, which gives it the name, "end-to-end test". We
will use some of the more advanced functions of the framework, but do not worry,
the advanced parts are few, and I will explain as we go through.  I will explain all
that in [part three of getting started](getting_started_part_3.md)