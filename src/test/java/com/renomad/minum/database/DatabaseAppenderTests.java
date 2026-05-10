package com.renomad.minum.database;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.*;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import static com.renomad.minum.testing.TestFramework.*;

public class DatabaseAppenderTests {

    static private Context context;
    static private TestLogger logger;
    static private IFileUtils fileUtils;
    static Path foosDirectory = Path.of("out/simple_db_for_engine2_tests/engine2/foos");

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("DatabaseAppenderTests");
        logger = (TestLogger)context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    /**
     * When the appender puts more lines into the currentAppendLog
     * than the "maxAppendCount", it will save off the file to
     * a new file in the appendLogDirectory with a name from the
     * current date-time stamp.
     */
    @Test
    public void test_SavingOffToNewFile() throws IOException {
        fileUtils.deleteDirectoryRecursivelyIfExists(foosDirectory.resolve("test_SavingOffToNewFile"));
        fileUtils.makeDirectory(foosDirectory.resolve("test_SavingOffToNewFile"));
        var da = new DatabaseAppender(foosDirectory.resolve("test_SavingOffToNewFile"), context, fileUtils);
        da.maxAppendCount = 5;

        da.appendCount = 4;

        String result1 = da.appendToDatabase(DatabaseChangeAction.UPDATE, "THIS IS A TEST");
        assertTrue(result1.isEmpty(), "Result must be empty");

        da.appendCount = 5;

        String result2 = da.appendToDatabase(DatabaseChangeAction.UPDATE, "THIS IS A TEST");
        assertFalse(result2.isEmpty(), "Result must not be empty");

        da.appendCount = 6;

        // need to have a little break here so we don't end up creating a new
        // append log named with the same timestamp
        MyThread.sleep(5);

        String result3 = da.appendToDatabase(DatabaseChangeAction.UPDATE, "THIS IS A TEST");
        assertFalse(result3.isEmpty(), "Result must not be empty");
    }

    @Test
    public void testDatabaseAppenderConstructor_NegativeCase_ExceptionThrown() {
        var throwingFileUtils = new ThrowingFileUtils();

        var ex = assertThrows(IOException.class, () -> new DatabaseAppender(Path.of(""), context, throwingFileUtils));

        assertEquals(ex.getMessage(), "THIS IS JUST THROWN FOR TESTING");
    }

    /**
     * It's possible for an {@link IOException} to be thrown when using a
     * {@link java.io.BufferedWriter} which is provided to us
     * by {@link IFileUtils#newBufferedWriter(Path, Charset, OpenOption...)}
     */
    @Test
    public void testAppendToDatabase_NegativeCase_ExceptionThrown() throws IOException {
        var myFileUtils = new FakeFileUtils();
        myFileUtils.newBufferedWriterResult = new FakeBufferedWriter();
        var da = new DatabaseAppender(Path.of(""), context, myFileUtils);

        var ex = assertThrows(IOException.class, () -> da.appendToDatabase(DatabaseChangeAction.UPDATE, "DOES_NOT_MATTER"));

        assertEquals(ex.getMessage(), "JUST A TEST");
    }

    public static class FakeBufferedWriter extends BufferedWriter {
        public FakeBufferedWriter() {
            super(new Writer() {
                @Override public void write(char[] cbuf, int off, int len) throws IOException {}
                @Override public void flush() throws IOException {}
                @Override public void close() throws IOException {}
            });
        }

        @Override
        public Writer append(CharSequence csq) throws IOException {
            throw new IOException("JUST A TEST");
        }
    }

    /**
     * It is possible, when running {@link DatabaseAppender#moveToReadyFolder(IFileUtils, Path, Path)}, for
     * an {@link IOException} to be thrown.  Let's see that in action.
     */
    @Test
    public void testMoveToReadyFolder_NegativeCase_ExceptionThrown() {
        var throwingFileUtils = new ThrowingFileUtils();

        var ex = assertThrows(IOException.class, () -> DatabaseAppender.moveToReadyFolder(throwingFileUtils, Path.of(""), Path.of("")));

        assertEquals(ex.getMessage(), "THIS IS JUST THROWN FOR TESTING");
    }

}
