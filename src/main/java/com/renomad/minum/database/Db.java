package com.renomad.minum.database;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.queue.AbstractActionQueue;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.utils.FileUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.renomad.minum.utils.Invariants.*;

/**
 * a memory-based disk-persisted database class.
 * @param <T> the type of data we'll be persisting (must extend from {@link DbData}
 */
public final class Db<T extends DbData<?>> {

    /**
     * The suffix we will apply to each database file
     */
    static final String DATABASE_FILE_SUFFIX = ".ddps";
    private final T emptyInstance;

    // some locks we use for certain operations
    private final Lock loadDataLock = new ReentrantLock();
    private final Lock writeLock = new ReentrantLock();
    private final Lock deleteLock = new ReentrantLock();

    /**
     * This is a lock that goes around the code that modifies data in the
     * map, so that it is not possible for two different modifications to
     * interleave (that is, cause race conditions).
     */
    private final Lock modificationLock = new ReentrantLock();

    /**
     * The full path to the file that contains the most-recent index
     * for this data.  As we add new files, each gets its own index
     * value.  When we start the program, we use this to determine
     * where to start counting for new indexes.
     */
    private final Path fullPathForIndexFile;

    final AtomicLong index;

    private final Path dbDirectory;
    private final AbstractActionQueue actionQueue;
    private final ILogger logger;
    private final Map<Long, T> data;
    private final FileUtils fileUtils;
    private boolean hasLoadedData;

    /**
     * Constructs an in-memory disk-persisted database.
     * @param dbDirectory this uniquely names your database, and also sets the directory
     *                    name for this data.  The expected use case is to name this after
     *                    the data in question.  For example, "users", or "accounts".
     * @param context used to provide important state data to several components
     * @param instance an instance of the {@link DbData} object relevant for use in this database. Note
     *                 that each database (that is, each instance of this class), focuses on just one
     *                 data, which must be an implementation of {@link DbData}.
     */
    public Db(Path dbDirectory, Context context, T instance) {
        this.hasLoadedData = false;
        data = new ConcurrentHashMap<>();
        actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, context).initialize();
        this.logger = context.getLogger();
        this.dbDirectory = dbDirectory;
        this.fullPathForIndexFile = dbDirectory.resolve("index" + DATABASE_FILE_SUFFIX);
        this.emptyInstance = instance;
        this.fileUtils = new FileUtils(logger, context.getConstants());

        if (Files.exists(fullPathForIndexFile)) {
            long indexValue;
            try (var fileReader = new FileReader(fullPathForIndexFile.toFile(), StandardCharsets.UTF_8)) {
                try (BufferedReader br = new BufferedReader(fileReader)) {
                    String s = br.readLine();
                    if (s == null) throw new DbException("index file for " + dbDirectory + " returned null when reading a line from it");
                    mustBeFalse(s.isBlank(), "Unless something is terribly broken, we expect a numeric value here");
                    String trim = s.trim();
                    indexValue = Long.parseLong(trim);
                }
            } catch (Exception e) {
                throw new DbException("Exception while reading "+fullPathForIndexFile+" in Db constructor", e);
            }

            this.index = new AtomicLong(indexValue);

        } else {
            this.index = new AtomicLong(1);
        }

