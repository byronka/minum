package com.renomad.minum.utils;

import com.renomad.minum.logging.ILogger;

/**
 * This exists so that we are able to slightly better manage
 * exceptions in a highly threaded system.  Here's the thing:
 * exceptions stop bubbling up at the thread invocation. If we
 * don't take care to deal with that in some way, we can easily
 * just lose the information.  Something could be badly broken and
 * we could be totally oblivious to it.  This interface is to
 * alleviate that situation.
 */
@FunctionalInterface
public interface ThrowingRunnable {

    void run();

    static Runnable throwingRunnableWrapper(ThrowingRunnable throwingRunnable, ILogger logger) {
        return () -> {
            try {
                throwingRunnable.run();
            } catch (Exception ex) {
                logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
            }
        };
    }
}