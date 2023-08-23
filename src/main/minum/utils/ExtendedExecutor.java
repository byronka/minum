package minum.utils;

import minum.Constants;
import minum.logging.LoggingLevel;

import java.util.concurrent.*;

/**
 * This lets us capture the stack traces thrown in a thread,
 * which the typical Executor does not.
 */
public final class ExtendedExecutor extends ThreadPoolExecutor {

    public ExtendedExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t == null
                && r instanceof Future<?>
                && ((Future<?>)r).isDone()) {
            try {
                ((Future<?>) r).get();
            } catch (CancellationException ce) {
                t = ce;
            } catch (ExecutionException ee) {
                t = ee.getCause();
            } catch (InterruptedException ie) {
                // ignore/reset
                Thread.currentThread().interrupt();
            }
        }
        if (t != null)
            t.printStackTrace();
    }

    public static ExecutorService makeExecutorService(Constants constants) {
        boolean useVirtualThreads = constants.USE_VIRTUAL;
        if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.println(TimeUtils.getTimestampIsoInstant() + " use virtual threads? " + useVirtualThreads);
        if (useVirtualThreads) {
            // the following line is only usable with the virtual threads API, which
            // is available on OpenJDK 19/20 in preview mode.
            return Executors.newVirtualThreadPerTaskExecutor();
        } else {
            return new ExtendedExecutor(0, Integer.MAX_VALUE,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    Executors.defaultThreadFactory());
        }
    }

}
