package database;

import database.owndatabase.DatabaseDiskPersistenceSimpler;
import database.owndatabase.SimpleDataType;
import primary.Tests;
import logging.TestLogger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import static framework.TestFramework.*;
import static java.util.stream.IntStream.range;

public class SimpleDatabaseTests {
    private final TestLogger logger;

    public SimpleDatabaseTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) throws InterruptedException {

        // the following will be used in the subsequent tests...

        // now let's apply that
        record Foo(int index, int a, String b) implements SimpleDataType<Foo>, Comparable<Foo> {

            static final Foo INSTANCE = new Foo(0,0,"");

            @Override
            public String serialize() {
                return index + " " + a + " " + URLEncoder.encode(b, StandardCharsets.UTF_8);
            }

            @Override
            public Foo deserialize(String serializedText) {
                final var indexEndOfIndex = serializedText.indexOf(' ');
                final var indexStartOfA = indexEndOfIndex + 1;
                final var indexEndOfA = serializedText.indexOf(' ', indexStartOfA);
                final var indexStartOfB = indexEndOfA + 1;

                final var rawStringIndex = serializedText.substring(0, indexEndOfIndex);
                final var rawStringA = serializedText.substring(indexStartOfA, indexEndOfA);
                final var rawStringB = serializedText.substring(indexStartOfB);

                return new Foo(Integer.parseInt(rawStringIndex), Integer.parseInt(rawStringA), rawStringB);
            }

            @Override
            public Long getIndex() {
                return (long) index();
            }

            // implementing comparator just so we can assertEqualsDisregardOrder on a collection of these
            @Override
            public int compareTo(Foo o) {
                return Long.compare( o.getIndex() , this.getIndex() );
            }
        }

        /*
        For any given collection of data, we will need to serialize that to disk.
        We can use some of the techniques we built up using r3z - like
        serializing to a json-like url-encoded text, eventually synchronized
        to disk.
         */
        logger.test("now let's try playing with serialization");
        {
            final var foo = new Foo(1, 123, "abc");
            final var deserializedFoo = foo.deserialize(foo.serialize());
            assertEquals(deserializedFoo, foo);
        }

        logger.test("what about serializing a collection of stuff");
        {
            final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();
            final var serializedFoos = foos.stream().map(Foo::serialize).toList();
            final var deserializedFoos = serializedFoos.stream().map(Foo.INSTANCE::deserialize).toList();
            assertEquals(foos, deserializedFoos);
        }

        logger.test("let's fold in some of the capability of DatabaseDiskPersistenceSimpler");
        {
            final var foosDirectory = "out/simple_db/foos";
            final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();
            final var ddps = new DatabaseDiskPersistenceSimpler<Foo>(foosDirectory, es, logger);

            // make some files on disk
            for (var foo : foos) {
                ddps.persistToDisk(foo);
            }

            // check that the files are now there.
            // note that since our database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            Thread.sleep(10);
            for (var foo : foos) {
                assertTrue(Files.exists(Path.of(foosDirectory, foo.getIndex() + DatabaseDiskPersistenceSimpler.databaseFileSuffix)));
            }

            // rebuild some objects from what was written to disk
            // note that since our database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            Thread.sleep(10);
            final var deserializedFoos = ddps.readAndDeserialize(Foo.INSTANCE);
            assertEqualsDisregardOrder(deserializedFoos, foos);

            // change those files
            final var updatedFoos = new ArrayList<Foo>();
            for (var foo : foos) {
                final var newFoo = new Foo(foo.index, foo.a + 1, foo.b + "_updated");
                updatedFoos.add(newFoo);
                ddps.updateOnDisk(newFoo);
            }

            // rebuild some objects from what was written to disk
            // note that since our database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            Thread.sleep(10);
            final var deserializedUpdatedFoos = ddps.readAndDeserialize(Foo.INSTANCE);
            assertEqualsDisregardOrder(deserializedUpdatedFoos, updatedFoos);

            // delete the files
            for (var foo : foos) {
                ddps.deleteOnDisk(foo);
            }

            // check that all the files are now gone
            // note that since our database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            Thread.sleep(50);
            for (var foo : foos) {
                assertFalse(Files.exists(Path.of(foosDirectory, foo.getIndex() + DatabaseDiskPersistenceSimpler.databaseFileSuffix)));
            }

            // give the action queue time to save files to disk
            // then shut down.
            ddps.stop();
        }

        /*
         * In this test, we'll turn off disk-syncing
         */
        logger.test("Just how fast is our database?");
        {
            final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();

            // change the foos
            Tests.startTimer("fast_database");
            for (var i = 1; i < 100_000; i++) {
                final var newFoos = new ArrayList<Foo>();
                for (var foo : foos) {
                    final var newFoo = new Foo(foo.index, foo.a + 1, foo.b + "_updated");
                    newFoos.add(newFoo);
                }
            }
            final var time = Tests.stopTimer("fast_database");

            logger.testPrint("time taken was " + time + " milliseconds");
        }
    }

}
