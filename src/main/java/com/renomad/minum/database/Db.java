package com.renomad.minum.database;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.queue.AbstractActionQueue;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static com.renomad.minum.utils.Invariants.mustBeFalse;
import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * a memory-based disk-persisted database class.
 * @param <T> the type of data we'll be persisting (must extend from {@link DbData}
 */
public class Db<T extends DbData<?>> extends AbstractDb<T> {

    /**
     * The suffix we will apply to each database file
     */
    static final String DATABASE_FILE_SUFFIX = ".ddps";
    private final AbstractActionQueue actionQueue;

    /**
     * A lock around loading the data - so we can't find ourselves
     * in a situation where multiple threads are loading the data.
     */
    private final ReentrantLock loadDataLock = new ReentrantLock();

    /**
     * A lock around any changes to the data of the database, such as
     * creating, updating, or deleting, so that we don't encounter
     * scenarios where our in-memory and on-disk data goes out of sync.
     */
    private final ReentrantLock dataChangeLock = new ReentrantLock();

    /**
     * The full path to the file that contains the most-recent index
     * for this data.  As we add new files, each gets its own index
     * value.  When we start the program, we use this to determine
     * where to start counting for new indexes.
     */
    private final Path fullPathForIndexFile;

    volatile boolean hasLoadedData;

    /**
     * Constructs an in-memory disk-persisted database.
     * Loading of data from disk happens at the first invocation of any command
     * changing or requesting data, such as {@link #write(DbData)}, {@link #delete(DbData)},
     * or {@link #values()}.  See the private method loadData() for details.
     * @param dbDirectory this uniquely names your database, and also sets the directory
     *                    name for this data.  The expected use case is to name this after
     *                    the data in question.  For example, "users", or "accounts".
     * @param context used to provide important state data to several components
     * @param instance an instance of the {@link DbData} object relevant for use in this database. Note
     *                 that each database (that is, each instance of this class), focuses on just one
     *                 data, which must be an implementation of {@link DbData}.
     */
    public Db(Path dbDirectory, Context context, T instance) {
        this(dbDirectory, context, instance, new FileUtils(context.getLogger(), context.getConstants()));
    }

    Db(Path dbDirectory, Context context, T instance, IFileUtils fileUtils) {
        super(dbDirectory, context, instance, fileUtils);
        this.hasLoadedData = false;
        this.fullPathForIndexFile = dbDirectory.resolve("index" + DATABASE_FILE_SUFFIX);
        this.actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, context).initialize();

        if (fileUtils.exists(fullPathForIndexFile)) {
            try {
                long indexValue;
                String indexValueString = fileUtils.readString(fullPathForIndexFile);
                indexValue = Long.parseLong(indexValueString);
                this.index = new AtomicLong(indexValue);
            } catch (Exception ex) {
                throw new DbException("Error in Db constructor", ex);
            }

        } else {
            this.index = new AtomicLong(1);
        }

