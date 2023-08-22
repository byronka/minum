package minum.utils;

import minum.logging.ILogger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

import static minum.utils.Invariants.mustNotBeNull;

/**
 * Helper functions for working with files.
 */
public class FileUtils {

    private FileUtils() {
        // private to prevent instantiation.
        // all these methods are static
    }

    /**
     * Write a string to a path on disk.
     */
    public static void writeString(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes a directory, deleting everything inside it
     * recursively afterwards.  A more dangerous method than
     * many others, take care.
     */
    public static void deleteDirectoryRecursivelyIfExists(Path myPath, ILogger logger) throws IOException {
        if (Files.exists(myPath)) {
            try (Stream<Path> walk = Files.walk(myPath)) {

                final var files = walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile).toList();

                for(var file: files) {
                    logger.logDebug(() -> "deleting " + file);
                    final var result = Files.deleteIfExists(file.toPath());
                    if (! result) {
                        logger.logDebug(() -> "failed to delete " + file);
                    }
                }
            }
        }
    }

    /**
     * Read a file as a string from the resources/templates directory.
     * Any files placed in subdirectories there will need to specify
     * those subdirectories here, but resources/templates is prepended.
     */
    public static String readTemplate(String filename) {
        return readFile(filename, "templates/");
    }

    /**
     * Read a file as a string from the resources/ directory.
     * Any files placed in subdirectories there will need to specify
     * those subdirectories here, but resources/ is prepended.
     */
    public static String readFile(String filename) {
        return readFile(filename, "");
    }

    /**
     * Reads files that are stored in the resources directory,
     * whether that is in a regular directory or in a jar file.
     */
    private static String readFile(String filename, String templatesDirectory) {
        try {
            final var url = mustNotBeNull(FileUtils.class.getClassLoader().getResource(templatesDirectory));
            URI uri = url.toURI();

            String result;
            if (uri.getScheme().equals("jar")) {
                /*
                This part is necessary because it's the only way we can set up to loop
                through paths (files) later.  That is to say, when we getResource(path), it works fine,
                but if we want to get a list of all the files in a directory inside our jar file,
                we have to do it this way.
                 */
                try (final var fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                    final var myPath = fileSystem.getPath(templatesDirectory);
                    result = Files.readString(myPath.resolve(filename));
                }
            } else {
                final var myPath = Paths.get(uri);
                result = Files.readString(myPath.resolve(filename));
            }
            return result;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * Creates a directory if it doesn't already exist.
     * <br>
     * If the directory does exist, the program will simply skip
     * building it, and mention it in the logs.
     * @throws IOException needs to get handled.
     */
    public static void makeDirectory(ILogger logger, Path directory) throws IOException {
        logger.logDebug(() -> "Creating a directory " + directory);
        boolean directoryExists = Files.exists(directory);
        logger.logDebug(() -> "Directory: " + directory + ". Already exists: " + directory);
        if (!directoryExists) {
            logger.logDebug(() -> "Creating directory, since it does not already exist: " + directory);
            Files.createDirectories(directory);
            logger.logDebug(() -> "Directory: " + directory + " created");
        }
    }

}
