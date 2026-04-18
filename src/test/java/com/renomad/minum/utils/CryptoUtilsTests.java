package com.renomad.minum.utils;

import org.junit.Test;

import java.security.NoSuchAlgorithmException;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;

public class CryptoUtilsTests {

    @Test
    public void testBytesToHex() {
        var cryptoUtils = new CryptoUtils();
        assertEquals(cryptoUtils.bytesToHex(new byte[]{1, 2, 3}), "010203");
        assertEquals(cryptoUtils.bytesToHex(new byte[0]), "");
        assertEquals(cryptoUtils.bytesToHex(new byte[]{0, (byte) 255,(byte) 255,(byte) 255}), "00ffffff");
    }

    @Test
    public void testCreatePasswordHash_BadAlgorithm() {
        var cryptoUtils = new CryptoUtils();
        var ex = assertThrows(NoSuchAlgorithmException.class, () -> cryptoUtils.createPasswordHash("foo_password", "mysalt", "badalgorithm"));
        assertEquals(ex.getMessage(), "badalgorithm SecretKeyFactory not available");
    }

    @Test
    public void testCreatePasswordHash() {
        var cryptoUtils = new CryptoUtils();
        String passwordHash = cryptoUtils.createPasswordHash("abc123", "saltysalt");
        assertEquals(passwordHash, "87f83512f4c18af25e82728cfdf96194");
    }
}
