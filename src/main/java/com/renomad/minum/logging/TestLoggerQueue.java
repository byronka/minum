package com.renomad.minum.logging;


import java.io.Serial;
import java.util.ArrayDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used in {@link TestLogger} as a circular queue to store
 * the most recent log statements for analysis.
 */
public final class TestLoggerQueue extends ArrayDeque<String> {

    @Serial
    private static final long serialVersionUID = -149106325553645154L;

    private final ReentrantLock queueLock;
    private final int capacity;

    public TestLoggerQueue(int capacity){
        this.capacity = capacity;
        this.queueLock = new ReentrantLock();
    }

    @Override
    public boolean add(String e) {
        queueLock.lock();
        try {
            if (size() >= capacity)
                removeFirst();
            return super.add(e);
        } finally {
            queueLock.unlock();
        }
    }
}