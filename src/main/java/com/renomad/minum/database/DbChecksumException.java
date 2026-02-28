package com.renomad.minum.database;

/**
 * An exception thrown when the data's checksum does not match expectations
 */
public class DbChecksumException extends RuntimeException {

    public DbChecksumException(Exception e) {
        super(e);
    }

    public DbChecksumException(String msg) {
        super(msg);
    }
}
