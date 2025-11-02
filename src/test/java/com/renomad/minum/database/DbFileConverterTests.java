package com.renomad.minum.database;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;

public class DbFileConverterTests {

    private Context context;
    private FileUtils fileUtils;

    @Before
    public void init() {
        var properties = new Properties();
        properties.setProperty("MAX_DATABASE_APPEND_COUNT", "5");
        properties.setProperty("MAX_DATABASE_CONSOLIDATED_FILE_LINES", "5");
        properties.setProperty("DB_DIRECTORY","out/simple_db_for_db_tests");
        properties.setProperty("LOG_LEVELS","DEBUG,ASYNC_ERROR,AUDIT");
        this.context = TestFramework.buildTestingContext("DbFileConverterTests", properties);
        this.fileUtils = new FileUtils(context.getLogger(), context.getConstants());
    }

    @After
    public void cleanup() {
        TestFramework.shutdownTestingContext(this.context);
    }

    /**
     * This test, we'll have our code read from a missing index.ddps file,
     * which should cause it to throw an exception.
     */
    @Test
    public void testConvertClassicFolderStructureToDbEngine2Form_EdgeCase_FileMissing() throws IOException {
        // arrange
        Path dbDirectory = Path.of("out/simple_db/db_file_converter_tests/classic_to_dbe2_file_missing");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbDirectory);
        DbFileConverter dbFileConverter = new DbFileConverter(context, dbDirectory);

        // act
        var ex = assertThrows(FileNotFoundException.class, () -> dbFileConverter.convertClassicFolderStructureToDbEngine2Form());

        // assert
        String adjustedErrorMessage = ex.getMessage().replace('/', '.').replace('\\', '.');
        assertTrue(adjustedErrorMessage.contains("out.simple_db.db_file_converter_tests.classic_to_dbe2_file_missing.index.ddps"), "Message was: " + adjustedErrorMessage);
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
        DbFileConverter dbFileConverter = new DbFileConverter(context, dbDirectory);

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
        DbFileConverter dbFileConverter = new DbFileConverter(context, dbDirectory);

        // act
        var ex = assertThrows(DbException.class, () -> dbFileConverter.convertClassicFolderStructureToDbEngine2Form());

        // assert
        String adjustedErrorMessage = ex.getMessage().replace('/', '.').replace('\\', '.');
        assertEquals(adjustedErrorMessage, "index file for out.simple_db.db_file_converter_tests.corrupted_index returned null when reading a line from it");
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
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbToDbEngine2(path, context.getLogger()));

        // assert
        assertEquals(ex.getMessage(), "Failed during the listing of files during conversion of db to db engine2");
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
        DbFileConverter.extractDataAndAppend(path, logger, path.resolve("1.ddps"));

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
        DbFileConverter.checkFileDetailsAreValid(path, logger);

        assertTrue(logger.doesMessageExist("file exists but empty, skipping"));
    }

    @Test
    public void test_checkFileDetailsAreValid_EdgeCase_EmptyPath() {
        // arrange
        Path path = Path.of(".");
        TestLogger logger = (TestLogger)context.getLogger();

        // act
        var ex = assertThrows(DbException.class, () -> DbFileConverter.checkFileDetailsAreValid(path, logger));

        // assert
        assertEquals(ex.getMessage(), "At checkFileDetailsAreValid, path . is not a regular file");
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
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger()));

        // assert
        assertEquals(ex.getMessage(), "Failed during the listing of files during conversion of db engine2 to db classic");
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
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger()));

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
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger()));

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
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger()));

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
        var ex = assertThrows(DbException.class, () -> DbFileConverter.walkFilesAndConvertDbEngine2ToDbClassic(path, context.getLogger()));

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

        var ex = assertThrows(DbException.class, () -> DbFileConverter.deleteEmptyDbEngine2Directories(Path.of("out/dbconvertertests/testing_edgecase1")));

        assertEquals(ex.getMessage(), "Failed to delete one of the DbEngine2 files");
    }

    /**
     * Once all the files from DbEngine2 have been read, we will know the maximum
     * index value, which we will write as index.ddps.  If we fail to write this for
     * whatever reason, a DbException should be thrown.  One way this could fail is
     * if somehow the index.ddps is already there.
     */
    @Test
    public void testCreatingNewIndexFileAfterConversion() throws IOException {
        Path path = Path.of("out/dbconvertertests/testing_edgecase2");
        fileUtils.deleteDirectoryRecursivelyIfExists(path);
        fileUtils.makeDirectory(path);

        Files.writeString(path.resolve("index.ddps"), "Testing");

        var ex = assertThrows(DbException.class, () -> DbFileConverter.createNewIndexFile(path, 42));
        assertEquals(ex.getMessage(), "Failed to create an index.ddps file");
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

}
