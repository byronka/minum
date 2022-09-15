package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

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

}
