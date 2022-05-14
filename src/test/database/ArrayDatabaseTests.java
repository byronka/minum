package database;

import logging.TestLogger;

import java.util.concurrent.ExecutorService;

import static framework.TestFramework.assertEquals;

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
        }
    }
}
