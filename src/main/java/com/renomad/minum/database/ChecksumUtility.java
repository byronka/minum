package com.renomad.minum.database;

import com.renomad.minum.utils.CryptoUtils;
import com.renomad.minum.utils.IFileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;

/**
 * This class contains some functions related to the "checksum" feature
 * of the database.  The "checksum" is a hash that is created when the
 * database writes data to a file, and is checked when reading that file.
 * <br>
 * If the value is different than expected while reading, an exception will be thrown
 * indicating data corruption.
 */
public class ChecksumUtility {

    private ChecksumUtility() {
        // not intended to be instantiated
    }

    /**
     * Given a path to a consolidated database file, check its data against
     * the recorded checksum and throw an exception if it doesn't match.
     * @param fullPathToConsolidatedFile the path to a consolidated database file
     * @param data the list of strings of data in a consolidated database file
     */
    static boolean compareWithChecksum(Path fullPathToConsolidatedFile, List<String> data, IFileUtils fileUtils) {
        // check against the checksum, if it exists. If it is not there or blank, just move on.
        Path checksumPath = fullPathToConsolidatedFile.resolveSibling(fullPathToConsolidatedFile.getFileName() + ".checksum");
        if (fileUtils.exists(checksumPath)) {
            String existingChecksumValue;
            try {
                existingChecksumValue = fileUtils.readString(checksumPath);
            } catch (IOException e) {
                throw new DbChecksumException(e);
            }
            String checksumString = buildChecksum(data);
            if (!checksumString.equals(existingChecksumValue)) {
                throw new DbChecksumException(generateChecksumErrorMessage(fullPathToConsolidatedFile));
            }
        }
        return true;
    }

    static String generateChecksumErrorMessage(Path fullPathToConsolidatedFile) {
        return """
                
                WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING
                **************************************************************************************
                **************************************************************************************
                
                Checksum failure for %s
                
                THIS FILE IS CORRUPTED, AND NEEDS TO BE RESTORED FROM BACKUP!
                
                The checksum file is at %s.checksum
                
                Next steps: This warning means the data does not align with its checksum, meaning
                it has changed by something other than the program.  That is, it is corrupted data.
                The best thing is to restore the data from backup for this.  Other options are to
                review the data.  Deleting the checksum file will cause this complaint to stop.
                
                **************************************************************************************
                **************************************************************************************
                WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING 
                
                """.stripIndent().formatted(fullPathToConsolidatedFile, fullPathToConsolidatedFile);
    }

    static String buildChecksum(Collection<String> updatedData) {
        // build a hash for this data
        MessageDigest messageDigestSha256 = getMessageDigest("SHA-256");
        for (String item : updatedData) {
            messageDigestSha256.update(item.getBytes(StandardCharsets.US_ASCII));
        }
        byte[] hash = messageDigestSha256.digest();
        return CryptoUtils.bytesToHex(hash);
    }

    static MessageDigest getMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new DbChecksumException(e);
        }
    }

}
