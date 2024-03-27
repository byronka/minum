package com.renomad.minum.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;

/**
 * Handy helpers for dealing with cryptographic functions
 */
public final class CryptoUtils {

    private CryptoUtils() {
        // cannot construct
    }

    /**
     * Converts an array of bytes to their corresponding hex string
     * @param bytes an array of bytes
     * @return a hex string of that array
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Hash the input string with the provided PBKDF2 algorithm, and return a string representation
     * Note that the PBKDF2WithHmacSHA1 algorithm is specifically designed to take a long time,
     * to slow down an attacker.
     * <p>
     * See docs/http_protocol/password_storage_cheat_sheet
     * </p>
     */
    public static String createPasswordHash(String password, String salt) {
        return createPasswordHash(password, salt, "PBKDF2WithHmacSHA1");
    }

    static String createPasswordHash(String password, String salt, String algorithm) {
        final KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 65536, 128);
        final SecretKeyFactory factory;

        try {
            factory = SecretKeyFactory.getInstance(algorithm);
            final byte[] hashed = factory.generateSecret(spec).getEncoded();
            return bytesToHex(hashed);
        } catch (Exception e) {
            throw new UtilsException(e);
        }
    }

}
