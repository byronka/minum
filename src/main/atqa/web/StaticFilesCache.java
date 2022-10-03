package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.FileUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static atqa.utils.Invariants.mustNotBeNull;

/**
 * This is a store of data for any static data we
 * are sending to the client.
 * <p>
 * Static data is just data that rarely changes - images,
 * CSS files, scripts.
 * <p>
 * Since by their nature these files only change between
 * server restarts, it makes plenty sense to put all
 * these data into one place for easy access.
 * <p>
 * the shape of this data is simply key -> value. It's a
 * map, but we wrap it in a custom class just to enable better
 * documentation.
 */
public class StaticFilesCache {

    /**
     * in the resources, where we store our static files
     */
    static final String STATIC_FILES_DIRECTORY = "resources/static/";

    private final Map<String, Response> staticResponses;
    private final ILogger logger;

    public StaticFilesCache(ILogger logger) {
        staticResponses = new HashMap<>();
        this.logger = logger;
    }

    public int getSize() {
        return staticResponses.size();
    }

    public Response getStaticResponse(String key) {
        return staticResponses.get(key);
    }

    public StaticFilesCache loadStaticFiles() throws IOException {
            final var urls = mustNotBeNull(FileUtils.getResources(STATIC_FILES_DIRECTORY));
            for (var url : urls) {
                URI uri = URI.create("");
                try {
                    uri = url.toURI();
                } catch (URISyntaxException ex) {
                    logger.logDebug(() -> "Exception thrown when converting URI to URL for "+url+": "+ex);
                }

                if (uri.getScheme().equals("jar")) {
                    /*
                    This part is necessary because it's the only way we can set up to loop
                    through paths (files) later.  That is to say, when we getResource(path), it works fine,
                    but if we want to get a list of all the files in a directory inside our jar file,
                    we have to do it this way.
                     */
                    try (final var fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                        final var myPath = fileSystem.getPath(STATIC_FILES_DIRECTORY);
                        processPath(myPath);
                    }
                } else {
                    final var myPath = Paths.get(uri);
                    processPath(myPath);
                }
            }
            return this;
        }

    private void processPath(Path myPath) throws IOException {
        try (final var pathsStream = Files.walk(myPath, 1)) {
            for (var path : pathsStream.toList()) {
                final var fileContents = FileUtils.read(STATIC_FILES_DIRECTORY + path.getFileName().toString());
                if (fileContents == null) continue;

                final var filename = path.getFileName().toString();
                Response result;
                if (filename.endsWith(".css")) {
                    result = createOkResponse(fileContents, ContentType.TEXT_CSS);
                } else if (filename.endsWith(".js")) {
                    result = createOkResponse(fileContents, ContentType.APPLICATION_JAVASCRIPT);
                } else if (filename.endsWith(".webp")) {
                    result = createOkResponse(fileContents, ContentType.IMAGE_WEBP);
                } else if (filename.endsWith(".html") || filename.endsWith(".htm")) {
                    result = createOkResponse(fileContents, ContentType.TEXT_HTML);
                } else {
                    result = createNotFoundResponse();
                }

                staticResponses.put(filename, result);
            }
        }
    }

    private Response createNotFoundResponse() {
        return new Response(
                StatusLine.StatusCode._404_NOT_FOUND,
                ContentType.TEXT_HTML,
                "<p>404 not found</p>");
    }

    private Response createOkResponse(byte[] fileContents, ContentType contentType) {
        return new Response(
                StatusLine.StatusCode._200_OK,
                contentType,
                new String(fileContents));
    }
}
