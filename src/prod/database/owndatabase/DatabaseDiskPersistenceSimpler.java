package database.owndatabase;

import logging.ILogger;
import utils.ActionQueue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static utils.FileUtils.writeString;
import static utils.Invariants.mustBeTrue;

/**
 * This allows us to run some disk-persistence operations more consistently
 * on any data that extends from {@link SimpleDataType}
 * @param <T> the type of data we'll be persisting
 */
public class DatabaseDiskPersistenceSimpler<T> {

    public static final String databaseFileSuffix = ".db";

    private final Path dbDirectory;
    private final ActionQueue actionQueue;
    private final ILogger logger;

    /**
     * Constructs a disk-persistence class well-suited for your data.
     *
     * There is a bit of subtlety to its use, see documentation on the params.
     * @param dbDirectory the directory where we will store this data, relative to
     *                    the location from which we run the application.  Recommend that
     *                    you use some consistency, and consider hierarchy.  For example,
     *                    you might want to store all your data at db/foo - foo being the
     *                    name of the type of data we're storing, and db being a place to
     *                    collect together all your directories.
     *
     *                    If you're consistent enough, you will have a top-level directory
     *                    like "db", with a bunch of sub-directories corresponding to the
     *                    types of data.  But really there is a lot of flexibility, you
     *                    can do it another way if you want.
     * @param executorService The executorService is our interface to the system which we
     *                        use for parallel processing.  We hand this sucker off to
     *                        an internal {@link ActionQueue} which handles all these
     *                        operations asynchronously, so that data is *eventually*
     *                        written to disk.
     */
    public DatabaseDiskPersistenceSimpler(String dbDirectory, ExecutorService executorService, ILogger logger) {
        this.dbDirectory = Path.of(dbDirectory);
        actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, executorService).initialize();
        this.logger = logger;

        actionQueue.enqueue(() -> {
            try {
                if (!Files.exists(this.dbDirectory)) {
                    Files.createDirectories(this.dbDirectory);
                }
            } catch (Exception ex) {
                logger.logDebug(() -> "failed to create directory " + this.dbDirectory);
                throw new RuntimeException(ex);
            }
            return null;
        });

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

    public ActionQueue getActionQueue() {
        return this.actionQueue;
    }

    /**
     * takes any serializable data and writes it to disk
     *
     * @param data the data we are writing
     */
    public void persistToDisk(SimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);

        actionQueue.enqueue(() -> {
            writeString(fullPath, data.serialize());

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
    }

    /**
     * Deletes a piece of data from the disk
     *
     * @param data the data we are serializing and writing
     */
    public void deleteOnDisk(SimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        actionQueue.enqueue(() -> {
            try {
                Files.delete(Path.of(fullPath));
            } catch (Exception ex) {
                logger.logDebug(() -> "failed to delete file "+fullPath);
                throw new RuntimeException(ex);
            }

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
        }


    public void updateOnDisk(SimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        final var file = new File(fullPath);

        actionQueue.enqueue(() -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update "+file+" but it doesn't exist");
            writeString(fullPath, data.serialize());

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
    }

    private String makeFullPathFromData(SimpleDataType<T> data) {
        return dbDirectory + "/" + data.getIndex() + databaseFileSuffix;
    }

    public List<T> readAndDeserialize(SimpleDataType<T> instance) {
        if (! Files.exists(dbDirectory)) {
            logger.logDebug(() -> dbDirectory + " directory missing, creating empty list of data");
            return Collections.emptyList();
        }

        final var data = new ArrayList<T>();

        try {
            final var listOfFiles = Files.walk(dbDirectory)
                    .filter(path -> Files.exists(path) && Files.isRegularFile(path)).toList();
            for (Path p : listOfFiles) {
                String fileContents;
                try {
                    fileContents = Files.readString(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (fileContents.isBlank()) {
                    logger.logDebug( () -> p.getFileName() + " file exists but empty, skipping");
                } else {
                    try {
                        data.add(instance.deserialize(fileContents));
                    } catch (DatabaseDiskPersistenceSimpler.DeserializationException e) {
                        throw new RuntimeException("Failed to deserialize "+p+" with data ("+fileContents+")");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }


    private static class DeserializationException extends RuntimeException {
        final String serializedData;

        public DeserializationException(String serializedData) {
            this.serializedData = serializedData;
        }
    }

}
