package minum.utils;

import minum.logging.ILogger;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static minum.utils.Invariants.mustNotBeNull;

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
        try {
            final var url = mustNotBeNull(FileUtils.class.getClassLoader().getResource("resources/templates/"));
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
                    final var myPath = fileSystem.getPath("resources/templates/");
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

    public static List<URL> getResources(String path) throws IOException {
        return Collections.list(FileUtils.class.getClassLoader().getResources(path));
    }

}
