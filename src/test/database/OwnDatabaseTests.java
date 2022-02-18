package database;

import logging.TestLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

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
                // make a big file, 10 megabytes
                final var numberMB = 10;
                final var fileSize = 1024 * 1024 * numberMB;
                for (int i = 0; i < fileSize; i++) {
                    bw.write(i);
                }
                final var earlier = System.currentTimeMillis();
                final var raf = new RandomAccessFile(path.toFile(), "rw");
                for (int i = 0; i < 128; i++) {
                    final var pos = fileSize / 2;
                    raf.seek(pos);
                    raf.write(i);
                }
                final var later = System.currentTimeMillis();
                System.out.println(later - earlier);
            } finally {
                Files.delete(filePath);
            }
        }

    }
}
