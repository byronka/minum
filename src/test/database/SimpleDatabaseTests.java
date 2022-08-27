package database;

import logging.TestLogger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import static framework.TestFramework.*;
import static java.util.stream.IntStream.range;

public class SimpleDatabaseTests {
    private final TestLogger logger;

    public SimpleDatabaseTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {

        // the following will be used in the subsequent tests...

        // let's define an interface for data we want to serialize.
        interface Serializable<T> {
            String serialize();
            T deserialize(String serializedText);
        }

        // now let's apply that
        record Foo(int a, String b) implements Serializable<Foo> {

            static Foo INSTANCE = new Foo(0,"");

            /**
             * we want this to be Foo: a=123 b=abc123
             */
            @Override
            public String serialize() {
                return a + " " + URLEncoder.encode(b, StandardCharsets.UTF_8);
            }

            @Override
            public Foo deserialize(String serializedText) {
                final var indexEndOfA = serializedText.indexOf(' ');
                final var indexStartOfB = indexEndOfA + 1;

                final var rawStringA = serializedText.substring(0, indexEndOfA);
                final var rawStringB = serializedText.substring(indexStartOfB);

                return new Foo(Integer.parseInt(rawStringA), rawStringB);
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
            final var foo = new Foo(123, "abc");
            final var deserializedFoo = foo.deserialize(foo.serialize());
            assertEquals(deserializedFoo, foo);
        }

        logger.test("what about serializing a collection of stuff");
        {
            final var foos = range(1,10).mapToObj(x -> new Foo(x, "abc"+x)).toList();
            final var serializedFoos = foos.stream().map(Foo::serialize).toList();
            final var deserializedFoos = serializedFoos.stream().map(x -> Foo.INSTANCE.deserialize(x)).toList();
            assertEquals(foos, deserializedFoos);
        }
    }

}
