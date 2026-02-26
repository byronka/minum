package com.renomad.minum.database;

public class DbChecksumException extends RuntimeException {

    public DbChecksumException(Exception e) {
        super(e);
    }

    public DbChecksumException(String msg) {
        super(msg);
    }
}
