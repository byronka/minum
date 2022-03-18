package database;

import logging.TestLogger;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;

import static framework.TestFramework.assertEquals;
import static utils.Crypto.*;

/**
 * Tests for our own database
 */
public class OwnDatabaseTests {
    private TestLogger logger;

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

    }
}
