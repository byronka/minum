package com.renomad.minum.database;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import com.renomad.minum.utils.ThrowingFileUtils;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;
import java.nio.file.*;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;

public class DatabaseConsolidatorTests {

    static private IFileUtils fileUtils;
    static private Context context;
    static private TestLogger logger;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("DatabaseConsolidatorTests");
        fileUtils = new FileUtils(context.getLogger(), context.getConstants());
        logger = (TestLogger)context.getLogger();
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
     * An exception should be thrown if the program cannot parse a line
     * coming from the file to update (the second parameter)
     */
    @Test
    public void testUpdatingData_EdgeCase_ParsingError() {
        var ex = assertThrows(DbException.class, () -> DatabaseConsolidator.updateData("foo", List.of("bar"), List.of()));
        assertEquals(ex.getMessage(), "Error parsing line in file.  File: foo line: bar");
    }

    /**
     * An exception should be thrown if the program cannot parse the index
     * value of the data
     */
    @Test
    public void testUpdatingData_EdgeCase_ParsingErrorForIndex() {
        var ex = assertThrows(DbException.class, () -> DatabaseConsolidator.updateData("foo", List.of("bar|biz|baz"), List.of()));
        assertEquals(ex.getMessage(), "Failed to parse index from line in file. File: foo line: bar|biz|baz");
    }

    /**
     * When we read a database change instruction string, the action must match an expected value, or
     * else we will throw an exception
     */
    @Test
    public void testParsingDatabaseChangeStrings_EdgeCase_InvalidAction() {
        var ex = assertThrows(DbException.class, () -> DatabaseConsolidator.parseDatabaseChangeInstructionString("DONUT 1|biz|baz", "foo"));
        assertEquals(ex.getMessage(), "Line in append-only log was missing an action (UPDATE or DELETE) in the first characters. Line was: DONUT 1|biz|baz");
    }

    /**
     * The append-only logs in the appendlog directory must all be formatted as date-time
     * strings, which we will parse and sort, so that we know which order to use when consolidating.
     * Here, we demonstrate a situation where one of the files is not a valid filename
     */
    @Test
    public void testParsingAppendLogs_EdgeCase_InvalidFilename() {
        var ex = assertThrows(ParseException.class, () -> DatabaseConsolidator.convertFileListToDateList(new String[]{"NOT_A_DATE"}));
        assertEquals(ex.getMessage(), "Unparseable date: \"NOT_A_DATE\"");
    }

    /**
     * If we ask for the list of append files and nothing is in the directory,
     * the program will return an empty list.
     */
    @Test
    public void testGetAppendFiles_EdgeCase_NoFiles() throws IOException, ParseException {
        Path path = Path.of("out/DOES_NOT_EXIST");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        assertEquals(List.of(), DatabaseConsolidator.getSortedAppendLogs(path));
    }

    @Test
    public void testConstructorHavingException() {
        var throwingFileUtils = new ThrowingFileUtils();

        assertThrows(IOException.class, () -> new DatabaseConsolidator(Path.of(""), context, throwingFileUtils));
    }

    @Test
    public void testProcessAppendLogFile_Throws() {
        var throwingFileUtils = new ThrowingFileUtils();

        assertThrows(IOException.class, () -> DatabaseConsolidator.processAppendLogFile("", Path.of(""), throwingFileUtils, 100, logger, Path.of("")));
    }

    @Test
    public void testRewriteFiles_Throws() {
        var throwingFileUtils = new ThrowingFileUtils();
        Map<Long, Collection<DatabaseConsolidator.DatabaseChangeInstruction>> groupedInstructions = new HashMap<>();
        groupedInstructions.put(1L, List.of());

        assertThrows(IOException.class, () -> DatabaseConsolidator.rewriteFiles(groupedInstructions, throwingFileUtils, 100, logger, Path.of("")));
    }

    @Test
    public void testReadConsolidatedFileWithChecksum_Throws() {
        var throwingFileUtils = new ThrowingFileUtils();

        assertThrows(IOException.class, () -> DatabaseConsolidator.readConsolidatedFileWithChecksum(Path.of(""), throwingFileUtils));
    }

}
