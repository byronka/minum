package com.renomad.minum.database;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFailureException;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FakeFileUtils;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import com.renomad.minum.utils.ThrowingFileUtils;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static com.renomad.minum.testing.TestFramework.*;

public class DbFileConverterTests {

    static private Context context;
    static private IFileUtils fileUtils;
    static private TestLogger logger;

    @BeforeClass
    public static void init() {
        var properties = new Properties();
        properties.setProperty("MAX_DATABASE_APPEND_COUNT", "5");
        properties.setProperty("MAX_DATABASE_CONSOLIDATED_FILE_LINES", "5");
        properties.setProperty("DB_DIRECTORY","out/simple_db_for_db_tests");
        properties.setProperty("LOG_LEVELS","DEBUG,ASYNC_ERROR,AUDIT");
        context = TestFramework.buildTestingContext("DbFileConverterTests", properties);
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
     * This test, we'll have our code read from a missing index.ddps file,
     * which should cause it to throw an exception.
     */
    @Test
    public void testConvertClassicFolderStructureToDbEngine2Form_EdgeCase_FileMissing() throws IOException {
        // arrange
        Path dbDirectory = Path.of("out/simple_db/db_file_converter_tests/classic_to_dbe2_file_missing");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbDirectory);
        DbFileConverter dbFileConverter = new DbFileConverter(context, dbDirectory, fileUtils);

        // act
        assertThrows(FileNotFoundException.class, () -> dbFileConverter.convertClassicFolderStructureToDbEngine2Form());
    }

    /**
     * This test, we'll have a file, currentAppendLog, with invalid data in it, and when
     * we try converting, it will fail with an exception.
     */
    @Test
    public void testConvertDbEngine2FolderStructureToDbClassicForm_EdgeCase_CorruptData() throws IOException {
        // arrange
        Path dbDirectory = Path.of("out/simple_db/db_file_converter_tests/dbe2_to_classic_file_missing");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbDirectory);
        fileUtils.makeDirectory(dbDirectory);
        Files.writeString(dbDirectory.resolve("currentAppendLog"), "UPDATE FOOOO", StandardOpenOption.CREATE);
        DbFileConverter dbFileConverter = new DbFileConverter(context, dbDirectory, fileUtils);

        // act
        var ex = assertThrows(DbException.class, () -> dbFileConverter.convertFolderStructureToDbClassic());

