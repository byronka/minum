package com.renomad.minum.database;

import com.renomad.minum.logging.Logger;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.SearchUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.renomad.minum.database.DbEngine2Tests.Foo.INSTANCE;
import static com.renomad.minum.database.DatabaseChangeAction.DELETE;
import static com.renomad.minum.database.DatabaseChangeAction.UPDATE;
import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;
import static java.util.stream.IntStream.range;

public class DbEngine2Tests {
    private Context context;
    private TestLogger logger;
    private FileUtils fileUtils;
    static Path foosDirectory = Path.of("out/simple_db_for_engine2_tests/engine2/foos");
    static Path fubarDirectory = Path.of("out/simple_db_for_engine2_tests/engine2/fubar");

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
     * what happens if we try deleting a file that doesn't exist?
     */
    @Test
    public void test_Edge_DeleteFileDoesNotExist() {
        // clear out the directory to start
        Path dbPathForTest = foosDirectory.resolve("test_Edge_DeleteFileDoesNotExist");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        final var db_throwaway = new DbEngine2<>(dbPathForTest, context, INSTANCE);

        var ex = assertThrows(DbException.class, () -> db_throwaway.delete(new Foo(123, 123, "")));
        assertEquals(ex.getMessage(), "no data was found with index of 123");

        db_throwaway.stop();
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
     *     Namely, the first load of data for any particular {@link DbEngine2} class
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
            var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
            var db2 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
            db2.write(foo2);
            MyThread.sleep(stepDelay);

            var foos2 = db2.values().stream().toList();
            assertEquals(foos2.size(), 2);
            assertEquals(foos2.get(0), foo1);
            assertEquals(foos2.get(1), foo2);
            db2.stop();
            MyThread.sleep(stepDelay);

            // third round - reading from a database that has yet to read from disk
            var db3 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
            var foos3 = db3.values().stream().toList();
            assertEquals(foos3.size(), 2);
            assertEquals(foos3.get(0), foo1);
            assertEquals(foos3.get(1), foo2);
            db3.stop();
            MyThread.sleep(stepDelay);

            // fourth round - deleting from a database that has yet to read from disk
            var db4 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
            db4.delete(foo2);
            var foos4 = db4.values().stream().toList();
            assertEquals(foos4.size(), 1);
            assertEquals(foos4.getFirst(), foo1);
            db4.stop();
            MyThread.sleep(stepDelay);

            // fifth round - updating an item in a database that has not yet read from disk
            var db5 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
            var updatedFoo1 = new Foo(1, 42, "update");
            db5.write(updatedFoo1);
            var foos5 = db5.values().stream().toList();
            assertEquals(foos5.size(), 1);
            assertEquals(foos5.getFirst(), updatedFoo1);
            db5.stop();
            MyThread.sleep(stepDelay);

            // sixth round - if we delete, it will reset the next index to 1
            var db6 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(foosDirectory.resolve("test_Db_Delete_EdgeCase_DoesNotExist"), context, INSTANCE);
        var ex = assertThrows(DbException.class, () -> db.delete(new Foo(1, 2, "a")));
        assertEquals(ex.getMessage(), "no data was found with index of 1");
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        var ex = assertThrows(NullPointerException.class, () -> db.delete(null));
        assertEquals(ex.getMessage(), "Cannot invoke \"com.renomad.minum.database.DbData.serialize()\" because \"dataToDelete\" is null");
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
        var myDatabase = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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

    @Test
    public void testWriteDeserializationComplaints() {
        Path dbPathForTest = fubarDirectory.resolve("testWriteDeserializationComplaints");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, new Fubar3(0, 0, ""));

        var ex = assertThrows(InvariantException.class, () -> db.write(new Fubar3(0, 2, "a")));
        assertEquals(ex.getMessage(), "the serialized form of data must not be blank. Is the serialization code written properly? Our datatype: Fubar{index=0, a=0, b=''}");
    }

    @Test
    public void testWriteDeserializationComplaints2() {
        Path dbPathForTest = fubarDirectory.resolve("testWriteDeserializationComplaints2");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, new Fubar2(0, 0, ""));

