package com.renomad.minum.database;

import com.renomad.minum.Context;
import com.renomad.minum.testing.RegexUtils;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import static java.util.stream.IntStream.range;
import static com.renomad.minum.database.DbTests.Foo.INSTANCE;
import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

public class DbTests {
    static private Context context;
    static private TestLogger logger;
    static private FileUtils fileUtils;
    static Path foosDirectory = Path.of("out/simple_db/foos");


    @BeforeClass
    public static void setUpClass() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger)context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    public DbTests() {
    }

    /**
     * For any given collection of data, we will need to serialize that to disk.
     * We can use some of the techniques we built up using r3z (https://github.com/byronka/r3z) - like
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
    public void test_GeneralCapability() throws IOException {
        fileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
        final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();
        final var db = new Db<Foo>(foosDirectory, context, INSTANCE);

        // make some files on disk
        for (var foo : foos) {
            db.write(foo);
        }

        // check that the files are now there.
        // note that since our minum.database is *eventually* synced to disk, we need to wait a
        // (milli)second or two here for them to get onto the disk before we check for them.
        MyThread.sleep(100);
        for (var foo : foos) {
            assertTrue(Files.exists(foosDirectory.resolve(foo.getIndex() + Db.databaseFileSuffix)));
        }

        // rebuild some objects from what was written to disk
        // note that since our minum.database is *eventually* synced to disk, we need to wait a
        // (milli)second or two here for them to get onto the disk before we check for them.
        MyThread.sleep(20);
        assertEqualsDisregardOrder(
                db.values().stream().map(Foo::toString).toList(),
                foos.stream().map(Foo::toString).toList());

        // change those files
        final var updatedFoos = new ArrayList<Foo>();
        for (var foo : db.values().stream().toList()) {
            final var newFoo = new Foo(foo.index, foo.a + 1, foo.b + "_updated");
            updatedFoos.add(newFoo);
            db.update(newFoo);
        }

        // rebuild some objects from what was written to disk
        // note that since our minum.database is *eventually* synced to disk, we need to wait a
        // (milli)second or two here for them to get onto the disk before we check for them.
        MyThread.sleep(40);
        assertEqualsDisregardOrder(
                db.values().stream().map(Foo::toString).toList(),
                updatedFoos.stream().map(Foo::toString).toList());

        // delete the files
        for (var foo : foos) {
            db.delete(foo);
        }

        // check that all the files are now gone
        // note that since our minum.database is *eventually* synced to disk, we need to wait a
        // (milli)second or two here for them to get onto the disk before we check for them.
        MyThread.sleep(50);
        for (var foo : foos) {
            assertFalse(Files.exists(foosDirectory.resolve(foo.getIndex() + Db.databaseFileSuffix)));
        }

        // give the action queue time to save files to disk
        // then shut down.
        db.stop();
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of("out/simple_db"), logger);
    }

    /**
     * what happens if we try deleting a file that doesn't exist?
     */
    @Test
    public void test_Edge_DeleteFileDoesNotExist() {
        final var db_throwaway = new Db<Foo>(foosDirectory, context, INSTANCE);

        var ex = assertThrows(RuntimeException.class, () -> db_throwaway.delete(new Foo(123, 123, "")));
        assertEquals(ex.getMessage(), "no data was found with id of 123");

        db_throwaway.stop();
    }

    @Test
    public void test_Deserialization_EdgeCases() throws IOException {
        // what if the directory is missing when try to deserialize?
        // note: this would only happen if, after instantiating our db,
        // the directory gets deleted/corrupted.

        // clear out the directory to start
        fileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
        final var db = new Db<Foo>(foosDirectory, context, INSTANCE);
        MyThread.sleep(10);

        // create an empty file, to create that edge condition
        final var pathToSampleFile = foosDirectory.resolve("1.ddps");
        Files.createFile(pathToSampleFile);
        db.loadDataFromDisk();
        MyThread.sleep(10);
        String existsButEmptyMessage = logger.findFirstMessageThatContains("file exists but");
        assertEquals(existsButEmptyMessage, "1.ddps file exists but empty, skipping");

        // create a corrupted file, to create that edge condition
        Files.write(pathToSampleFile, "invalid data".getBytes());
        final var ex = assertThrows(RuntimeException.class, () -> db.loadDataFromDisk());
        assertEquals(ex.getMessage().replace('\\','/'), "Failed to deserialize out/simple_db/foos/1.ddps with data (\"invalid data\")");

        fileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
        db.loadDataFromDisk();
        MyThread.sleep(10);
        String directoryMissingMessage = logger.findFirstMessageThatContains("directory missing, adding nothing").replace('\\', '/');
        assertTrue(directoryMissingMessage.contains("foos directory missing, adding nothing to the data list"));
        db.stop();
    }

    /**
     * When this is looped a hundred thousand times, it takes 500 milliseconds to finish
     * making the updates in memory.  It takes several minutes later for it to
     * finish getting those changes persisted to disk.
     * a million writes in 500 milliseconds means 2 million writes in one sec.
     */
    @Test
    public void test_Performance() throws IOException {
        // clear out the directory to start
        fileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
        final var db = new Db<Foo>(foosDirectory, context, INSTANCE);
        MyThread.sleep(10);

        final var foos = new ArrayList<Foo>();

        // write the foos
        for (int i = 0; i < 10; i++) {
            final var newFoo = new Foo(i, i + 1, "original");
            foos.add(newFoo);
            db.write(newFoo);
        }

        // change the foos
        final var outerTimer = new StopwatchUtils().startTimer();
        final var innerTimer = new StopwatchUtils().startTimer();

        final var newFoos = new ArrayList<Foo>();
        for (var i = 1; i < 10; i++) {
            newFoos.clear();
                /*
                loop through the old foos and update them to new values,
                creating a new list in the process.  There should only
                ever be 10 foos.
                 */
            for (var foo : foos) {
                final var newFoo = new Foo(foo.getIndex(), foo.a + 1, foo.b + "_updated");
                newFoos.add(newFoo);
                db.update(newFoo);
            }
        }
        logger.logDebug(() -> "It took " + innerTimer.stopTimer() + " milliseconds to make the updates in memory");
        db.stop(10, 20);
        logger.logDebug(() -> "It took " + outerTimer.stopTimer() + " milliseconds to finish writing everything to disk");

        final var db1 = new Db<Foo>(foosDirectory, context, INSTANCE);
        Collection<Foo> values = db1.values();
        assertTrue(newFoos.containsAll(values));
        db1.stop(10, 20);

        final var pathToIndex = foosDirectory.resolve("index.ddps");
        // this file should not be empty, but we are making it empty
        Files.writeString(pathToIndex,"");
        var ex = assertThrows(RuntimeException.class, () -> new Db<Foo>(foosDirectory, context, INSTANCE));
        // because the error message includes a path that varies depending on which OS, using regex to search.
        assertTrue(RegexUtils.isFound("Exception while reading out.simple_db.foos.index.ddps in Db constructor",ex.getMessage()));
    }


    static class Foo extends DbData<Foo> implements Comparable<Foo> {

        private long index;
        private final int a;
        private final String b;

        public Foo(long index, int a, String b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }

        static final Foo INSTANCE = new Foo(0,0,"");

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

}
