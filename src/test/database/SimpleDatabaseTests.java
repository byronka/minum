package database;

import database.stringdb.SimpleDatabase;
import logging.TestLogger;

import java.util.concurrent.ExecutorService;

import static framework.TestFramework.*;

public class SimpleDatabaseTests {
    private final TestLogger logger;

    public SimpleDatabaseTests(TestLogger logger) {
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
            record Foo(String color, String flavor) { }

            class FooDatabaseAdapter {
                private final SimpleDatabase database;

                public FooDatabaseAdapter(SimpleDatabase database) {
                    this.database = database;
                }

                public void createNew(String color, String flavor) {
                    database.add(new String[]{color, flavor});
                }

                public Foo findByColorSingle(String color) {
                    final String[] foundFooRow = database.findSingle(x -> color.equals(x[0]));
                    if (foundFooRow == null) {
                        return null;
                    } else {
                        return new Foo(foundFooRow[0], foundFooRow[1]);
                    }
                }

                public Foo findSingle(Foo foo) {
                    final String[] foundFooRow = database.findSingle(x -> foo.color().equals(x[0]) && foo.flavor().equals(x[1]));
                    if (foundFooRow == null) {
                        return null;
                    } else {
                        return new Foo(foundFooRow[0], foundFooRow[1]);
                    }
                }

                public void delete(Foo foo) {
                    database.removeIf(row -> foo.color().equals(row[0]) && foo.flavor().equals(row[1]));
                }
            }

            // the database itself
            var database = new SimpleDatabase();

            final var fda = new FooDatabaseAdapter(database);
            fda.createNew("orange", "vanilla");
            fda.createNew("blue", "candy corn");
            fda.createNew( "white", "chocolate");
            fda.createNew("black", "taffy");
            fda.createNew( "orange", "oreo");

            final var foundFoo = fda.findByColorSingle("black");
            assertEquals(foundFoo, new Foo("black", "taffy"));

            fda.delete(foundFoo);

            assertTrue(fda.findSingle(foundFoo) == null);

            // try to get an exception - we ask for single but there's multiple
            try {
                fda.findByColorSingle("orange");
                failTest("We should not have reached this line");
            } catch (Exception ex) {
                assertEquals(ex.getMessage(), "we must find only one row of data with this predicate");
            }
        }
    }
}
