package database.owndatabase;

import logging.ILogger;
import utils.ActionQueue;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.regex.Pattern;

import static utils.FileUtils.writeString;
import static utils.Invariants.mustBeTrue;
import static utils.Invariants.mustNotBeNull;
import static utils.StringUtils.decode;

public class DatabaseDiskPersistence {

    static final String databaseFileSuffix = ".db";

    public static final Pattern serializedStringRegex = Pattern.compile(" (.*?): (.*?) ");
    private final String dbDirectory;
    private final ActionQueue actionQueue;
    private final ILogger logger;

    public DatabaseDiskPersistence(String dbDirectory, ExecutorService executorService, ILogger logger) {
        this.dbDirectory = dbDirectory;
        actionQueue = new ActionQueue("DatabaseWriter " + Integer.toHexString(hashCode()), executorService).initialize();
        this.logger = logger;
    }

    /**
     * This function will stop the database persistence cleanly.
     *
     * In order to do this, we need to wait for our threads
     * to finish their work.  In particular, we
     * have offloaded our file writes to [actionQueue], which
     * has an internal thread for serializing all actions
     * on our database
     */
    public void stop() {
        actionQueue.stop();
    }


    /**
     * takes any serializable data and writes it to disk
     *
     * @param item the data we are serializing and writing
     * @param name the name of the data
     */
    <T extends IndexableSerializable<?>> void persistToDisk(T item,String name) {
        final var parentDirectory = "%s/%s".formatted(dbDirectory, name);
        actionQueue.enqueue(() -> {
            try {
                if (!Files.exists(Path.of(parentDirectory))) {
                    final var didSucceed = new File(parentDirectory).mkdirs();
                    if (!didSucceed) throw new Exception("Did not build directory at " + parentDirectory);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        final var fullPath = "%s/%s%s".formatted(parentDirectory, item.getIndex(), databaseFileSuffix);

        actionQueue.enqueue(() -> writeString(fullPath, item.serialize()));
    }

    /**
     * Deletes a piece of data from the disk
     *
     * Our data consists of directories as containers and each
     * individual piece of data (e.g. [TimeEntry], [Project], etc.) as
     * a file in that directory.  This method simply finds the proper
     * file and deletes it.
     *
     * @param item the data we are serializing and writing
     * @param subDirectory the name of the data, for finding the directory
     */
    public <T extends IndexableSerializable<?>> void deleteOnDisk(T item, String subDirectory) {
        final var fullPath = "%s%s/%s%s".formatted(dbDirectory, subDirectory, item.getIndex(), databaseFileSuffix);
        actionQueue.enqueue(() -> {
            try {
                final var didSucceed = new File(fullPath).delete();
                if (!didSucceed) throw new Exception("Failed to delete file at " + fullPath);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        }


    public <T extends IndexableSerializable<?>> void updateOnDisk(T item, String subDirectory) {
        final var fullPath = "%s%s/%s%s".formatted(dbDirectory, subDirectory, item.getIndex(), databaseFileSuffix);
        final var file = new File(fullPath);

        actionQueue.enqueue(() -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update %s but it doesn't exist".formatted(file));
            writeString(fullPath, item.serialize());
        });
    }


    public static <T extends IndexableSerializable<?>> T deserialize(String serialized, Function<Map<SerializationKeys, String>, T> converter, List<SerializationKeys> serializationKeys) {
        final var matcher = DatabaseDiskPersistence.serializedStringRegex.matcher(serialized);
        final var myMap = new HashMap<SerializationKeys, String>();
        while(matcher.find()) {
            final var keys = serializationKeys.stream().filter(x -> x.getKeyString().equals(matcher.group(1))).toList();
            mustBeTrue(keys.size() == 1, "There should only be one key found");
            myMap.put(keys.get(0), decode(matcher.group(2)));
        }
        return converter.apply(myMap);
    }


    // WORK ZONE - DANGER - KOTLIN FOLLOWS
    // WORK ZONE - DANGER - KOTLIN FOLLOWS
    // WORK ZONE - DANGER - KOTLIN FOLLOWS

    /**
     * This factory method handles the nitty-gritty about starting
     * the database with respect to the files on disk.  If you plan
     * to use the database with the disk, here's a great place to
     * start.
     */
    public PureMemoryDatabase startWithDiskPersistence(Map<String, ChangeTrackingSet<?>> schema) {
        PureMemoryDatabase restoredPMD = null;
        final var topDirectory = new File(mustNotBeNull(dbDirectory));
        final var innerFiles = topDirectory.listFiles();
        if ((!topDirectory.exists()) || innerFiles == null || innerFiles.length == 0) {
            logger.logImperative("directory %s was not found".formatted(dbDirectory));
        } else {
            // check constraints (?)
            // loop through all the schema, checking that relations are sound
            restoredPMD = new PureMemoryDatabase(
                    this,
                    schema,
                    logger
            );
        }

        if (restoredPMD != null) {
            return restoredPMD;
        } else {
            logger.logImperative("Building new database at %s".formatted(dbDirectory));
            // if nothing is there, we build a new database
            // and add a clean set of directories
            final var pmd = PureMemoryDatabase.createEmptyDatabase(this);
            logger.logImperative("Created new PureMemoryDatabase");
            return pmd;
        }
    }


    public <T extends IndexableSerializable<?>> ChangeTrackingSet<T> readAndDeserialize(String dataName, Function<String, T> deserializer) {
        final var dataDirectory = new File("%s/%s".formatted(dbDirectory, dataName));

        if (! dataDirectory.exists()) {
            logger.logDebug(() -> "%s directory missing, creating empty set of data".formatted(dataName));
            return new ChangeTrackingSet<>();
        }

        final var data = new ChangeTrackingSet<T>();

        try {
            Files.walk(dataDirectory.toPath())
                    .filter (path -> Files.exists(path) && Files.isRegularFile(path))
                    .forEach(
                        x -> {
                            String fileContents = "";
                            try {
                                fileContents = Files.readString(x);
                            } catch (IOException e) {
                                // TODO: if we hit here, what then? test.
                            }
                            if (fileContents.isBlank()) {
                                logger.logDebug( () -> "%s file exists but empty, skipping".formatted(x.getFileName()));
                            } else {
                                data.addWithoutTracking(deserializer.apply(fileContents));
                            }
                        }
                );
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (data.isEmpty()) {
            data.nextIndex.set(1);
        } else {
            data.nextIndex.set(data.stream().max(Comparator.comparing(IndexableSerializable::getIndex)).map(x -> x.getIndex()).orElse(0) + 1);
        }
        return data;
    }


    // WORK ZONE - DANGER - KOTLIN BEFORE THIS LINE
    // WORK ZONE - DANGER - KOTLIN BEFORE THIS LINE
    // WORK ZONE - DANGER - KOTLIN BEFORE THIS LINE

}
