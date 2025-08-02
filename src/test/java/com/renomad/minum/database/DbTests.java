package com.renomad.minum.database;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.Logger;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.RegexUtils;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.SearchUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.renomad.minum.database.DbTests.Foo.INSTANCE;
import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;
import static java.util.stream.IntStream.range;

public class DbTests {
    private Context context;
    private TestLogger logger;
    private FileUtils fileUtils;
    static Path foosDirectory = Path.of("out/simple_db/foos");
    static Path fubarDirectory = Path.of("out/simple_db/fubar");

    /**
     * The time we will wait for the asynchronous actions
     * to finish before moving to the next test.
     */
    static private final int FINISH_TIME = 50;

    @Before
    public void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger)context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * For any given collection of data, we will need to serialize that to disk.
     * We can use some of the techniques we built up using r3z (<a href="https://github.com/byronka/r3z">R3z</a>) - like
     * serializing to a json-like url-encoded text, eventually synchronized
     * to disk.
     */
    @Test
    public void test_Serialization_SimpleCase() {
        final var foo = new Foo(1, 123, "abc");
        final var deserializedFoo = foo.deserialize(foo.serialize());
        assertEquals(deserializedFoo, foo);
    }

    /**
     * When we serialize something null
     */
    @Test
    public void test_Serialization_Null() {
        final var foo = new Foo(1, 123, null);
        final var deserializedFoo = foo.deserialize(foo.serialize());
        assertEquals(deserializedFoo, foo);
    }

    @Test
    public void test_Serialization_Collection() {
        final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();
        final var serializedFoos = foos.stream().map(Foo::serialize).toList();
        final var deserializedFoos = serializedFoos.stream().map(INSTANCE::deserialize).toList();
        assertEquals(foos, deserializedFoos);
    }

    /**
     * Wide-ranging capabilities of the database
     */
    @Test
    public void test_GeneralCapability() {
        Path dbPathForTest = foosDirectory.resolve("test_GeneralCapability");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        MyThread.sleep(FINISH_TIME);

        final var db = new Db<>(dbPathForTest, context, INSTANCE);
        MyThread.sleep(FINISH_TIME);

        for (int i = 0; i < 7; i++) {

            int finalI = i;
            logger.logDebug(() -> "DBTests general capability, round " + finalI);
            final var foos = range(1, 40).mapToObj(x -> new Foo(0, x, "abc" + x)).toList();

            // make some files on disk
            for (var foo : foos) {
                db.write(foo);
            }

            MyThread.sleep(300);

            // check that the files are now there.
            for (var foo : foos) {
                Path foundFile = dbPathForTest.resolve(foo.getIndex() + Db.DATABASE_FILE_SUFFIX);
                assertTrue(Files.exists(foundFile), "should find file at " + foundFile);
            }

            MyThread.sleep(FINISH_TIME);

            assertEqualsDisregardOrder(
                    db.values().stream().map(Foo::toString).toList(),
                    foos.stream().map(Foo::toString).toList());

            // change those files
            final var updatedFoos = new ArrayList<Foo>();
            for (var foo : db.values().stream().toList()) {
                final var newFoo = new Foo(foo.index, foo.a + 1, foo.b + "_updated");
                updatedFoos.add(newFoo);
                db.write(newFoo);
            }

            MyThread.sleep(300);

            assertEqualsDisregardOrder(
                    db.values().stream().map(Foo::toString).toList(),
                    updatedFoos.stream().map(Foo::toString).toList());

            // delete the files
            for (var foo : foos) {
                db.delete(foo);
            }

            // check that the files are all gone
            MyThread.sleep(300);

            for (var foo : foos) {
                assertFalse(Files.exists(dbPathForTest.resolve(foo.getIndex() + Db.DATABASE_FILE_SUFFIX)));
            }
        }
        // give the action queue time to save files to disk
        // then shut down.
        db.stop();
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * If we run create, update, and delete in a fast loop,
     * the locks are there to keep things stable.
     */
    @Test
    public void test_Locking() {
        int iterationCount = 10;
        Path dbPathForTest = foosDirectory.resolve("test_Locking");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);

        final var db = new Db<>(dbPathForTest, context, INSTANCE);

        for (int i = 0; i < iterationCount; i++) {
            Foo a = new Foo(0, 1, "a".repeat(1000));
            db.write(a);
            db.write(new Foo(a.index, 2, "b".repeat(500)));
            db.delete(a);
        }
        MyThread.sleep(iterationCount * 5);
        Path foundFile = dbPathForTest.resolve(1 + Db.DATABASE_FILE_SUFFIX);
        assertFalse(Files.exists(foundFile), "should not find file at " + foundFile);

        // give the action queue time to save files to disk
        // then shut down.
        db.stop();
        MyThread.sleep(FINISH_TIME);
    }


    @Test
    public void test_Locking_2() throws IOException {
        Path dbPathForTest = foosDirectory.resolve("test_Locking_2");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        int iterationCount = 10;

        final var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("id", x -> String.valueOf(x.getA()));

        IntStream.range(0, iterationCount).boxed().parallel().forEach(x -> {
            // create a unique entry
            Foo a = db.write(new Foo(0, x, "a"));
            // update that entry
            db.write(new Foo(a.index, x, "b"));
        });

        IntStream.range(0, iterationCount).boxed().parallel().forEach(x -> {
            Foo foo = db.findExactlyOne("id", String.valueOf(x));
            db.delete(foo);
        });

        MyThread.sleep(iterationCount * 5);
        Path foundFile = dbPathForTest.resolve(1 + Db.DATABASE_FILE_SUFFIX);
        assertFalse(Files.exists(foundFile), "should not find file at " + foundFile);
        assertEquals(Files.readString(dbPathForTest.resolve("index.ddps")), "1");

        // give the action queue time to save files to disk
        // then shut down.
        db.stop();
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * what happens if we try deleting a file that doesn't exist?
     */
    @Test
    public void test_Edge_DeleteFileDoesNotExist() {
        // clear out the directory to start
        Path dbPathForTest = foosDirectory.resolve("test_Edge_DeleteFileDoesNotExist");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        final var db_throwaway = new Db<>(dbPathForTest, context, INSTANCE);

        var ex = assertThrows(DbException.class, () -> db_throwaway.delete(new Foo(123, 123, "")));
        assertEquals(ex.getMessage(), "no data was found with index of 123");

        db_throwaway.stop();
        MyThread.sleep(FINISH_TIME);
    }

    @Test
    public void test_Deserialization_EdgeCases() throws IOException {
        // what if the directory is missing when try to deserialize?
        // note: this would only happen if, after instantiating our db,
        // the directory gets deleted/corrupted.

        // clear out the directory to start
        Path dbPathForTest = foosDirectory.resolve("test_Deserialization_EdgeCases");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        final var db = new Db<>(dbPathForTest, context, INSTANCE);
        MyThread.sleep(10);

        // create an empty file, to create that edge condition
        final var pathToSampleFile = dbPathForTest.resolve("1.ddps");
        Files.createFile(pathToSampleFile);
        db.loadDataFromDisk();
        MyThread.sleep(10);
        String existsButEmptyMessage = logger.findFirstMessageThatContains("file exists but");
        assertEquals(existsButEmptyMessage, "1.ddps file exists but empty, skipping");

        // create a corrupted file, to create that edge condition
        Files.writeString(pathToSampleFile, "invalid data", StandardCharsets.UTF_8);
        final var ex = assertThrows(DbException.class, db::loadDataFromDisk);
        assertTrue(ex.getMessage().replace('\\','/').startsWith("Failed to deserialize out/simple_db/foos/test_Deserialization_EdgeCases/1.ddps with data (\"invalid data\"). Caused by: java.lang.NumberFormatException: For input string: \"invalid data\""));

        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        db.loadDataFromDisk();
        MyThread.sleep(10);
        String directoryMissingMessage = logger.findFirstMessageThatContains("directory missing, adding nothing").replace('\\', '/');
        assertTrue(directoryMissingMessage.contains("test_Deserialization_EdgeCases directory missing, adding nothing to the data list"));
        db.stop();
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * When this is looped a hundred thousand times, it takes 500 milliseconds to finish
     * making the updates in memory.  It takes several minutes later for it to
     * finish getting those changes persisted to disk.
     * a million writes in 500 milliseconds means 2 million writes in one sec.
     */
    @Test
    public void test_Performance() throws IOException {
        int originalFooCount = 10;
        int loopCount = 50;

        // clear out the directory to start
        Path dbPathForTest = foosDirectory.resolve("test_Performance");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);

        // build a Context without testlogger - testlogger would impact perf here
        Context contextWithRegularLogger = buildTestingContextWithRegularLogger();
        final var db = new Db<>(dbPathForTest, contextWithRegularLogger, INSTANCE);
        MyThread.sleep(10);

        final var foos = new ArrayList<Foo>();

        // write the foos
        for (int i = 0; i < originalFooCount; i++) {
            final var newFoo = new Foo(0, i + 1, "original");
            foos.add(newFoo);
            db.write(newFoo);
        }

        // change the foos
        final var outerTimer = new StopwatchUtils().startTimer();
        final var innerTimer = new StopwatchUtils().startTimer();

        final var newFoos = new ArrayList<Foo>();
        for (var i = 1; i < loopCount; i++) {
            newFoos.clear();
                /*
                loop through the old foos and update them to new values,
                creating a new list in the process.  There should only
                ever be 10 foos.
                 */
            for (var foo : foos) {
                final var newFoo = new Foo(foo.getIndex(), foo.a + 1, foo.b + "_updated");
                newFoos.add(newFoo);
                db.write(newFoo);
            }
        }
        logger.logDebug(() -> "It took " + innerTimer.stopTimer() + " milliseconds to make the updates in memory");
        db.stop(100, 50);
        logger.logDebug(() -> "It took " + outerTimer.stopTimer() + " milliseconds to finish writing everything to disk");

        final var db1 = new Db<>(dbPathForTest, context, INSTANCE);
        Collection<Foo> values = db1.values();
        assertTrue(newFoos.containsAll(values));
        db1.stop(100, 50);

        final var pathToIndex = dbPathForTest.resolve("index.ddps");
        // this file should not be empty, but we are making it empty
        Files.writeString(pathToIndex,"");
        var ex = assertThrows(DbException.class, () -> new Db<>(dbPathForTest, context, INSTANCE));
        // because the error message includes a path that varies depending on which OS, using regex to search.
        assertTrue(RegexUtils.isFound("Exception while reading out.simple_db.foos.test_Performance.index.ddps in Db constructor",ex.getMessage()));
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * Test read and write.
     * <p>
     *      The database is mainly focused on in-memory, with eventual
     *      disk persistence.  With that in mind, the files are only
     *      read from disk once - the first time they are needed.  From
     *      there, all disk operations are writes.
     * </p>
     * <p>
     *      This test will try writing and reading in various ways to exercise
     *      many of the ways the database can be used.
     * </p>
     * <p>
     *     Namely, the first load of data for any particular {@link Db} class
     *     can occur when the database is asked to analyze data, or to
     *     write, update, or delete.
     * </p>
     */
    @Test
    public void test_Db_Write_and_Read() {
        // in this test, we're stopping and starting our database
        // over and over - a very unnatural activity.  We need to
        // provide sleep time for the actions to finish before
        // moving to the next step.
        int stepDelay = 40;

        for (int i = 0; i < 3; i++) {
            Path dbPathForTest = foosDirectory.resolve("test_Db_Write_and_Read");
            fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
            var db = new Db<>(dbPathForTest, context, INSTANCE);
            MyThread.sleep(stepDelay);
            Foo foo1 = new Foo(0, 2, "a");
            Foo foo2 = new Foo(0, 3, "b");

            // first round - adding to an empty database
            db.write(foo1);
            var foos = db.values().stream().toList();
            assertEquals(foos.size(), 1);
            assertEquals(foos.getFirst(), foo1);
            db.stop();
            MyThread.sleep(stepDelay);

            // second round - adding to a database that has stuff on disk
            var db2 = new Db<>(dbPathForTest, context, INSTANCE);
            db2.write(foo2);
            MyThread.sleep(stepDelay);

            var foos2 = db2.values().stream().toList();
            assertEquals(foos2.size(), 2);
            assertEquals(foos2.get(0), foo1);
            assertEquals(foos2.get(1), foo2);
            db2.stop();
            MyThread.sleep(stepDelay);

            // third round - reading from a database that has yet to read from disk
            var db3 = new Db<>(dbPathForTest, context, INSTANCE);
            var foos3 = db3.values().stream().toList();
            assertEquals(foos3.size(), 2);
            assertEquals(foos3.get(0), foo1);
            assertEquals(foos3.get(1), foo2);
            db3.stop();
            MyThread.sleep(stepDelay);

            // fourth round - deleting from a database that has yet to read from disk
            var db4 = new Db<>(dbPathForTest, context, INSTANCE);
            db4.delete(foo2);
            var foos4 = db4.values().stream().toList();
            assertEquals(foos4.size(), 1);
            assertEquals(foos4.getFirst(), foo1);
            db4.stop();
            MyThread.sleep(stepDelay);

            // fifth round - updating an item in a database that has not yet read from disk
            var db5 = new Db<>(dbPathForTest, context, INSTANCE);
            var updatedFoo1 = new Foo(1, 42, "update");
            db5.write(updatedFoo1);
            var foos5 = db5.values().stream().toList();
            assertEquals(foos5.size(), 1);
            assertEquals(foos5.getFirst(), updatedFoo1);
            db5.stop();
            MyThread.sleep(stepDelay);

            // sixth round - if we delete, it will reset the next index to 1
            var db6 = new Db<>(dbPathForTest, context, INSTANCE);
            db6.delete(updatedFoo1);
            var foos6 = db6.values().stream().toList();
            assertEquals(foos6.size(), 0);

            Foo newData = db6.write(new Foo(0, 1, "new data"));
            assertEquals(newData.index, 1L);

            db6.delete(newData);

            db6.stop();
            MyThread.sleep(stepDelay);
        }
    }

    /**
     * If we command the database to delete a file that does
     * not exist, it should throw an exception
     */
    @Test
    public void test_Db_Delete_EdgeCase_DoesNotExist() {
        fileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory.resolve("test_Db_Delete_EdgeCase_DoesNotExist"));
        var db = new Db<>(foosDirectory.resolve("test_Db_Delete_EdgeCase_DoesNotExist"), context, INSTANCE);
        var ex = assertThrows(DbException.class, () -> db.delete(new Foo(1, 2, "a")));
        assertEquals(ex.getMessage(), "no data was found with index of 1");
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * If somehow the file to delete is gone, an exception will be thrown
     * as an async error.  This is a pretty far-out edge case - if the
     * administrator deletes the database from disk while the system is
     * operating, this could happen.
     */
    @Test
    public void test_Db_Delete_EdgeCase_FileGone() throws IOException {
        Path dbPathForTest = foosDirectory.resolve("test_Db_Delete_EdgeCase_FileGone");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        Foo foo = new Foo(0, 2, "a");
        db.write(foo);
        MyThread.sleep(20);
        Files.delete(dbPathForTest.resolve("1.ddps"));
        MyThread.sleep(10);

        db.delete(foo);

        db.stop();
        MyThread.sleep(10);
        assertTrue(logger.doesMessageExist("failed to delete file"));
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * If we command the database to delete data that is null,
     * an exception will be thrown
     */
    @Test
    public void test_Db_Delete_EdgeCase_NullValue() {
        Path dbPathForTest = foosDirectory.resolve("test_Db_Delete_EdgeCase_NullValue");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        var ex = assertThrows(DbException.class, () -> db.delete(null));
        assertEquals(ex.getMessage(), "Invalid to be given a null value to delete");
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * Investigate race conditions
     * <p>
     *     This is NOT a test.  It is a program that lets us experiment with how
     *     threading works in the database, where we use the debugger to examine
     *     certain situations.
     * </p>
     * <p>
     *     If two different threads are running, and end up calling
     *     update and delete at the exact same time, there is a possibility
     *     we could crash.
     * </p>
     * <p>
     *     This code is a laboratory for examining how threads interact
     *     inside the database code.  Use this in debugging mode.
     * </p>
     */
    @Ignore("This is used as a laboratory for investigating threads.  It is not a test")
    public void test_Db_RaceConditionLaboratory() throws ExecutionException, InterruptedException {
        // prepare a database instance
        Path dbPathForTest = foosDirectory.resolve("test_Db_RaceConditionLaboratory");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var myDatabase = new Db<>(dbPathForTest, context, INSTANCE);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // create our "racers".  On your marks!

        var foo = new Foo(0, 42, "hi I am foo");

        Runnable racer1Writer = () -> {
            Thread.currentThread().setName("racer1_writer");
            myDatabase.write(foo);
        };

        Runnable racer2Updater = () -> {
            Thread.currentThread().setName("racer2_updater");
            myDatabase.write(foo);
        };

        Runnable racer3Deleter = () -> {
            Thread.currentThread().setName("racer3_deleter");
            myDatabase.delete(foo);
        };


        Future<?> racer1WriterFuture = executor.submit(racer1Writer);
        Future<?> racer2UpdaterFuture = executor.submit(racer2Updater);
        Future<?> racer3DeleterFuture = executor.submit(racer3Deleter);

        racer1WriterFuture.get();
        racer2UpdaterFuture.get();
        racer3DeleterFuture.get();
    }

    /**
     * Database files must have a "ddps" suffix.
     */
    @Test
    public void testPoorlyNamedDbFile() throws IOException {
        Path dbPathForTest = foosDirectory.resolve("testPoorlyNamedDbFile");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);

        Foo foo1 = new Foo(0, 2, "a");
        Foo foo2 = new Foo(0, 3, "b");

        db.write(foo1);
        db.write(foo2);
        db.stop();
        MyThread.sleep(FINISH_TIME);

        Files.writeString(dbPathForTest.resolve("foo"), "testing");
        assertThrows(DbException.class, "the files must have a ddps suffix, like 1.ddps.  filename: foo", () -> db.readAndDeserialize(dbPathForTest.resolve("foo")));
        Files.copy(dbPathForTest.resolve("1.ddps"), dbPathForTest.resolve("3.ddps"));
        var ex = assertThrows(DbException.class,
                () -> db.readAndDeserialize(dbPathForTest.resolve("3.ddps")));
        assertTrue(ex.getMessage().contains("Failed to deserialize") &&
                ex.getMessage().contains("with data (\"1|2|a\"). Caused by: com.renomad.minum.utils.InvariantException: The filename must correspond to the data's index. e.g. 1.ddps must have an id of 1"));
    }

    /**
     * There is code in the database that verifies the
     * deserializing code (written in the DbData implementation)
     * <p>
     *     This test runs by using an improperly-written DbData
     *     implementation, {@link Fubar3}.
     * </p>
     */
    @Test
    public void testDeserializerComplaints() {
        Path dbPathForTest = fubarDirectory.resolve("testDeserializerComplaints");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, new Fubar(0,0,""));

        db.write(new Fubar(0, 2, "a"));
        db.stop();
        MyThread.sleep(FINISH_TIME);

        var ex1 = assertThrows(DbException.class,
                () -> db.readAndDeserialize(dbPathForTest.resolve("1.ddps")));
        assertTrue(ex1.getMessage().contains("Failed to deserialize") && ex1.getMessage().contains("with data (\"1|2|a\"). " +
                "Caused by: com.renomad.minum.utils.InvariantException: deserialization of Fubar{index=0, a=0, b=''} resulted in a " +
                "null value. Was the serialization method implemented properly?"));
    }

    /**
     * If we ask for the filename and it returns null, should get an exception thrown
     */
    @Test
    public void testReadAndDeserialize_nullFilename() {
        Path dbPathForTest = fubarDirectory.resolve("testReadAndDeserialize_nullFilename");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, new Fubar(0,0,""));
        File file = new File("/");
        DbException dbException = assertThrows(DbException.class, () -> db.readAndDeserialize(file.toPath()));
        assertTrue(dbException.getMessage().contains( "returned a null filename"));
    }

    /**
     * Similar to {@link #testDeserializerComplaints()} but
     * for code in {@link Db#write(DbData)}
     */
    @Test
    public void testWriteDeserializationComplaints() {
        Path dbPathForTest = fubarDirectory.resolve("testWriteDeserializationComplaints");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, new Fubar3(0, 0, ""));

        db.write(new Fubar3(0, 2, "a"));
        db.stop();
        MyThread.sleep(FINISH_TIME);
        assertTrue(logger.doesMessageExist("the serialized form of data must not be blank. Is the serialization code written properly?"));
    }

    /**
     * Similar to {@link #testDeserializerComplaints()} but
     * for code in {@link Db#write(DbData)}
     */
    @Test
    public void testWriteDeserializationComplaints2() {
        Path dbPathForTest = fubarDirectory.resolve("testWriteDeserializationComplaints2");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, new Fubar2(0, 0, ""));

        db.write(new Fubar2(0, 2, "a"));
        db.stop();
        MyThread.sleep(FINISH_TIME);
        assertTrue(logger.doesMessageExist("the serialized form of data must not be blank. Is the serialization code written properly?"));
    }

    /**
     * This method walks through the files of a directory, loading
     * each one into memory.
     */
    @Test
    public void testWalkAndLoad() {
        var dbPathForTest = Path.of("out/simple_db/biz");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(foosDirectory.resolve("testWalkAndLoad"), context, INSTANCE);
        var ex = assertThrows(DbException.class,
                () -> db.walkAndLoad(dbPathForTest));
        assertTrue(RegexUtils.isFound("java.nio.file.NoSuchFileException: out.simple_db.biz", ex.getMessage()) );
    }

    /**
     * Now that {@link Db#write(DbData)} has subsumed the capabilities
     * of update, it must not create a new data entry if there is no
     * existing data at that index.
     * <p>
     *     Putting it another way - the only way to create new data entries
     *     is to provide an object inheriting from DbData with an index of
     *     0.  Any other value will cause an exception.
     * </p>
     */
    @Test
    public void testWrite_PositiveIndexNotExisting() {
        Path dbPathForTest = foosDirectory.resolve("testWrite_PositiveIndexNotExisting");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);

        Foo foo1 = new Foo(1, 2, "a");

        assertThrows(DbException.class,
                "Positive indexes are only allowed when updating existing data. Index: 1",
                () -> db.write(foo1));
        db.stop();
        MyThread.sleep(FINISH_TIME);
    }

    @Test
    public void testWrite_NegativeIndex() {
        Path dbPathForTest = foosDirectory.resolve("testWrite_NegativeIndex");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);

        Foo foo1 = new Foo(-1, 2, "a");

        assertThrows(DbException.class, "Negative indexes are disallowed", () -> db.write(foo1));
        db.stop();
        MyThread.sleep(FINISH_TIME);
    }

    /**
     * There s a little function that determines whether it is
     * necessary to load the data - called the first time
     * data is needed.
     * <br>
     * In this instance, we're saying the data has not been
     * loaded yet, so our method will run loadData (we're
     * giving it our own runner as a mock)
     */
    @Test
    public void testLoadDataCore_False() {
        final List<Boolean> wasCalled = new ArrayList<>();
        wasCalled.add(false);
        Runnable runner = () -> {
            wasCalled.clear();
            wasCalled.add(true); // this *should* run
        };

        Db.loadDataCore(false, runner);

        assertTrue(wasCalled.getFirst());
    }

    /**
     * Similar to {@link #testLoadDataCore_False()} but here
     * we're telling the method our data has already been loaded,
     * so no need to load it.
     */
    @Test
    public void testLoadDataCore_True() {
        final List<Boolean> wasCalled = new ArrayList<>();
        wasCalled.add(false);

        Runnable runner = () -> {
            wasCalled.clear();
            wasCalled.add(true); // hopefully this does not run
        };

        Db.loadDataCore(true, runner);

        assertFalse(wasCalled.getFirst());
    }

    @Test
    public void testStopping() {
        Path dbPathForTest = foosDirectory.resolve("testStopping");
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        MyThread.sleep(20);
        db.stop();
        assertTrue(logger.doesMessageExist("Stopping queue DatabaseWriter", 8));
    }

    @Test
    public void testStopping2() {
        Path dbPathForTest = foosDirectory.resolve("testStopping2");
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        MyThread.sleep(20);
        db.stop(1, 1);
        assertTrue(logger.doesMessageExist("Stopping queue DatabaseWriter", 8));
    }

    /**
     * This is TDD code to develop "indexed" data.
     * Database indexing is a technique used to speed up data retrieval operations in a
     * database. It's like having an index in a book that helps you quickly find specific
     * information without scanning the entire book.
     */
    @Test
    public void testCreateIndexes() {
        Path dbPathForTest = foosDirectory.resolve("testCreateIndexes");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        MyThread.sleep(20);

        db.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));
        Foo originalFoo = new Foo(0, 456, "for testing indexes");
        db.write(originalFoo);

        // This is a later step - where we already have an index in place,
        // and we wish to get data much faster than requiring a table scan.
        // in many cases, like here, we might expect to only find one item,
        // so we could getFirst() on the data.  In others, like searching
        // for favorite ice cream among thousands of people, we would
        // anticipate having to follow up with a search in the much smaller
        // group found.
        Foo resultantFoo = List.copyOf(db.getIndexedData("indexes_by_a_value", "456")).getFirst();
        assertEquals(originalFoo, resultantFoo);

        // after deleting an item, it shouldn't appear in the index
        db.delete(originalFoo);

        assertTrue(db.getIndexedData("indexes_by_a_value", "456").isEmpty());
    }


    /**
     * In {@link #testCreateIndexes()} we looked at a situation of the
     * index representing 1-to-1 data - an identifier pointing at one object,
     * and vice-versa.
     * <br>
     * But, another way this works is for partitioning data.  Let's say there
     * are a hundred pieces of data, having a property of "color".  And, let's
     * say there are 10 colors split amongst those 100 pieces of data.  In that
     * case, if we index by color, it's not 1-to-1 but it does help us find
     * a grouping by color to make our search more performant - so we only
     * end up searching through, let's say, "orange", rather than everything.
     * <br>
     * In this case we'll partition by number.
     */
    @Test
    public void testIndexesOnPartitionedData() {
        Path dbPathForTest = foosDirectory.resolve("testIndexesOnPartitionedData");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        MyThread.sleep(20);

        db.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));
        db.write(new Foo(0, 10, "for testing indexes"));
        db.write(new Foo(0, 10, "for testing indexes"));
        db.write(new Foo(0, 10, "for testing indexes"));
        db.write(new Foo(0, 20, "for testing indexes"));
        db.write(new Foo(0, 20, "for testing indexes"));
        db.write(new Foo(0, 20, "for testing indexes"));
        Foo thirdToLastFoo = db.write(new Foo(0, 30, "for testing indexes"));
        Foo penultimateFoo = db.write(new Foo(0, 30, "for testing indexes"));
        Foo lastFoo = db.write(new Foo(0, 30, "for testing indexes"));

        assertEquals(db.getIndexedData("indexes_by_a_value", "30").size(), 3);

        // after deleting an item, it shouldn't appear in the index
        db.delete(lastFoo);

        assertEquals(db.getIndexedData("indexes_by_a_value", "30").size(), 2);

        // now there's two left. Kill them.
        db.delete(penultimateFoo);
        db.delete(thirdToLastFoo);

        assertTrue(db.getIndexedData("indexes_by_a_value", "30").isEmpty());
    }

    /**
     * Let's see the difference in performance using indexes.
     * <br>
     * We'll create 1000 elements in a database, each with a random
     * UUID value as a propertly.  Then, we'll try repeatedly getting
     * a particular item by uuid, and compare that to getting using
     * the index.
     */
     @Test
    public void testIndexSpeedDifference() {
        Path path = Path.of("out/simple_db/bar");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        var db = new Db<>(path, context, new Bar(0, new UUID(0,0)));
        db.registerIndex("identifier", x -> x.getIdentifier().toString());

        List<UUID> uuids = new ArrayList<>();
        int collectionSize = 10;
        for (int i = 0; i < collectionSize; i++) {
            UUID uuid = UUID.randomUUID();
            uuids.add(uuid);
            Bar bar = new Bar(0, uuid);
            db.write(bar);
        }


        // now we have data.  Let's see how long it takes if we search
        // for items, using a scan, and comparing strings.

        StopwatchUtils stopwatchUtilsScanning = new StopwatchUtils().startTimer();
        for (int i = 0; i < 10; i++) {
            Collections.shuffle(uuids);
            for (UUID uuid : uuids) {
                Bar bar = SearchUtils.findExactlyOne(db.values().stream(), x -> x.identifier.toString().equals(uuid.toString()));
                assertEquals(bar.identifier, uuid);
            }
        }
        long timeInMillisForScan = stopwatchUtilsScanning.stopTimer();
        System.out.println("It took this long, in millis, to scan for unique items: " + timeInMillisForScan);
        // initial testing: this took about 850 milliseconds for a collection size of 1000


        StopwatchUtils stopwatchUtilsIndexed = new StopwatchUtils().startTimer();
        for (int i = 0; i < 10; i++) {
            Collections.shuffle(uuids);
            for (UUID uuid : uuids) {
                Bar bar = db.getIndexedData("identifier", uuid.toString()).stream().findFirst().orElseThrow();
                assertEquals(bar.identifier, uuid);
            }
        }
        long timeInMillisForIndex = stopwatchUtilsIndexed.stopTimer();
        System.out.println("It took this long, in millis, to get unique items by index: " + timeInMillisForIndex);
        // initial testing: this part took about 10 milliseconds.  Around 100x faster!
    }

    /**
     * Handling the situation well when a user tries to get data by an index, but
     * that index hasn't been registered.  Maybe it would be good at that point
     * in the error message to include the registered indexes.
     */
    @Test
    public void testIndex_NegativeCase_RequestingWithNoIndexRegistered() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_NegativeCase_RequestingWithNoIndexRegistered");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.write(new Foo(0, 1, "for testing"));

        var ex = assertThrows(DbException.class, () -> db.getIndexedData("indexes_by_a_value", "30"));
        assertEquals(ex.getMessage(), "There is no index registered on the database Db<Foo> with a name of \"indexes_by_a_value\"");
    }

    /**
     * If a user tries registering the same index on the same database twice,
     * throw an exception.  There's never a need to do that and it makes things confusing.
     */
    @Test
    public void testIndex_NegativeCase_RegisteringSameIndexTwice() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_NegativeCase_RegisteringSameIndexTwice");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("index", x -> x.b);
        var ex = assertThrows(DbException.class, () -> db.registerIndex("index", x -> ""));
        assertEquals(ex.getMessage(), "It is forbidden to register the same index more than once.  Duplicate index: \"index\"");
    }

    /**
     * A totally normal and expected use is to have multiple indexes available
     * for a database. Even if the different indexes have identical algorithms,
     * it's all good.
     */
    @Test
    public void testIndex_EdgeCase_MultipleIndexes() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_EdgeCase_MultipleIndexes");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("index", x -> x.b);
        assertTrue(db.registerIndex("foo", x -> x.b));
    }

    /**
     * If the user requests, a list of registered indexes can be returned,
     * maybe useful for debugging
     */
    @Test
    public void testIndex_GetListOfIndexes() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_GetListOfIndexes");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("index", x -> x.b);
        db.registerIndex("foo", x -> x.b);
        Set<String> indexes = db.getSetOfIndexes();
        assertEquals(Set.of("index", "foo"), indexes);
    }

    /**
     * If the user tries registering an index with a null value, complain
     */
    @Test
    public void testIndex_NegativeCase_IndexNameNull() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_NegativeCase_IndexNameNull");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        var ex = assertThrows(DbException.class, () -> db.registerIndex(null, x -> x.b));
        assertEquals(ex.getMessage(), "When registering an index, value must be a non-empty string");
    }

    /**
     * If a user tries to register an index with an empty string, complain
     */
    @Test
    public void testIndex_NegativeCase_IndexNameEmptyString() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_NegativeCase_IndexNameEmptyString");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        var ex = assertThrows(DbException.class, () -> db.registerIndex("", x -> x.b));
        assertEquals(ex.getMessage(), "When registering an index, value must be a non-empty string");
    }

    /**
     * A partitioning / indexing algorithm must be provided
     */
    @Test
    public void testIndex_NegativeCase_PartitioningAlgorithmNull() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_NegativeCase_PartitioningAlgorithmNull");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        var ex = assertThrows(DbException.class, () -> db.registerIndex("index", null));
        assertEquals(ex.getMessage(), "When registering an index, the partitioning algorithm must not be null");
    }

    /**
     * When the partitioning algorithm is applied, if an error occurs, make
     * clear to the user.
     */
    @Test
    public void testIndex_NegativeCase_ExceptionThrownByPartitionAlgorithm() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_NegativeCase_ExceptionThrownByPartitionAlgorithm");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("indexes_by_a_value", x -> String.valueOf(0/0));
        var ex = assertThrows(ArithmeticException.class, () -> db.write(new Foo(0, 30, "for testing indexes")));
        assertEquals(ex.getMessage(), "/ by zero");
    }

    @Test
    public void testSearchUtils_ShouldAccomodateUsingIndexes() {
        Path dbPathForTest = foosDirectory.resolve("testSearchUtils_ShouldAccomodateUsingIndexes");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));
        Foo foo = db.write(new Foo(0, 1, "for testing"));
        Foo indexesByAValue = db.findExactlyOne("indexes_by_a_value", "1");
        assertEquals(foo, indexesByAValue);
    }

    @Test
    public void testSearchUtils_SearchFindsNothing() {
        Path dbPathForTest = foosDirectory.resolve("testSearchUtils_SearchFindsNothing");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));
        db.write(new Foo(0, 1, "for testing"));
        Foo indexesByAValue = db.findExactlyOne("indexes_by_a_value", "2");
        assertTrue(indexesByAValue == null);
    }

    /**
     * Ensure that when updating data in the database (versus initial creation)
     * that the indexes work as expected
     */
    @Test
    public void testIndex_Update() {
        Path dbPathForTest = foosDirectory.resolve("testIndex_Update");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));
        Foo foo = db.write(new Foo(0, 1, "for testing"));
        Foo indexesByAValue = db.findExactlyOne("indexes_by_a_value", "1");
        assertEquals(foo, indexesByAValue);

        Foo updatedFoo = new Foo(foo.index, 42, "an update");
        db.write(updatedFoo);

        // we updated the data, so won't find anything now with an "a" value of "1"
        assertTrue(db.findExactlyOne("indexes_by_a_value", "1") == null);

        Foo indexesByAValue42 = db.findExactlyOne("indexes_by_a_value", "42");
        assertEquals(indexesByAValue42, updatedFoo);

        Foo updatedFoo2 = new Foo(foo.index, 42, "an update, again, but not changing the a value this time");
        db.write(updatedFoo2);

        Foo indexesByAValue42_again = db.findExactlyOne("indexes_by_a_value", "42");
        assertEquals(indexesByAValue42_again, updatedFoo2);
    }

    @Test
    public void testSearchUtility() {
        Path dbPathForTest = foosDirectory.resolve("testSearchUtility");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, INSTANCE);

        // negative case - no index yet
        var ex1 = assertThrows(DbException.class, () -> db.findExactlyOne("indexes_by_a_value", "1"));
        assertEquals(ex1.getMessage(), "There is no index registered on the database Db<Foo> with a name of \"indexes_by_a_value\"");

        db.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));

        // negative case - no data found by index "1"
        assertTrue(db.findExactlyOne("indexes_by_a_value", "1") == null);

        // same thing, but we specify what to return instead of null
        assertEquals(INSTANCE, db.findExactlyOne("indexes_by_a_value", "1", () -> INSTANCE));

        // negative case: what we specify to return instead of null throws an exception!
        var ex = assertThrows(DbException.class, () -> db.findExactlyOne("indexes_by_a_value", "1", () -> {
            throw new RuntimeException("Testing");
        }));
        assertEquals(ex.getMessage(), "java.lang.RuntimeException: Testing");

        Foo foo = db.write(new Foo(0, 1, "for testing"));

        // happy path - find something.
        Foo indexesByAValue = db.findExactlyOne("indexes_by_a_value", "1");
        assertEquals(foo, indexesByAValue);

        // this may look like the first Foo we added, but since I've set the index
        // to 0, it will create a nearly-identical piece of data in the database,
        // only differing in its index.  But, since our registered index is on a
        // field, "a", that might not be unique (as we have done here), then using
        // "findExactlyOne" will find more than one, and thus throw an exception.
        db.write(new Foo(0, 1, "for testing"));
        var ex2 = assertThrows(DbException.class, () -> db.findExactlyOne("indexes_by_a_value", "1"));
        assertEquals(ex2.getMessage(), "More than one item found when searching database Db<Foo> on index \"indexes_by_a_value\" with key 1");
    }

    /**
     * This is a simulation just to better understand timing - we're just comparing
     * a table scan of a list versus a lookup in a map.  If we did this with our
     * real database it would cause havoc on the disk and it tried writing thousands
     * of files.
     * <br>
     * Running this with 10 million entries takes 531 milliseconds for the search
     * by scan, and 0 when using the map.  So, 10,000x speedup?
     */
    @Test
    public void testSearchUtilityPerformance() {
        int countOfData = 10;
        List<Bar> barList = new ArrayList<>();
        Map<String, Bar> barMap = new HashMap<>();

        for (int i = 0; i < countOfData; i++) {
            UUID identifier = UUID.randomUUID();
            Bar b = new Bar(i, identifier);
            barList.add(b);
            barMap.put(identifier.toString(), b);
        }
        UUID identifier = UUID.randomUUID();
        Bar b = new Bar(-1, identifier);
        barList.add(b);
        barMap.put(identifier.toString(), b);

        var fullScanStopwatch = new StopwatchUtils().startTimer();
        Bar exactlyOne = SearchUtils.findExactlyOne(barList.stream(), x -> x.identifier.equals(identifier));
        assertEquals(exactlyOne, b);
        System.out.println("full scan took " + fullScanStopwatch.stopTimer());

        var indexedStopwatch = new StopwatchUtils().startTimer();
        Bar bar = barMap.get(identifier.toString());
        assertEquals(bar, b);
        System.out.println("find by index took " + indexedStopwatch.stopTimer());
    }

    public static class Bar extends DbData<Bar> {

        private long index;
        private final UUID identifier;

        public Bar(long index, UUID identifier) {

            this.index = index;
            this.identifier = identifier;
        }

        public UUID getIdentifier() {
            return identifier;
        }

        @Override
        protected String serialize() {
            return serializeHelper(index, identifier);
        }

        @Override
        protected Bar deserialize(String serializedText) {
            final var tokens =  deserializeHelper(serializedText);
            return new Bar(
                    Integer.parseInt(tokens.get(0)),
                    UUID.fromString(tokens.get(1)));
        }

        @Override
        protected long getIndex() {
            return this.index;
        }

        @Override
        protected void setIndex(long index) {
            this.index = index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Bar bar = (Bar) o;
            return index == bar.index && Objects.equals(identifier, bar.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, identifier);
        }

        @Override
        public String toString() {
            return "Bar{" +
                    "index=" + index +
                    ", identifier=" + identifier +
                    '}';
        }
    }

    public static class Foo extends DbData<Foo> implements Comparable<Foo> {

        private long index;
        private final int a;
        private final String b;

        public Foo(long index, int a, String b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }

        static final Foo INSTANCE = new Foo(0,0,"");

        public int getA() {
            return a;
        }

        @Override
        public String serialize() {
            return serializeHelper(index, a, b);
        }

        @Override
        public Foo deserialize(String serializedText) {
            final var tokens =  deserializeHelper(serializedText);
            return new Foo(
                    Integer.parseInt(tokens.get(0)),
                    Integer.parseInt(tokens.get(1)),
                    tokens.get(2)
                    );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Foo foo = (Foo) o;
            return index == foo.index && a == foo.a && Objects.equals(b, foo.b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, a, b);
        }

        @Override
        public long getIndex() {
            return index;
        }

        @Override
        public void setIndex(long index) {
            this.index = index;
        }

        // implementing comparator just so we can assertEqualsDisregardOrder on a collection of these
        @Override
        public int compareTo(Foo o) {
            return Long.compare( o.getIndex() , this.getIndex() );
        }

        @Override
        public String toString() {
            return "Foo{" +
                    "index=" + index +
                    ", a=" + a +
                    ", b='" + b + '\'' +
                    '}';
        }
    }

    static class Fubar extends DbData<Fubar3> {

        private long index;
        private final int a;
        private final String b;

        @Override
        public String toString() {
            return "Fubar{" +
                    "index=" + index +
                    ", a=" + a +
                    ", b='" + b + '\'' +
                    '}';
        }

        public Fubar(long index, int a, String b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }


        @Override
        public String serialize() {
            return serializeHelper(index, a, b);
        }

        @Override
        public Fubar3 deserialize(String serializedText) {
            return null;
        }

        @Override
        public long getIndex() {
            return index;
        }

        @Override
        public void setIndex(long index) {
            this.index = index;
        }

    }

    static class Fubar2 extends DbData<Fubar3> {

        private long index;
        private final int a;
        private final String b;

        @Override
        public String toString() {
            return "Fubar{" +
                    "index=" + index +
                    ", a=" + a +
                    ", b='" + b + '\'' +
                    '}';
        }

        public Fubar2(long index, int a, String b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }

        @Override
        public String serialize() {
            return null;
        }

        @Override
        public Fubar3 deserialize(String serializedText) {
            return null;
        }

        @Override
        public long getIndex() {
            return index;
        }

        @Override
        public void setIndex(long index) {
            this.index = index;
        }

    }

    static class Fubar3 extends DbData<Fubar3> {

        private long index;
        private final int a;
        private final String b;

        @Override
        public String toString() {
            return "Fubar{" +
                    "index=" + index +
                    ", a=" + a +
                    ", b='" + b + '\'' +
                    '}';
        }

        public Fubar3(long index, int a, String b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }

        @Override
        public String serialize() {
            return "";
        }

        @Override
        public Fubar3 deserialize(String serializedText) {
            return null;
        }

        @Override
        public long getIndex() {
            return index;
        }

        @Override
        public void setIndex(long index) {
            this.index = index;
        }

    }


    private Context buildTestingContextWithRegularLogger() {
        var constants = new Constants();
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var logger = new Logger(constants, executorService, "db_perf_testing");

        var context = new Context(executorService, constants);
        context.setLogger(logger);

        return context;
    }
}
