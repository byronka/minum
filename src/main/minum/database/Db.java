package minum.database;

import minum.Context;
import minum.logging.ILogger;
import minum.utils.ActionQueue;
import minum.utils.FileUtils;
import minum.utils.StacktraceUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static minum.utils.FileUtils.writeString;
import static minum.utils.Invariants.mustBeTrue;

/**
 * This allows us to run some disk-persistence operations more consistently
 * on any data that extends from {@link DbData}
 * @param <T> the type of data we'll be persisting
 */
public class Db<T extends DbData<?>> {

    /**
     * The suffix we will apply to each database file
     */
    static final String databaseFileSuffix = ".ddps";
    private final T emptyInstance;

    // some locks we use for certain operations
    private final ReentrantLock loadDataLock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock updateLock = new ReentrantLock();
    private final ReentrantLock deleteLock = new ReentrantLock();

    /**
     * The full path to the file that contains the most-recent index
     * for this data.  As we add new files, each gets its own index
     * value.  When we start the program, we use this to determine
     * where to start counting for new indexes.
     */
    private final Path fullPathForIndexFile;

    final AtomicLong index;

    private final Path dbDirectory;
    private final ActionQueue actionQueue;
    private final ILogger logger;
    private final Map<Long, T> data;
    private boolean hasLoadedData;

    /**
     * Constructs a disk-persistence class well-suited for your data.
     * @param dbDirectory the directory for a particular domain (*not* the top-level
     *                     directory).  For example, if the top-level directory is
     *                     "db", and we're building this for a domain "foo", we
     *                     might expect to receive "db/foo" here.
     */
    public Db(Path dbDirectory, Context context, T instance) {
        this.hasLoadedData = false;
        data = new HashMap<>();
        actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, context).initialize();
        this.logger = context.getLogger();
        this.dbDirectory = dbDirectory;
        this.fullPathForIndexFile = dbDirectory.resolve("index" + databaseFileSuffix);
        this.emptyInstance = instance;

        if (Files.exists(fullPathForIndexFile)) {
            long indexValue;
            try (var fileReader = new FileReader(fullPathForIndexFile.toFile())) {
                String trim = new BufferedReader(fileReader).readLine().trim();
                indexValue = Long.parseLong(trim);
            } catch (IOException e) {
                logger.logDebug(() -> "Exception while reading file in DatabaseDiskPersistenceSimpler constructor: " + e);
                indexValue = 1;
            }

            this.index = new AtomicLong(indexValue);

        } else {
            this.index = new AtomicLong(1);
        }

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
    void stop() {
        actionQueue.stop();
    }

    /**
     * Similar to {@link #stop()} but gives more control over how long
     * we'll wait before crashing it closed.  See {@link ActionQueue#stop(int, int)}
     */
    void stop(int count, int sleepTime) {
        actionQueue.stop(count, sleepTime);
    }

