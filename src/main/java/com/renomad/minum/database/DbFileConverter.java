package com.renomad.minum.database;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.IFileUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.renomad.minum.utils.Invariants.mustBeFalse;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.*;

/**
 * This class exists to handle converting from one file/folder
 * style of database to another.
 */
final class DbFileConverter {

    private final Path dbDirectory;
    private final ILogger logger;
    private final DatabaseAppender databaseAppender;
    private final DatabaseConsolidator databaseConsolidator;
    private final IFileUtils fileUtils;

    /**
     * Construct a converter instance
     * @param context this is used for its constants, logger, and so on.
     * @param dbDirectory this is the specific directory for this database,
     *                    for example, the full path to the "users" database directory.
     */
    DbFileConverter(Context context, Path dbDirectory, IFileUtils fileUtils) {
        this.dbDirectory = dbDirectory;
        this.logger = context.getLogger();
        this.fileUtils = fileUtils;
        try {
            this.databaseAppender = new DatabaseAppender(dbDirectory, context, fileUtils);
            this.databaseConsolidator = new DatabaseConsolidator(dbDirectory, context, fileUtils);
        } catch (IOException ex) {
            throw new DbException("Error in DbFileConverter constructor", ex);
        }

    }

    /**
     * convert a directory of database files from the old Db classic form to the DbEngine2 form.
     * The old form was one file per data item, the new form has append-only
     * data logs and consolidated files.
     */
    void convertClassicFolderStructureToDbEngine2Form() throws IOException {
        displayWarningConvertingClassicToDbEngine2();
        try (var fileReader = new FileReader(dbDirectory.resolve("index.ddps").toFile(), US_ASCII);
             BufferedReader br = new BufferedReader(fileReader)) {
            String s = br.readLine();
            if (s == null)
                throw new DbException("index file for " + dbDirectory + " returned null when reading a line from it");
            mustBeFalse(s.isBlank(), "Unless something is terribly broken, we expect a numeric value here");
        }

        walkFilesAndConvertDbToDbEngine2(this.dbDirectory, this.logger, fileUtils);
    }



    private void displayWarningConvertingClassicToDbEngine2() {
        logger.logDebug(() -> "*****************************************************************");
        logger.logDebug(() -> "*****************************************************************");
        logger.logDebug(() -> "........");
        logger.logDebug(() -> "About to convert database files from Db Classic to Db Engine 2");
        logger.logDebug(() -> "........");
        logger.logDebug(() -> "*****************************************************************");
        logger.logDebug(() -> "*****************************************************************");
    }

    private void displayWarningConvertingDbEngine2ToClassic() {
        logger.logDebug(() -> "*****************************************************************");
        logger.logDebug(() -> "*****************************************************************");
        logger.logDebug(() -> "........");
        logger.logDebug(() -> "About to convert database files from Db Engine2 to Db Classic");
        logger.logDebug(() -> "........");
        logger.logDebug(() -> "*****************************************************************");
        logger.logDebug(() -> "*****************************************************************");
    }

    /**
     * walk through all the files in this directory, collecting
     * all regular files (non-subdirectories) except for index.ddps
     */
    static void walkFilesAndConvertDbToDbEngine2(Path dbDirectory, ILogger logger, IFileUtils fileUtils) throws IOException {
        List<Path> listOfFiles = getListOfFiles(dbDirectory, fileUtils);
        // convert each file to the new database schema by appending it
        // to the append log, and then delete it
        for (int i = 0; i < listOfFiles.size(); i++) {
            int percentCompletion = listOfFiles.size() / 100;
            if (i == percentCompletion) {
                logger.logDebug(() -> "File converting is %d percent complete".formatted(percentCompletion));
            }
            Path p = extractDataAndAppend(dbDirectory, logger, listOfFiles.get(i), fileUtils);
            fileUtils.delete(p);
        }

        // at this point, after all the ordinary files have been removed, kill the index file
        fileUtils.delete(dbDirectory.resolve("index.ddps"));
    }

