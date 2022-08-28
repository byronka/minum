package database;

import database.owndatabase.DatabaseDiskPersistenceSimpler;
import database.owndatabase.SimpleDataType;
import logging.TestLogger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static framework.TestFramework.*;
import static java.util.stream.IntStream.range;

public class SimpleDatabaseTests {
    private final TestLogger logger;

    public SimpleDatabaseTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {

        // the following will be used in the subsequent tests...

        // now let's apply that
        record Foo(int index, int a, String b) implements SimpleDataType<Foo> {

            static final Foo INSTANCE = new Foo(0,0,"");

            /**
             * we want this to be Foo: a=123 b=abc123
             */
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
            final var foos = range(1,10).mapToObj(x -> new Foo(x, x, "abc"+x)).toList();
            final var ddps = new DatabaseDiskPersistenceSimpler<Foo>("out/simple_db", es, logger);

            for (var foo : foos) {
                ddps.persistToDisk(foo);
            }

            // give the action queue time to save files to disk
            // then shut down.
            try {
                ddps.getActionQueue().getPrimaryFuture().get(10, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // do nothing
            }
        }
    }

}
