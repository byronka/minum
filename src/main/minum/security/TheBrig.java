package minum.security;

import minum.Constants;
import minum.Context;
import minum.database.Db;
import minum.database.DbData;
import minum.logging.ILogger;
import minum.logging.LoggingLevel;
import minum.utils.MyThread;
import minum.utils.TimeUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static minum.utils.Invariants.mustBeTrue;
import static minum.utils.SerializationUtils.deserializeHelper;
import static minum.utils.SerializationUtils.serializeHelper;

public final class TheBrig implements ITheBrig {
    private final ExecutorService es;
    private final Db<Inmate> db;
    private final ILogger logger;
    private final Constants constants;
    private final ReentrantLock lock = new ReentrantLock();
    private Thread myThread;

    /**
     * How long our inner thread will sleep before waking up to scan
     * for old keys
     */
    private final int sleepTime;

    /**
     * These are all the keys (identifiers of clients, sometimes with extra info) that have been added.
     * For example, if 1.2.3.4 downloads files too frequently, we might create a
     * key of 1.2.3.4_too_freq_downloads, so we can dispense justice appropriately.
     */
    private final Map<String, Long> clientKeys;

    /**
     * Represents an inmate in our "jail".  If someone does something we don't like, they do their time here.
     */
    private static class Inmate extends DbData<Inmate> {

        /**
         * Builds an empty version of this class, except
         * that it has a current Context object
         */
        public static final Inmate EMPTY = new Inmate(0L, "", 0L);
        private Long index;
        private final String clientId;
        private final Long duration;

        /**
         * Represents an inmate in our "jail".  If someone does something we don't like, they do their time here.
         * @param clientId a string representation of the client address plus a string representing the offense,
         *                 for example, "1.2.3.4_vuln_seeking" - 1.2.3.4 was seeking out vulnerabilities.
         * @param duration how long they are stuck in jail, in milliseconds
         */
        public Inmate(Long index, String clientId, Long duration) {
            this.index = index;
            this.clientId = clientId;
            this.duration = duration;
        }

        @Override
        public long getIndex() {
            return index;
        }

        @Override
        public void setIndex(long index) {
            this.index = index;
        }

        @Override
        public String serialize() {
            return serializeHelper(index, clientId, duration);
        }

        @Override
        public Inmate deserialize(String serializedText) {
            final var tokens = deserializeHelper(serializedText);

            return new Inmate(
                    Long.parseLong(tokens.get(0)),
                    tokens.get(1),
                    Long.parseLong(tokens.get(2)));
        }

        public String getClientId() {
            return clientId;
        }

        public Long getDuration() {
            return duration;
        }
    }

    public TheBrig(int sleepTime, Context context) {
        this.es = context.getExecutorService();
        this.constants = context.getConstants();
        this.logger = context.getLogger();
        Path dbDir = Path.of(constants.DB_DIRECTORY);
        this.db = new Db<>(dbDir.resolve("the_brig"), context, Inmate.EMPTY);
        this.clientKeys = this.db.values().stream().collect(Collectors.toMap(Inmate::getClientId, Inmate::getDuration));
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
    @SuppressWarnings({"BusyWait"})
    public ITheBrig initialize() {
        logger.logDebug(() -> "Initializing TheBrig main loop");
        Callable<Object> innerLoopThread = () -> {
            Thread.currentThread().setName("TheBrig");
            myThread = Thread.currentThread();
            while (true) {
                try {
                    int size = clientKeys.size();
                    if (size > 0) {
                        logger.logTrace(() -> "TheBrig reviewing current inmates. Count: " + size);
                    }
                    var now = System.currentTimeMillis();
                    List<String> keysToRemove = new ArrayList<>();
                    // figure out which clients have paid their dues
                    for (var e : clientKeys.entrySet()) {
                        if (e.getValue() < now) {
                            logger.logTrace(() -> "UnderInvestigation: " + e.getKey() + " has paid its dues and is getting released");
                            keysToRemove.add(e.getKey());
                        }
                    }
                    for (var k : keysToRemove) {
                        logger.logTrace(() -> "TheBrig: removing " + k + " from jail");
                        List<Inmate> inmates1 = db.values().stream().filter(x -> x.clientId.equals(k)).toList();
                        mustBeTrue(inmates1.size() == 1, "There must be exactly one inmate found or there's a bug");
                        Inmate inmateToRemove = inmates1.get(0);
                        clientKeys.remove(k);
                        db.delete(inmateToRemove);
                    }
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {

                    /*
                    this is what we expect to happen.
                    once this happens, we just continue on.
                    this only gets called when we are trying to shut everything
                    down cleanly
                     */

                    if (constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " TheBrig is stopped.%n");
                    return null;
                } catch (Exception ex) {
                    System.out.printf(TimeUtils.getTimestampIsoInstant() + " ERROR: TheBrig has stopped unexpectedly. error: %s%n", ex);
                    throw ex;
                }
            }
        };
        es.submit(innerLoopThread);
        return this;
    }

    @Override
    public void stop() {
        logger.logDebug(() -> "TheBrig has been told to stop");
        for (int i = 0; i < 10; i++) {
            if (myThread != null) {
                logger.logDebug(() -> "TheBrig: Sending interrupt to thread");
                myThread.interrupt();
                return;
            } else {
                MyThread.sleep(20);
            }
        }
        throw new RuntimeException("TheBrig: Leaving without successfully stopping thread");
    }


    @Override
    public void sendToJail(String clientIdentifier, long sentenceDuration) {
        if (!constants.IS_THE_BRIG_ENABLED) {
            return;
        }
        lock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
        try {
            logger.logDebug(() -> "TheBrig: Putting away " + clientIdentifier + " for " + sentenceDuration + " milliseconds");
            clientKeys.put(clientIdentifier, System.currentTimeMillis() + sentenceDuration);
            var existingInmates = db.values().stream().filter(x -> x.clientId.equals(clientIdentifier)).count();
            mustBeTrue(existingInmates < 2, "count of inmates must be either 0 or 1, anything else is a bug");
            if (existingInmates == 0) {
                Inmate newInmate = new Inmate(0L, clientIdentifier, System.currentTimeMillis() + sentenceDuration);
                db.write(newInmate);
            }
        } finally {
            lock.unlock();
        }

    }

    @Override
    public boolean isInJail(String clientIdentifier) {
        if (!constants.IS_THE_BRIG_ENABLED) {
            return false;
        }
        return clientKeys.containsKey(clientIdentifier);
    }

    @Override
    public List<Map.Entry<String, Long>> getInmates() {
        return clientKeys.entrySet().stream().toList();
    }
}
