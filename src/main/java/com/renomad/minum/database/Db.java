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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

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

    private final Lock loadDataLock = new ReentrantLock();

    /**
     * The full path to the file that contains the most-recent index
     * for this data.  As we add new files, each gets its own index
     * value.  When we start the program, we use this to determine
     * where to start counting for new indexes.
     */
    private final Path fullPathForIndexFile;

    final AtomicLong index;

    /**
     * An in-memory representation of the value of the current max index
     * that we store in index.ddps, in memory, so we can compare whether
     * we need to update the disk without checking the disk so often.
     */
    private long maxIndexOnDisk;

    private final Path dbDirectory;
    private final AbstractActionQueue actionQueue;
    private final ILogger logger;
    private final Map<Long, T> data;
    private final FileUtils fileUtils;
    private boolean hasLoadedData;

    // components for registered indexes (for faster read performance)
    private final Map<String, Map<String, Set<T>>> registeredIndexes;
    private final Map<String, Function<T, String>> partitioningMap;

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
        this.hasLoadedData = false;
        data = new ConcurrentHashMap<>();
        actionQueue = new ActionQueue("DatabaseWriter " + dbDirectory, context).initialize();
        this.logger = context.getLogger();
        this.dbDirectory = dbDirectory;
        this.fullPathForIndexFile = dbDirectory.resolve("index" + DATABASE_FILE_SUFFIX);
        this.emptyInstance = instance;
        this.fileUtils = new FileUtils(logger, context.getConstants());
        this.registeredIndexes = new HashMap<>();
        this.partitioningMap = new HashMap<>();

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
        // load data if needed
        if (!hasLoadedData) loadData();

        writeToMemory(newData);

        // *** now handle the disk portion ***
        actionQueue.enqueue("persist data to disk", () -> writeToDisk(newData));

        // returning the data at this point is the most convenient
        // way users will have access to the new index of the data.
        return newData;
    }

    /**
     * Write database data into memory
     * @param newData the new data may be totally new or an update
     */
    private void writeToMemory(T newData) {
        // *** deal with the in-memory portion ***
        boolean newElementCreated = false;
        // create a new index for the data, if needed
        if (newData.getIndex() == 0) {
            newData.setIndex(index.getAndIncrement());
            newElementCreated = true;
        } else {
            // if the data does not exist, and a positive non-zero
            // index was provided, throw an exception.
            boolean dataEntryExists = data.values().stream().anyMatch(x -> x.getIndex() == newData.getIndex());
            if (!dataEntryExists) {
                throw new DbException(
                        String.format("Positive indexes are only allowed when updating existing data. Index: %d",
                                newData.getIndex()));
            }
        }
        // if we got here, we are safe to proceed with putting the data into memory and disk
        logger.logTrace(() -> String.format("in thread %s, writing data %s", Thread.currentThread().getName(), newData));
        T oldData = data.put(newData.getIndex(), newData);

        // handle the indexes differently depending on whether this is a create or delete
        if (newElementCreated) {
            addToIndexes(newData);
        } else {
            removeFromIndexes(oldData);
            addToIndexes(newData);
        }
    }

    private void writeToDisk(T newData) {
        final Path fullPath = dbDirectory.resolve(newData.getIndex() + DATABASE_FILE_SUFFIX);
        logger.logTrace(() -> String.format("writing data to %s", fullPath));
        String serializedData = newData.serialize();
        mustBeFalse(serializedData == null || serializedData.isBlank(),
                "the serialized form of data must not be blank. " +
                        "Is the serialization code written properly? Our datatype: " + emptyInstance);
        fileUtils.writeString(fullPath, serializedData);
        if (maxIndexOnDisk < index.get()) {
            maxIndexOnDisk = index.get();
            fileUtils.writeString(fullPathForIndexFile, String.valueOf(maxIndexOnDisk));
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
        // load data if needed
        if (!hasLoadedData) loadData();

        // deal with the in-memory portion
        deleteFromMemory(dataToDelete);

        // now handle the disk portion
        actionQueue.enqueue("delete data from disk", () -> deleteFromDisk(dataToDelete.getIndex()));
    }

    private void deleteFromDisk(long dataIndexToDelete) {
        final Path fullPath = dbDirectory.resolve(dataIndexToDelete + DATABASE_FILE_SUFFIX);
        logger.logTrace(() -> String.format("deleting data at %s", fullPath));
        try {
            if (!fullPath.toFile().exists()) {
                throw new DbException(fullPath + " must already exist before deletion");
            }
            Files.delete(fullPath);
            if (maxIndexOnDisk > index.get()) {
                maxIndexOnDisk = index.get();
                fileUtils.writeString(fullPathForIndexFile, String.valueOf(maxIndexOnDisk));

            }
        } catch (Exception ex) {
            logger.logAsyncError(() -> "failed to delete file " + fullPath + " during deleteOnDisk. Exception: " + ex);
        }
    }

    private void deleteFromMemory(T dataToDelete) {
        long dataIndex;
        if (dataToDelete == null) {
            throw new DbException("Invalid to be given a null value to delete");
        }
        dataIndex = dataToDelete.getIndex();
        if (!data.containsKey(dataIndex)) {
            throw new DbException("no data was found with index of " + dataIndex);
        }
        long finalDataIndex = dataIndex;
        logger.logTrace(() -> String.format("in thread %s, deleting data with index %d", Thread.currentThread().getName(), finalDataIndex));
        data.remove(dataIndex);
        removeFromIndexes(dataToDelete);
        // if all the data was just now deleted, we need to
        // reset the index back to 1

        if (data.isEmpty()) {
            index.set(1);
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
                addToIndexes(deserializedData);

            } catch (Exception e) {
                throw new DbException("Failed to deserialize "+ p +" with data (\""+fileContents+"\"). Caused by: " + e);
            }
        }
    }

    private void addToIndexes(T dbData) {
        // add the data to registered indexes.  For each of the registered indexes,
        // get the stored function to obtain a string value which helps divide
        // the overall data into partitions.
        for (var entry : partitioningMap.entrySet()) {
            // a function provided by the user to obtain an index-key: a unique or semi-unique
            // value to help partition / index the data
            Function<T, String> indexStringFunction = entry.getValue();
            String propertyAsString = indexStringFunction.apply(dbData);
            Map<String, Set<T>> stringIndexMap = registeredIndexes.get(entry.getKey());
            synchronized (this) {
                stringIndexMap.computeIfAbsent(propertyAsString, k -> new HashSet<>());
            }
            // if the index-key provides a 1-to-1 mapping to items, like UUIDs, then
            // each value will have only one item in the collection.  In other cases,
            // like when partitioning the data into multiple groups, there could easily
            // be many items per index value.
            Set<T> dataSet = stringIndexMap.get(propertyAsString);
            dataSet.add(dbData);
        }
    }

    /**
     * Run when an item is deleted from the database
     */
    private void removeFromIndexes(T dbData) {
        for (var entry : partitioningMap.entrySet()) {
            // a function provided by the user to obtain an index-key: a unique or semi-unique
            // value to help partition / index the data
            Function<T, String> indexStringFunction = entry.getValue();
            String propertyAsString = indexStringFunction.apply(dbData);
            Map<String, Set<T>> stringIndexMap = registeredIndexes.get(entry.getKey());
            synchronized (this) {
                stringIndexMap.get(propertyAsString).removeIf(x -> x.getIndex() == dbData.getIndex());

                // in certain cases, we're removing one of the items that is indexed but
                // there are more left.  If there's nothing left though, we'll remove the mapping.
                if (stringIndexMap.get(propertyAsString).isEmpty()) {
                    stringIndexMap.remove(propertyAsString);
                }
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

    /**
     * Register an index in the database for higher performance data access
     * @param indexName a string used to distinguish this index.  This string will be used again
     *                  when requesting data in a method like {@link #getIndexedData} or {@link #findExactlyOne}
     * @param keyObtainingFunction a function which obtains data from the data in this database, used
     *                             to partition the data into groups (potentially up to a 1-to-1 correspondence
     *                             between id and object)
     * @return true if the registration succeeded
     */
    public boolean registerIndex(String indexName, Function<T, String> keyObtainingFunction) {
        if (keyObtainingFunction == null) {
            throw new DbException("When registering an index, the partitioning algorithm must not be null");
        }
        if (indexName == null || indexName.isBlank()) {
            throw new DbException("When registering an index, value must be a non-empty string");
        }
        if (registeredIndexes.containsKey(indexName)) {
            throw new DbException("It is forbidden to register the same index more than once.  Duplicate index: \""+indexName+"\"");
        }
        HashMap<String, Set<T>> stringCollectionHashMap = new HashMap<>();
        registeredIndexes.put(indexName, stringCollectionHashMap);
        partitioningMap.put(indexName, keyObtainingFunction);
        return true;
    }

    /**
     * Given the name of a registered index (see {@link #registerIndex(String, Function)}),
     * use the key to find the collection of data that matches it.
     * @param indexName the name of an index
     * @param key a string value that matches a partition calculated from the partition
     *            function provided to {@link #registerIndex(String, Function)}
     * @return a collection of data, an empty collection if nothing found
     */
    public Collection<T> getIndexedData(String indexName, String key) {
        if (!registeredIndexes.containsKey(indexName)) {
            throw new DbException("There is no index registered on the database Db<"+this.emptyInstance.getClass().getSimpleName()+"> with a name of \""+indexName+"\"");
        }
        Set<T> values = registeredIndexes.get(indexName).get(key);
        // return an empty set rather than null
        return Objects.requireNonNullElseGet(values, Set::of);
    }

    /**
     * Get a set of the currently-registered indexes on this database, useful
     * for debugging.
     */
    public Set<String> getSetOfIndexes() {
        return partitioningMap.keySet();
    }

    /**
     * A utility to find exactly one item from the database.
     * <br>
     * This utility will search the indexes for a particular data by
     * indexName and indexKey.  If not found, it will return null. If
     * found, it will be returned. If more than one are found, an exception
     * will be thrown.  Use this tool when the data has been uniquely
     * indexed, like for example when setting a unique identifier into
     * each data.
     * @param indexName the name of the index, an arbitrary value set by the
     *                  user to help distinguish among potentially many indexes
     *                  set on this data
     * @param indexKey the key for this particular value, such as a UUID or a name
     *                 or any other way to partition the data
     * @see #findExactlyOne(String, String, Callable)
     */
    public T findExactlyOne(String indexName, String indexKey) {
        return findExactlyOne(indexName, indexKey, () -> null);
    }

    /**
     * Find one item, with an alternate value if null
     * <br>
     * This utility will search the indexes for a particular data by
     * indexName and indexKey.  If not found, it will return null. If
     * found, it will be returned. If more than one are found, an exception
     * will be thrown.  Use this tool when the data has been uniquely
     * indexed, like for example when setting a unique identifier into
     * each data.
     * @param indexName the name of the index, an arbitrary value set by the
     *                  user to help distinguish among potentially many indexes
     *                  set on this data
     * @param indexKey the key for this particular value, such as a UUID or a name
     *                 or any other way to partition the data
     * @param alternate a functional interface that will be run if the result would
     *                  have been null, useful for situations where you don't want
     *                  the output to be null when nothing is found.
     * @see #findExactlyOne(String, String)
     */
    public T findExactlyOne(String indexName, String indexKey, Callable<T> alternate) {
        Collection<T> indexedData = getIndexedData(indexName, indexKey);
        if (indexedData.isEmpty()) {
            try {
                return alternate.call();
            } catch (Exception ex) {
                throw new DbException(ex);
            }
        } else if (indexedData.size() == 1) {
            return indexedData.stream().findFirst().orElseThrow();
        } else {
            throw new DbException("More than one item found when searching database Db<%s> on index \"%s\" with key %s"
                    .formatted(emptyInstance.getClass().getSimpleName(), indexName, indexKey));
        }
    }
}
