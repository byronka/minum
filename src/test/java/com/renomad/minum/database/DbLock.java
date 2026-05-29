package com.renomad.minum.database;

public class DbLock {
    final DbData<?> dataObject;
    final long index;

    public DbLock(DbData<?> dataObject, long index) {
        this.dataObject = dataObject;
        this.index = index;
    }
}
