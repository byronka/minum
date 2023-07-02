package minum.security;

import minum.Constants;
import minum.Context;
import minum.database.DatabaseDiskPersistenceSimpler;
import minum.database.ISimpleDataType;
import minum.database.SimpleDataTypeImpl;
import minum.logging.ILogger;
import minum.logging.LoggingLevel;
import minum.utils.MyThread;
import minum.utils.TimeUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static minum.utils.Invariants.mustBeTrue;

/**
 * This class is responsible for monitoring the inmates who have
 * misbehaved in our system.  It's relatively simple - a client who
 * needs addressing will be stored in a map in this class for as
 * long as required.  After they have served their time, they
 * are released, but in certain cases (like when we deem them to
 * have no redeeming value to us) we may set their time extremely high.
 */
public class TheBrig {
    private final ExecutorService es;
    private final DatabaseDiskPersistenceSimpler<Inmate> ddps;
    private final List<Inmate> inmates;
    private final ILogger logger;
    private final boolean isUsingDiskPersistence;
    private final Constants constants;

    private Thread myThread;
    private final AtomicLong newInmateIndex;

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
    private static class Inmate extends SimpleDataTypeImpl<Inmate> {

        /**
         * Builds an empty version of this class, except
         * that it has a current Context object
         */
        public static final ISimpleDataType<Inmate> EMPTY = new Inmate(0L, "", 0L);
        private final Long index;
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

    public TheBrig(int sleepTime, Context context, boolean isUsingDiskPersistence) {
        this.es = context.getExecutorService();
        this.constants = context.getConstants();
        this.logger = context.getLogger();
        this.isUsingDiskPersistence = isUsingDiskPersistence;
        if (isUsingDiskPersistence) {
            Path dbDir = Path.of(constants.DB_DIRECTORY);
            this.ddps = new DatabaseDiskPersistenceSimpler<>(dbDir.resolve("TheBrig"), context);
            this.inmates = ddps.readAndDeserialize(Inmate.EMPTY);
            this.clientKeys = this.inmates.stream().collect(Collectors.toMap(Inmate::getClientId, Inmate::getDuration));
            newInmateIndex = new AtomicLong(ddps.calculateNextIndex(inmates));
        } else {
            this.ddps = null;
            this.inmates = new ArrayList<>();
            this.clientKeys = new HashMap<>();
            newInmateIndex = new AtomicLong(1);
        }
        this.sleepTime = sleepTime;

    }


    /**
     * In this class we create a thread that runs throughout the lifetime
     * of the application, in an infinite loop removing keys from the list
     * under consideration.
     */
    public TheBrig(Context context) {
        this(10 * 1000, context, true);
    }

    public TheBrig(Context context, boolean isUsingDiskPersistence) {
        this(10 * 1000, context, isUsingDiskPersistence);
    }



    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings({"BusyWait"})
    public TheBrig initialize() {
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
                        List<Inmate> inmates1 = inmates.stream().filter(x -> x.clientId.equals(k)).toList();
                        mustBeTrue(inmates1.size() == 1, "There must be exactly one inmate found or there's a bug");
                        Inmate inmateToRemove = inmates1.get(0);
                        inmates.remove(inmateToRemove);
                        if (inmates.size() == 0) {
                            newInmateIndex.set(1);
                        }
                        clientKeys.remove(k);
                        if (isUsingDiskPersistence) ddps.deleteOnDisk(inmateToRemove);
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


    /**
     * Kills the infinite loop running inside this class.
     */
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


    /**
     * Put a client in jail for some infraction, for a specified time.
     * @param clientIdentifier the client's address plus some feature identifier, like 1.2.3.4_too_freq_downloads
     * @param sentenceDuration length of stay, in milliseconds
     */
    public synchronized void sendToJail(String clientIdentifier, long sentenceDuration) {
        logger.logDebug(() -> "TheBrig: Putting away " + clientIdentifier + " for " +sentenceDuration + " milliseconds");
        clientKeys.put(clientIdentifier, System.currentTimeMillis() + sentenceDuration);
        var existingInmates = inmates.stream().filter(x -> x.clientId.equals(clientIdentifier)).count();
        mustBeTrue(existingInmates < 2, "count of inmates must be either 0 or 1, anything else is a bug" );
        if (existingInmates == 0) {
            Inmate newInmate = new Inmate(newInmateIndex.getAndIncrement(), clientIdentifier, System.currentTimeMillis() + sentenceDuration);
            inmates.add(newInmate);
            if (isUsingDiskPersistence) ddps.persistToDisk(newInmate);
        }

    }

    public boolean isInJail(String clientIdentifier) {
        return clientKeys.containsKey(clientIdentifier);
    }

    public List<Map.Entry<String, Long>> getInmates() {
        return clientKeys.entrySet().stream().toList();
    }
}
