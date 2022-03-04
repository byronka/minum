package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static utils.Invariants.stringMustNotBeNullOrBlank;

/**
 * Cryptographic functions
 */
public class Crypto {

    private static final MessageDigest md256;
    private static final MessageDigest md1;

    static {
        try {
            md256 = MessageDigest.getInstance("SHA-256");
            md1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * hash a string using SHA-256
     * @param value a user's password
     * @return a hash of the password value.  a one-way function that returns a unique value,
     *          but different than the original, cannot be converted back to its original value.
     */
    public static byte[] hashString(String value, MessageDigest md) {
        stringMustNotBeNullOrBlank(value);
        return md.digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] hashStringSha1(String value) {
        return hashString(value, md1);
    }
    public static byte[] hashStringSha256(String value) {
        return hashString(value, md256);
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


}
