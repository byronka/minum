package minum.database;

import minum.Context;
import minum.logging.ILogger;
import minum.utils.ActionQueue;
import minum.utils.FileUtils;
import minum.utils.StacktraceUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static minum.utils.FileUtils.writeString;
import static minum.utils.Invariants.mustBeTrue;

/**
 * This allows us to run some disk-persistence operations more consistently
 * on any data that extends from {@link ISimpleDataType}
 * @param <T> the type of data we'll be persisting
 */
public class DatabaseDiskPersistenceSimpler<T> {

    /**
     * The suffix we will apply to each database file
     */
    static final String databaseFileSuffix = ".ddps";

    private final Path dbDirectory;
    private final ActionQueue actionQueue;
    private final ILogger logger;

    /**
     * Constructs a disk-persistence class well-suited for your data.
     * @param dbDirectory the directory for a particular domain (*not* the top-level
     *                     directory).  For example, if the top-level directory is
     *                     "db", and we're building this for a domain "foo", we
     *                     might expect to receive "db/foo" here.
     */
    public DatabaseDiskPersistenceSimpler(Path dbDirectory, Context context) {
        actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, context).initialize();
        this.logger = context.getLogger();
        this.dbDirectory = dbDirectory;
        actionQueue.enqueue("create directory" + dbDirectory, () -> {
            try {
                FileUtils.makeDirectory(logger, dbDirectory);
            } catch (IOException ex) {
                logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
            }
            return null;
        });

    }

    /**
     * This function will stop the minum.database persistence cleanly.
     * <p>
     * In order to do this, we need to wait for our threads
     * to finish their work.  In particular, we
     * have offloaded our file writes to [actionQueue], which
     * has an internal thread for serializing all actions
     * on our minum.database
     * </p>
     */
    public void stop() {
        actionQueue.stop();
    }

    /**
     * takes any serializable data and writes it to disk
     *
     * @param data the data we are writing
     */
    public void persistToDisk(ISimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        actionQueue.enqueue("persist data to disk", () -> {
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
    public void deleteOnDisk(ISimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        actionQueue.enqueue("delete data from disk", () -> {
            try {
                Files.delete(Path.of(fullPath));
            } catch (Exception ex) {
                logger.logAsyncError(() -> "failed to delete file "+fullPath+" during deleteOnDisk");
            }

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
        }


    public void updateOnDisk(ISimpleDataType<T> data) {
        final String fullPath = makeFullPathFromData(data);
        final var file = new File(fullPath);

        actionQueue.enqueue("update data on disk", () -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update "+file+" but it doesn't exist");
            writeString(fullPath, data.serialize());

            // needs to return null because this is a Callable, which allows us to
            // bubble exceptions back.
            return null;
        });
    }

    private String makeFullPathFromData(ISimpleDataType<T> data) {
        return dbDirectory + "/" + data.getIndex() + databaseFileSuffix;
    }

    /**
     * Grabs all the data from disk and returns it as a list.  This
     * method is run by various programs when the system first loads.
     */
    public List<T> readAndDeserialize(ISimpleDataType<T> instance) {
        if (! Files.exists(dbDirectory)) {
            logger.logDebug(() -> dbDirectory + " directory missing, creating empty list of data");
            return new ArrayList<>();
        }

        final var data = new ArrayList<T>();

        try (final var pathStream = Files.walk(dbDirectory)) {
            final var listOfFiles = pathStream.filter(path -> Files.exists(path) && Files.isRegularFile(path)).toList();
            for (Path p : listOfFiles) {
                String fileContents;
                fileContents = Files.readString(p);
                if (fileContents.isBlank()) {
                    logger.logDebug( () -> p.getFileName() + " file exists but empty, skipping");
                } else {
                    try {
                        data.add(instance.deserialize(fileContents));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to deserialize "+p+" with data (\""+fileContents+"\")");
                    }
                }
            }
        } catch (IOException e) { // if we fail to walk() the dbDirectory.  I don't even know how to test this.
            throw new RuntimeException(e);
        }
        return data;
    }

    /**
     * Calculate what the next index should be for the data.
     * <p>
     * The data we use in our application uses indexes (that is, just
     * a plain old number [of Long type]) to distinguish one from
     * another.  When we start the system, we need to calculate what
     * the next index will be (for example, on disk we might already
     * have data with indexes of 1, 2, and 3 - and therefore the next
     * index would be 4).
     * </p>
     * <p>
     * If there is no data in the collection, just return the number 1.
     * </p>
     */
    public long calculateNextIndex(Collection<? extends SimpleIndexed> data) {
        return data
                .stream()
                .max(Comparator.comparingLong(SimpleIndexed::getIndex))
                .map(SimpleIndexed::getIndex)
                .orElse(0L) + 1L;
    }

}
