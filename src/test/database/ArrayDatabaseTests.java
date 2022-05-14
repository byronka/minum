package database;

import logging.TestLogger;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static framework.TestFramework.assertEquals;
import static framework.TestFramework.assertTrue;

public class ArrayDatabaseTests {
    private final TestLogger logger;

    public ArrayDatabaseTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {

        /*
         * Stepping away from a strongly-typed database, what if we simply
         * store our data in multi-dimensional arrays?  We lose strong
         * typing but frankly, the complexity necessary to enforce strong
         * typing seems overly burdensome.
         *
         * Here, we'll play a bit. CRUD - create, read, update, delete.
         * Add some data, search for some data, update the data, delete some data
         *
         * Trying it this time with an array
         */
        logger.test("how well does a pure array database suit us");
        {
            // the schema for the data
            record Foo(String color, String flavor){}

            // the database itself
            var database = new String[100][2];

            // adding some new data
            database[0] = new String[]{"orange", "vanilla"};
            database[1] = new String[]{"blue", "candy corn"};
            database[2] = new String[]{"white", "chocolate"};
            database[3] = new String[]{"black", "taffy"};
            database[4] = new String[]{"orange", "oreo"};


            // search for data
            Foo foundFoo = null;
            for (var i = 0; i < database.length; i++) {
                if ( "black".equals(database[i][0])) {
                    foundFoo = new Foo(database[i][0], database[i][1]);
                }
            }
            assertEquals(foundFoo, new Foo("black", "taffy"));

            // updating some data - change taffy to lemon where color
            for (var i = 0; i < database.length; i++) {
                if ( "taffy".equals(database[i][1])) {
                    database[i][1] = "lemon";
                }
            }

            // confirm the change
            for (var i = 0; i < database.length; i++) {
                if ( "black".equals(database[i][0])) {
                    foundFoo = new Foo(database[i][0], database[i][1]);
                }
            }
            assertEquals(foundFoo, new Foo("black", "lemon"));

            // delete the data at "white"
            for (var i = 0; i < database.length; i++) {
                if ( "white".equals(database[i][0])) {
                    database[i][0] = null;
                    database[i][1] = null;
                }
            }

            // confirm the delete
            foundFoo = null;
            for (var i = 0; i < database.length; i++) {
                if ( "white".equals(database[i][0])) {
                    foundFoo = new Foo(database[i][0], database[i][1]);
                }
            }
            assertTrue(foundFoo == null);

        }

        logger.test("using a list for the data instead of an array");
        {
            // the schema for the data
            record Foo(String color, String flavor){}

            // the database itself
            var database = new ArrayList<String[]>();

            // adding some new data
            database.add(new String[]{"orange", "vanilla"});
            database.add(new String[]{"blue", "candy corn"});
            database.add(new String[]{"white", "chocolate"});
            database.add(new String[]{"black", "taffy"});
            database.add(new String[]{"orange", "oreo"});

            // search for data
            final var foundFoo = database.stream().filter(x -> "black".equals(x[0])).map(x -> new Foo(x[0], x[1])).toList();
            assertEquals(foundFoo.size(), 1);
            assertEquals(foundFoo.get(0), new Foo("black", "taffy"));

            // updating some data - change taffy to lemon where color
            for (String[] row : database) {
                if ( "taffy".equals(row[1])) {
                    row[1] = "lemon";
                }
            }

            // confirm the change
            final var foundFoo2 = database.stream().filter(x -> "black".equals(x[0])).map(x -> new Foo(x[0], x[1])).toList();
            assertEquals(foundFoo2.size(), 1);
            assertEquals(foundFoo2.get(0), new Foo("black", "lemon"));

            // delete the data at "white"
            database.removeIf(row -> "white".equals(row[0]));

            // confirm the delete
            final var foundFoo3 = database.stream().filter(x -> "white".equals(x[0])).map(x -> new Foo(x[0], x[1])).toList();
            assertEquals(foundFoo3.size(), 0);
        }
    }
}
