package com.renomad.minum.security;

import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
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
    private final AbstractDb<Inmate> inmatesDb;
    private final ILogger logger;
    private final ReentrantLock lock = new ReentrantLock();
    private Thread myThread;
    private static final String CLIENT_IDENTIFIER_INDEX = "client_identifier_index";

    /**
     * How long our inner thread will sleep before waking up to scan
     * for old keys
     */
    private final int sleepTime;

    public TheBrig(int sleepTime, Context context) {
        this.es = context.getExecutorService();
        this.logger = context.getLogger();
        this.inmatesDb = context.getDb2("the_brig", Inmate.EMPTY);
        this.inmatesDb.registerIndex(CLIENT_IDENTIFIER_INDEX, Inmate::getClientId);
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
        lock.lock();
        try {
            processInmateList(now, values, logger, inmatesDb);
        } finally {
            lock.unlock();
        }
        Thread.sleep(sleepTime);
    }

    /**
     * figure out which clients have paid their dues
     * @param now the current time, in milliseconds past the epoch
     * @param inmatesDb the database of all inmates
     */
    static void processInmateList(long now, Collection<Inmate> inmates, ILogger logger, AbstractDb<Inmate> inmatesDb) {
        for (Inmate clientKeyAndDuration : inmates) {
            // if the release time is in the past (that is, the release time is
            // before / less-than now), add them to the list to be released.
            if (clientKeyAndDuration.getReleaseTime() < now) {
                logger.logTrace(() -> "UnderInvestigation: " + clientKeyAndDuration.getClientId() + " has paid its dues as of " + clientKeyAndDuration.getReleaseTime() + " and is getting released. Current time: " + now);
                inmatesDb.delete(clientKeyAndDuration);
            }
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
        lock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
        try {
            long now = System.currentTimeMillis();

            Inmate existingInmate = inmatesDb.findExactlyOne(CLIENT_IDENTIFIER_INDEX, clientIdentifier);
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
        lock.lock();
        try {
            return inmatesDb.findExactlyOne(CLIENT_IDENTIFIER_INDEX, clientIdentifier) != null;
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
