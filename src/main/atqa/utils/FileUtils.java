package atqa.utils;

import atqa.logging.ILogger;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileUtils {

    private FileUtils() {
        // private to prevent instantiation.
        // all these methods are static
    }

    public static void writeString(String path, String content) {
        try {
            Files.writeString(Path.of(path), content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteDirectoryRecursivelyIfExists(String path, ILogger logger) throws IOException {
        final var myPath = Path.of(path);
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
     * Read a file
     */
    public static byte[] read(String filename) throws IOException {
        final var file = FileUtils.class.getClassLoader().getResource(filename);
        if (file == null) {
            return null;
        } else {
            try (final var fileStream = file.openStream()) {
                return fileStream.readAllBytes();
            }
        }
    }

    public static List<URL> getResources(String path) throws IOException {
        return Collections.list(FileUtils.class.getClassLoader().getResources(path));
    }

}
