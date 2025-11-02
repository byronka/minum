package com.renomad.minum.database;

import com.renomad.minum.state.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.renomad.minum.utils.Invariants.mustBeFalse;
import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * a memory-based disk-persisted database class.
 *
 * <p>
 *     Engine 2 is a database engine that improves on the performance from the first
 *     database provided by Minum. It does this by using different strategies for disk persistence.
 * </p>
 * <p>
 *     The mental model of the previous Minum database has been an in-memory data
 *     structure in which every change is eventually written to its own file on disk for
 *     persistence.  Data changes affect just their relevant files.  The benefit of this approach is
 *     extreme simplicity. It requires very little code, relying as it does on the operating system's file capabilities.
 * </p>
 * <p>
 *     However, there are two performance problems with this approach.  First is when the
 *     data changes are arriving at a high rate.  In that situation, the in-memory portion keeps up to date,
 *     but the disk portion may lag by minutes.  The second problem is start-up time.  When
 *     the database starts, it reads files into memory.  The database can read about 6,000
 *     files a second in the best case.  If there are a million data items, it would take
 *     about 160 seconds to load it into memory, which is far too long.
 * </p>
 * <p>
 *      The new approach to disk persistence is to append each change to a file.  Append-only file
 *      changes can be very fast.  These append files are eventually consolidated into files
 *      partitioned by their index - data with indexes between 1 and 1000 go into one file, between
 *      1001 and 2000 go into another, and so on.
 *  </p>
 *  <p>
 *      Startup is magnitudes faster by this approach.  What took the previous database 160 seconds
 *      to load requires only 2 seconds. Writes to disk are also faster. What would have taken
 *      several minutes to write should only take a few seconds now.
 *  </p>
 *  <p>
 *      This new approach uses a different file structure than the previous. If it is
 *      desired to use the new engine on existing data, it is possible to convert the old
 *      data format to the new.  Construct an instance of the new engine, pointing
 *      at the same name as the previous, and it will convert the data.  If the previous
 *      call looked like this:
 *  </p>
 *  <code>
 *  Db<Photograph> photoDb = context.getDb("photos", Photograph.EMPTY);
 *  </code>
 *  <p>
 *  Then converting to the new database is just replacing it with the following
 *  line. <b>Please, backup your database before this change.</b>
 *  </p>
 *  <p>
 * <code>
 *     DbEngine2<Photograph> photoDb = context.getDb2("photos", Photograph.EMPTY);
 * </code>
 *  </p>
 *  <p>
 *     Once the new engine starts up, it will notice the old file structure and convert it
 *     over.  The methods and behaviors are mostly the same between the old and new engines, so the
 *     update should be straightforward.
 * </p>
 * <p>
 *     (By the way, it *is* possible to convert back to the old file structure,
 *     by starting the database the old way again.  Just be aware that each time the
 *     files are converted, it takes longer than normal to start the database)
 * </p>
 * <p>
 *     However, something to note is that using the old database is still fine in many cases,
 *     particularly for prototypes or systems which do not contain large amounts of data. If
 *     your system is working fine, there is no need to change things.
 * </p>
 *
 * @param <T> the type of data we'll be persisting (must extend from {@link DbData})
 */
public final class DbEngine2<T extends DbData<?>> extends AbstractDb<T> {

    private final ReentrantLock loadDataLock;
    private final ReentrantLock consolidateLock;
    private final ReentrantLock writeLock;
    int maxLinesPerAppendFile;
    boolean hasLoadedData;
    final DatabaseAppender databaseAppender;
    private final DatabaseConsolidator databaseConsolidator;

    /**
     * Here we track the number of appends we have made.  Once it hits
     * a certain number, we will kick off a consolidation in a thread
     */
    final AtomicInteger appendCount = new AtomicInteger(0);

