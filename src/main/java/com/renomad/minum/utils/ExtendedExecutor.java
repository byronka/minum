package com.renomad.minum.utils;

import com.renomad.minum.Constants;

import java.util.concurrent.*;

/**
 * This lets us capture the stack traces thrown in a thread,
 * which the typical Executor does not.
 */
public final class ExtendedExecutor extends ThreadPoolExecutor {

    public ExtendedExecutor(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public static ExecutorService makeExecutorService(Constants constants) {
        boolean useVirtualThreads = constants.useVirtual;
        if (useVirtualThreads) {
            // the following line is only usable with the virtual threads API, which
            // is available on JDK 19/20 in preview mode, or JDK 21 without preview.
            return Executors.newVirtualThreadPerTaskExecutor();
        } else {
            return new ExtendedExecutor(0, Integer.MAX_VALUE,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    Executors.defaultThreadFactory());
        }
    }

}