        actionQueue.enqueue("create directory" + dbDirectory, () -> fileUtils.makeDirectory(dbDirectory));
    }

    /**
     * Write data to the database.  Use an index of 0 to store new data, and a positive
     * non-zero value to update data.
     * <p><em>
     *     Example of adding new data to the database:
     * </em></p>
     * {@snippet :
     *          final var newSalt = StringUtils.generateSecureRandomString(10);
     *          final var hashedPassword = CryptoUtils.createPasswordHash(newPassword, newSalt);
     *          final var newUser = new User(0L, newUsername, hashedPassword, newSalt);
     *          userDb.write(newUser);
     * }
     * <p><em>
     *     Example of updating data:
     * </em></p>
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
     * @return the data with its new index assigned.
     */
    @Override
    public T write(T newData) {
        if (newData.getIndex() < 0) throw new DbException("Negative indexes are disallowed");
        // load data if needed
        if (!hasLoadedData) loadData();

        dataChangeLock.lock();
        try {
            boolean newElementCreated = processDataIndex(newData);
            writeToMemory(newData, newElementCreated);

            // *** now handle the disk portion ***
            actionQueue.enqueue("persist data to disk",
                    () -> writeToDisk(newData, dbDirectory, fileUtils, emptyInstance,
                            newElementCreated, fullPathForIndexFile, logger));

            // returning the data at this point is the most convenient
            // way users will have access to the new index of the data.
            return newData;
        } finally {
            dataChangeLock.unlock();
        }
    }

    static <T extends DbData<?>> void writeToDisk(T newData, Path dbDirectory, IFileUtils fileUtils, T emptyInstance,
                                                  boolean newItemCreated, Path fullPathForIndexFile, ILogger logger) {
        final Path fullPath = dbDirectory.resolve(newData.getIndex() + DATABASE_FILE_SUFFIX);
        logger.logTrace(() -> String.format("writing data to %s", fullPath));
        String serializedData = newData.serialize();
        mustBeFalse(serializedData == null || serializedData.isBlank(),
                "the serialized form of data must not be blank. " +
                        "Is the serialization code written properly? Our datatype: " + emptyInstance);
        try {
            fileUtils.writeString(fullPath, serializedData);

            if (newItemCreated) {
                fileUtils.writeString(fullPathForIndexFile, String.valueOf(newData.getIndex() + 1));
            }
        } catch (IOException e) {
            throw new DbException("Error in Db.writeToDisk", e);
        }
    }

    /**
     * Delete data
     * <p><em>Example:</em></p>
     * {@snippet :
     *      userDb.delete(user);
     * }
     * @param dataToDelete the data we are serializing and writing
     */
    @Override
    public void delete(T dataToDelete) {
        // load data if needed
        if (!hasLoadedData) loadData();

        dataChangeLock.lock();
        try {
            // deal with the in-memory portion
            deleteFromMemory(dataToDelete);

            // get the current index
            boolean shouldResetIndexOnDisk = values().isEmpty();

            actionQueue.enqueue("delete data from disk",
                    () -> deleteFromDisk(dataToDelete, dbDirectory, fileUtils,
                            shouldResetIndexOnDisk, fullPathForIndexFile, logger));
        } finally {
            dataChangeLock.unlock();
        }
    }

    static <T extends DbData<?>> void deleteFromDisk(T dataToDelete, Path dbDirectory, IFileUtils fileUtils,
                                                     boolean shouldResetIndexOnDisk, Path fullPathForIndexFile, ILogger logger) {
        final Path fullPath = dbDirectory.resolve(dataToDelete.getIndex() + DATABASE_FILE_SUFFIX);
        logger.logTrace(() -> String.format("deleting data at %s", fullPath));
        try {
            if (!fileUtils.exists(fullPath)) {
                throw new DbException(fullPath + " must already exist before deletion");
            }
            fileUtils.delete(fullPath);

            if (shouldResetIndexOnDisk) {
                fileUtils.writeString(fullPathForIndexFile, "1");
            }
        } catch (Exception ex) {
            throw new DbException("Error in Db.deleteFromDisk", ex);
        }
    }

    /**
     * Grabs all the data from disk and returns it as a list.  This
     * method is run by various programs when the system first loads.
     */
    private void loadDataFromDisk() throws IOException, ParseException {
        logger.logDebug(() -> "Loading data from disk. Db classic. Directory: " + dbDirectory);

        // check if the folder has content for a DbEngine2 database, meaning we
        // need to convert it back to the classic DB file structure.
        if (fileUtils.exists(dbDirectory.resolve("currentAppendLog"))) {
            new DbFileConverter(context, dbDirectory, fileUtils).convertFolderStructureToDbClassic();
        }

        if (! fileUtils.exists(dbDirectory)) {
            logger.logDebug(() -> dbDirectory + " directory missing, adding nothing to the data list");
            return;
        }

        walkAndLoad(dbDirectory);
    }

    void walkAndLoad(Path dbDirectory) throws IOException {
        // walk through all the files in this directory, collecting
        // all regular files (non-subdirectories) except for index.ddps
        try (final var pathStream = fileUtils.walk(dbDirectory)) {
            final var listOfFiles = pathStream.filter(path ->
                        fileUtils.isRegularFile(path) &&
                        !path.getFileName().toString().startsWith("index")
            ).toList();
            for (Path p : listOfFiles) {
                readAndDeserialize(p);
            }
        }
    }

    /**
     * Carry out the process of reading data files into our in-memory structure
     * @param p the path of a particular file
     */
    void readAndDeserialize(Path p) {
        Path fileName = p.getFileName();
        if (fileName == null) throw new DbException("At readAndDeserialize, path " + p + " returned a null filename");
        String filename = fileName.toString();
        int startOfSuffixIndex = filename.indexOf('.');
        if(startOfSuffixIndex == -1) {
            throw new DbException("the files must have a ddps suffix, like 1.ddps.  filename: " + filename);
        }
        String fileContents = null;
        try {
            fileContents = fileUtils.readString(p);
        } catch (IOException e) {
            throw new DbException("Error at Db.readAndDeserialize", e);
        }
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
                addToIndexes(deserializedData);

            } catch (Exception e) {
                throw new DbException("Failed to deserialize "+ p +" with data (\""+fileContents+"\"). Caused by: " + e);
            }
        }
    }

    @Override
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
    @Override
    public AbstractDb<T> loadData() {
        loadDataLock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
        try {
            if (!hasLoadedData) {
                loadDataFromDisk();
            }
            hasLoadedData = true;
            return this;
        } catch (Exception ex) {
            throw new DbException("Failed to load data from disk.", ex);
        } finally {
            loadDataLock.unlock();
        }
    }

    /**
     * Register an index in the database for higher performance data access.
     * <p>
     *     This command should be run immediately after database declaration,
     *     or more specifically, before any data is loaded from disk. Otherwise,
     *     it would be possible to skip indexing that data.
     * </p>
     * <br>
     * Example:
     *  {@snippet :
     *           final var myDatabase = context.getDb("photos", Photograph.EMPTY);
     *           myDatabase.registerIndex("url", photo -> photo.getUrl());
     *  }
     * @param indexName a string used to distinguish this index.  This string will be used again
     *                  when requesting data in a method like {@link #getIndexedData} or {@link #findExactlyOne}
     * @param keyObtainingFunction a function which obtains data from the data in this database, used
     *                             to partition the data into groups (potentially up to a 1-to-1 correspondence
     *                             between id and object)
     * @return the database instance if the registration succeeded
     * @throws DbException if the parameters are not entered properly, if the index has already
     * been registered, or if the data has already been loaded. It is necessary that
     * this is run immediately after declaring the database. To explain further: the data is not
     * actually loaded until the first time it is needed, such as running a write or delete, or
     * if the {@link #loadDataFromDisk()} method is run.  Creating an index map for the data that
     * is read from disk only occurs once, at data load time.  Thus, it is crucial that the
     * registerIndex command is run before any data is loaded.
     */
    @Override
    public AbstractDb<T> registerIndex(String indexName, Function<T, String> keyObtainingFunction) {
        if (hasLoadedData) {
            throw new DbException("This method must be run before the database loads data from disk.  Typically, " +
                    "it should be run immediately after the database is created.  See this method's documentation");
        }
        return super.registerIndex(indexName, keyObtainingFunction);
    }

    /**
     * Given the name of a registered index (see {@link #registerIndex(String, Function)}),
     * use the key to find the collection of data that matches it.
     * @param indexName the name of an index
     * @param key a string value that matches a partition calculated from the partition
     *            function provided to {@link #registerIndex(String, Function)}
     * @return a collection of data, an empty collection if nothing found
     */
    @Override
    public Collection<T> getIndexedData(String indexName, String key) {
        // load data if needed
        if (!hasLoadedData) loadData();
        return super.getIndexedData(indexName, key);
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
    @Override
    public void stop() {
        actionQueue.stop();
    }

    /**
     * Similar to {@link #stop()} but gives more control over how long
     * we'll wait before crashing it closed.  See {@link ActionQueue#stop(int, int)}
     */
    @Override
    public void stop(int count, int sleepTime) {
        actionQueue.stop(count, sleepTime);
    }

}