        var ex = assertThrows(InvariantException.class, () -> db.write(new Fubar2(0, 2, "a")));
        assertEquals(ex.getMessage(), "the serialized form of data must not be blank. Is the serialization code written properly? Our datatype: Fubar{index=0, a=0, b=''}");
    }


    /**
     * Now that {@link DbEngine2#write(DbData)} has subsumed the capabilities
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);

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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);

        Foo foo1 = new Foo(-1, 2, "a");

        assertThrows(DbException.class, "Negative indexes are disallowed", () -> db.write(foo1));
        db.stop();
        MyThread.sleep(FINISH_TIME);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(path, context, new Bar(0, new UUID(0,0)));
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("indexes_by_a_value", x -> String.valueOf(0/0));
        var ex = assertThrows(ArithmeticException.class, () -> db.write(new Foo(0, 30, "for testing indexes")));
        assertEquals(ex.getMessage(), "/ by zero");
    }

    @Test
    public void testSearchUtils_ShouldAccommodateUsingIndexes() {
        Path dbPathForTest = foosDirectory.resolve("testSearchUtils_ShouldAccommodateUsingIndexes");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        db.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));
        Foo foo = db.write(new Foo(0, 1, "for testing"));
        Foo indexesByAValue = db.findExactlyOne("indexes_by_a_value", "1");
        assertEquals(foo, indexesByAValue);
    }

    @Test
    public void testSearchUtils_SearchFindsNothing() {
        Path dbPathForTest = foosDirectory.resolve("testSearchUtils_SearchFindsNothing");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
    public void test_NegativeCase_NoIndex() {
        Path dbPathForTest = foosDirectory.resolve("test_NegativeCase_NoIndex");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);

        // negative case - no index yet
        var ex1 = assertThrows(DbException.class, () -> db.findExactlyOne("indexes_by_a_value", "1"));
        assertEquals(ex1.getMessage(), "There is no index registered on the database Db<Foo> with a name of \"indexes_by_a_value\"");
    }

    @Test
    public void testSearchUtility() {
        Path dbPathForTest = foosDirectory.resolve("testSearchUtility");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
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
     * When the first thing we do is request data from an index
     * on a database, if no other reads have happened it will be
     * the first thing causing a load to happen.
     */
    @Test
    public void test_firstActionIsRequestingDataByIndex() {
        Path dbPathForTest = foosDirectory.resolve("dbengine2_test_firstActionIsRequestingDataByIndex");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        Foo writtenFoo = db.write(new Foo(0, 1, "a"));
        db.stop(10, 50);

        MyThread.sleep(50);

        var dbRestarted = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        dbRestarted.registerIndex("index", Foo::getB);
        Collection<Foo> indexedData = dbRestarted.getIndexedData("index", "a");
        assertEquals(new ArrayList<>(indexedData), List.of(writtenFoo));
    }


    /**
     * When the first thing we do is request data from an index
     * on a database, if no other reads have happened it will be
     * the first thing causing a load to happen.
     */
    @Test
    public void test_firstActionIsFindExactlyOne() {
        Path dbPathForTest = foosDirectory.resolve("dbengine2_test_firstActionIsFindExactlyOne");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        Foo writtenFoo = db.write(new Foo(0, 1, "a"));
        db.stop(10, 50);

        MyThread.sleep(50);

        var dbRestarted = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        dbRestarted.registerIndex("index", Foo::getB);
        Foo exactlyOne = dbRestarted.findExactlyOne("index", "a");
        assertEquals(exactlyOne, writtenFoo);
    }

    /**
     * When this is looped a hundred thousand times, it takes 500 milliseconds to finish
     * making the updates in memory.  It takes several minutes later for it to
     * finish getting those changes persisted to disk.
     * a million writes in 500 milliseconds means 2 million writes in one sec.
     */
    @Test
    public void test_Performance() {
        int originalFooCount = 10;
        int loopCount = 10;

        // clear out the directory to start
        Path dbPathForTest = foosDirectory.resolve("test_Performance");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);

        // build a Context without testlogger - testlogger would impact perf here
        Context contextWithRegularLogger = buildTestingContextWithRegularLogger();
        final var db = new DbEngine2<>(dbPathForTest, contextWithRegularLogger, INSTANCE);
        MyThread.sleep(10);

        final var foos = new ArrayList<DbEngine2Tests.Foo>();

        // write the foos
        for (int i = 0; i < originalFooCount; i++) {
            final var newFoo = new DbEngine2Tests.Foo(0, i + 1, "original");
            foos.add(newFoo);
            db.write(newFoo);
        }

        db.flush();

        // change the foos
        final var outerTimer = new StopwatchUtils().startTimer();
        final var innerTimer = new StopwatchUtils().startTimer();

        final var newFoos = new ArrayList<DbEngine2Tests.Foo>();
        for (var i = 1; i < loopCount; i++) {
            newFoos.clear();
                /*
                loop through the old foos and update them to new values,
                creating a new list in the process.  There should only
                ever be 10 foos.
                 */
            for (var foo : foos) {
                final var newFoo = new DbEngine2Tests.Foo(foo.getIndex(), foo.getA() + 1, foo.getB() + "_updated");
                newFoos.add(newFoo);
                db.write(newFoo);
            }
        }

        db.flush();

        logger.logDebug(() -> "It took " + innerTimer.stopTimer() + " milliseconds to make the updates in memory");
        db.stop(100, 50);
        logger.logDebug(() -> "It took " + outerTimer.stopTimer() + " milliseconds to finish writing everything to disk");

        MyThread.sleep(100);

        final var db1 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        Collection<DbEngine2Tests.Foo> values = db1.values();
        assertTrue(newFoos.containsAll(values));
        db1.stop(100, 50);

        MyThread.sleep(FINISH_TIME);

        shutdownTestingContext(contextWithRegularLogger);
    }

    /**
     * Just a TDD introduction to this new idea, where each database CRUD action
     * causes an append to a file, and a followup process condenses it.
     * <br>
     * Notes: 50,000 writes in 43 milliseconds is 220,264 writes per second. Not bad.
     */
    @Test
    public void testGettingStarted() throws IOException {
        int loopCount = 50;
        Path newPersistenceDirectory = foosDirectory.resolve("new_persistence");
        fileUtils.deleteDirectoryRecursivelyIfExists(newPersistenceDirectory);
        fileUtils.makeDirectory(newPersistenceDirectory);
        var dc = new DatabaseConsolidator(newPersistenceDirectory, context);
        var da = new DatabaseAppender(newPersistenceDirectory, context);
        var random = new java.util.Random();

        // this outer loop simulates "reality", that is, it simulates a user
        // causing lots of database updates/deletes, but we'll cap it
        // for this test.
        StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();
        for (int i = 0; i < loopCount; i++) {
            var id = random.nextInt(1000);
            // 80% of the time, the operation will be an update
            DatabaseChangeAction operation = random.nextInt(100) < 80 ? UPDATE : DELETE;
            da.appendToDatabase(operation, new Foo(id, id, "this is foo " + id).serialize());
        }
        da.flush();
        long l = stopwatchUtils.stopTimer();
        System.out.println("Took this many millis to add all the data to disk: " + l);


        // consolidate the data into files
        dc.consolidate();
        MyThread.sleep(2000);

        // load the database and use a combination of the consolidated files
        // and the append-only files to recreate state in memory
        DbEngine2<Foo> fooDbImproved = new DbEngine2<>(newPersistenceDirectory, context, Foo.INSTANCE);
        fooDbImproved.registerIndex("a_index", x -> String.valueOf(x.getA()));

        fooDbImproved.loadData();
        Collection<Foo> values = fooDbImproved.values();
        assertTrue(values.size() > 10, "The size of Values was supposed to be greater than 10.  Actual value: " + values.size());

        fooDbImproved.write(new Foo(0, 12345, "The final countdown"));

        Foo returnedFoo = fooDbImproved.findExactlyOne("a_index", "12345");
        assertEquals(returnedFoo.getA(), 12345);
    }


    /**
     * Wide-ranging capabilities of the database
     */
    @Test
    public void test_GeneralCapability() {
        // build a context with customized values to make testing easier.
        // Explanation: The files that are created with the new appender/consolidator
        // only split up files at large values, like 100k lines.  Here, we'll create
        // a Constants class with values much smaller, so we can verify processing
        // without having to create huge files.
        var properties = new Properties();
        properties.setProperty("MAX_DATABASE_APPEND_COUNT", "5");
        properties.setProperty("MAX_DATABASE_CONSOLIDATED_FILE_LINES", "5");
        properties.setProperty("DB_DIRECTORY","out/simple_db_for_engine2_tests");
        var customContext = TestFramework.buildTestingContext("test_GeneralCapability", properties);
        Path dbPathForTest = foosDirectory.resolve("test_GeneralCapability");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        MyThread.sleep(FINISH_TIME);

        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        MyThread.sleep(FINISH_TIME);

        for (int i = 0; i < 7; i++) {

            int finalI = i;
            logger.logDebug(() -> "DbEngine2 general capability, round " + finalI);
            final var foos = range(1, 40).mapToObj(x -> new Foo(0, x, "abc" + x)).toList();

            // make some files on disk
            for (var foo : foos) {
                db.write(foo);
            }

            MyThread.sleep(300);

            // check that the files are now there.
            Path foundFile = dbPathForTest.resolve("currentAppendLog");
            assertTrue(Files.exists(foundFile), "should find file at " + foundFile);

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
            assertTrue(db.values().isEmpty());
        }
        // give the action queue time to save files to disk
        // then shut down.
        db.stop();
        MyThread.sleep(FINISH_TIME);
        TestFramework.shutdownTestingContext(customContext);
    }


    /**
     * This test examines the behavior when a user creates an
     * instance of {@link DbEngine2} pointing at the directory
     * of data that was previously for {@link Db}.
     * <br>
     * In that case, it will convert the data to exclusively
     * work with the new database format.
     */
    @Test
    public void test_ConvertingDatabase_Db_To_DbEngine2() throws IOException {
        // build a context with customized values to make testing easier.
        // Explanation: The files that are created with the new appender/consolidator
        // only split up files at large values, like 100k lines.  Here, we'll create
        // a Constants class with values much smaller, so we can verify processing
        // without having to create huge files.
        var properties = new Properties();
        properties.setProperty("MAX_DATABASE_APPEND_COUNT", "5");
        properties.setProperty("MAX_DATABASE_CONSOLIDATED_FILE_LINES", "5");
        properties.setProperty("DB_DIRECTORY","out/simple_db_for_engine2_tests");
        String directoryName = "test_ConvertingDatabase_Db_To_DbEngine2";
        var customContext = TestFramework.buildTestingContext(directoryName, properties);

        // arrange
        Path newPersistenceDirectory = Path.of(customContext.getConstants().dbDirectory).resolve(directoryName);
        fileUtils.deleteDirectoryRecursivelyIfExists(newPersistenceDirectory);

        // create database of first type
        Db<Foo> db1 = customContext.getDb(directoryName, INSTANCE);

        // add some data
        List<Foo> foos = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Foo data = db1.write(new Foo(0, i, "a" + i));
            foos.add(data);
            MyThread.sleep(5);
        }

        // stop the database
        db1.stop(10, 50);

        // create DbEngine2 database type, pointing at old directory
        DbEngine2<Foo> db2 = customContext.getDb2(directoryName, INSTANCE);
        List<Foo> foos2 = new ArrayList<>(db2.values().stream().toList());

        // assertions
        foos.sort(Comparator.comparingLong(Foo::getIndex));
        foos2.sort(Comparator.comparingLong(Foo::getIndex));
        assertEquals(foos2.toString(), foos.toString());
        long countOfOldFiles = Files.list(newPersistenceDirectory).filter(x -> x.getFileName().endsWith(".ddps")).count();
        assertEquals(countOfOldFiles, 0L);

        // stop new database
        db2.stop(10, 50);

        // restart new database
        DbEngine2<Foo> db2Restarted = customContext.getDb2(directoryName, INSTANCE);
        List<Foo> listRestarted = new ArrayList<>(db2Restarted.values().stream().toList());

        // assertions
        foos.sort(Comparator.comparingLong(Foo::getIndex));
        listRestarted.sort(Comparator.comparingLong(Foo::getIndex));
        assertEquals(listRestarted.toString(), foos.toString());
        List<String> newDirectoryFiles = new ArrayList<>(Files.walk(newPersistenceDirectory).map(x -> x.getFileName().toString()).toList());
        newDirectoryFiles.sort(Comparator.naturalOrder());
        assertEquals(newDirectoryFiles.toString(), "[11_to_15, 16_to_20, 1_to_5, 21_to_25, 26_to_30, 31_to_35, 36_to_40, 41_to_45, 46_to_50, 6_to_10, append_logs, consolidated_data, currentAppendLog, test_ConvertingDatabase_Db_To_DbEngine2]");

        TestFramework.shutdownTestingContext(customContext);
    }

    @Test
    public void test_EdgeCase_RegisteringIndexTooLate() {
        Path dbPathForTest = foosDirectory.resolve("test_EdgeCase_RegisteringIndexTooLate");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        db.write(new Foo(0, 1, "a"));
        var ex = assertThrows(DbException.class, () -> db.registerIndex("index_too_late", x -> x.b));
        assertEquals(ex.getMessage(), "This method must be run before the database loads data from disk.  Typically, it should be run immediately after the database is created.  See this method's documentation");
    }

    /**
     * Flush is able to fail in certain very unusual situations, like disk
     * running out of space, using the wrong encoding for a writer, and so on,
     * none of which are easy to test or likely.  So instead, we'll just
     * wrap flush for easier testing.
     */
    @Test
    public void test_EdgeCase_FlushFailure() {
        Writer exceptionThrowingWriter = new Writer() {
            @Override public void write(char[] cbuf, int off, int len) throws IOException {}
            public void flush() throws IOException {throw new IOException("This is a test exception");}
            @Override public void close() throws IOException {}
        };
        var ex = assertThrows(DbException.class, () -> DatabaseAppender.flush(exceptionThrowingWriter, logger));
        assertEquals(ex.getMessage(), "java.io.IOException: This is a test exception");
        assertTrue(logger.doesMessageExist("Error while flushing"));
    }

    /**
     * There is a lock surrounding the method to save off
     * the current append-only log to a new file, so that
     * we can avoid worrying about race conditions.  However,
     * it is difficult to test the situation of getting into
     * the inner method and finding we can skip the code.
     * <br>
     * A new method, {@link DatabaseAppender#saveOffWrapped(int, int)}
     * was added just to make testing easier.
     */
    @Test
    public void test_EdgeCase_PreventSaveOff() throws IOException {
        var da = new DatabaseAppender(Path.of("out/test_EdgeCase_PreventSaveOff"), context);
        assertEquals(da.saveOffWrapped(99, 100), "");
    }

    /**
     * Because we are writing an unexpected file to the append_logs directory,
     * the consolidation process will fail to convert it to a date, and an exception
     * will bubble to the top.
     */
    @Test
    public void test_LoadingData_NegativeCase() throws IOException {
        Path path = Path.of("out/test_LoadingData_NegativeCase/");
        DbEngine2<Foo> fooDbEngine2 = new DbEngine2<>(path, context, INSTANCE);
        Files.writeString(path.resolve("append_logs/foofoo"), "THIS IS A NEGATIVE TEST");

        var ex = assertThrows(DbException.class, () -> fooDbEngine2.loadData());

        assertEquals(ex.getMessage(), "Failed to load data from disk.");
    }

    /**
     * If multiple threads are trying to load the data at once, only one
     * will get in.
     */
    @Test
    public void test_LoadingData_MultipleThreads() throws ExecutionException, InterruptedException {
        // instantiate a database, give it data, then shutdown the database
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, true);
        Path path = Path.of("out/test_LoadingData_MultipleThreads/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        DbEngine2<Foo> originalDb = new DbEngine2<>(path, context, INSTANCE);
        originalDb.write(new Foo(0, 42, "alice"));
        originalDb.write(new Foo(0, 43, "bob"));
        originalDb.write(new Foo(0, 44, "carol"));
        originalDb.stop();

        // start the same database again, and make sure we load data properly.
        // then start up multiple threads to load the data concurrently (an edge scenario)
        DbEngine2<Foo> db = new DbEngine2<>(path, context, INSTANCE);

        ExecutorService executorService = context.getExecutorService();
        var racers = new ArrayList<Future<?>>();
        racers.add(executorService.submit(db::loadData));
        racers.add(executorService.submit(db::loadData));
        racers.add(executorService.submit(db::loadData));
        racers.add(executorService.submit(db::loadData));
        // wait here until all threads are finished.
        for (var racer : racers) {
            racer.get();
        }

        // assert that the resulting values are as expected
        assertEqualsDisregardOrder(db.values().stream().map(Foo::getB).toList(), List.of("alice", "bob", "carol"));
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, false);
    }

    /**
     * If the data doesn't properly deserialize, an exception is thrown
     */
    @Test
    public void test_readAndDeserialize_NegativeCase() {
        // instantiate a database, give it data, then shutdown the database
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, true);
        Path path = Path.of("out/test_readAndDeserialize_NegativeCase/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        DbEngine2<Foo> db = new DbEngine2<>(path, context, INSTANCE);

        var ex = assertThrows(DbException.class, () -> db.readAndDeserialize("THIS IS INVALID DATA", "myfile"));

        assertEquals(ex.getMessage(), "Failed to deserialize THIS IS INVALID DATA with data (\"myfile\"). Caused by: java.lang.NumberFormatException: For input string: \"THIS IS INVALID DATA\"");
    }


    /**
     * If the DbData type for this database lacks a serializer, an
     * exception must be thrown.
     */
    @Test
    public void test_readAndDeserialize_NegativeCase_LackingSerializer() {
        // instantiate a database, give it data, then shutdown the database
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, true);
        Path path = Path.of("out/test_readAndDeserialize_NegativeCase/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        DbEngine2<Fubar3> db = new DbEngine2<>(path, context, new Fubar3(0, 0, ""));

        var ex = assertThrows(DbException.class, () -> db.readAndDeserialize("THIS IS INVALID DATA", "myfile"));

        assertEquals(ex.getMessage(), "Failed to deserialize THIS IS INVALID DATA with data (\"myfile\"). Caused by: com.renomad.minum.utils.InvariantException: deserialization of Fubar{index=0, a=0, b=''} resulted in a null value. Was the serialization method implemented properly?");
    }

    /**
     * If the data doesn't properly deserialize, an exception is thrown. This
     * scenario is where we have invalid data in the database and tell the
     * database to load its data.
     */
    @Test
    public void test_Initialize_readAndDeserialize_NegativeCase() {
        // instantiate a database, give it data, then shutdown the database
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, true);
        Path path = Path.of("out/test_Initialize_readAndDeserialize_NegativeCase/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        DbEngine2<Foo> db = new DbEngine2<>(path, context, INSTANCE);

        // add some bad data - really and truly, the bad data is the fact that
        // 1_to_10 is a directory rather than a text file.
        fileUtils.makeDirectory(path.resolve("consolidated_data/1_to_10/"));

        // now ask the database to load, expecting failure
        var ex = assertThrows(DbException.class, () -> db.loadData());

        assertEquals(ex.getMessage(), "Failed to load data from disk.");
    }

    /**
     * If the data is already loaded, then the loadData method
     * will not need to be run.
     * The question is, how do we know whether it was run or not?
     * One way to tell: if there is no data to load, there will be a log
     * statement mentioning "adding nothing to the data". If we don't see
     * that log statement, it means we skipped loadDataFromDisk() successfully.
     */
    @Test
    public void test_LoadData_NoNeed() {
        Path dbPathForTest = foosDirectory.resolve("test_LoadData_NoNeed");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        final var db = new DbEngine2<>(dbPathForTest, context, DbTests.Foo.INSTANCE);
        db.hasLoadedData = true;

        db.loadData();

        assertThrows(TestLoggerException.class,
                () -> logger.doesMessageExist("Loading data from disk for dbEngine2"));
    }

    /**
     * If the file to be analyzed as a consolidated file doesn't match
     * expectations, an exception gets thrown.
     */
    @Test
    public void test_parseConsolidatedFileName_NegativeCase() {
        // completely missing the format
        var ex = assertThrows(DbException.class, () -> DbEngine2.parseConsolidatedFileName("AN INVALID FILENAME"));
        assertEquals(ex.getMessage(), "Consolidated filename was invalid: AN INVALID FILENAME");

        // lacks a digit at the beginning
        var ex2 = assertThrows(NumberFormatException.class, () -> DbEngine2.parseConsolidatedFileName("a_to_10"));
        assertEquals(ex2.getMessage(), "Error at index 0 in: \"a\"");
    }

    /**
     * If an exception takes place in the consolidation after calling the
     * deleteFromDisk method, it will end up being caught and logged down in the consolidation method.
     */
    @Test
    public void test_FailureDuringConsolidation() throws IOException {
        var properties = new Properties();
        properties.setProperty("MAX_DATABASE_APPEND_COUNT", "1");
        properties.setProperty("MAX_DATABASE_CONSOLIDATED_FILE_LINES", "1");
        var customContext = TestFramework.buildTestingContext("test_FailureDuringConsolidation", properties);

        // instantiate a database, give it data, then shutdown the database
        TestLogger logger1 = (TestLogger)customContext.getLogger();
        logger1.getActiveLogLevels().put(LoggingLevel.TRACE, true);
        Path path = Path.of("out/test_FailureDuringConsolidation/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        DbEngine2<Foo> db = new DbEngine2<>(path, customContext, INSTANCE);

        // now ask the database to load, expecting failure
        Foo foo = new Foo(0, 1, "a");
        db.write(foo);

        // add some bad data in the right place and time
        Files.writeString(path.resolve("append_logs/foofoo"), "BROKEN DATA");

        db.delete(foo);

        assertTrue(logger1.doesMessageExist("Error during consolidation: com.renomad.minum.database.DbException: java.text.ParseException: Unparseable date: \"foofoo\""));
        TestFramework.shutdownTestingContext(customContext);
    }

    /**
     * The {@link DbEngine2} constructor can throw an exception because
     * when it starts, it initializes the {@link DatabaseAppender} which
     * can throw an exception, because it starts with doing some file operations.
     * <br>
     * Here, we will try to get things into a state so an exception will
     * be thrown.
     */
    @Test
    public void test_FailureDuringInstantiation() {
        // instantiate a database, give it data, then shutdown the database
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, true);
        Path path = Path.of("out/test_FailureDuringInstantiation/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);

        // now, if there is a file, "currentAppendLog", that is not
        // actually a file but is actually a directory, that should
        // cause an exception to be thrown.

        fileUtils.makeDirectory(path.resolve("currentAppendLog"));

        var ex = assertThrows(DbException.class, () -> new DbEngine2<>(path, context, INSTANCE));
        assertEquals(ex.getMessage(), "Error while initializing DatabaseAppender in DbEngine2");
    }

    /**
     * When DbEngine2 tries to delete, if any exceptions take place further down
     * it will log the error and fail to write to disk. Obviously that's not a
     * great outcome - it means the memory change will exist but not the disk change.
     */
    @Test
    public void test_FailureDuringDelete() {
        Path dbPathForTest = foosDirectory.resolve("test_FailureDuringDelete");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        db.databaseAppender.bufferedWriter = new BrokenBufferedWriter(new BrokenWriter());
        db.hasLoadedData = true;
        db.index = new AtomicLong(1);

        var ex = assertThrows(DbException.class, () -> db.delete(new Foo(0, 1, "a")));

        assertEquals(ex.getMessage(), "failed to delete data Foo{index=0, a=1, b='a'}");
    }

    /**
     * When DbEngine2 tries to write to disk, if any exceptions take place further down
     * it will log the error and fail to write to disk. Obviously that's not a
     * great outcome - it means the memory change will exist but not the disk change.
     */
    @Test
    public void test_FailureDuringWrite() {
        Path dbPathForTest = foosDirectory.resolve("test_FailureDuringWrite");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        db.databaseAppender.bufferedWriter = new BrokenBufferedWriter(new BrokenWriter());
        db.hasLoadedData = true;
        db.index = new AtomicLong(1);

        var ex = assertThrows(DbException.class, () -> db.write(new Foo(0, 1, "a")));

        assertEquals(ex.getMessage(), "failed to write data Foo{index=1, a=1, b='a'}");
    }

    /**
     * If the thread enters this method and is prevented from running its code
     * because of failing the predicate at the beginning, then the appendCount
     * will remain unchanged after this method returns.
     */
    @Test
    public void test_ConsolidateInnerCode() {
        Path dbPathForTest = foosDirectory.resolve("test_ConsolidateInnerCode");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        db.appendCount.set(1);
        db.maxLinesPerAppendFile = 2;
        db.consolidationIsRunning = true;

        // act
        db.consolidateInnerCode();

        // assert
        assertEquals(db.appendCount.get(), 1);

        // adjust, but we still won't make it through because not enough append count
        db.appendCount.set(2);

        // act
        db.consolidateInnerCode();

        // assert
        assertEquals(db.appendCount.get(), 2);

        // adjust high enough to go through, but since consolidation is running, we won't.
        db.appendCount.set(3);

        // act
        db.consolidateInnerCode();

        // assert
        assertEquals(db.appendCount.get(), 3);

        // adjust so the consolidation is not running but we're not high enough in append count
        db.consolidationIsRunning = false;
        db.appendCount.set(2);

        // act
        db.consolidateInnerCode();

        // assert
        assertEquals(db.appendCount.get(), 2);

        // adjust append count to be high enough
        db.consolidationIsRunning = false;
        db.appendCount.set(3);

        // act
        db.consolidateInnerCode();

        // assert
        assertEquals(db.appendCount.get(), 0);
    }

    /**
     * If the thread enters this method and is prevented from running its code
     * because of failing the predicate at the beginning, then the appendCount
     * will remain unchanged after this method returns.
     */
    @Test
    public void test_ConsolidateIfNecessary() {
        Path dbPathForTest = foosDirectory.resolve("test_ConsolidateIfNecessary");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        db.appendCount.set(1);
        db.maxLinesPerAppendFile = 2;
        db.consolidationIsRunning = true;

        // act
        assertFalse(db.consolidateIfNecessary());

        // adjust, but we still won't make it through because not enough append count
        db.appendCount.set(2);

        assertFalse(db.consolidateIfNecessary());

        // adjust high enough to go through, but since consolidation is running, we won't.
        db.appendCount.set(3);

        assertFalse(db.consolidateIfNecessary());

        // adjust so the consolidation is not running but we're not high enough in append count
        db.consolidationIsRunning = false;
        db.appendCount.set(2);

        assertFalse(db.consolidateIfNecessary());

        // adjust append count to be high enough
        db.consolidationIsRunning = false;
        db.appendCount.set(3);

        assertTrue(db.consolidateIfNecessary());
    }

    /**
     * A basic negative case for the walk-and-load algorithm,
     * where the data in the consolidated data file is not
     * able to be deserialized.
     */
    @Test
    public void test_WalkAndLoad_NegativeCase() throws IOException {
        Path dbPathForTest = foosDirectory.resolve("test_WalkAndLoad_NegativeCase");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        fileUtils.makeDirectory(dbPathForTest.resolve("consolidated_data/1_to_100"));

        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);

        var ex = assertThrows(DbException.class, () -> db.walkAndLoad(dbPathForTest));

        // The exception thrown is very different on Mac and Windows.
        // This is on Mac:
//        assertEquals(ex.getMessage(), "java.io.UncheckedIOException: java.io.IOException: Is a directory");

        // This is on Windows:
//        assertEquals(ex.getMessage(), "java.nio.file.AccessDeniedException: out\\simple_db_for_engine2_tests\\engine2\\foos\\test_WalkAndLoad_NegativeCase\\consolidated_data\\1_to_100");
    }


    private static class BrokenBufferedWriter extends BufferedWriter {

        public BrokenBufferedWriter(Writer out) {
            super(out);
        }

        @Override
        public Writer append(char c) throws IOException {
            throw new IOException("THIS IS BREAKAGE");
        }
    }

    private static class BrokenWriter extends Writer {

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("THIS IS BREAKAGE IN WRITER");
        }

        @Override
        public void flush() throws IOException {

        }

        @Override
        public void close() throws IOException {

        }
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
        public String serialize() {
            return serializeHelper(index, identifier);
        }

        @Override
        public Bar deserialize(String serializedText) {
            final var tokens =  deserializeHelper(serializedText);
            return new Bar(
                    Integer.parseInt(tokens.get(0)),
                    UUID.fromString(tokens.get(1)));
        }

        @Override
        public long getIndex() {
            return this.index;
        }

        @Override
        public void setIndex(long index) {
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

        public String getB() {
            return b;
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

        return new Context(executorService, constants, logger);
    }

    /**
     * This is a test of the database when it gets larger, like a couple
     * million entries.  This method is not a test, it is a laboratory for
     * seeing what happens when we add millions of files to the
     * disk, around several hundred megabytes worth, so it is not
     * a test that is typically run.
     * For 11,500 rows of small data, it takes 2 seconds to load, or 5750 files per second
     * For 61,272 rows, it takes 9.771 seconds, or 6270 files per second
     * <br>
     * With those values, we can extrapolate that it will take 159 seconds to read a million files,
     * or almost 3 minutes.
     */
    @Ignore("This is a lab, not a test")
    public void test_LargeDatabasePerformance() {
        Path dbPathForTest = foosDirectory.resolve("test_large_database");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new DbEngine2<>(dbPathForTest, context, INSTANCE);
        int outerLoopCount = 5000;
        int innerLoopCount = 1000;

        {
            var timer = new StopwatchUtils().startTimer();

            for (int j = 0; j < outerLoopCount; j++) {
                for (int i = innerLoopCount * j; i < ((innerLoopCount * j) + innerLoopCount); i++) {
                    db.write(new Foo(0, i, "for testing: " + i));
                }
            }
            db.stop(100, 500);
            System.out.println("Time to write database " + timer.stopTimer());
        }

        logger.logDebug(() -> "Allow time for the operating system to clean up");
        MyThread.sleep(1000);

        {
            var db2 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
            db2.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));

            StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();
            // find one random value, to check indexing speed
            Foo foundFoo = db2.findExactlyOne("indexes_by_a_value", "11502");
            long timeToLoadDatabase = stopwatchUtils.stopTimer();
            System.out.println("Time to load database: " + timeToLoadDatabase);
            assertEquals(foundFoo.getA(), 11502);
            MyThread.sleep(500);

            StopwatchUtils stopwatchUtils2 = new StopwatchUtils().startTimer();
            // check getting another random value, further along
            Foo foundFoo2 = db2.findExactlyOne("indexes_by_a_value", "60000");
            assertEquals(foundFoo2.getA(), 60000);
            long timeToFindData = stopwatchUtils2.stopTimer();
            System.out.println("Time to find data after loading: " + timeToFindData);
        }


        {
            var db3 = new DbEngine2<>(dbPathForTest, context, INSTANCE);
            db3.registerIndex("indexes_by_a_value", x -> String.valueOf(x.getA()));

            StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();
            // find one random value, to check indexing speed
            Foo foundFoo = db3.findExactlyOne("indexes_by_a_value", "11502");
            long timeToLoadDatabase = stopwatchUtils.stopTimer();
            System.out.println("Time to load database: " + timeToLoadDatabase);
            assertEquals(foundFoo.getA(), 11502);
            MyThread.sleep(500);

            StopwatchUtils stopwatchUtils2 = new StopwatchUtils().startTimer();
            // check getting another random value, further along
            Foo foundFoo2 = db3.findExactlyOne("indexes_by_a_value", "60000");
            assertEquals(foundFoo2.getA(), 60000);
            long timeToFindData = stopwatchUtils2.stopTimer();
            System.out.println("Time to find data after loading: " + timeToFindData);
        }
    }

}
