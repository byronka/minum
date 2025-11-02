package com.renomad.minum.database;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.renomad.minum.database.DatabaseAppender.simpleDateFormat;

/**
 * Consolidates the database append logs.
 * <br>
 * As the append logs get filled up, the consolidator comes
 * along after to analyze those changes and determine a
 * consolidated version.  For example, if the append logs
 * have three updates for a particular element, then the consolidated file
 * will have just the last update.
 */
final class DatabaseConsolidator {

    /**
     * This is the path to the append-only files, where incoming
     * changes to the data are quickly stored.
     */
    private final Path appendLogDirectory;

    /**
     * This is the path to where we store consolidated data, so that
     * database startup is as fast as possible.
     */
    private final Path consolidatedDataDirectory;

    private final ILogger logger;

    private final int maxLinesPerFile;

    /**
     * This represents an instruction for how to change the overall consolidated
     * database files on disk.  Instructions are either to UPDATE or DELETE. This
     * also encapsulates the data we're updating.
     */
    private record DatabaseChangeInstruction(DatabaseChangeAction action, long dataIndex, String data) {}

    DatabaseConsolidator(Path persistenceDirectory, Context context) {
        this.appendLogDirectory = persistenceDirectory.resolve("append_logs");
        this.consolidatedDataDirectory = persistenceDirectory.resolve("consolidated_data");
        var constants = context.getConstants();
        this.logger = context.getLogger();
        FileUtils fileUtils = new FileUtils(logger, constants);
        fileUtils.makeDirectory(this.consolidatedDataDirectory);
        this.maxLinesPerFile = constants.maxLinesPerConsolidatedDatabaseFile;
    }

    /**
     * Loop through all the append-only files
     */
    void consolidate() throws IOException {
        logger.logDebug(() -> "Starting database consolidator");
        List<Date> sortedList = getSortedAppendLogs(appendLogDirectory);
        if (sortedList.isEmpty()) {
            logger.logDebug(() -> "No database files found to consolidate - exiting");
            return;
        } else {
            logger.logDebug(() -> "Files to consolidate: " + sortedList.stream().map(simpleDateFormat::format).collect(Collectors.joining(";")));
        }

        // process the files in order.  This does potentially cause
        // multiple updates for the consolidated files, but that's
        // safer than building up too large a structure in memory
        // before writing, and in any case, we're prioritizing efficiency
        // so there should only be one write to each file per loop.
        //
        // after each append-only file is fully processed, it gets deleted.
        for (Date date : sortedList) {
            String filename = simpleDateFormat.format(date);
            logger.logDebug(() -> "consolidator processing file " + filename + " in " + appendLogDirectory);
            processAppendLogFile(filename);
            logger.logDebug(() -> "consolidator finished with file " + filename + " in " + appendLogDirectory);
        }
        logger.logDebug(() -> "Database consolidation finished");
    }


    /**
     * The expectation is that after we finish reading the X lines in
     * this append log, we will have a set of clear instructions to
     * apply to our previously consolidated files. There should end up
     * being just one action for each id - update or delete.
     * <br>
     * Build a data structure holding instructions for the next step.
     */
    private void processAppendLogFile(String filename) throws IOException {
        Path fullPathToFile = this.appendLogDirectory.resolve(filename);
        List<String> lines = Files.readAllLines(fullPathToFile);
        Map<Long, DatabaseChangeInstruction> resultingInstructions = new HashMap<>();

        // process each line from the file

        for (String line : lines) {
            DatabaseChangeInstruction databaseChange = parseDatabaseChangeInstructionString(line, filename);

            // the trick here is that by using a Map, only the last item added will remain at the end
            resultingInstructions.put(databaseChange.dataIndex(), databaseChange);
        }

        // now we have the concise list of state changes, but the next step is figuring out how
        // to organize them by their destination.  consolidated files will be grouped somehow.
        // For example, indexes 1 - 1000, 1001-2000, etc (there may be more than 1000 per file).
        // <br>
        // So, we will group our data that way,
        // and then efficiently update the files (a bad outcome, in contrast, would be updating
        // the files multiple times each).

        Map<Long, Collection<DatabaseChangeInstruction>> groupedInstructions = groupInstructionsByPartition(resultingInstructions);

        rewriteFiles(groupedInstructions);

        // delete the file
        Files.delete(fullPathToFile);
    }

    /**
     * Given a {@link Map} of database change instructions, grouped by keys representing the
     * first index in a group of indexes (like 1 to 100, or 101 to 200, etc), write the
     * data to files, with consideration for what might already exist.  That is to say,
     * if we are adding grouped instructions to an existing file such as "1_to_100", then
     * we want to merge our incoming data with what is already there.  Otherwise, we are just
     * creating a new file.
     */
    private void rewriteFiles(Map<Long, Collection<DatabaseChangeInstruction>> groupedInstructions) throws IOException {
        for (Map.Entry<Long, Collection<DatabaseChangeInstruction>> instructions : groupedInstructions.entrySet()) {
            String filename = String.format("%d_to_%d", instructions.getKey(), instructions.getKey() + (maxLinesPerFile - 1));
            logger.logTrace(() -> "Writing consolidated data to " + filename);
            List<String> data;
            // if the file doesn't exist, we'll just start with an empty list. If it
            // does exist, read its lines into a List data structure.
            Path fullPathToConsolidatedFile = this.consolidatedDataDirectory.resolve(filename);
            if (!Files.exists(fullPathToConsolidatedFile)) {
                data = new ArrayList<>();
            } else {
                data = Files.readAllLines(fullPathToConsolidatedFile);
            }

            // update the data in memory per the instructions
            Collection<String> updatedData = updateData(filename, data, instructions.getValue());

            // write the data to disk
            Files.write(fullPathToConsolidatedFile, updatedData, StandardCharsets.US_ASCII);
        }
    }