        // assert
        assertTrue(ex.getMessage().contains("Failed to find index of the first pipe in the file"), "Message was: " + ex.getMessage());
    }

    /**
     * This test, we'll have our code read from a corrupted index.ddps file,
     * which should cause it to throw an exception.
     */
    @Test
    public void testConvertClassicFolderStructureToDbEngine2Form_EdgeCase_IndexFileCorrupted() throws IOException {
        // arrange
        Path dbDirectory = Path.of("out/simple_db/db_file_converter_tests/corrupted_index");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbDirectory);
        fileUtils.makeDirectory(dbDirectory);
        Files.writeString(dbDirectory.resolve("index.ddps"), "", StandardOpenOption.CREATE);
        DbFileConverter dbFileConverter = new DbFileConverter(context, dbDirectory, fileUtils);

        // act
        var ex = assertThrows(DbException.class, () -> dbFileConverter.convertClassicFolderStructureToDbEngine2Form());

        // assert
        String adjustedErrorMessage = ex.getMessage().replace('/', '.').replace('\\', '.');
        assertEquals(adjustedErrorMessage, "index file for out.simple_db.db_file_converter_tests.corrupted_index returned null when reading a line from it");
    }

    @Test
    public void testConstructorExceptionThrown() {
        var throwingFileUtils = new ThrowingFileUtils();
        assertThrows(DbException.class, () -> new DbFileConverter(context, Path.of(""), throwingFileUtils));
    }

    /**
     * If we are listing the files in walkFilesAndConvertDbToDbEngine2 and
     * an exception is thrown, it should be handled properly.
     */
    @Test
    public void testDbConverter_WalkingFiles_EdgeCase_FilesListException() throws IOException {
        // arrange
        Path path = Path.of("out/sample_file_for_test_gggfff");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        Files.writeString(path, "hello world", StandardOpenOption.CREATE);

        // act
        assertThrows(NotDirectoryException.class, () -> DbFileConverter.walkFilesAndConvertDbToDbEngine2(path, context.getLogger(), fileUtils));
    }

    /**
     * If we encounter a file that is empty while converting from DbClassic to
     * DbEngine2, just skip its data and delete it.
     */
    @Test
    public void testDbConverter_WalkingFiles_EdgeCase_FileEmpty() throws IOException {
        // arrange
        Path path = Path.of("out/sample_file_for_test_def456");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        fileUtils.makeDirectory(path);

        Files.writeString(path.resolve("1.ddps"), "", StandardOpenOption.CREATE);

        // act
        TestLogger logger = (TestLogger)context.getLogger();
        DbFileConverter.extractDataAndAppend(path, logger, path.resolve("1.ddps"), fileUtils);

        // assert
        assertTrue(logger.doesMessageExist("1.ddps file exists but empty, skipping"));
    }

    @Test
    public void test_checkFileDetailsAreValid_EdgeCase_EmptyFile() throws IOException {
        // arrange
        Path path = Path.of("out/sample_file_for_test_lllllkkkk");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        Files.writeString(path, "", StandardOpenOption.CREATE);

        // act
        TestLogger logger = (TestLogger)context.getLogger();
        DbFileConverter.checkFileDetailsAreValid(path, logger, fileUtils);

        assertTrue(logger.doesMessageExist("file exists but empty, skipping"));
    }

    /**
     * If while walking the files we encounter an unusual file, like for example a directory,
     * then an exception should be thrown.
     */
    @Test
    public void test_checkFileDetailsAreValid_EdgeCase_NonRegularFile() throws IOException {
        // arrange
        Path path = Path.of("out/dbconvertertests/test_checkFileDetailsAreValid_EdgeCase_NonRegularFile");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        Files.createDirectories(path);

        // act
        TestLogger logger = (TestLogger)context.getLogger();
        var ex = assertThrows(DbException.class, () -> DbFileConverter.checkFileDetailsAreValid(path, logger, fileUtils));
        assertTrue(ex.getMessage().contains("test_checkFileDetailsAreValid_EdgeCase_NonRegularFile is not a regular file"), "message was " + ex.getMessage());
    }

    /**
     * If we are listing the files in walkFilesAndConvertDbEngine2ToDbClassic and
     * an exception is thrown, it should be handled properly.
     */
    @Test
    public void testDbConverter_walkFilesAndConvertDbEngine2ToDbClassic_EdgeCase_FilesListException() throws IOException {
        // arrange
        Path path = Path.of("out/sample_file_for_test_dddddtttt");
        Files.deleteIfExists(path);
        Files.writeString(path, "hello world", StandardOpenOption.CREATE);

        // act
        try {
            DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger(), fileUtils);
            throw new TestFailureException("Should not have gotten here.");
        } catch (IOException e) {
            // all good at this point.
        }
    }

    /**
     * When reviewing the current DbEngine2 files, the expected file format is x_to_y, meaning
     * the contained data is from index x to index y.  If we see files that don't match
     * that format, throw an exception
     */
    @Test
    public void testDbConverter_walkFilesAndConvertDbEngine2ToDbClassic_EdgeCase_MalformedFilenames() throws IOException {
        // arrange
        Path path = Path.of("out/simple_db_for_engine2_tests/engine2/conversiontest/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        fileUtils.makeDirectory(path.resolve("consolidated_data"));
        Files.writeString(path.resolve("consolidated_data").resolve("foofoo"), "hello world", StandardOpenOption.CREATE);
        Files.writeString(path.resolve("consolidated_data").resolve("foofoo2"), "hello world", StandardOpenOption.CREATE);

        // act
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger(), fileUtils));

        assertEquals(ex.getMessage(), "Error: Failed to find first underscore in filename: foofoo2");
    }

    /**
     * This is similar to {@link #testDbConverter_walkFilesAndConvertDbEngine2ToDbClassic_EdgeCase_MalformedFilenames}
     * except that we are unable to convert the x in x_to_y to a number.
     */
    @Test
    public void testDbConverter_walkFilesAndConvertDbEngine2ToDbClassic_EdgeCase_MalformedFilenamesNumber() throws IOException {
        // arrange
        Path path = Path.of("out/simple_db_for_engine2_tests/engine2/conversiontest2/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        fileUtils.makeDirectory(path.resolve("consolidated_data"));
        Files.writeString(path.resolve("consolidated_data").resolve("a_to_b"), "hello world", StandardOpenOption.CREATE);
        Files.writeString(path.resolve("consolidated_data").resolve("c_to_d"), "hello world", StandardOpenOption.CREATE);

        // act
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger(), fileUtils));

        assertTrue(ex.getMessage().contains("Failed to convert first part of filename to a number: "), "Message was: " + ex.getMessage());
    }

    /**
     * Here we are converting the inner content of the DbEngine2 files, but find that
     * the inner data isn't formatted correctly
     */
    @Test
    public void testDbConverter_walkFilesAndConvertDbEngine2ToDbClassic_EdgeCase_MalformedData() throws IOException {
        // arrange
        Path path = Path.of("out/simple_db_for_engine2_tests/engine2/conversiontest3/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        fileUtils.makeDirectory(path.resolve("consolidated_data"));
        Files.writeString(path.resolve("consolidated_data").resolve("1_to_5"), "hello world", StandardOpenOption.CREATE);
        Files.writeString(path.resolve("consolidated_data").resolve("6_to_10"), "hello world", StandardOpenOption.CREATE);

        // act
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger(), fileUtils));

        String adjustedMessage = ex.getMessage().replace('/', '.').replace('\\', '.');
        assertEquals(adjustedMessage, "Unable to convert a line - check for corruption.  File: out.simple_db_for_engine2_tests.engine2.conversiontest3.consolidated_data.1_to_5 Data: hello world");
    }

    /**
     * Here we are converting the inner content of the DbEngine2 files, but find that
     * the inner data isn't formatted correctly
     */
    @Test
    public void testDbConverter_walkFilesAndConvertDbEngine2ToDbClassic_EdgeCase_MalformedData2() throws IOException {
        // arrange
        Path path = Path.of("out/simple_db_for_engine2_tests/engine2/conversiontest4/");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        fileUtils.makeDirectory(path.resolve("consolidated_data"));
        Files.writeString(path.resolve("consolidated_data").resolve("1_to_5"), "a|b|c|d", StandardOpenOption.CREATE);
        Files.writeString(path.resolve("consolidated_data").resolve("6_to_10"), "a|b|c|d", StandardOpenOption.CREATE);

        // act
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger(), fileUtils));

        String adjustedMessage = ex.getMessage().replace('/', '.').replace('\\', '.');
        assertEquals(adjustedMessage, "Unable to convert a line - check for corruption.  File: out.simple_db_for_engine2_tests.engine2.conversiontest4.consolidated_data.1_to_5 Data: a|b|c|d");
    }

    /**
     * When converting from DbEngine2 to Db Classic, there is no fast and easy way to tell
     * how many data items need to be converted before we get through reading it all.  This
     * is contrasting to the other direction, where we store the current maximum index in
     * a file called "index.ddps".
     * <br>
     * In any case, we want to see an output along the way.  Currently, the code is
     * configured to output a log every 1000 entries converted.  The code for this is
     * written to be more easily tested, by adjusting to any modulo value.
     */
    @Test
    public void testLogOutputAlongConversion() {
        DbFileConverter.logAlongConversion(context.getLogger(), 5, 5);
        assertTrue(((TestLogger)context.getLogger()).doesMessageExist("DbFileConverter has converted 5 files to Db Classic form"));
    }

    @Test
    public void testLogOutputAlongConversion_NegativeCase() {
        DbFileConverter.logAlongConversion(context.getLogger(), 4, 5);
        assertThrows(TestLoggerException.class, () -> ((TestLogger)context.getLogger()).doesMessageExist("DbFileConverter has converted 5 files to Db Classic form"));
    }

    /**
     * If we try deleting directories at the end of the conversion from DbEngine2 to
     * DbClassic, an exception should be thrown.
     */
    @Test
    public void testDeletingDirectories_EdgeCase_ExceptionOccurs() throws IOException {
        Path path = Path.of("out/dbconvertertests/testing_edgecase1/consolidated_data");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        fileUtils.makeDirectory(path);
        Files.writeString(path.resolve("bar"), "Testing");

        var ex = assertThrows(DirectoryNotEmptyException.class, () -> DbFileConverter.deleteEmptyDbEngine2Directories(Path.of("out/dbconvertertests/testing_edgecase1"), fileUtils));
    }

    /**
     * If the index in the file contents doesn't correspond to the filename,
     * we're dealing with corrupt data.
     */
    @Test
    public void testDeserializeDataFromDbFile_EdgeCase_ContentIndexConflictsWithFilename() {
        var ex = assertThrows(DbException.class, () -> DbFileConverter.deserializeDataFromDbFile("1.ddps", 1, "2||"));
        assertEquals(ex.getMessage(), "The filename (1.ddps) must correspond to the index in its contents (2)");
    }

    /**
     * If the prefix of the filename isn't a valid number ("42.ddps" is valid),
     * then this method will fail on parsing.
     */
    @Test
    public void testDeserializeDataFromDbFile_EdgeCase_InvalidFilename() {
        var ex = assertThrows(NumberFormatException.class, () -> DbFileConverter.deserializeDataFromDbFile("a.ddps", 1, ""));
        assertEquals(ex.getMessage(), "For input string: \"a\"");
    }

    @Test
    public void testIsDataFile_Filtering() throws IOException {
        Path myPath = Path.of("out/simple_db/db_file_converter_tests/testing_is_data_file");
        fileUtils.deleteDirectoryRecursivelyIfExists(myPath);

        // make directories
        fileUtils.makeDirectory(myPath);
        fileUtils.makeDirectory(myPath.resolve("baz"));
        fileUtils.makeDirectory(myPath.resolve("baz.checksum"));

        // write some regular files
        Path dataFile = myPath.resolve("1_to_100");
        Path checksumFile = myPath.resolve("1_to_100.checksum");
        Files.writeString(dataFile, "foo");
        Files.writeString(checksumFile, "bar");


        assertTrue(DbFileConverter.isDataFile(dataFile, fileUtils));
        assertFalse(DbFileConverter.isDataFile(checksumFile, fileUtils));
        assertFalse(DbFileConverter.isDataFile(myPath.resolve("baz"), fileUtils));
        assertFalse(DbFileConverter.isDataFile(myPath.resolve("baz.checksum"), fileUtils));
    }

    @Test
    public void testWalkFilesAndConvertDbToDbEngine2_NegativeCase_ExceptionThrown() {
        var myFileUtils = new FakeFileUtils();
        myFileUtils.deleteShouldThrow = true;

        assertThrows(IOException.class, () -> DbFileConverter.walkFilesAndConvertDbToDbEngine2(Path.of(""), logger, myFileUtils));
    }

    @Test
    public void testExtractDataAndAppend_NegativeCase_ExceptionThrown() {
        var myFileUtils = new FakeFileUtils();
        myFileUtils.deleteShouldThrow = true;
        myFileUtils.isRegularFileValue = true;
        myFileUtils.readStringValue = "1|foo";
        myFileUtils.writeStringShouldThrow = true;

        assertThrows(IOException.class, () -> DbFileConverter.extractDataAndAppend(Path.of(""), logger, Path.of("1.ddps"), myFileUtils));
    }

    @Test
    public void testCheckFileDetailsAreValid_NegativeCase_ExceptionThrown() {
        var myFileUtils = new FakeFileUtils();
        myFileUtils.deleteShouldThrow = true;
        myFileUtils.isRegularFileValue = true;
        myFileUtils.readStringShouldThrow = true;

        assertThrows(IOException.class, () -> DbFileConverter.checkFileDetailsAreValid(Path.of(""), logger, myFileUtils));
    }


}
