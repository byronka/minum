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
import java.util.stream.Stream;

import static minum.utils.FileUtils.writeString;
import static minum.utils.Invariants.mustBeTrue;

/**
 * This allows us to run some disk-persistence operations more consistently
 * on any data that extends from {@link ISimpleDataType}
 * @param <T> the type of data we'll be persisting
 */
public class AlternateDatabaseDiskPersistenceSimpler<T> {

    /**
     * The suffix we will apply to each database file
     */
    static final String databaseFileSuffix = ".ddps";


    /**
     * The full path to the file that contains the most-recent index
     * for this data.  As we add new files, each gets its own index
     * value.  When we start the program, we use this to determine
     * where to start counting for new indexes.
     */
    private final String fullPathForIndexFile;

    private final Path dbDirectory;
    private final ActionQueue actionQueue;
    private final ILogger logger;
    private final List<ISimpleDataType<T>> data;

    /**
     * Constructs a disk-persistence class well-suited for your data.
     * @param dbDirectory the directory for a particular domain (*not* the top-level
     *                     directory).  For example, if the top-level directory is
     *                     "db", and we're building this for a domain "foo", we
     *                     might expect to receive "db/foo" here.
     */
    public AlternateDatabaseDiskPersistenceSimpler(Path dbDirectory, Context context) {
        this.data = new ArrayList<>();
        actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, context).initialize();
        this.logger = context.getLogger();
        this.dbDirectory = dbDirectory;
        this.fullPathForIndexFile = dbDirectory + "/index" + databaseFileSuffix;
        actionQueue.enqueue("create directory" + dbDirectory, () -> {
            try {
                FileUtils.makeDirectory(logger, dbDirectory);
            } catch (IOException ex) {
                logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
            }
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
     * Similar to {@link #stop()} but gives more control over how long
     * we'll wait before crashing it closed.  See {@link ActionQueue#stop(int, int)}
     */
    public void stop(int count, int sleepTime) {
        actionQueue.stop(count, sleepTime);
    }

    /**
     * takes any serializable data and writes it to disk
     *
     * @param newData the data we are writing
     */
    public void persistToDisk(ISimpleDataType<T> newData) {
        // deal with the in-memory portion
        data.add((int) newData.getIndex(), newData);

        // now handle the disk portion
        final String fullPath = makeFullPathFromData(newData);
        final String indexPath = makeFullPathForIndexFile(newData);
        actionQueue.enqueue("persist data to disk", () -> {
            writeString(fullPath, newData.serialize());
            writeString(indexPath, String.valueOf(newData.getIndex()));
        });
    }

    /**
     * Deletes a piece of data from the disk
     *
     * @param dataToDelete the data we are serializing and writing
     * @return true if this list contained the specified element (or
     * equivalently, if this list changed as a result of the call).
     */
    public boolean deleteOnDisk(ISimpleDataType<T> dataToDelete) {
        // deal with the in-memory portion
        boolean result = data.remove(dataToDelete.getIndex());

        // now handle the disk portion
        final String fullPath = makeFullPathFromData(dataToDelete);
        actionQueue.enqueue("delete data from disk", () -> {
            try {
                Files.delete(Path.of(fullPath));
            } catch (Exception ex) {
                logger.logAsyncError(() -> "failed to delete file "+fullPath+" during deleteOnDisk");
            }
        });
        return result;
    }


    /**
     * @return the element previously at the specified position
     */
    public ISimpleDataType<T> updateOnDisk(ISimpleDataType<T> dataUpdate) {
        // deal with the in-memory portion
        ISimpleDataType<T> result = data.set((int) dataUpdate.getIndex(), dataUpdate);

        // now handle the disk portion
        final String fullPath = makeFullPathFromData(dataUpdate);
        final var file = new File(fullPath);

        actionQueue.enqueue("update data on disk", () -> {
            // if the file isn't already there, throw an exception
            mustBeTrue(file.exists(), "we were asked to update "+file+" but it doesn't exist");
            writeString(fullPath, dataUpdate.serialize());
        });
        return result;
    }

    /**
     * The full path to the files of this data
     */
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

        try (final var pathStream = Files.walk(dbDirectory)) {
            final var listOfFiles = pathStream.filter(path -> Files.exists(path) && Files.isRegularFile(path)).toList();
            for (Path p : listOfFiles) {
                extracted(instance, p);
            }
        } catch (IOException e) { // if we fail to walk() the dbDirectory.  I don't even know how to test this.
            throw new RuntimeException(e);
        }
        return data;
    }

    private T extracted(ISimpleDataType<T> instance, Path p) throws IOException {
        String fileContents;
        fileContents = Files.readString(p);
        if (fileContents.isBlank()) {
            logger.logDebug( () -> p.getFileName() + " file exists but empty, skipping");
        } else {
            try {
                return instance.deserialize(fileContents);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize "+ p +" with data (\""+fileContents+"\")");
            }
        }
        return null;
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
    public static long calculateNextIndex(Collection<? extends SimpleIndexed> data) {
        return data
                .stream()
                .max(Comparator.comparingLong(SimpleIndexed::getIndex))
                .map(SimpleIndexed::getIndex)
                .orElse(0L) + 1L;
    }

    public Stream<T> stream() {
        return data.stream().map(x -> (T)x);
    }

    /**
     * @return the latest index used for this data.
     */
    public long getLatestIndex() {
        return Long.parseLong(FileUtils.readFile(fullPathForIndexFile));
    }
}