    /**
     * Here, we have raw lines of data from a file, and a list of instructions for updating
     * that data.  We will organize the raw data better, apply the instructions, and return
     * the updated data
     *
     * @param linesOfData  raw lines of data from a file
     * @param instructions details of how to change the data in the file, either UPDATE or DELETE
     * @return an updated and sorted list of strings (sorted by index, which is the first value on each line)
     */
    static Collection<String> updateData(String filename, List<String> linesOfData, Collection<DatabaseChangeInstruction> instructions) {
        SortedMap<Long, String> result = new TreeMap<>();
        // put the original data into a map
        for (String data : linesOfData) {
            // the first pipe symbol is where the index number ends.  Apologies for
            // the overlap of terms here, index and index.
            int indexOfFirstPipe = data.indexOf('|');
            if (indexOfFirstPipe == -1) {
                throw new DbException(String.format("Error parsing line in file.  File: %s line: %s", filename, data));
            }
            String dataIndexString = data.substring(0, indexOfFirstPipe);
            long dataIndexLong;
            try {
                dataIndexLong = Long.parseLong(dataIndexString);
            } catch (NumberFormatException ex) {
                throw new DbException(String.format("Failed to parse index from line in file. File: %s line: %s", filename, data), ex);
            }
            result.put(dataIndexLong, data);
        }

        // change that data per instructions
        for (DatabaseChangeInstruction instruction : instructions) {
            if (DatabaseChangeAction.UPDATE.equals(instruction.action())) {
                result.put(instruction.dataIndex(), instruction.data());
            } else {
                // only other option is DELETE
                result.remove(instruction.dataIndex());
            }
        }
        return result.values();
    }

    /**
     * This method will group the instructions for changes to the database by which
     * consolidated files they apply to, so that we only need to make one change
     * to each file.  Files are named like this: 1, 1001, etc., or
     * in other words, the starting index of each set of consolidated data.
     * @param databaseChangeInstructionMap this is a map between keys representing the
     *                                     index of the data, and the data itself.
     * @return a map consisting of keys representing the target file for the data, and
     * a collection of DatabaseChangeInstruction data to place in that file.
     */
    private Map<Long, Collection<DatabaseChangeInstruction>> groupInstructionsByPartition(
            Map<Long, DatabaseChangeInstruction> databaseChangeInstructionMap) {

        // initialize a data structure to store our results
        Map<Long, Collection<DatabaseChangeInstruction>> instructionsGroupedByPartition = new HashMap<>();

        // loop through the incoming data, grouping and ordering as necessary
        for (var databaseChangeInstruction : databaseChangeInstructionMap.entrySet()) {

            // determine the expected filename for this file.  For example, if the index is 1234, then
            // the filename should be 1001
            long expectedFilename = (((databaseChangeInstruction.getKey() - 1) / maxLinesPerFile) * maxLinesPerFile) + 1;

            // If there is no key found, we need to add one, and add a new collection
            instructionsGroupedByPartition.computeIfAbsent(expectedFilename, x -> new ArrayList<>());

            // add a new item to the collection for this filename
            instructionsGroupedByPartition.get(expectedFilename).add(databaseChangeInstruction.getValue());
        }

        return instructionsGroupedByPartition;
    }

    /**
     * read first 6 characters - is it update or delete?
     * skip a character
     * read digits until we hit a pipe symbol, that's our index.
     * read the rest of the content
     */
    static DatabaseChangeInstruction parseDatabaseChangeInstructionString(String databaseInstructionString, String filename) {
        String actionString = databaseInstructionString.substring(0, 6);
        DatabaseChangeAction action;
        if ("UPDATE".equals(actionString)) {
            action = DatabaseChangeAction.UPDATE;
        } else if ("DELETE".equals(actionString)) {
            action = DatabaseChangeAction.DELETE;
        } else {
            throw new DbException("Line in append-only log was missing an action (UPDATE or DELETE) in the first characters. Line was: " + databaseInstructionString);
        }
        // confusing overlap of terms - index is used here to mean two things:
        // a) where we find the first pipe symbol
        // b) the index value of the data
        int indexOfPipe = databaseInstructionString.indexOf('|', 7);
        if (indexOfPipe == -1) {
            throw new DbException(
                    "Failed to find index of the first pipe in the file %s, with content %s".formatted(filename, databaseInstructionString));
        }
        String dataIndex = databaseInstructionString.substring(7, indexOfPipe);
        long dataIndexLong = Long.parseLong(dataIndex);

        return new DatabaseChangeInstruction(action, dataIndexLong, databaseInstructionString.substring(7));
    }

    /**
     * Given a directory, convert the list of files into a sorted
     * list of dates.
     * @return a sorted list of dates, or an empty list if nothing found
     */
    static List<Date> getSortedAppendLogs(Path appendLogDirectory) {
        // get the list of file names, which are date-time stamps
        String[] fileList = appendLogDirectory.toFile().list();

        // if there aren't any append-only files, bail out with an empty list
        if (fileList == null) {
            return List.of();
        }

        List<Date> appendLogDates = convertFileListToDateList(fileList);

        // sort
        return appendLogDates.stream().sorted().toList();
    }

    /**
     * Convert a list of filenames to a list of dates
     */
    static List<Date> convertFileListToDateList(String[] listOfFiles) {
        // initialize a list which will hold the dates associated with each file name
        List<Date> appendLogDates = new ArrayList<>();

        // convert the names to dates
        for (String file : listOfFiles) {
            Date date;
            try {
                date = simpleDateFormat.parse(file);
            } catch (ParseException e) {
                throw new DbException(e);
            }
            appendLogDates.add(date);
        }

        return appendLogDates;
    }
}