        actionQueue.enqueue("create directory" + dbDirectory, () -> fileUtils.makeDirectory(dbDirectory));
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
     * Write data to the database.  Use an index of 0 to store new data, and a positive
     * non-zero value to update data.
     * <p><em>
     *     Example of adding new data to the database:
     * </p></em>
     * {@snippet :
     *          final var newSalt = StringUtils.generateSecureRandomString(10);
     *          final var hashedPassword = CryptoUtils.createPasswordHash(newPassword, newSalt);
     *          final var newUser = new User(0L, newUsername, hashedPassword, newSalt);
     *          userDb.write(newUser);
     * }
     * <p><em>
     *     Example of updating data:
     * </p></em>
     * {@snippet :
     *         // write the updated salted password to the database
     *         final var updatedUser = new User(
     *                 user().getIndex(),
     *                 user().getUsername(),
     *                 hashedPassword,
     *                 newSalt);
     *         userDb.write(updatedUser);
     * }
     *
     * @param newData the data we are writing
     */
    public T write(T newData) {
        if (newData.getIndex() < 0) throw new DbException("Negative indexes are disallowed");
        writeLock.lock();
        try {
            // load data if needed
            if (!hasLoadedData) loadData();
            boolean newIndexCreated = false;

            modificationLock.lock();
            try {
                // *** deal with the in-memory portion ***

                // create a new index for the data, if needed
                if (newData.getIndex() == 0) {
                    newData.setIndex(index.getAndIncrement());
                    newIndexCreated = true;
                } else {
                    // if the data does not exist, and a positive non-zero
                    // index was provided, throw an exception.
                    boolean dataEntryExists = data.values().stream().anyMatch(x -> x.getIndex() == newData.getIndex());
                    if (! dataEntryExists) {
                        throw new DbException(
                                String.format("Positive indexes are only allowed when updating existing data. Index: %d",
                                        newData.getIndex()));
                    }
                }
                // if we got here, we are safe to proceed with putting the data into memory and disk
                logger.logTrace(() -> String.format("in thread %s, writing data %s", Thread.currentThread().getName(), newData));
                data.put(newData.getIndex(), newData);
            } finally {
                modificationLock.unlock();
            }

            // *** now handle the disk portion ***
            boolean finalNewIndexCreated = newIndexCreated;
            actionQueue.enqueue("persist data to disk", () -> {
                final Path fullPath = dbDirectory.resolve(newData.getIndex() + DATABASE_FILE_SUFFIX);
                logger.logTrace(() -> String.format("writing data to %s", fullPath));
                String serializedData = newData.serialize();
                mustBeFalse(serializedData == null || serializedData.isBlank(),
                        "the serialized form of data must not be blank. " +
                                "Is the serialization code written properly? Our datatype: " + emptyInstance);
                fileUtils.writeString(fullPath, serializedData);
                if (finalNewIndexCreated) {
                    fileUtils.writeString(fullPathForIndexFile, String.valueOf(newData.getIndex() + 1));
                }
            });

            // returning the data at this point is the most convenient
            // way users will have access to the new index of the data.
            return newData;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Delete data
     * <p><em>Example:</p></em>
     * {@snippet :
     *      userDb.delete(user);
     * }
     * @param dataToDelete the data we are serializing and writing
     */
    public void delete(T dataToDelete) {
        deleteLock.lock();
        try {
            // load data if needed
            if (!hasLoadedData) loadData();

            boolean hasResetIndex;
            long dataIndex;

            // deal with the in-memory portion
            modificationLock.lock();
            try {
                if (dataToDelete == null) {
                    throw new DbException("Db.delete was given a null value to delete");
                }
                dataIndex = dataToDelete.getIndex();
                if (!data.containsKey(dataIndex)) {
                    throw new DbException("no data was found with index of " + dataIndex);
                }
                logger.logTrace(() -> String.format("in thread %s, deleting data with index %d", Thread.currentThread().getName(), dataIndex));
                data.remove(dataIndex);

                // if all the data was just now deleted, we need to
                // reset the index back to 1

                if (data.isEmpty()) {
                    index.set(1);
                    hasResetIndex = true;
                } else {
                    hasResetIndex = false;
                }
            } finally {
                modificationLock.unlock();
            }

            // now handle the disk portion
            actionQueue.enqueue("delete data from disk", () -> {
                final Path fullPath = dbDirectory.resolve(dataIndex + DATABASE_FILE_SUFFIX);
                logger.logTrace(() -> String.format("deleting data at %s", fullPath));
                try {
                    mustBeTrue(fullPath.toFile().exists(), fullPath + " must already exist before deletion");
                    Files.delete(fullPath);
                    if (hasResetIndex) {
                        fileUtils.writeString(fullPathForIndexFile, String.valueOf(1));
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
     * Grabs all the data from disk and returns it as a list.  This
     * method is run by various programs when the system first loads.
     */
    void loadDataFromDisk() {
        if (! Files.exists(dbDirectory)) {
            logger.logDebug(() -> dbDirectory + " directory missing, adding nothing to the data list");
            return;
        }

        walkAndLoad(dbDirectory);
    }

    void walkAndLoad(Path dbDirectory) {
        // walk through all the files in this directory, collecting
        // all regular files (non-subdirectories) except for index.ddps
        try (final var pathStream = Files.walk(dbDirectory)) {
            final var listOfFiles = pathStream.filter(path ->
                        Files.isRegularFile(path) &&
                        !path.getFileName().toString().startsWith("index")
            ).toList();
            for (Path p : listOfFiles) {
                readAndDeserialize(p);
            }
        } catch (IOException e) {
            throw new DbException(e);
        }
    }

    /**
     * Carry out the process of reading data files into our in-memory structure
     * @param p the path of a particular file
     */
    void readAndDeserialize(Path p) throws IOException {
        Path fileName = p.getFileName();
        if (fileName == null) throw new DbException("At readAndDeserialize, path " + p + " returned a null filename");
        String filename = fileName.toString();
        int startOfSuffixIndex = filename.indexOf('.');
        if(startOfSuffixIndex == -1) {
            throw new DbException("the files must have a ddps suffix, like 1.ddps.  filename: " + filename);
        }
        String fileContents = Files.readString(p);
        if (fileContents.isBlank()) {
            logger.logDebug( () -> fileName + " file exists but empty, skipping");
        } else {
            try {
                @SuppressWarnings("unchecked")
                T deserializedData = (T) emptyInstance.deserialize(fileContents);
                mustBeTrue(deserializedData != null, "deserialization of " + emptyInstance +
                        " resulted in a null value. Was the serialization method implemented properly?");
                int fileNameIdentifier = Integer.parseInt(filename.substring(0, startOfSuffixIndex));
                mustBeTrue(deserializedData.getIndex() == fileNameIdentifier,
                        "The filename must correspond to the data's index. e.g. 1.ddps must have an id of 1");

                // put the data into the in-memory data structure
                data.put(deserializedData.getIndex(), deserializedData);
            } catch (Exception e) {
                throw new DbException("Failed to deserialize "+ p +" with data (\""+fileContents+"\"). Caused by: " + e);
            }
        }
    }

    /**
     * This method provides read capability for the values of a database.
     * <br>
     * The returned collection is a read-only view over the data, through {@link Collections#unmodifiableCollection(Collection)}
     *
     * <p><em>Example:</em></p>
     * {@snippet :
     * boolean doesUserAlreadyExist(String username) {
     *     return userDb.values().stream().anyMatch(x -> x.getUsername().equals(username));
     * }
     * }
     */
    public Collection<T> values() {
        // load data if needed
        if (!hasLoadedData) loadData();

        return Collections.unmodifiableCollection(data.values());
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
            loadDataCore(hasLoadedData, this::loadDataFromDisk);
            hasLoadedData = true;
        } finally {
            loadDataLock.unlock();
        }
    }

    static void loadDataCore(boolean hasLoadedData, Runnable loadDataFromDisk) {
        if (!hasLoadedData) {
            loadDataFromDisk.run();
        }
    }

}