    /**
     * writes new data to the in-memory data
     * and persists it to disk
     *
     * @param newData the data we are writing
     */
    public void write(T newData) {
        writeLock.lock();
        try {
            // load data if needed
            if (!hasLoadedData) loadData();

            // deal with the in-memory portion
            newData.setIndex(index.getAndIncrement());
            data.put(newData.getIndex(), newData);

            // now handle the disk portion
            final Path fullPath = dbDirectory.resolve(newData.getIndex() + databaseFileSuffix);
            actionQueue.enqueue("persist data to disk", () -> {
                mustBeTrue(!fullPath.toFile().exists(), fullPath + " must not already exist before persisting");
                writeString(fullPath, newData.serialize());
                writeString(fullPathForIndexFile, String.valueOf(newData.getIndex() + 1));
            });
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Deletes a piece of data from the in-memory data structure
     * and delete it from the disk
     *
     * @param dataToDelete the data we are serializing and writing
     */
    public void delete(T dataToDelete) {
        deleteLock.lock();
        try {
            // load data if needed
            if (!hasLoadedData) loadData();

            // deal with the in-memory portion
            long dataIndex = dataToDelete.getIndex();
            if (!data.containsKey(dataIndex)) {
                throw new RuntimeException("no data was found with id of " + dataIndex);
            }
            data.remove(dataIndex);

            // if all the data was just now deleted, we need to
            // reset the index back to 1
            boolean hasResetIndex;
            if (data.isEmpty()) {
                index.set(1);
                hasResetIndex = true;
            } else {
                hasResetIndex = false;
            }

            // now handle the disk portion
            actionQueue.enqueue("delete data from disk", () -> {
                final Path fullPath = dbDirectory.resolve(dataIndex + databaseFileSuffix);
                try {
                    mustBeTrue(fullPath.toFile().exists(), fullPath + " must already exist before deletion");
                    Files.delete(fullPath);
                    if (hasResetIndex) {
                        writeString(fullPathForIndexFile, String.valueOf(1));
                    }
                } catch (Exception ex) {
                    logger.logAsyncError(() -> "failed to delete file " + fullPath + " during deleteOnDisk. Exception: " + ex);
                }
            });
        } finally {
            deleteLock.unlock();
        }
    }

    /**
     * updates an element in the in-memory data structure by
     * replacing the element having the same id
     * if the data to update is not found, throw an exception
     * update the data on disk with this id.
     */
    public void update(T dataUpdate) {
        updateLock.lock();
        try {
            // load data if needed
            if (!hasLoadedData) loadData();

            // deal with the in-memory portion
            long dataIndex = dataUpdate.getIndex();
            if (!data.containsKey(dataIndex)) {
                throw new RuntimeException("no data was found with id of " + dataIndex);
            }
            data.put(dataIndex, dataUpdate);

            // now handle the disk portion
            actionQueue.enqueue("update data on disk", () -> {
                final Path fullPath = dbDirectory.resolve(dataIndex + databaseFileSuffix);
                // if the file isn't already there, throw an exception
                mustBeTrue(fullPath.toFile().exists(), fullPath + " must already exist during updates");
                writeString(fullPath, dataUpdate.serialize());
            });
        } finally {
            updateLock.unlock();
        }
    }

    /**
     * Grabs all the data from disk and returns it as a list.  This
     * method is run by various programs when the system first loads.
     */
    void loadDataFromDisk() {
        if (! Files.exists(dbDirectory)) {
            logger.logDebug(() -> dbDirectory + " directory missing, adding nothing to the data list");
            return;
        }

        try (final var pathStream = Files.walk(dbDirectory)) {
            final var listOfFiles = pathStream.filter(path -> {
                return Files.exists(path) &&
                        Files.isRegularFile(path) &&
                        !path.getFileName().toString().startsWith("index");
            }).toList();
            for (Path p : listOfFiles) {
                readAndDeserialize(p);
            }
        } catch (IOException e) { // if we fail to walk() the dbDirectory.  I don't even know how to test this.
            throw new RuntimeException(e);
        }
    }

    /**
     * Carry out the process of reading data files into our in-memory structure
     * @param p the path of a particular file
     */
    void readAndDeserialize(Path p) throws IOException {
        String fileContents;
        fileContents = Files.readString(p);
        if (fileContents.isBlank()) {
            logger.logDebug( () -> p.getFileName() + " file exists but empty, skipping");
        } else {
            try {
                @SuppressWarnings("unchecked")
                T deserializedData = (T) emptyInstance.deserialize(fileContents);
                data.put(deserializedData.getIndex(), deserializedData);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize "+ p +" with data (\""+fileContents+"\")");
            }
        }
    }

    /**
     * The primary way to analyze this data.
     *
     * @return a {@link Collection} view of the data
     */
    public Collection<T> values() {
        // load data if needed
        if (!hasLoadedData) loadData();

        return data.values();
    }

    /**
     * This is what loads the data from disk the
     * first time someone needs it.  Because it is
     * locked, only one thread can enter at
     * a time.  The first one in will load the data,
     * and the second will encounter a branch which skips loading.
     */
    private void loadData() {
        loadDataLock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
        try {
            if (!hasLoadedData) {
                loadDataFromDisk();
                hasLoadedData = true;
            }
        } finally {
            loadDataLock.unlock();
        }
    }

}