    /**
     * This method digs into the Db Classic file, checks everything is kosher, and
     * if so, appends it to the append-only log file which is part of DbEngine2
     */
    static Path extractDataAndAppend(Path dbDirectory, ILogger logger, Path fileToAnalyze, IFileUtils fileUtils) throws IOException {
        String fileContents = checkFileDetailsAreValid(fileToAnalyze, logger, fileUtils);
        if (!fileContents.isBlank()) {
            fileUtils.writeString(
                dbDirectory.resolve("currentAppendLog"),
                "UPDATE %s\n".formatted(fileContents), APPEND, CREATE);
        }
        return fileToAnalyze;
    }

    /**
     * Get the files that make up the file schema of Db Classic
     */
    private static List<Path> getListOfFiles(Path dbDirectory, IFileUtils fileUtils) throws IOException {
        List<Path> listOfFiles;
        try (Stream<Path> fileStream = fileUtils.list(dbDirectory)) {
            listOfFiles = fileStream.filter(path ->
                            fileUtils.isRegularFile(path) &&
                                    path.getFileName().toString().endsWith(".ddps") &&
                                    !path.getFileName().toString().startsWith("index"))
                    .toList();
        }
        return listOfFiles;
    }

    /**
     * This code inspects that the data in the file is valid
     * and returns the data. This method is called as part of
     * convert Db Classic to DbEngine2
     */
    static String checkFileDetailsAreValid(Path p, ILogger logger, IFileUtils fileUtils) throws IOException {
        if (!fileUtils.isRegularFile(p)) {
            throw new DbException("At checkFileDetailsAreValid, path " + p + " is not a regular file");
        }
        String fileName = p.getFileName().toString();
        int startOfSuffixIndex = fileName.indexOf('.');
        String fileContents = fileUtils.readString(p);
        if (fileContents.isBlank()) {
            logger.logDebug( () -> fileName + " file exists but empty, skipping");
            return "";
        } else {
            return deserializeDataFromDbFile(fileName, startOfSuffixIndex, fileContents);
        }
    }

    /**
     * While converting Db Classic files to DbEngine2, each data file will be inspected.
     * Here, we are looking at the contents of the files.
     */
    static String deserializeDataFromDbFile(String filename, int startOfSuffixIndex, String fileContents) {
        int fileNameIdentifier = Integer.parseInt(filename.substring(0, startOfSuffixIndex));
        int indexOfFirstPipe = fileContents.indexOf('|');
        String indexString = fileContents.substring(0, indexOfFirstPipe);
        long index = Long.parseLong(indexString);
        if (index != fileNameIdentifier) {
            throw new DbException( "The filename (%s) must correspond to the index in its contents (%d)"
                    .formatted(filename, index));
        }
        return fileContents;
    }

    /**
     * Convert the folder/file structure.  From DbEngine2 format to Db classic.
     */
    void convertFolderStructureToDbClassic() throws IOException, ParseException {
        displayWarningConvertingDbEngine2ToClassic();

        // if there are any remnant items in the current append-only file, move them
        // to a new file
        databaseAppender.saveOffCurrentDataToReadyFolder();

        // consolidate whatever files still exist in the append logs
        databaseConsolidator.consolidate();

        // at this point, all the data is consolidated, in order, so we can step
        // through the data, creating new files, and finish up with an index.ddps
        // set to the proper value (i.e. one greater than the max index in the database)
        walkFilesAndConvertDbEngine2ToDbClassic(this.dbDirectory, this.logger, fileUtils);
    }

