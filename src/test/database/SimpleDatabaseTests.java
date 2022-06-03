package database;

import database.stringdb.DatabaseEntry;
import database.stringdb.Databaseable;
import database.stringdb.SimpleDatabase;
import logging.TestLogger;
import utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import static framework.TestFramework.*;

// the schema for the data
record Foo(String color, String flavor) { }

class FooDatabaseAdapter {
    private final SimpleDatabase database;

    public FooDatabaseAdapter(SimpleDatabase database) {
        this.database = database;
    }

    public Foo createNew(String color, String flavor) {
        final var data = new String[]{color, flavor};
        database.add(data);
        database.createMapForIndex(color, data);
        return new Foo(color, flavor);
    }

    public Foo findByColorSingle(String color) {
        final String[] foundFooRow = database.findSingle(x -> color.equals(x[0]));
        if (foundFooRow == null) {
            return null;
        } else {
            return new Foo(foundFooRow[0], foundFooRow[1]);
        }
    }

    /**
     * Here we search for an item in the table by its index
     * @param color this is the index to the data
     */
    public Foo findByColorIndex(String color) {
        final String[] foundFooRow = database.findByIndex(color);
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


record DoesNotMatter(int a, String flavor, String color) implements Databaseable<DoesNotMatter> {
    public static final DoesNotMatter EMPTY = new DoesNotMatter(0, "", "");

    @Override
    public DatabaseEntry toDatabaseEntry() {
        final var innerData = new HashMap<String, String>();
        innerData.put("a", String.valueOf(a));
        innerData.put("flavor", flavor);
        innerData.put("color", color);
        return new DatabaseEntry(this.getClass(), innerData);
    }

    @Override
    public DoesNotMatter fromDatabaseEntry(DatabaseEntry m) {
        return new DoesNotMatter(
                Integer.parseInt(m.data().get("a")),
                m.data().get("flavor"),
                m.data().get("color")
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DoesNotMatter that = (DoesNotMatter) o;
        return a == that.a && Objects.equals(flavor, that.flavor) && Objects.equals(color, that.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, flavor, color);
    }
}

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

        /*
        What if we have a data type that has an index to it, so we can
        quickly find it by index?  We may find it more performant to have a map
        between the index and the values in the list, if we search by index.
         */
        logger.test("including a hashmap for indexed data");
        {
            // the database itself
            var database = new SimpleDatabase();

            // here we'll assume that color is the index
            final var fda = new FooDatabaseAdapter(database);
            fda.createNew("orange", "vanilla");
            fda.createNew("blue", "candy corn");
            fda.createNew( "white", "chocolate");

            final var foundFoo = fda.findByColorIndex("blue");
            assertEquals(foundFoo, new Foo("blue", "candy corn"));
        }

        /*
         What if we have an interface with like, toDatabase and fromDatabase.

         Maybe it's a data structure that's a pair - part a is the class, part
         b is the map of property names to values, and all the values are strings
         or null.
         */
        logger.test("playing with converting a class to and from a string-based data structure");
        {
            final var a = new DoesNotMatter(42, "vanilla", "blue");
            final var entry = a.toDatabaseEntry();
            final var expectedInnerData = new HashMap<String, String>();
            expectedInnerData.put("a", "42");
            expectedInnerData.put("flavor", "vanilla");
            expectedInnerData.put("color", "blue");
            final var expected = new DatabaseEntry(DoesNotMatter.class, expectedInnerData);
            assertEquals(expected, entry);

            final var dnm = DoesNotMatter.EMPTY.fromDatabaseEntry(entry);
            assertEquals(a, dnm);
        }

        /*
        Continuing along this path, we should be able to take a DatabaseEntry and
        serialize it, following URL encoding to prevent any issues with spaces, commas
        or really any UTF-8 character.
         */
        logger.test("can serialize to / from a string (encoded of course)");
        {
            // first we create a database entry
            final var expectedInnerData = new HashMap<String, String>();
            expectedInnerData.put("a", " 2");
            expectedInnerData.put("flavor", "   ");
            expectedInnerData.put("color", null);
            final var dbEntry = new DatabaseEntry(DoesNotMatter.class, expectedInnerData);

            /*
            then we serialize it to a string, encoding each value
            what we want: class: foo.bar.Class, values: a=42, flavor=vanilla, color=blue

            If the value has interesting characters (newlines, tabs, commas, etc),
            they will get encoded by the URL-encoder we're using.

            how do we differentiate between null and empty string?

            we could choose for null to be represented by an extremely unusual
            set of characters, something that cannot exist in URL encoding, like,
            %NULL%

            So, if a is newline and flavor = (lots of whitespace) and color is
            null, we might get:
            class: foo.bar.Class, values: a=%2a, flavor=%20%20%20, color=%NULL%
            */

            // no need to URL-encode the class
            StringBuilder sb = new StringBuilder("class: ").append(dbEntry.c().getCanonicalName()).append(", values: ");

            for (var i : dbEntry.data().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                // no need to encode the property name
                sb.append(i.getKey())
                        .append("=");
                final var value = i.getValue();
                if (value == null) {
                    sb.append("%NULL%, ");
                } else {
                    sb.append(StringUtils.encode(i.getValue()))
                            .append(", ");
                }
            }

            final var expected = "class: database.DoesNotMatter, values: a=+2, color=%NULL%, flavor=+++, ";
            assertEquals(expected, sb.toString());

            // and now we go the other direction.  How to serialize from the string back to the record?
            // regex magic, of course.

            // TODO

        }
    }
}