    /**
     * Used to determine whether to kick off consolidation.  If it is
     * already running, we don't want to kick it off again. This would
     * only affect us if we are updating the database very fast.
     */
    boolean consolidationIsRunning;

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
    public DbEngine2(Path dbDirectory, Context context, T instance) {
        super(dbDirectory, context, instance);

        this.databaseConsolidator = new DatabaseConsolidator(dbDirectory, context);
        try {
            this.databaseAppender = new DatabaseAppender(dbDirectory, context);
        } catch (IOException e) {
            throw new DbException("Error while initializing DatabaseAppender in DbEngine2", e);
        }
        this.loadDataLock = new ReentrantLock();
        this.consolidateLock = new ReentrantLock();
        this.writeLock = new ReentrantLock();
        this.maxLinesPerAppendFile = context.getConstants().maxAppendCount;
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
     * @return the data with its new index assigned.
     * @throws DbException if there is a failure to write
     */
    @Override
    public T write(T newData) {
        if (newData.getIndex() < 0) throw new DbException("Negative indexes are disallowed");
        // load data if needed
        if (!hasLoadedData) loadData();

        writeLock.lock();
        try {
            boolean newElementCreated = processDataIndex(newData);
            writeToDisk(newData);
            writeToMemory(newData, newElementCreated);
        } catch (IOException ex) {
           throw new DbException("failed to write data " + newData, ex);
        } finally {
            writeLock.unlock();
        }

        // returning the data at this point is the most convenient
        // way users will have access to the new index of the data.
        return newData;
    }


    private void writeToDisk(T newData) throws IOException {
        logger.logTrace(() -> String.format("writing data to disk: %s", newData));
        String serializedData = newData.serialize();
        mustBeFalse(serializedData == null || serializedData.isBlank(),
                "the serialized form of data must not be blank. " +
                        "Is the serialization code written properly? Our datatype: " + emptyInstance);
        databaseAppender.appendToDatabase(DatabaseChangeAction.UPDATE, serializedData);
        appendCount.incrementAndGet();
        consolidateIfNecessary();
    }

    /**
     * If the append count is large enough, we will call the
     * consolidation method on the DatabaseConsolidator and
     * reset the append count to 0.
     */
    boolean consolidateIfNecessary() {
        if (appendCount.get() > maxLinesPerAppendFile && !consolidationIsRunning) {
            consolidateLock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
            try {
                consolidateInnerCode();
            } finally {
                consolidateLock.unlock();
            }
            return true;
        }
        return false;
    }

    /**
     * This code is only called in production from {@link #consolidateIfNecessary()},
     * and is necessarily protected by mutex locks.  However, it is provided
     * here as its own method for ease of testing.
     */
    void consolidateInnerCode() {
        if (appendCount.get() > maxLinesPerAppendFile && !consolidationIsRunning) {
            context.getExecutorService().submit(() -> {
                try {
                    consolidationIsRunning = true;
                    databaseConsolidator.consolidate();
                    consolidationIsRunning = false;
                } catch (Exception e) {
                    logger.logAsyncError(() -> "Error during consolidation: " + e);
                }
            });
            appendCount.set(0);
        }
    }

    /**
     * Delete data
     * <p><em>Example:</p></em>
     * {@snippet :
     *      userDb.delete(user);
     * }
     * @param dataToDelete the data we are serializing and writing
     * @throws DbException if there is a failure to delete
     */
    @Override
    public void delete(T dataToDelete) {
        // load data if needed
        if (!hasLoadedData) loadData();

        writeLock.lock();
        try {
            deleteFromDisk(dataToDelete);
            deleteFromMemory(dataToDelete);
        } catch (IOException ex) {
            throw new DbException("failed to delete data " + dataToDelete, ex);
        } finally {
            writeLock.unlock();
        }
    }

    private void deleteFromDisk(T dataToDelete) throws IOException {
        logger.logTrace(() -> String.format("deleting data from disk: %s", dataToDelete));
        databaseAppender.appendToDatabase(DatabaseChangeAction.DELETE, dataToDelete.serialize());
        appendCount.incrementAndGet();
        consolidateIfNecessary();
    }


    /**
     * Tells the database to load its data into memory immediately rather
     * than wait for a command that would require data (like {@link #write(DbData)},
     * {@link #delete(DbData)}, or {@link #values()}). This may be valuable
     * in cases where the developer wants greater control over the timing - such
     * as getting the data loaded into memory immediately at program start.
     */
    private void loadDataFromDisk() throws IOException {
        // if we find the "index.ddps" file, it means we are looking at an old
        // version of the database.  Update it to the new version, and then afterwards
        // remove the old version files.
        if (Files.exists(dbDirectory.resolve("index.ddps"))) {
            new DbFileConverter(context, dbDirectory).convertClassicFolderStructureToDbEngine2Form();
        }

        fileUtils.makeDirectory(dbDirectory);
        // if there are any remnant items in the current append-only file, move them
        // to a new file
        databaseAppender.saveOffCurrentDataToReadyFolder();
        databaseAppender.flush();

        // consolidate whatever files still exist in the append logs
        databaseConsolidator.consolidate();

        // load the data into memory
        walkAndLoad(dbDirectory);

        if (data.isEmpty()) {
            this.index = new AtomicLong(1);
        } else {
            var initialIndex = Collections.max(data.keySet()) + 1L;
            this.index = new AtomicLong(initialIndex);
        }
    }

    /**
     * Loops through each line of data in the consolidated data files,
     * converting each to its strongly-typed form and adding to the database
     */
    void walkAndLoad(Path dbDirectory) {
        List<String> consolidatedFiles = new ArrayList<>(
                List.of(Objects.requireNonNull(dbDirectory.resolve("consolidated_data").toFile().list())));

        // if there aren't any files, bail out
        if (consolidatedFiles.isEmpty()) return;

        // sort
        consolidatedFiles.sort(Comparator.comparingLong(DbEngine2::parseConsolidatedFileName));

        for (String fileName : consolidatedFiles) {
            logger.logDebug(() -> "Processing database file: " + fileName);
            Path consolidatedDataFile = dbDirectory.resolve("consolidated_data").resolve(fileName);

            // By using a lazy stream, we are able to read each item from the file into
            // memory without needing to read the whole file contents into memory at once,
            // thus avoiding requiring a great amount of memory
            try(Stream<String> fileStream = Files.lines(consolidatedDataFile, StandardCharsets.US_ASCII)) {
                fileStream.forEach(line -> readAndDeserialize(line, fileName));
            } catch (Exception e) {
                throw new DbException(e);
            }
        }
    }

    /**
     * Given a file like 1_to_1000 or 1001_to_2000, extract out the
     * beginning index (i.e. 1, or 1001).
     */
    static long parseConsolidatedFileName(String file) {
        int index = file.indexOf("_to_");
        if (index == -1) {
            throw new DbException("Consolidated filename was invalid: " + file);
        }
        return Long.parseLong(file, 0, index, 10);
    }

    /**
     * Converts a serialized string to a strongly-typed data structure
     * and adds it to the database.
     */
    void readAndDeserialize(String lineOfData, String fileName) {
        try {
            @SuppressWarnings("unchecked")
            T deserializedData = (T) emptyInstance.deserialize(lineOfData);
            mustBeTrue(deserializedData != null, "deserialization of " + emptyInstance +
                    " resulted in a null value. Was the serialization method implemented properly?");

            // put the data into the in-memory data structure
            data.put(deserializedData.getIndex(), deserializedData);
            addToIndexes(deserializedData);

        } catch (Exception e) {
            throw new DbException("Failed to deserialize " + lineOfData + " with data (\"" + fileName + "\"). Caused by: " + e);
        }
    }


    /**
     * This is what loads the data from disk the
     * first time someone needs it.  Because it is
     * locked, only one thread can enter at
     * a time.  The first one in will load the data,
     * and the second will encounter a branch which skips loading.
     */
    @Override
    public void loadData() {
        loadDataLock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
        try {
            if (!hasLoadedData) {
                loadDataFromDisk();
            }
            hasLoadedData = true;
        } catch (Exception ex) {
            throw new DbException("Failed to load data from disk.", ex);
        } finally {
            loadDataLock.unlock();
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
    @Override
    public Collection<T> values() {
        // load data if needed
        if (!hasLoadedData) loadData();

        return Collections.unmodifiableCollection(data.values());
    }

    @Override
    public boolean registerIndex(String indexName, Function<T, String> keyObtainingFunction) {
        if (hasLoadedData) {
            throw new DbException("This method must be run before the database loads data from disk.  Typically, " +
                    "it should be run immediately after the database is created.  See this method's documentation");
        }
        return super.registerIndex(indexName, keyObtainingFunction);
    }


    @Override
    public Collection<T> getIndexedData(String indexName, String key) {
        // load data if needed
        if (!hasLoadedData) loadData();
        return super.getIndexedData(indexName, key);
    }

    /**
     * This command calls {@link DatabaseAppender#flush()}, which will
     * force any in-memory-buffered data to be written to disk.  This is
     * not commonly necessary to call for business purposes, but tests
     * may require it if you want to be absolutely sure the data is written
     * to disk at a particular moment.
     */
    public void flush() {
        this.databaseAppender.flush();
    }

    /**
     * This is here to match the contract of {@link Db}
     * but all it does is tell the interior file writer
     * to write its data to disk.
     */
    @Override
    public void stop() {
        flush();
    }

    /**
     * No real difference to {@link #stop()} but here
     * to have a similar contract to {@link Db}
     */
    @Override
    public void stop(int count, int sleepTime) {
        flush();
    }
}
