package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static utils.Invariants.stringMustNotBeNullOrBlank;

/**
 * Cryptographic functions
 */
public class Crypto {

    /**
     * hash a string using SHA-256
     * @param value a user's password
     * @return a hash of the password value.  a one-way function that returns a unique value,
     *          but different than the original, cannot be converted back to its original value.
     */
    public static String hashStringSha256(String value) {
        stringMustNotBeNullOrBlank(value);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Converts an array of bytes to their corresponding hex string
     * @param bytes an array of bytes
     * @return a hex string of that array
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }


}
