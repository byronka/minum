package com.renomad.minum.database;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static com.renomad.minum.testing.TestFramework.assertFalse;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class DatabaseAppenderTests {

    private Context context;
    private TestLogger logger;
    private FileUtils fileUtils;
    static Path foosDirectory = Path.of("out/simple_db_for_engine2_tests/engine2/foos");

    @Before
    public void init() {
        this.context = TestFramework.buildTestingContext("DatabaseAppenderTests");
        this.logger = (TestLogger)context.getLogger();
        this.fileUtils = new FileUtils(logger, context.getConstants());
    }

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
        var da = new DatabaseAppender(foosDirectory.resolve("test_SavingOffToNewFile"), context);
        da.maxAppendCount = 5;

        da.appendCount = 4;

        String result1 = da.appendToDatabase(DatabaseChangeAction.UPDATE, "THIS IS A TEST");
        assertTrue(result1.isEmpty(), "Result must be empty");

        da.appendCount = 5;

        String result2 = da.appendToDatabase(DatabaseChangeAction.UPDATE, "THIS IS A TEST");
        assertFalse(result2.isEmpty(), "Result must not be empty");

        da.appendCount = 6;

        String result3 = da.appendToDatabase(DatabaseChangeAction.UPDATE, "THIS IS A TEST");
        assertFalse(result3.isEmpty(), "Result must not be empty");
    }
}
