package com.renomad.minum.database;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.security.ForbiddenUseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Deliberately-failing regression tests, one per finding.  Each test asserts the
 * correct/desired behavior of the database code, and therefore fails against the
 * current implementation.  Each test's javadoc states the finding, the location of
 * the defect, and the behavior that ought to happen instead.
 * <br>
 * Note: these tests deliberately keep their data outside the project's "out"
 * directory, because the project directory is on a mount with unreliable metadata
 * coherency during concurrent directory creation, which would confuse these tests.
 */
public class FindingsDatabaseTests {

    static private Context context;
    static private TestLogger logger;
    static private IFileUtils fileUtils;

    /**
     * All databases for these tests live here, deliberately outside the
     * project directory.  See this class's javadoc.
     */
    static final Path BASE_DIRECTORY = Path.of("/tmp/minum_findings");

    @BeforeClass
    public static void init() {
        context = buildTestingContext("FindingsDatabaseTests");
        logger = (TestLogger) context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    /**
     * FINDING 1: {@link Db#stop()} silently discards queued writes, losing data.
     * <br>
     * Defect: Db.java:384 and Db.java:393 - {@link Db#stop()} delegates to
     * {@link com.renomad.minum.queue.ActionQueue#stop(int, long)} with a count of 5 and a sleep
     * of 20 milliseconds.  ActionQueue.java:125-131 loops at most 5 times, waiting 20
     * milliseconds each time, and then simply returns while the queue still holds a backlog.
     * The framework's own shutdown ({@link com.renomad.minum.queue.ActionQueueKiller#killAllQueues()})
     * then interrupts the writer thread, and every remaining queued write is discarded with
     * no error and no log of the lost data.
     * <br>
     * Correct behavior: {@link Db#stop()} is documented as waiting "for our threads to finish
     * their work".  When it returns, every record that was accepted by {@link Db#write(DbData)}
     * must be durable on disk.  Shutting a database down must never silently lose accepted writes.
     */
    @Test
    public void test_Finding_StopDiscardsQueuedWrites() throws IOException {
        Path dbPathForTest = BASE_DIRECTORY.resolve("stop_discards_queued_writes");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        int recordCount = 20_000;

        var db = new Db<>(dbPathForTest, context, Foo.INSTANCE);
        for (int i = 0; i < recordCount; i++) {
            db.write(new Foo(0, i, "data for record " + i));
        }

        // this is the framework's own clean-shutdown path for a database
        db.stop();

        long countOnDisk = countDatabaseFiles(dbPathForTest);
        assertTrue(countOnDisk == recordCount,
                "Every record accepted by Db.write must be persisted to disk by the time Db.stop() returns. " +
                        "Expected " + recordCount + " data files on disk, but found " + countOnDisk +
                        ".  " + (recordCount - countOnDisk) + " records were silently discarded when the database stopped.");
    }

    /**
     * FINDING 2: deleting data leaves a permanent phantom entry in a registered index.
     * <br>
     * Defect: AbstractDb.java:240 - {@link AbstractDb#deleteFromMemory(DbData)} removes the row
     * from the in-memory map by its numeric index (the authoritative identity), but then calls
     * removeFromIndexes using the <em>caller's</em> object.  The index keys are recomputed from
     * the caller's field values rather than from the row that was actually stored, so when the
     * caller passes an object whose fields do not match the stored row (a stale copy, or a copy
     * built from a form submission), the entry is scrubbed from the wrong index bucket.  The real
     * bucket keeps pointing at the deleted row forever.
     * <br>
     * Correct behavior: after a successful delete, no registered index may return the deleted
     * row.  The delete must un-index the row that was actually stored (looked up by index),
     * not whatever the caller happened to hand over.
     */
    @Test
    public void test_Finding_DeleteLeavesPhantomIndexEntry() throws IOException {
        Path dbPathForTest = BASE_DIRECTORY.resolve("delete_leaves_phantom_index_entry");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        var db = new Db<>(dbPathForTest, context, Foo.INSTANCE);
        db.registerIndex("name", x -> x.getB());

        Foo rowWithNameX = db.write(new Foo(0, 1, "x"));
        db.write(new Foo(0, 2, "y"));

        // delete the first row, but hand over an object whose "name" field is stale.
        // The database identifies data by its index, and index 1 is the row named "x".
        db.delete(new Foo(rowWithNameX.getIndex(), 1, "y"));

        // the main data store is correct - the row is gone
        assertTrue(db.values().size() == 1,
                "After deleting the row at index " + rowWithNameX.getIndex() +
                        ", exactly one row should remain.  Found: " + db.values());

        Collection<Foo> stillIndexedUnderX = db.getIndexedData("name", "x");
        assertTrue(stillIndexedUnderX.isEmpty(),
                "After a row is deleted, no registered index may still return it.  The index \"name\" " +
                        "still returns the deleted row under key \"x\": " + stillIndexedUnderX);

        db.stop();
    }

    /**
     * FINDING 3: a single failed consolidation permanently disables consolidation.
     * <br>
     * Defect: DbEngine2.java:271-276 - inside
     * {@link DbEngine2#consolidateInnerCode()} the statement
     * {@code consolidationIsRunning = false} is the last statement <em>inside the try block</em>,
     * not in a finally block.  If {@code databaseConsolidator.consolidate()} throws, the catch
     * clause only logs, and the flag stays true forever.  Since
     * {@link DbEngine2#consolidateIfNecessary()} refuses to run while that flag is true, the
     * database never consolidates again for the remaining life of the process - the append logs
     * grow without bound and startup time degrades permanently.
     * <br>
     * Correct behavior: the running flag must be cleared in a finally block, so that one failed
     * consolidation is a transient error and a later consolidation is still able to run.
     */
    @Test
    public void test_Finding_ConsolidationFlagStuckAfterFailure() throws IOException {
        var properties = new Properties();
        properties.setProperty("MAX_DATABASE_APPEND_COUNT", "1");
        properties.setProperty("MAX_DATABASE_CONSOLIDATED_FILE_LINES", "1");
        properties.setProperty("DB_DIRECTORY", BASE_DIRECTORY.toString());
        var customContext = TestFramework.buildTestingContext("finding_consolidation_flag", properties);
        var customLogger = (TestLogger) customContext.getLogger();

        Path dbPathForTest = BASE_DIRECTORY.resolve("consolidation_flag_stuck");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, customContext, Foo.INSTANCE);

        Foo foo = db.write(new Foo(0, 1, "a"));

        // plant a file the consolidator cannot parse as a date, which makes consolidation fail
        Files.writeString(dbPathForTest.resolve("append_logs/foofoo"), "THIS FILENAME IS NOT A DATE");

        // this triggers a consolidation, which will fail
        db.delete(foo);
        MyThread.sleep(200);

        // setup check - this test is only meaningful if the consolidation really did fail
        boolean consolidationFailed;
        try {
            consolidationFailed = customLogger.doesMessageExist("Error during consolidation", 20);
        } catch (TestLoggerException ex) {
            consolidationFailed = false;
        }
        assertTrue(consolidationFailed,
                "Setup check: planting an unparseable file in append_logs should have made consolidation fail");

        // remove the bad file so that a later consolidation would be able to succeed
        Files.delete(dbPathForTest.resolve("append_logs/foofoo"));

        assertFalse(db.consolidationIsRunning,
                "After a consolidation fails, the consolidationIsRunning flag must be reset to false, " +
                        "otherwise consolidation is disabled for the life of the process");

        db.appendCount.set(db.maxLinesPerAppendFile + 1);
        assertTrue(db.consolidateIfNecessary(),
                "After a consolidation fails, a subsequent consolidation must still be able to run");

        db.stop();
        TestFramework.shutdownTestingContext(customContext);
    }

