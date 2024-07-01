package com.renomad.minum.security;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.ThrowingRunnable;
import com.renomad.minum.utils.TimeUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * See {@link ITheBrig}
 */
public final class TheBrig implements ITheBrig {
    private final ExecutorService es;
    private final Db<Inmate> inmatesDb;
    private final ILogger logger;
    private final Constants constants;
    private final ReentrantLock lock = new ReentrantLock();
    private Thread myThread;

    /**
     * How long our inner thread will sleep before waking up to scan
     * for old keys
     */
    private final int sleepTime;

    public TheBrig(int sleepTime, Context context) {
        this.es = context.getExecutorService();
        this.constants = context.getConstants();
        this.logger = context.getLogger();
        this.inmatesDb = context.getDb("the_brig", Inmate.EMPTY);
        this.sleepTime = sleepTime;
    }

    /**
     * In this class we create a thread that runs throughout the lifetime
     * of the application, in an infinite loop removing keys from the list
     * under consideration.
     */
    public TheBrig(Context context) {
        this(10 * 1000, context);
    }

    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @Override
    public ITheBrig initialize() {
        logger.logDebug(() -> "Initializing TheBrig main loop");
        ThrowingRunnable innerLoopThread = () -> {
            Thread.currentThread().setName("TheBrigThread");
            myThread = Thread.currentThread();
            while (true) {
                try {
                    reviewCurrentInmates();
                } catch (InterruptedException ex) {

                    /*
                    this is what we expect to happen.
                    once this happens, we just continue on.
                    this only gets called when we are trying to shut everything
                    down cleanly
                     */

                    logger.logDebug(() -> String.format("%s TheBrig is stopped.%n", TimeUtils.getTimestampIsoInstant()));
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        };
        es.submit(ThrowingRunnable.throwingRunnableWrapper(innerLoopThread, logger));
        return this;
    }

    private void reviewCurrentInmates() throws InterruptedException {
        Collection<Inmate> values = inmatesDb.values();
        if (! values.isEmpty()) {
            logger.logTrace(() -> "TheBrig reviewing current inmates. Count: " + values.size());
        }
        var now = System.currentTimeMillis();

        processInmateList(now, values, logger, inmatesDb);
        Thread.sleep(sleepTime);
    }

    /**
     * figure out which clients have paid their dues
     * @param now the current time, in milliseconds past the epoch
     * @param inmatesDb the database of all inmates
     */
    static void processInmateList(long now, Collection<Inmate> inmates, ILogger logger, Db<Inmate> inmatesDb) {
        List<String> keysToRemove = new ArrayList<>();
        for (Inmate clientKeyAndDuration : inmates) {
            reviewForParole(now, keysToRemove, clientKeyAndDuration, logger);
        }
        for (var k : keysToRemove) {
            logger.logTrace(() -> "TheBrig: removing " + k + " from jail");
            Inmate inmateToRemove = SearchUtils.findExactlyOne(inmates.stream(), x -> x.getClientId().equals(k));
            inmatesDb.delete(inmateToRemove);
        }
    }

    private static void reviewForParole(
            long now,
            List<String> keysToRemove,
            Inmate inmate,
            ILogger logger) {
        // if the release time is in the past (that is, the release time is
        // before / less-than now), add them to the list to be released.
        if (inmate.getReleaseTime() < now) {
            logger.logTrace(() -> "UnderInvestigation: " + inmate.getClientId() + " has paid its dues as of " + inmate.getReleaseTime() + " and is getting released. Current time: " + now);
            keysToRemove.add(inmate.getClientId());
        }
    }

    @Override
    public void stop() {
        logger.logDebug(() -> "TheBrig has been told to stop");
        if (myThread != null) {
            logger.logDebug(() -> "TheBrig: Sending interrupt to thread");
            myThread.interrupt();
            this.inmatesDb.stop();
        } else {
            throw new MinumSecurityException("TheBrig was told to stop, but it was uninitialized");
        }
    }


    @Override
    public boolean sendToJail(String clientIdentifier, long sentenceDuration) {
        if (!constants.isTheBrigEnabled) {
            return false;
        }
        lock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
        try {
            long now = System.currentTimeMillis();

            Inmate existingInmate = SearchUtils.findExactlyOne(inmatesDb.values().stream(), x -> x.getClientId().equals(clientIdentifier));
            if (existingInmate == null) {
                // if this is a new inmate, add them
                long releaseTime = now + sentenceDuration;
                logger.logDebug(() -> "TheBrig: Putting away " + clientIdentifier + " for " + sentenceDuration + " milliseconds. Release time: " + releaseTime + ". Current time: " + now);
                Inmate newInmate = new Inmate(0L, clientIdentifier, releaseTime);
                inmatesDb.write(newInmate);
            } else {
                // if this is an existing inmate continuing to attack us, just update their duration
                long releaseTime = existingInmate.getReleaseTime() + sentenceDuration;
                logger.logDebug(() -> "TheBrig: Putting away " + clientIdentifier + " for " + sentenceDuration + " milliseconds. Release time: " + releaseTime + ". Current time: " + now);
                inmatesDb.write(new Inmate(existingInmate.getIndex(), existingInmate.getClientId(), releaseTime));
            }
        } finally {
            lock.unlock();
        }
        return true;

    }

    @Override
    public boolean isInJail(String clientIdentifier) {
        if (!constants.isTheBrigEnabled) {
            return false;
        }
        lock.lock();
        try {
            return inmatesDb.values().stream().anyMatch(x -> x.getClientId().equals(clientIdentifier));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Inmate> getInmates() {
        lock.lock();
        try {
            return inmatesDb.values().stream().toList();
        } finally {
            lock.unlock();
        }
    }

}
