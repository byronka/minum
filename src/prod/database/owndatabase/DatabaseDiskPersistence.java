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
                    // TODO this section seems unsophisticated.  Investigate.
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
        final var fullPath = "%s/%s/%s%s".formatted(dbDirectory, subDirectory, item.getIndex(), databaseFileSuffix);
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
        final var fullPath = "%s/%s/%s%s".formatted(dbDirectory, subDirectory, item.getIndex(), databaseFileSuffix);
        final var file = new File(fullPath);

        actionQueue.enqueue(() -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update %s but it doesn't exist".formatted(file));
            writeString(fullPath, item.serialize());
        });
    }


    /**
     * Converts the data in a file to a strongly typed value
     * @param serialized the string that is found in a file which represents the serialized form of a value
     * @param converter a function which converts the serialized form to the type of data
     * @param serializationKeys a list of {@link SerializationKeys} representing the key values - the data is stored in key-value pairs when serialized
     * @param <T> the type of data to be deserialized
     * @throws DeserializationException this must be handled - if there is bad data, we will throw this with samples of the bad data to be examined in the calling method.
     */
    public static <T extends IndexableSerializable<?>> T deserialize(String serialized, Function<Map<SerializationKeys, String>, T> converter, List<SerializationKeys> serializationKeys){
        final var matcher = DatabaseDiskPersistence.serializedStringRegex.matcher(serialized);
        final var myMap = new HashMap<SerializationKeys, String>();
        while(matcher.find()) {
            final var keys = serializationKeys.stream().filter(x -> x.getKeyString().equals(matcher.group(1))).toList();
            mustBeTrue(keys.size() == 1, "There should only be one key found");
            myMap.put(keys.get(0), decode(matcher.group(2)));
        }
        if (myMap.size() == 0) throw new DeserializationException(serialized);
        return converter.apply(myMap);
    }

    public <T extends IndexableSerializable<?>> ChangeTrackingSet<T> readAndDeserialize(String dataName, Function<String, T> deserializer) {
        final var dataDirectory = new File("%s/%s".formatted(dbDirectory, dataName));

        if (! dataDirectory.exists()) {
            logger.logDebug(() -> "%s directory missing, creating empty set of data".formatted(dataName));
            return new ChangeTrackingSet<>();
        }

        final var data = new ChangeTrackingSet<T>();

        try {
            final var listOfPaths = Files.walk(dataDirectory.toPath())
                    .filter(path -> Files.exists(path) && Files.isRegularFile(path)).toList();
            for (Path p : listOfPaths) {
                String fileContents = "";
                try {
                    fileContents = Files.readString(p);
                } catch (IOException e) {
                    // TODO: if we hit here, what then? test.
                }
                if (fileContents.isBlank()) {
                    logger.logDebug( () -> "%s file exists but empty, skipping".formatted(p.getFileName()));
                } else {
                    try {
                        data.addWithoutTracking(deserializer.apply(fileContents));
                    } catch (DeserializationException e) {
                        throw new RuntimeException("Failed to deserialize %s with data (%s)".formatted(p, fileContents));
                    }
                }
            }
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

    public <T extends IndexableSerializable<T>> void updateSchema(Map<String, ChangeTrackingSet<?>> schema, T dataType) {
        schema.put(dataType.getDataName(), readAndDeserialize(dataType.getDataName(), dataType::deserialize));
    }

    private static class DeserializationException extends RuntimeException {
        final String serializedData;

        public DeserializationException(String serializedData) {
            this.serializedData = serializedData;
        }
    }
}