    /**
     * FINDING 4: rolling over the append-only log leaks a file descriptor each time.
     * <br>
     * Defect: DatabaseAppender.java:121 - {@code createNewAppendFile()} assigns a brand-new
     * writer to the {@code bufferedWriter} field without ever closing the writer that was there
     * before.  That method is called on every rollover (from
     * {@link DatabaseAppender#saveOffCurrentDataToReadyFolder()}), so a long-running server leaks
     * one open file descriptor per rollover and will eventually die with "Too many open files".
     * <br>
     * Correct behavior: the previous writer must be flushed and closed before it is replaced, so
     * the number of open file descriptors stays flat no matter how many rollovers occur.
     */
    @Test
    public void test_Finding_AppendFileRolloverLeaksFileDescriptor() throws IOException {
        var properties = new Properties();
        // roll over to a new append file on every single append
        properties.setProperty("MAX_DATABASE_APPEND_COUNT", "1");
        properties.setProperty("DB_DIRECTORY", BASE_DIRECTORY.toString());
        var customContext = TestFramework.buildTestingContext("finding_fd_leak", properties);

        Path dbPathForTest = BASE_DIRECTORY.resolve("append_file_rollover");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);
        fileUtils.makeDirectory(dbPathForTest);

        var appender = new DatabaseAppender(dbPathForTest, customContext, fileUtils);
        int rolloverCount = 150;

