package minum.database;

import minum.TestContext;
import minum.testing.TestLogger;
import minum.testing.TestRecordingLogger;
import minum.utils.MyThread;
import minum.utils.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import static java.util.stream.IntStream.range;
import static minum.database.SimpleDatabaseTests.Foo.INSTANCE;
import static minum.testing.TestFramework.*;
import static minum.utils.FileUtils.deleteDirectoryRecursivelyIfExists;

public class SimpleDatabaseTests {
    private final TestLogger logger;
    private final ExecutorService es;
    private final TestContext context;

    public SimpleDatabaseTests(TestContext context) {
        this.context = context;
        this.logger = context.getLogger();
        this.es = context.getExecutorService();
        logger.testSuite("SimpleDatabase Tests", "SimpleDatabaseTests");
    }

    public void tests() throws IOException {

        /*
        For any given collection of data, we will need to serialize that to disk.
        We can use some of the techniques we built up using r3z (https://github.com/byronka/r3z) - like
        serializing to a json-like url-encoded text, eventually synchronized
        to disk.
         */
        logger.test("now let's try playing with serialization");{
            final var foo = new Foo(1, 123, "abc");
            final var deserializedFoo = foo.deserialize(foo.serialize());
            assertEquals(deserializedFoo, foo);
        }

        logger.test("When we serialize something null");{
            final var foo = new Foo(1, 123, null);
            final var deserializedFoo = foo.deserialize(foo.serialize());
            assertEquals(deserializedFoo, foo);
        }

        logger.test("what about serializing a collection of stuff");{
            final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();
            final var serializedFoos = foos.stream().map(Foo::serialize).toList();
            final var deserializedFoos = serializedFoos.stream().map(INSTANCE::deserialize).toList();
            assertEquals(foos, deserializedFoos);
        }

        logger.test("let's fold in some of the capability of DatabaseDiskPersistenceSimpler");
        Path foosDirectory = Path.of("out/simple_db/foos");
        {
            deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
            final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();
            final var ddps = new DatabaseDiskPersistenceSimpler<Foo>(foosDirectory, context);

            // make some files on disk
            for (var foo : foos) {
                ddps.persistToDisk(foo);
            }

            // check that the files are now there.
            // note that since our minum.database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            MyThread.sleep(100);
            for (var foo : foos) {
                assertTrue(Files.exists(foosDirectory.resolve(foo.getIndex() + DatabaseDiskPersistenceSimpler.databaseFileSuffix)));
            }

            // rebuild some objects from what was written to disk
            // note that since our minum.database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            MyThread.sleep(20);
            final var deserializedFoos = ddps.readAndDeserialize(INSTANCE);
            assertEqualsDisregardOrder(
                    deserializedFoos.stream().map(Foo::toString).toList(),
                    foos.stream().map(Foo::toString).toList());

            // change those files
            final var updatedFoos = new ArrayList<Foo>();
            for (var foo : foos) {
                final var newFoo = new Foo(foo.index, foo.a + 1, foo.b + "_updated");
                updatedFoos.add(newFoo);
                ddps.updateOnDisk(newFoo);
            }

            // rebuild some objects from what was written to disk
            // note that since our minum.database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            MyThread.sleep(40);
            final var deserializedUpdatedFoos = ddps.readAndDeserialize(INSTANCE);
            assertEqualsDisregardOrder(
                    deserializedUpdatedFoos.stream().map(Foo::toString).toList(),
                    updatedFoos.stream().map(Foo::toString).toList());

            // delete the files
            for (var foo : foos) {
                ddps.deleteOnDisk(foo);
            }

            // check that all the files are now gone
            // note that since our minum.database is *eventually* synced to disk, we need to wait a
            // (milli)second or two here for them to get onto the disk before we check for them.
            MyThread.sleep(50);
            for (var foo : foos) {
                assertFalse(Files.exists(foosDirectory.resolve(foo.getIndex() + DatabaseDiskPersistenceSimpler.databaseFileSuffix)));
            }

            // give the action queue time to save files to disk
            // then shut down.
            ddps.stop();
            deleteDirectoryRecursivelyIfExists(Path.of("out/simple_db"), logger);
        }

        logger.test("what happens if we try deleting a file that doesn't exist?"); {
            final var myLogger = new TestRecordingLogger();
            final var ddps_throwaway = new DatabaseDiskPersistenceSimpler<Foo>(foosDirectory, context);

            // if we try deleting something that doesn't exist, we get an error shown in the log
            ddps_throwaway.deleteOnDisk(new Foo(123, 123, ""));
            MyThread.sleep(10);
            String failureMessage = myLogger.findFirstMessageThatContains("failed to").replace('\\', '/');
            assertEquals(failureMessage, "failed to delete file out/simple_db/foos/123.ddps during deleteOnDisk");

            ddps_throwaway.stop();
        }

//        logger.test("edge cases for deserialization"); {
//            // what if the directory is missing when try to deserialize?
//            // note: this would only happen if, after instantiating our ddps,
//            // the directory gets deleted/corrupted.
//            final var myLogger = new TestRecordingLogger();
//            final var emptyFooInstance = new Foo(0, 0, "", context);
//
//            // clear out the directory to start
//            FileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
//            final var ddps = new DatabaseDiskPersistenceSimpler<Foo>(foosDirectory, context);
//            MyThread.sleep(10);
//
//            // create an empty file, to create that edge condition
//            final var pathToSampleFile = foosDirectory.resolve("1.ddps");
//            Files.createFile(pathToSampleFile);
//            ddps.readAndDeserialize(emptyFooInstance);
//            MyThread.sleep(10);
//            String existsButEmptyMessage = myLogger.findFirstMessageThatContains("file exists but");
//            assertEquals(existsButEmptyMessage, "1.ddps file exists but empty, skipping");
//
//            // create a corrupted file, to create that edge condition
//            Files.write(pathToSampleFile, "invalid data".getBytes());
//            final var ex = assertThrows(RuntimeException.class, ThrowingRunnable.throwingRunnableWrapper(() -> ddps.readAndDeserialize(emptyFooInstance), logger));
//            assertEquals(ex.getMessage().replace('\\','/'), "java.lang.RuntimeException: Failed to deserialize out/simple_db/foos/1.ddps with data (\"invalid data\")");
//
//            FileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory, logger);
//            ddps.readAndDeserialize(emptyFooInstance);
//            MyThread.sleep(10);
//            String directoryMissingMessage = myLogger.findFirstMessageThatContains("directory missing").replace('\\', '/');
//            assertEquals(directoryMissingMessage, "out/simple_db/foos directory missing, creating empty list of data");
//            ddps.stop();
//        }

    }


