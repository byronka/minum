package com.renomad.minum.utils;

import java.util.concurrent.LinkedBlockingQueue;

public interface AbstractActionQueue {
    // Regarding the InfiniteLoopStatement - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that is what it is.
    AbstractActionQueue initialize();

    void enqueue(String description, ThrowingRunnable action);

    void stop(int count, int sleepTime);

    void stop();

    Thread getQueueThread();

    LinkedBlockingQueue<RunnableWithDescription> getQueue();

    boolean isStopped();
}
