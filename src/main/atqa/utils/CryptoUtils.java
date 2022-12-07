package atqa.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.KeySpec;

public class CryptoUtils {

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
        Invariants.mustNotBeNull(value);
        Invariants.mustBeFalse(value.isBlank());
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

    /**
     * Hash the input string with the provided PBKDF2 algorithm, and return a string representation
     *<br><br>
     * See docs/password_storage_cheat_sheet
     */
    public static String createHash(String password, String salt) {
        final KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 65536, 128);
        final SecretKeyFactory factory;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final byte[] hashed = factory.generateSecret(spec).encoded;
        return bytesToHex(hashed);
    }

}
