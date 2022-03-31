package database;

import database.owndatabase.Database;
import logging.TestLogger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static framework.TestFramework.assertEquals;
import static framework.TestFramework.assertTrue;
import static utils.Crypto.*;

/**
 * Tests for our own database
 */
public class OwnDatabaseTests {
    private final TestLogger logger;

    public OwnDatabaseTests(TestLogger logger) {
        this.logger = logger;
    }

    /*
    We'll use this to drive the initial creation of our database.  Intent here
    is to keep things as simple as possible.  Initial design is to make a
    pure memory-based database with occasional file writing, directly
    controlled through Java (that is, no need for intermediating SQL language).
     */
    public void tests() throws Exception {


        /*
         * One aspect we need to control is to have the ability to write and
         * read to individual blocks within a file.  Back when I developed r3z,
         * the database that was built stored each piece of data in its own
         * file so that reads and writes would not take so long.  The thinking
         * was that a read from any particular file would be as quick
         * as possible because of how little data existed in that file.  Also,
         * that we could rely on the OS file system for easy access to various pieces of
         * data.
         *
         * It does appear that it should be possible to write and read to individual blocks
         * inside a file, so that we don't need to have (literally) millions of files
         * (e.g. in the case where we have millions of individual pieces of data).
         *
         * Let's try it out.
         *
         */
        logger.test("Create a file, write to parts of it, read parts of it");
        {
            final var filePath = Path.of("a_test_file");
            try {
                final var path = Files.createFile(filePath);
                final var bw = new BufferedWriter(new FileWriter(path.toFile()));
                // make a file
                final var fileSize = 10;
                for (int i = 0; i < fileSize; i++) {
                    bw.write(0x0);

                }
                bw.flush();
                final var earlier = System.currentTimeMillis();
                final var raf = new RandomAccessFile(path.toFile(), "rws");
                for (int i = 0; i < 5; i++) {
                    final var pos = fileSize / 2;
                    raf.seek(pos);
                    raf.write('c');
                }
                final var later = System.currentTimeMillis();
                raf.seek(fileSize);
                // write after the end of the file
                raf.write("this is a test".getBytes(StandardCharsets.UTF_8));
                System.out.println(later - earlier);
            } finally {
                Files.delete(filePath);
            }
        }


        /*
         * The question is: what kind of data structure should we use?  Let's assume
         * we'll primarily keep our data in memory for this database and occasionally
         * write to disk.  If we make good choices about our data and don't just
         * stuff everything in there, this should work for most situations.
         *
         * If we use something like a hashmap, we'll have O(1) speed for access, and
         * if we can serialize to a form that lets us make block-level edits to a file,
         * we can save quickly.
         *
         * What's the simplest approach?
         */
        logger.test("serialize some data");
        {
            record Thingamajig(String name, String favoriteColor, String favoriteIceCream) {}

            // let's say we have a hash map.  that's what we'll keep in
            // raw array
            // hash map
            // vectors
            // list
            // if we want to have random access into a file, we'll need to basically control all
            // the data, byte for byte, precisely, right?

            final var foo = new HashMap<Integer, Thingamajig>();
        }

        /*
        Like, fo' sha

        Tested out a few.
        SHA-1: 110 millis
        SHA-256: 90 millis
        SHA-512: 150 millis

        I guess we'll go with sha-256
         */
        logger.test("how fast is sha");
        {
            final var earlier = System.currentTimeMillis();
            for (var i = 0; i < 100_000; i++) {
                hashStringSha256("hello");
            }
            final var later = System.currentTimeMillis();
            System.out.println("sha256 took " + (later - earlier) + " millis for 100,000 cycles");
        }

        logger.test("playing with getting a base64 value from a hash sha-256");
        {
            final var encodedhash = bytesToHex(hashStringSha256("hello"));
            assertEquals(encodedhash, "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        }

        logger.test("playing with getting a base64 value from a hash sha-1");
        {
            final var encodedhash = bytesToHex(hashStringSha1("hello"));
            assertEquals(encodedhash, "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d");
        }

        logger.test("starting to craft a memory database similar to r3z");
        {
            /*
              start with a basic hashmap, with a string as the key being
              effectively the name of the table, and then the value being
              a list, effectively the "rows" of the table.
             */
            final var db = new HashMap<String, List<?>>();

            record Thing(int a, String b){}

            final var a_list_of_things = List.of(new Thing(42, "the meaning of life"));

            // add something to the database
            db.put("thing", a_list_of_things);

            // add another thing
            final var a_new_thing = new Thing(1, "one is the loneliest number");
            final var concatenatedList = Stream.concat(a_list_of_things.stream(), Stream.of(a_new_thing)).toList();
            db.put("thing", concatenatedList);

            // get all the things
            final var things = db.get("thing");
            assertEquals(things.toString(), "[Thing[a=42, b=the meaning of life], Thing[a=1, b=one is the loneliest number]]");
        }

        /*
         * The database needs to use generics a bit to have some type-safety,
         * since each of its lists will be of a different type - that is, we
         * may have a list of dogs, of cars, of bicycles ...
         */
        logger.test("Creating a generic method for the database");
        {
            record Thing(int a, String b){}
            final var db = Database.createDatabase();
            db.createNewList("things", Thing.class);
            final Database.DbList<Thing> things = db.getList("things", Thing.class);
            things.actOn(t -> t.add(new Thing(42, "the meaning of life")));
            final var result = things.read(t -> t.stream().filter(x -> x.b.equals("the meaning of life")).toList());
            assertEquals(result.get(0).a, 42);
        }

        /*
         * If we previously registered some data as "Thing" and we subsequently
         * ask for it as type "Other", we should get a complaint
         */
        logger.test("Testing the type safety");
        {
            record Thing(int a, String b){}
            record Other(int a, String b){}
            final var db = Database.createDatabase();
            db.createNewList("things", Thing.class);
            try {
                db.getList("things", Other.class);
            } catch (RuntimeException ex) {
                assertEquals(ex.getMessage(), "It should not be possible to have multiple matching keys here. " +
                        "keys: " +
                        "[NameAndType[name=things, clazz=class database.OwnDatabaseTests$2Thing], " +
                        "NameAndType[name=things, clazz=class database.OwnDatabaseTests$3Thing]]");
            }
        }

        /*
        Here, I'm wondering whether I can create separate data structures
        that cover the data in others.  For example, if I create a list of a, b, c, can I
        create a map that contains those values?

        This is valuable in the case of our home-cooked database.  The data is stored in
        sets, so if I am running a query that accesses data by identifier, it has to loop
        through all the data.  If instead I create a separate hash map for the same data,
        then I suddenly gain all the speed of a SQL indexed table lookup.
         */
        logger.test("can I have a hashmap that refers to the same objects in a list?");
        {
            // short answer: yes.  Longer answer: see below.
            final var listOfStuff = List.of(new Object(), new Object());

            Map<Integer, Object> myMap = new HashMap<>();
            var index = 0;
            for (var item : listOfStuff) {
                myMap.put(index, item);
                /*
                check to make sure the objects being pointed to are
                the same (note that unlike the normal case where we
                compare values, here I'm specifically concerned
                that the items in question are pointing to the
                exact same object in memory
                 */
                assertTrue(item == myMap.get(index));
                index += 1;
            }

        }

    }
}
