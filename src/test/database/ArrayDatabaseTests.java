package database;

import database.stringdb.SimpleDatabase;
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
            record Foo(String color, String flavor){
                public static Foo findByColor(String color, SimpleDatabase database) {
                    final String[] foundFooRow = database.findSingle(x -> color.equals(x[0]));
                    return new Foo(foundFooRow[0], foundFooRow[1]);
                }

                /**
                 * This prevents us from accidentally occasionally inserting
                 * data in the wrong order.
                 */
                public void persist(SimpleDatabase database) {
                    database.add(new String[]{color, flavor});
                }
            }

            // the database itself
            var database = new SimpleDatabase();

            // adding some new data
            new Foo("orange", "vanilla").persist(database);
            new Foo("blue", "candy corn").persist(database);
            new Foo("white", "chocolate").persist(database);
            new Foo("black", "taffy").persist(database);
            new Foo("orange", "oreo").persist(database);

            // search for data
            Foo foundFoo = Foo.findByColor("black", database);
            assertEquals(foundFoo, new Foo("black", "taffy"));

            // updating some data - change taffy to lemon where color
            database.update(x -> {
                if ("taffy".equals(x[1])) {
                    x = new String[]{"black", "lemon"};
                }
            });

            // confirm the change
            final String[] foundFooRow2 = database.findSingle(x -> "black".equals(x[0]));
            assertEquals(new Foo(foundFooRow2[0], foundFooRow2[1]), new Foo("black", "lemon"));

            // delete the data at "white"
            database.removeIf(row -> "white".equals(row[0]));

            // confirm the delete
            final String[] foundFooRow3 = database.findSingle(x -> "white".equals(x[0]));
            assertTrue(foundFooRow3 == null);
        }
    }
}