    static class Foo extends SimpleDataTypeImpl<Foo> implements Comparable<Foo> {

        private final int index;
        private final int a;
        private final String b;

        public Foo(int index, int a, String b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }

        static final Foo INSTANCE = new Foo(0,0,"");

        @Override
        public String serialize() {
            return StringUtils.encode(String.valueOf(index)) + " " + StringUtils.encode(String.valueOf(a)) + " " + StringUtils.encode(b);
        }

        @Override
        public Foo deserialize(String serializedText) {
            final var indexEndOfIndex = serializedText.indexOf(' ');
            final var indexStartOfA = indexEndOfIndex + 1;
            final var indexEndOfA = serializedText.indexOf(' ', indexStartOfA);
            final var indexStartOfB = indexEndOfA + 1;

            final var rawStringIndex = serializedText.substring(0, indexEndOfIndex);
            final var rawStringA = serializedText.substring(indexStartOfA, indexEndOfA);
            final var rawStringB = StringUtils.decode(serializedText.substring(indexStartOfB));

            return new Foo(Integer.parseInt(rawStringIndex), Integer.parseInt(rawStringA), rawStringB);
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
        public Long getIndex() {
            return (long) index;
        }

        // implementing comparator just so we can assertEqualsDisregardOrder on a collection of these
        @Override
        public int compareTo(Foo o) {
            return Long.compare( o.getIndex() , this.getIndex() );
        }
    }

}