        long fileDescriptorsBefore = countOpenFileDescriptors();
        for (int i = 0; i < rolloverCount; i++) {
            appender.appendToDatabase(DatabaseChangeAction.UPDATE, new Foo(i + 1, i, "data " + i).serialize());
            // the append-log files are named by millisecond, so pause to
            // keep the rolled-over file names distinct
            MyThread.sleep(2);
        }
        long fileDescriptorsAfter = countOpenFileDescriptors();
        long growth = fileDescriptorsAfter - fileDescriptorsBefore;

        assertTrue(growth < 20,
                "Rolling over the append-only log must close the previous writer, so the count of open " +
                        "file descriptors must stay flat.  After " + rolloverCount + " rollovers the count of open " +
                        "file descriptors grew by " + growth + " (from " + fileDescriptorsBefore + " to " +
                        fileDescriptorsAfter + ")");

        TestFramework.shutdownTestingContext(customContext);
    }

    /**
     * FINDING 5: a torn final line in the append log makes the database permanently unstartable.
     * <br>
     * Defect: DatabaseConsolidator.java:270 (in
     * {@link DatabaseConsolidator#parseDatabaseChangeInstructionString(String, String)}) does
     * {@code databaseInstructionString.substring(0, 6)} with no length guard.  If the process
     * died mid-write (SIGKILL, power loss, a full disk), the last line of "currentAppendLog" is a
     * partial line.  On the next startup that line reaches this method and throws
     * StringIndexOutOfBoundsException, which becomes a DbException out of
     * {@link DbEngine2#loadData()}.  Every subsequent startup hits the same line, so the database
     * can never be opened again without hand-editing the file.
     * <br>
     * Correct behavior: an incomplete trailing line in the append log is an expected consequence
     * of a hard crash.  Startup must skip (and log) the torn final record and load all the
     * records that were completely written.
     */
    @Test
    public void test_Finding_TornAppendLogTailPreventsStartup() throws IOException {
        Path dbPathForTest = BASE_DIRECTORY.resolve("torn_append_log_tail");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);

        DbEngine2<Foo> db = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        db.write(new Foo(0, 1, "alpha"));
        db.write(new Foo(0, 2, "bravo"));
        db.write(new Foo(0, 3, "charlie"));
        db.stop();
        MyThread.sleep(50);

        // simulate a hard crash in the middle of writing the third record: the tail
        // of the file is cut down to a couple of characters.
        Path currentAppendLog = dbPathForTest.resolve("currentAppendLog");
        String content = Files.readString(currentAppendLog, StandardCharsets.US_ASCII);
        int endOfSecondRecord = content.indexOf('\n', content.indexOf('\n') + 1) + 1;
        String tornContent = content.substring(0, endOfSecondRecord) + "UP";
        Files.writeString(currentAppendLog, tornContent, StandardCharsets.US_ASCII);

        DbEngine2<Foo> restartedDb = new DbEngine2<>(dbPathForTest, context, Foo.INSTANCE);
        Collection<Foo> values;
        try {
            values = restartedDb.values();
        } catch (Exception ex) {
            throw new AssertionError(
                    "A database whose append log has a torn final line (from a crash mid-write) must still start, " +
                            "skipping the incomplete record.  Instead, startup failed with: " + ex, ex);
        }

        assertTrue(values.size() == 2,
                "The two completely-written records must be loaded after skipping the torn final line.  Found: " + values);
        restartedDb.stop();
    }

    /**
     * FINDING 6: when a load fails part way through, the automatic retry double-adds rows to the
     * registered indexes.
     * <br>
     * Defect: {@link Db#loadData()} (Db.java:300-315) leaves the rows that were read before the
     * failure in the in-memory map and in the registered index buckets, while leaving
     * {@code hasLoadedData} false.  Every read method ({@link Db#values()},
     * {@link Db#getIndexedData(String, String)}, ...) automatically retries the load, and
     * {@link Db#readAndDeserialize(java.nio.file.Path)} (Db.java:281) calls
     * {@link AbstractDb#addToIndexes(DbData)} again for the rows that were already indexed.  The
     * index bucket now holds two objects for one row, so
     * {@link AbstractDb#findExactlyOne(String, String)} throws "More than one item found" for
     * data that exists exactly once.
     * <br>
     * Correct behavior: a failed load must not leave partial state behind (or the retry must
     * clear it first), so that once the transient I/O problem clears, the loaded database is
     * exactly as if the load had succeeded the first time: one index entry per row.
     */
    @Test
    public void test_Finding_FailedLoadRetryDuplicatesIndexEntries() throws IOException {
        Path dbPathForTest = BASE_DIRECTORY.resolve("failed_load_retry");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPathForTest);

        var setupDb = new Db<>(dbPathForTest, context, Widget.INSTANCE);
        setupDb.write(new Widget(0, "alpha"));
        setupDb.write(new Widget(0, "bravo"));
        setupDb.write(new Widget(0, "charlie"));
        setupDb.stop(10, 50);
        MyThread.sleep(100);
        assertTrue(countDatabaseFiles(dbPathForTest) == 3,
                "Setup check: three data files should be on disk before we test loading them");

        // this file utility fails once, on the second data file it is asked to read,
        // simulating a transient I/O error part way through a load.
        var transientlyFailingFileUtils = new TransientlyFailingFileUtils(fileUtils);
        var db = new Db<>(dbPathForTest, context, Widget.INSTANCE, transientlyFailingFileUtils);
        db.registerIndex("name", Widget::getName);

        assertThrows(DbException.class, db::loadData);

        // the transient failure has passed - the database automatically retries the load
        Collection<Widget> values = db.values();
        assertTrue(values.size() == 3, "All three rows should be loaded after the retry.  Found: " + values);

        for (String name : List.of("alpha", "bravo", "charlie")) {
            Collection<Widget> indexed = db.getIndexedData("name", name);
            assertTrue(indexed.size() == 1,
                    "After a failed load is retried, each index key must hold exactly one entry per row.  " +
                            "The index \"name\" holds " + indexed.size() + " entries for key \"" + name + "\": " + indexed);
        }

        Widget alpha = db.findExactlyOne("name", "alpha");
        assertTrue(alpha != null && alpha.getName().equals("alpha"),
                "findExactlyOne must find the single row named \"alpha\" after the load was retried");

        db.stop();
    }

    /**
     * FINDING 7: the shared {@link java.text.SimpleDateFormat} is not thread-safe.
     * <br>
     * Defect: DatabaseAppender.java:33 declares
     * {@code static final SimpleDateFormat simpleDateFormat}.  SimpleDateFormat is documented as
     * not thread-safe (it keeps mutable parsing/formatting state in a shared Calendar), yet this
     * single instance is shared by every {@link DatabaseAppender} and every
     * {@link DatabaseConsolidator} in the JVM.  Each database has its own appender and
     * consolidator, and each consolidation runs on its own thread, so real deployments format
     * (DatabaseAppender.java:207, DatabaseConsolidator.java:73 and :84) and parse
     * (DatabaseConsolidator.java:322) concurrently.  The result is malformed append-log file
     * names, mis-parsed file names (so append logs are consolidated out of order, which loses the
     * "last write wins" ordering guarantee), and spurious exceptions.
     * <br>
     * Correct behavior: date formatting must be thread-safe - for example a
     * {@link java.time.format.DateTimeFormatter}, or a per-instance / ThreadLocal
     * SimpleDateFormat.  Concurrent formatting and parsing must always produce correct results.
     */
    @Test
    public void test_Finding_SharedSimpleDateFormatIsThreadSafe() throws Exception {
        String knownFileName = "2025_08_30_13_01_49_123";
        Date knownDate = DatabaseAppender.simpleDateFormat.parse(knownFileName);

        int threadCount = 8;
        int iterations = 20_000;
        List<String> anomalies = Collections.synchronizedList(new ArrayList<>());
        var startingLine = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                // half the threads format dates (as the appender and consolidator do),
                // half parse file names (as the consolidator does)
                boolean isFormatter = i % 2 == 0;
                futures.add(executor.submit(() -> {
                    startingLine.await();
                    for (int j = 0; j < iterations && anomalies.isEmpty(); j++) {
                        if (isFormatter) {
                            try {
                                String formatted = DatabaseAppender.simpleDateFormat.format(knownDate);
                                if (!knownFileName.equals(formatted)) {
                                    anomalies.add("formatting produced \"" + formatted + "\" instead of \"" + knownFileName + "\"");
                                }
                            } catch (Exception ex) {
                                anomalies.add("formatting threw " + ex);
                            }
                        } else {
                            try {
                                List<Date> dates = DatabaseConsolidator.convertFileListToDateList(new String[]{knownFileName});
                                if (!knownDate.equals(dates.getFirst())) {
                                    anomalies.add("parsing \"" + knownFileName + "\" produced " + dates.getFirst() +
                                            " instead of " + knownDate);
                                }
                            } catch (Exception ex) {
                                anomalies.add("parsing threw " + ex);
                            }
                        }
                    }
                    return null;
                }));
            }
            startingLine.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertTrue(anomalies.isEmpty(),
                "Formatting and parsing database append-log file names concurrently must always produce " +
                        "correct results.  Anomalies seen: " + anomalies);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * counts the data files (e.g. "1.ddps") in a classic database directory,
     * excluding the index file.
     */
    private static long countDatabaseFiles(Path databaseDirectory) throws IOException {
        try (Stream<Path> files = Files.list(databaseDirectory)) {
            return files
                    .map(x -> x.getFileName().toString())
                    .filter(x -> x.endsWith(Db.DATABASE_FILE_SUFFIX))
                    .filter(x -> !x.equals("index" + Db.DATABASE_FILE_SUFFIX))
                    .count();
        }
    }

    /**
     * The count of file descriptors this JVM currently holds open.  This is
     * a Linux-specific technique.
     */
    private static long countOpenFileDescriptors() {
        String[] fileDescriptors = new File("/proc/self/fd").list();
        Objects.requireNonNull(fileDescriptors, "this test requires /proc/self/fd, which exists on Linux");
        return fileDescriptors.length;
    }

    /**
     * A file utility that behaves exactly like the real one, except that the second
     * database file it is asked to read fails once, with an {@link IOException}.  This
     * simulates a transient I/O failure part way through loading a database.
     */
    static class TransientlyFailingFileUtils implements IFileUtils {

        private final IFileUtils delegate;
        private int countOfDataFileReads;
        private boolean hasFailedAlready;

        TransientlyFailingFileUtils(IFileUtils delegate) {
            this.delegate = delegate;
        }

        @Override
        public String readString(Path path) throws IOException {
            String filename = path.getFileName().toString();
            boolean isDataFile = filename.endsWith(Db.DATABASE_FILE_SUFFIX) &&
                    !filename.equals("index" + Db.DATABASE_FILE_SUFFIX);
            if (isDataFile) {
                countOfDataFileReads += 1;
                if (countOfDataFileReads == 2 && !hasFailedAlready) {
                    hasFailedAlready = true;
                    throw new IOException("JUST FOR TESTING - a transient failure reading " + path);
                }
            }
            return delegate.readString(path);
        }

        @Override
        public void writeString(Path path, String content, OpenOption... options) throws IOException {
            delegate.writeString(path, content, options);
        }

        @Override
        public Path write(Path path, Iterable<? extends CharSequence> lines, Charset cs, OpenOption... options) throws IOException {
            return delegate.write(path, lines, cs, options);
        }

        @Override
        public void deleteDirectoryRecursivelyIfExists(Path myPath) throws IOException {
            delegate.deleteDirectoryRecursivelyIfExists(myPath);
        }

        @Override
        public void makeDirectory(Path directory) throws IOException {
            delegate.makeDirectory(directory);
        }

        @Override
        public byte[] readBinaryFile(String path) throws IOException {
            return delegate.readBinaryFile(path);
        }

        @Override
        public List<String> readAllLines(Path path) throws IOException {
            return delegate.readAllLines(path);
        }

        @Override
        public String readTextFile(String path) throws IOException {
            return delegate.readTextFile(path);
        }

        @Override
        public void checkFileIsWithinDirectory(String path, String directoryPath) throws IOException {
            delegate.checkFileIsWithinDirectory(path, directoryPath);
        }

        @Override
        public Path safeResolve(String parentDirectory, String path) throws IOException, ForbiddenUseException {
            return delegate.safeResolve(parentDirectory, path);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(path);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            delegate.move(source, target, options);
        }

        @Override
        public boolean exists(Path path, LinkOption... options) {
            return delegate.exists(path, options);
        }

        @Override
        public BufferedWriter newBufferedWriter(Path path, Charset cs, OpenOption... options) throws IOException {
            return delegate.newBufferedWriter(path, cs, options);
        }

        @Override
        public BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
            return delegate.newBufferedReader(path, cs);
        }

        @Override
        public Stream<Path> walk(Path start, FileVisitOption... options) throws IOException {
            return delegate.walk(start, options);
        }

        @Override
        public boolean isRegularFile(Path path, LinkOption... options) {
            return delegate.isRegularFile(path, options);
        }

        @Override
        public Stream<String> lines(Path path, Charset cs) throws IOException {
            return delegate.lines(path, cs);
        }

        @Override
        public boolean deleteIfExists(Path path) throws IOException {
            return delegate.deleteIfExists(path);
        }

        @Override
        public long size(Path path) throws IOException {
            return delegate.size(path);
        }

        @Override
        public Stream<Path> list(Path dbDirectory) throws IOException {
            return delegate.list(dbDirectory);
        }
    }

    /**
     * A standard piece of test data, in the same shape as the Foo classes used
     * by {@link DbTests} and {@link DbEngine2Tests}.
     */
    public static class Foo extends DbData<Foo> implements Comparable<Foo> {

        private long index;
        private final int a;
        private final String b;

        public Foo(long index, int a, String b) {
            this.index = index;
            this.a = a;
            this.b = b;
        }

        static final Foo INSTANCE = new Foo(0, 0, "");

        public int getA() {
            return a;
        }

        public String getB() {
            return b;
        }

        @Override
        public String serialize() {
            return serializeHelper(index, a, b);
        }

        @Override
        public Foo deserialize(String serializedText) {
            final var tokens = deserializeHelper(serializedText);
            return new Foo(
                    Integer.parseInt(tokens.get(0)),
                    Integer.parseInt(tokens.get(1)),
                    tokens.get(2));
        }

        @Override
        public long getIndex() {
            return index;
        }

        @Override
        public void setIndex(long index) {
            this.index = index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Foo foo = (Foo) o;
            return index == foo.index && a == foo.a && Objects.equals(b, foo.b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, a, b);
        }

        @Override
        public int compareTo(Foo o) {
            return Long.compare(o.getIndex(), this.getIndex());
        }

        @Override
        public String toString() {
            return "Foo{" +
                    "index=" + index +
                    ", a=" + a +
                    ", b='" + b + '\'' +
                    '}';
        }
    }

    /**
     * A perfectly ordinary piece of database data.  Note that {@link DbData} does not
     * require (and the database documentation does not ask for) an implementation of
     * equals/hashCode, so this class - like many real data classes - does not have one.
     */
    public static class Widget extends DbData<Widget> {

        private long index;
        private final String name;

        public Widget(long index, String name) {
            this.index = index;
            this.name = name;
        }

        static final Widget INSTANCE = new Widget(0, "");

        public String getName() {
            return name;
        }

        @Override
        public String serialize() {
            return serializeHelper(index, name);
        }

        @Override
        public Widget deserialize(String serializedText) {
            final var tokens = deserializeHelper(serializedText);
            return new Widget(
                    Long.parseLong(tokens.get(0)),
                    tokens.get(1));
        }

        @Override
        public long getIndex() {
            return index;
        }

        @Override
        public void setIndex(long index) {
            this.index = index;
        }

        @Override
        public String toString() {
            return "Widget{" +
                    "index=" + index +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}
