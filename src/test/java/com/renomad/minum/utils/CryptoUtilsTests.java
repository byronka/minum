package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.security.NoSuchAlgorithmException;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.testing.TestFramework.shutdownTestingContext;

public class CryptoUtilsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("CryptoUtilsTests");
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    @Test
    public void testBytesToHex() {
        assertEquals(CryptoUtils.bytesToHex(new byte[]{1, 2, 3}), "010203");
        assertEquals(CryptoUtils.bytesToHex(new byte[0]), "");
        assertEquals(CryptoUtils.bytesToHex(new byte[]{0, (byte) 255,(byte) 255,(byte) 255}), "00ffffff");
    }

    @Test
    public void testCreatePasswordHash_BadAlgorithm() {
        var ex = assertThrows(UtilsException.class, () -> CryptoUtils.createPasswordHash("foo_password", "mysalt", "badalgorithm"));
        assertEquals(ex.getMessage(), "java.security.NoSuchAlgorithmException: badalgorithm SecretKeyFactory not available");
        assertEquals(ex.getCause().getMessage(), "badalgorithm SecretKeyFactory not available");
    }

    @Test
    public void testCreatePasswordHash() {
        String passwordHash = CryptoUtils.createPasswordHash("abc123", "saltysalt");
        assertEquals(passwordHash, "87f83512f4c18af25e82728cfdf96194");
    }
}
