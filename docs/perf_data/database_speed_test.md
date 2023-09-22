Database Speed Test
===================

The distinction of our database is that the data values are of a particular type,
DbData, which provide support for disk persistence.  Otherwise, they
are treated just like any ordinary data and can be arranged in any collection
shape you could wish - trees, lists, whatever.

Summary of the code below: we can make two million adjustments to our database in
one second.  The persistence to disk will happen over the ensuing minutes - this
is not an ACID-compliant database.  That risk, however, is allowable for many
use cases.

```java

 /**
 * When this is looped a hundred thousand times, it takes 500 milliseconds to finish
 * making the updates in memory.  It takes several minutes later for it to
 * finish getting those changes persisted to disk.
 *
 * a million writes in 500 milliseconds means 2 million writes in one sec.
 */
logger.test("Just how fast is our minum.database?");{
    // clear out the directory to start
    FileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
    final var db = new Db<Foo>(foosDirectory, context);
    MyThread.sleep(10);

    final var foos = new ArrayList<Foo>();

    // write the foos
    for (int i = 0; i < 10; i++) {
        final var newFoo = new Foo(i, i + 1, "original");
        foos.add(newFoo);
        db.persistToDisk(newFoo);
    }

    // change the foos
    final var outerTimer = new StopwatchUtils().startTimer();
    final var innerTimer = new StopwatchUtils().startTimer();
    for (var i = 1; i < 10; i++) {
        final var newFoos = new ArrayList<Foo>();
        /*
        loop through the old foos and update them to new values,
        creating a new list in the process.  There should only
        ever be 10 foos.
         */
        for (var foo : foos) {
            final var newFoo = new Foo(foo.index, foo.a + 1, foo.b + "_updated");
            newFoos.add(newFoo);
            db.persistToDisk(newFoo);
        }
    }
    logger.logDebug(() -> "It took " + innerTimer.stopTimer() + " milliseconds to make the updates in memory");
    db.stop(10, 20);
    logger.logDebug(() -> "It took " + outerTimer.stopTimer() + " milliseconds to finish writing everything to disk");
}
```