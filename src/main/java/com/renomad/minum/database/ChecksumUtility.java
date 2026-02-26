package com.renomad.minum.database;

import com.renomad.minum.utils.CryptoUtils;

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
 * database writes data, and is checked when reading.  If the value is
 * different than expected while reading, an exception will be thrown
 * indicating that the data is considered corrupt.
 */
public class ChecksumUtility {


    /**
     * Given a path to a consolidated database file, check its data against
     * the recorded checksum and throw an exception if it doesn't match.
     * @param fullPathToConsolidatedFile the path to a consolidated database file
     * @param data the list of strings of data in a consolidated database file
     */
    static void compareWithChecksum(Path fullPathToConsolidatedFile, List<String> data) {
        // check against the checksum, if it exists. If it is not there or blank, just move on.
        Path checksumPath = fullPathToConsolidatedFile.resolveSibling(fullPathToConsolidatedFile.getFileName() + ".checksum");
        if (Files.exists(checksumPath)) {
            String existingChecksumValue;
            try {
                existingChecksumValue = Files.readString(checksumPath);
            } catch (IOException e) {
                throw new DbChecksumException(e);
            }
            if (!existingChecksumValue.isBlank()) {
                String checksumString = buildChecksum(data);
                if (!checksumString.equals(existingChecksumValue)) {
                    throw new DbChecksumException(generateChecksumErrorMessage(fullPathToConsolidatedFile));
                }
            }
        }
    }

    static String generateChecksumErrorMessage(Path fullPathToConsolidatedFile) {
        return """
                
                WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING 
                **************************************************************************************
                **************************************************************************************
                
                Checksum failure for %s
                
                file considered corrupted
                
                See also %s.checksum
                
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
        MessageDigest messageDigestSha256;
        try {
            messageDigestSha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new DbChecksumException(e);
        }
        for (String item : updatedData) {
            messageDigestSha256.update(item.getBytes(StandardCharsets.US_ASCII));
        }
        byte[] hash = messageDigestSha256.digest();
        return CryptoUtils.bytesToHex(hash);
    }

}
