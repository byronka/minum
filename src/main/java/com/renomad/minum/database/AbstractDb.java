package com.renomad.minum.database;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The abstract database class is a representation of the essential capabilities of
 * a Minum database.
 * <p>
 *     There are two kinds of database provided, which only differ in how they
 *     store data on disk.  The "classic" kind, {@link Db}, stores each piece of
 *     data in its own file.  This is the simplest approach.
 * </p>
 * <p>
 *     However, for significant speed gains, the new {@link DbEngine2} will
 *     store each change as an append to a file, and will consolidate the on-disk
 *     data occasionally, and on start.  That way is thousands of times faster
 *     to write to disk and to read from disk at startup.
 * </p>
 * @param <T> This is the type of data, which is always an implementation of
 *           the {@link DbData} class.  See the code of {@link com.renomad.minum.security.Inmate}
 *           for an example of how this should look.
 */
public abstract class AbstractDb<T extends DbData<?>> {

    /**
     * The directory of the database on disk
     */
    protected final Path dbDirectory;

    /**
     * An empty instance of the type of data stored by this
     * database, used for better handling of generics.
     */
    protected final T emptyInstance;

    /**
     * Used for handling some file utilities in the database like creating directories
     */
    protected final FileUtils fileUtils;

    /**
     * Holds some system-wide information that is beneficial for components of the database
     */
    protected final Context context;

    /**
     * Used for providing logging throughout the database
     */
    protected final ILogger logger;

    /**
     * The internal data structure of the database that resides in memory.  The beating heart
     * of the database while it runs.
     */
    protected final Map<Long, T> data;

    /**
     * The current index, used when creating new data items.  Each item has its own
     * index value, this is where it is tracked.
     */
    protected AtomicLong index;

    // components for registered indexes (for faster read performance)

    /**
     * This data structure is a nested map used for providing indexed data search.
     * <br>
     * The outer map is between the name of the index and the inner map.
     * <br>
     * The inner map is between strings and sets of items related to that string.
     */
    protected final Map<String, Map<String, Set<T>>> registeredIndexes;

    /**
     * This map holds the functions that are registered to indexes, which are used
     * to construct the mappings between string values and items in the database.
     */
    protected final Map<String, Function<T, String>> partitioningMap;

    protected AbstractDb(Path dbDirectory, Context context, T instance) {
        this.dbDirectory = dbDirectory;
        this.context = context;
        this.emptyInstance = instance;
        this.data = new ConcurrentHashMap<>();
        this.logger = context.getLogger();
        this.registeredIndexes = new HashMap<>();
        this.partitioningMap = new HashMap<>();
        this.fileUtils = new FileUtils(logger, context.getConstants());
    }

    /**
     * Used to cleanly stop the database.
     * <br>
     * In the case of {@link Db} this will interrupt its internal queue and tell it
     * to finish up processing.
     * <br>
     * In the case of {@link DbEngine2} this will flush data to disk.
     */
    public abstract void stop();

    /**
     * Used to cleanly stop the database, with extra allowance of time
     * for cleanup.
     * <br>
     * Note that this method mostly applies to {@link Db}, and not as much
     * to {@link DbEngine2}.  Only Db uses a processing queue on a thread which
     * is what requires a longer shutdown time for interruption.
     * @param count number of loops before we are done waiting for a clean close
     *              and instead crash the instance closed.
     * @param sleepTime how long to wait, in milliseconds, for each iteration of the waiting loop.
     */
    public abstract void stop(int count, int sleepTime);


    /**
     * Write data to the database.  Use an index of 0 to store new data, and a positive
     * non-zero value to update data.
     * <p><em>
     * Example of adding new data to the database:
     * </p></em>
     * {@snippet :
     *          final var newSalt = StringUtils.generateSecureRandomString(10);
     *          final var hashedPassword = CryptoUtils.createPasswordHash(newPassword, newSalt);
     *          final var newUser = new User(0L, newUsername, hashedPassword, newSalt);
     *          userDb.write(newUser);
     * }
     * <p><em>
     * Example of updating data:
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
     * @return the data with its new index assigned.
     */
    public abstract T write(T newData);

    /**
     * Write database data into memory
     * @param newData the new data may be totally new or an update
     * @param newElementCreated if true, this is a create.  If false, an update.
     */
    protected void writeToMemory(T newData, boolean newElementCreated) {
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

    /**
     * When new data comes in, we look at its "index" value. If
     * it is zero, it's a create, and we assign it a new value.  If it is
     * positive, it is an update, and we had better find it in the database
     * already, or else throw an exception.
     * @return true if a create, false if an update
     */
    protected boolean processDataIndex(T newData) {
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
        return newElementCreated;
    }

    /**
     * Delete data
     * <p><em>Example:</p></em>
     * {@snippet :
     *      userDb.delete(user);
     * }
     *
     * @param dataToDelete the data we are serializing and writing
     */
    public abstract void delete(T dataToDelete);


    /**
     * Remove a particular item from the internal data structure in memory
     */
    protected void deleteFromMemory(T dataToDelete) {
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
     *  add the data to registered indexes.
     *  <br>
     *  For each of the registered indexes,
     *  get the stored function to obtain a string value which helps divide
     *  the overall data into partitions.
     */
    protected void addToIndexes(T dbData) {

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
     * Grabs all the data from disk and returns it as a list.  This
     * method is run by various programs when the system first loads.
     */
    public abstract void loadData() throws IOException;

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
    public abstract Collection<T> values();

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
     * @return true if the registration succeeded
     * @throws DbException if the parameters are not entered properly, if the index has already
     * been registered, or if the data has already been loaded. It is necessary that
     * this is run immediately after declaring the database. To explain further: the data is not
     * actually loaded until the first time it is needed, such as running a write or delete, or
     * if the {@link #loadData()} ()} method is run.  Creating an index map for the data that
     * is read from disk only occurs once, at data load time.  Thus, it is crucial that the
     * registerIndex command is run before any data is loaded.
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