    /**
     * This is a pretty intricate method.  It basically steps through the consolidated
     * data files, writing each line to a new file.  It has to handle this through
     * multiple files and deleting files when finished, and closing file handles
     * appropriately when done with a file, and doing it in the proper order.
     */
    static void walkFilesAndConvertDbEngine2ToDbClassic(Path dbDirectory, ILogger logger, IFileUtils fileUtils) throws IOException {
        // get the list of consolidated data files
        List<Path> listOfFiles;
        try (Stream<Path> fileStream = fileUtils.list(dbDirectory.resolve("consolidated_data"))) {
            listOfFiles = new ArrayList<>(fileStream.filter(x -> DbFileConverter.isDataFile(x, fileUtils)).toList());
        }

        // sort the data in ascending order.  Files like "1_to_10" will come before "11_to_20"
        listOfFiles.sort(Comparator.comparing(x -> {
            String filename = x.getFileName().toString();
            int indexOfFirstUnderscore = filename.indexOf('_');
            if (indexOfFirstUnderscore == -1) {
                throw new DbException("Error: Failed to find first underscore in filename: " + filename);
            }
            String firstIndexNumberOfFile = filename.substring(0, indexOfFirstUnderscore);
            try {
                return Long.parseLong(firstIndexNumberOfFile);
            } catch (NumberFormatException ex) {
                throw new DbException("Failed to convert first part of filename to a number: " + filename);
            }
        }));

        // initialize a variable to record the current maximum index value
        long currentMaxIndexValue = -1;

        // initialize a variable to record the count of data items converted to Db Classic
        long countConvertedFiles = 0;

        // convert each line of each file to its own file, per the needs of Db Classic
        for (Path filePath : listOfFiles) {
            try (BufferedReader reader = fileUtils.newBufferedReader(filePath.toFile().toPath(), US_ASCII)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int i = line.indexOf('|');
                    if (i == -1) {
                        throw new DbException(("Unable to convert a line - check for " +
                                "corruption.  File: %s Data: %s").formatted(filePath, line));
                    }
                    String indexNumberString = line.substring(0, i);
                    long indexNumber;
                    try {
                        indexNumber = Long.parseLong(indexNumberString);
                        currentMaxIndexValue = Math.max(indexNumber, currentMaxIndexValue);
                    } catch (NumberFormatException ex) {
                        throw new DbException(("Unable to convert a line - check for " +
                                "corruption.  File: %s Data: %s").formatted(filePath, line));
                    }
                    String dbFilename = indexNumber + ".ddps";
                    Path dbFullPath = dbDirectory.resolve(dbFilename);
                    fileUtils.writeString(dbFullPath, line, CREATE, WRITE);
                    countConvertedFiles += 1;
                    logAlongConversion(logger, countConvertedFiles, 1000);
                }
                // now we've converted everything from this file, delete it and its checksum (if available)
                fileUtils.delete(filePath);
                Path checksumPath = filePath.resolveSibling(filePath.getFileName() + ".checksum");
                fileUtils.deleteIfExists(checksumPath);
            }
        }

        deleteEmptyDbEngine2Directories(dbDirectory, fileUtils);
        fileUtils.writeString(dbDirectory.resolve("index.ddps"), String.valueOf(currentMaxIndexValue + 1), CREATE_NEW);
    }

    /**
     * A predicate used to filter for just regular database files, not checksum files
     */
    static boolean isDataFile(Path path, IFileUtils fileUtils) {
        return fileUtils.isRegularFile(path) && !path.toString().contains("checksum");
    }

    /**
     * This method is called after conversion to Db classic, at which point these directories
     * will be empty.
     */
    static void deleteEmptyDbEngine2Directories(Path dbDirectory, IFileUtils fileUtils) throws IOException {
        fileUtils.deleteIfExists(dbDirectory.resolve("consolidated_data"));
        fileUtils.deleteIfExists(dbDirectory.resolve("currentAppendLog"));
        fileUtils.deleteIfExists(dbDirectory.resolve("append_logs"));
    }

    /**
     * A helper to choose when to output logging statements during the conversion
     * of files from one database file format to another.
     * @param countConvertedFilesModulo modulo this number at which a log statement will be output.
     */
    static void logAlongConversion(ILogger logger, long countConvertedFiles, int countConvertedFilesModulo) {
        if (countConvertedFiles % countConvertedFilesModulo == 0) {
            logger.logDebug(() -> "DbFileConverter has converted %d files to Db Classic form"
                    .formatted(countConvertedFiles));
        }
    }
}
