package com.renomad.minum.database;


import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.StacktraceUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class provide the capability of appending database changes
 * to the disk, quickly and efficiently.
 */
final class DatabaseAppender {

    /**
     * Results in output like "2025_08_30_13_01_49_123", which is year_month_day_hour_minute_second_millisecond.
     * This can be used to parse the file names to {@link java.util.Date} so we can process the oldest
     * file first.
     */
    static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS");

    private final Path persistenceDirectory;

    Writer bufferedWriter;

    /**
     * if true, there is data in the buffered writer that needs to be
     * written to disk using {@link BufferedWriter#flush()}
     */
    private boolean bufferedWriterHasUnwrittenData;

    /**
     * This class field tracks the status of the loop which runs
     * a flush every second
     */
    private boolean flushLoopRunning;

    /**
     * The directory for this database
     */
    private final Path appendLogDirectory;

    /**
     * Used to create a thread that contains an inner loop
     * to flush the data to disk on a periodic basis
     */
    private final ExecutorService executorService;

    private final ILogger logger;

    private final ReentrantLock moveFileLock;

    /**
     * The maximum number of data's we will add to the append-only
     * file before we move on to a new file.
     */
    int maxAppendCount;

    /**
     * this is the current count of how many appends have
     * been made to the current database file.  Once it
     * exceeds a certain maximum, we'll switch to a
     * different file.
     */
    int appendCount;

    /**
     * This is the count of bytes that have been appended
     */
    private long appendBytes;

    DatabaseAppender(Path persistenceDirectory, Context context) throws IOException {
        this.persistenceDirectory = persistenceDirectory;
        this.appendLogDirectory = persistenceDirectory.resolve("append_logs");
        this.executorService = context.getExecutorService();
        this.logger = context.getLogger();
        Constants constants = context.getConstants();
        FileUtils fileUtils = new FileUtils(logger, constants);
        this.maxAppendCount = constants.maxAppendCount;
        fileUtils.makeDirectory(this.appendLogDirectory);
        moveFileLock = new ReentrantLock();
        createNewAppendFile();
    }

    /**
     * Creates a new append-file (a file used for appending data) and
     * resets the append count to zero.
     */
    private void createNewAppendFile() throws IOException {
        Path currentAppendFile = this.persistenceDirectory.resolve("currentAppendLog");

        // if we are starting up with an existing currentAppendLog, set the appendCount
        // appropriately.  Otherwise, initialize to 0.  The currentAppendLog file is
        // never very large - it's mostly a temporary place to store incoming data
        // until we can store it off elsewhere. For that reason, it's not a performance
        // concern to read all the existing lines, just to get the count of current lines.
        if (Files.exists(currentAppendFile)) {
            List<String> lines = Files.readAllLines(currentAppendFile);
            appendCount = lines.size();
        } else {
            // reset the count to zero, we're starting a new file.
            logger.logDebug(() -> "Creating a new database append file. Previous file: %,d lines, %.2f megabytes".formatted(appendCount, (appendBytes / 1_048_576.0)));
            appendCount = 0;
            appendBytes = 0;
        }

        bufferedWriter = Files.newBufferedWriter(currentAppendFile, StandardCharsets.US_ASCII, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Appends new data to the end of a file.
     * @return if we created a new append file, we'll return the name of it. Otherwise, an empty string.
     */
    String appendToDatabase(DatabaseChangeAction action, String serializedData) throws IOException {
        String newlyCreatedFileName = "";
        if (appendCount >= maxAppendCount) {
            moveFileLock.lock(); // block threads here if multiple are trying to get in - only one gets in at a time
            try {
                newlyCreatedFileName = saveOffWrapped(appendCount, maxAppendCount);
            } finally {
                moveFileLock.unlock();
            }
        }

        bufferedWriter.append(action.toString()).append(' ').append(serializedData).append('\n');
        setBufferedWriterHasUnwrittenData();
        appendCount += 1;
        appendBytes += serializedData.length() + 8; // 8 includes the action (e.g. UPDATE), a space character, and a newline
        return newlyCreatedFileName;
    }

    private void setBufferedWriterHasUnwrittenData() {
        bufferedWriterHasUnwrittenData = true;
        if (!flushLoopRunning) {
            initializeTimedFlusher();
        }
    }

    /**
     * This method is kicked off when there is new data added to
     * the {@link BufferedWriter}.  While there is data to write, it
     * will wake up every second to flush the data.  Once there is
     * no more data, it will end.
     */
    private void initializeTimedFlusher() {
        Runnable timedFlusherLoop = () -> {
            flushLoopRunning = true;
            Thread.currentThread().setName("database_timed_flusher");
            while (bufferedWriterHasUnwrittenData) {
                flush();

                // this code only runs when there is data to add, so no need to take a
                // lot of waiting time.  But, if the data is coming fast and furious,
                // at least a small wait will allow greater efficiency.
                MyThread.sleep(50);
            }
            flushLoopRunning = false;
        };
        executorService.submit(timedFlusherLoop);
    }

    /**
     * This helper just wraps a method to enable easier testing.
     * @return true if the appendCount is greater or equal to maxAppendCount,
     * meaning that we moved on to calling {@link #saveOffCurrentDataToReadyFolder()},
     * false otherwise.
     */
    String saveOffWrapped(int appendCount, int maxAppendCount) throws IOException {
        if (appendCount >= maxAppendCount) {
            return saveOffCurrentDataToReadyFolder();
        }
        return "";
    }

    /**
     * Move the append-only file to a new place to prepare for
     * consolidation, and reset the append count.
     * @return the name of the newly-created file
     */
    String saveOffCurrentDataToReadyFolder() throws IOException {
        flush();
        String newFileName = moveToReadyFolder();
        createNewAppendFile();
        return newFileName;
    }

    /**
     * When we are done filling a file, move it to the ready
     * folder named by the date + time + millis.
     * @return the name of the new file
     */
    private String moveToReadyFolder() throws IOException {
        String appendFile = simpleDateFormat.format(new java.util.Date());
        Files.move(persistenceDirectory.resolve("currentAppendLog"), this.appendLogDirectory.resolve(appendFile));
        return appendFile;
    }

    void flush() {
        flush(this.bufferedWriter, this.logger);
        this.bufferedWriterHasUnwrittenData = false;
    }

    static void flush(Writer writer, ILogger logger) {
        try {
            writer.flush();
        } catch (IOException e) {
            logger.logAsyncError(() -> "Error while flushing in TimedFlusher: " + StacktraceUtils.stackTraceToString(e));
            throw new DbException(e);
        }
    }
}