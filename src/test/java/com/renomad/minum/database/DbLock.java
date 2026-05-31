package com.renomad.minum.database;

public class DbLock {
    final AbstractDb<?> database;
    final long index;

    public DbLock(AbstractDb<?> database, long index) {
        this.database = database;
        this.index = index;
    }
}
