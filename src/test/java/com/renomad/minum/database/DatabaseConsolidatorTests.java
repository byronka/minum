package com.renomad.minum.database;

import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;

public class DatabaseConsolidatorTests {

    private FileUtils fileUtils;
    private Context context;

    @Before
    public void init() {
        this.context = TestFramework.buildTestingContext("DatabaseConsolidatorTests");
        this.fileUtils = new FileUtils(context.getLogger(), context.getConstants());
    }

    @After
    public void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

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
        var ex = assertThrows(DbException.class, () -> DatabaseConsolidator.convertFileListToDateList(new String[]{"NOT_A_DATE"}));
        assertEquals(ex.getMessage(), "java.text.ParseException: Unparseable date: \"NOT_A_DATE\"");
    }

    /**
     * If we ask for the list of append files and nothing is in the directory,
     * the program will return an empty list.
     */
    @Test
    public void testGetAppendFiles_EdgeCase_NoFiles() {
        Path path = Path.of("out/DOES_NOT_EXIST");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        assertEquals(List.of(), DatabaseConsolidator.getSortedAppendLogs(path));
    }
}
