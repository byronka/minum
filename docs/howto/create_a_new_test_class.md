HOWTO - Creating a new test class
=================================

If you're following good practices, you are creating automated tests for your
work.  Here are the practical necessities of what you need
to do if you are adding a new test class.

### Step 1 - Name and location

Decide the name and location of your test class.  This might be the most difficult part. As
they say, there are only two hard problems in computer science, and naming things is one!

But enough joking.

Consider the location of your test class.  All tests should
be placed under the `src/test` directory, but be aware that the subdirectories underneath
that are packages that align with the production packages in `src/main`. Java has some rules
about package scoping that can work in your favor.  If your production code is a method
with no modifier (that is, no `public` or `private`, e.g. `int add(int a, int b)`), then it
is scoped _package private_, meaning that it can only be accessed within that same package.
So, if you have code in `src/main/minum/database`, and it is package-private, then a test
file at `src/test/minum/database` will still be able to access it. 

That's probably all you need as far as scoping.  Keep it simple, ok? On the one hand it
is nice that Java enables things like this, but you don't want things to get out of hand.

Personally, I mainly use `public`, `package-private`, and `private`.  `public` means it is part 
of the intended outside-world-facing API and meant to be used by users.  I use 
`package-private` mainly when there's a method that isn't public but needs direct 
access during a test, probably because it needs deeper testing (and bear in mind, the
easier something is to test, the more and better we can test it, and the easier to
build high-quality systems).  `private` indicates it is won't have direct access
from outside its class and is generally used for better code organization.

### Step 2 - Add the class

If we were to add a new test class for the database class at `src/main/minum/database/Foo.java`, then
the test should probably be `src/test/minum/FooTests.java`.  Here's a template of how it
ought to look:

```java

package minum.database;

import minum.Context;
import minum.logging.TestLogger;

public class FooTests {

    private final TestLogger logger;
    private final Context context;

    public SimpleDatabaseTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("FooTests");
    }

    public void tests() {

        /*
        Four score and seven years ago our fathers brought forth, upon 
        this continent, a new nation, conceived in Liberty, and dedicated 
        to the proposition that all men are created equal.
         */
        logger.test("Here is the name of the test");
        {
            final var foo = new Foo(1, 123, "abc");
            final var deserializedFoo = foo.deserialize(foo.serialize());
            assertEquals(deserializedFoo, foo);
        }
    }
}

```

---

### Step 3 - Add a call to your new test

Add a call to your `tests()` method in `src/test/minum/Tests.java`, in the `unitAndIntegrationTests` method, like this:

```java

private void unitAndIntegrationTests() throws Exception {
        // (skipping some code)
    
      new WebTests(context).tests();
      new StackTraceUtilsTests(context).tests();
      new FooTests(context).tests();  /* <----- *** Your new test here  *** */
    
    logger.writeTestReport();
    FileUtils.deleteDirectoryRecursivelyIfExists(Path.of(constants.DB_DIRECTORY), logger);
    new ActionQueueKiller(context).killAllQueues();
    context.getExecutorService().shutdownNow();
}

```

---

That's basically it.  There's some pretty strong contrast between the style here and
what you may have seen on other projects. I've written a lot of tests in many 
languages and frameworks, and despite the unconventionality, this approach will
give you sufficient tooling for excellent testing. It's an intentional approach that
will pay high dividends in maintainability and quality.