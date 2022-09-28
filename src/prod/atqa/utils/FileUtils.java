package atqa.utils;

import atqa.logging.ILogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .peek(x -> logger.logDebug(() -> "deleting " + x))
                        .forEach(File::delete);
            }
        }
    }

}
