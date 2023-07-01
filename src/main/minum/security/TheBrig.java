package minum.security;

import minum.Constants;
import minum.database.DatabaseDiskPersistenceSimpler;
import minum.database.SimpleDataType;
import minum.database.SimpleSerializable;
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

import static minum.database.SimpleIndexed.calculateNextIndex;
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
     * @param clientId a string representation of the client address plus a string representing the offense,
     *                 for example, "1.2.3.4_vuln_seeking" - 1.2.3.4 was seeking out vulnerabilities.
     * @param duration how long they are stuck in jail, in milliseconds
     */
    private record Inmate(Long index, String clientId, Long duration) implements SimpleDataType<Inmate> {

        public static final SimpleDataType<Inmate> EMPTY = new Inmate(0L, "", 0L);

        @Override
        public Long getIndex() {
            return index;
        }

        @Override
        public String serialize() {
            return SimpleSerializable.serializeHelper(index, clientId(), duration());
        }

        @Override
        public Inmate deserialize(String serializedText) {
            final var tokens = SimpleSerializable.deserializeHelper(serializedText);

            return new Inmate(
                    Long.parseLong(tokens.get(0)),
                    tokens.get(1),
                    Long.parseLong(tokens.get(2)));
        }
    }

    public TheBrig(int sleepTime, ExecutorService es, ILogger logger, boolean isUsingDiskPersistence) {
        this.es = es;
        this.isUsingDiskPersistence = isUsingDiskPersistence;
        if (isUsingDiskPersistence) {
            Path dbDir = Path.of(Constants.DB_DIRECTORY);
            this.ddps = new DatabaseDiskPersistenceSimpler<>(dbDir.resolve("TheBrig"), es, logger);
            this.inmates = ddps.readAndDeserialize(Inmate.EMPTY);
            this.clientKeys = this.inmates.stream().collect(Collectors.toMap(Inmate::clientId, Inmate::duration));
            newInmateIndex = new AtomicLong(calculateNextIndex(inmates));
        } else {
            this.ddps = null;
            this.inmates = new ArrayList<>();
            this.clientKeys = new HashMap<>();
            newInmateIndex = new AtomicLong(1);
        }
        this.sleepTime = sleepTime;
        this.logger = logger;
    }


    /**
     * In this class we create a thread that runs throughout the lifetime
     * of the application, in an infinite loop removing keys from the list
     * under consideration.
     */
    public TheBrig(ExecutorService es, ILogger logger) {
        this(10 * 1000, es, logger, true);
    }

    public TheBrig(ExecutorService es, ILogger logger, boolean isUsingDiskPersistence) {
        this(10 * 1000, es, logger, isUsingDiskPersistence);
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

                    if (Constants.LOG_LEVELS.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " TheBrig is stopped.%n");
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
        var existingInmates = inmates.stream().filter(x -> x.clientId().equals(clientIdentifier)).count();
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
