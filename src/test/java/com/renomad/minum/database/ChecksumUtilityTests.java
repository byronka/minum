package com.renomad.minum.database;

import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.renomad.minum.database.ChecksumUtility.compareWithChecksum;
import static com.renomad.minum.database.ChecksumUtility.getMessageDigest;
import static com.renomad.minum.testing.TestFramework.*;

public class ChecksumUtilityTests {

    private FileUtils fileUtils;
    private Context context;

    @Before
    public void init() {
        this.context = TestFramework.buildTestingContext("ChecksumUtilityTests");
        this.fileUtils = new FileUtils(this.context.getLogger(), this.context.getConstants());
    }

    @After
    public void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    /**
     * We have a helper utility in {@link ChecksumUtility} to build {@link MessageDigest}
     * instances.  An annoyance is that we have to use a string when building and handle
     * a possible {@link NoSuchAlgorithmException}.  This will test what exactly happens
     * if we pass an invalid string.
     */
    @Test
    public void testInvalidCryptoAlgorithm() {
        var ex = assertThrows(DbChecksumException.class, () -> getMessageDigest("I AM INVALID"));
        assertEquals(ex.getMessage(), "java.security.NoSuchAlgorithmException: I AM INVALID MessageDigest not available");
    }

    /**
     * If, somehow, the system fails to read data from the checksum file when it
     * becomes time, an exception should be thrown.
     */
    @Test
    public void testCompareWithChecksum_NegativeCase_ChecksumMissing() {
        Path directory = Path.of("out/simple_db_for_engine2_tests/engine2/checksum/missing");

        // delete existing directory
        fileUtils.deleteDirectoryRecursivelyIfExists(directory);

        // make directory and a sub directory that is meant to cause an exception
        // when our code tries reading from it, thus testing a negative case.
        fileUtils.makeDirectory(directory);
        fileUtils.makeDirectory(directory.resolve("1_to_100.checksum"));

        assertThrows(DbChecksumException.class, () -> compareWithChecksum(directory.resolve("1_to_100"), List.of()));
    }

    @Test
    public void testCompareWithChecksum_NegativeCase_ChecksumConflict() throws IOException {
        Path directory = Path.of("out/simple_db_for_engine2_tests/engine2/checksum/conflict");

        // delete existing directory
        fileUtils.deleteDirectoryRecursivelyIfExists(directory);

        // make directory and a sub directory that is meant to cause an exception
        // when our code tries reading from it, thus testing a negative case.
        fileUtils.makeDirectory(directory);
        Files.writeString(directory.resolve("101_to_200.checksum"), "foo");

        var ex = assertThrows(DbChecksumException.class, () -> compareWithChecksum(directory.resolve("101_to_200"), List.of("a")));
        assertTrue(ex.getMessage().contains("checksum"), "message was: " + ex.getMessage());
    }

    /**
     * If the checksum file does not exist, then move along without checking.
     * This should only be the case when a system is rewriting files that haven't
     * yet been upgraded to use checksums.
     */
    @Test
    public void testCompareWithChecksum_NegativeCase_ChecksumDoesNotExist() {
        Path directory = Path.of("out/simple_db_for_engine2_tests/engine2/checksum/conflict");

        // delete existing directory
        fileUtils.deleteDirectoryRecursivelyIfExists(directory);

        // make directory and a sub directory that is meant to cause an exception
        // when our code tries reading from it, thus testing a negative case.
        fileUtils.makeDirectory(directory);

        assertTrue(compareWithChecksum(directory.resolve("101_to_200"), List.of("a")));
    }
}
