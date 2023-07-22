package minum.web;

import minum.logging.ILogger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

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
     * in the resources, where we store our static files, like jpegs,
     * css files, scripts
     */
    public static final String STATIC_FILES_DIRECTORY = "static/";
    public static final Pattern badFilePathPatterns = Pattern.compile("//|\\.\\.|:");

    private final Map<String, Response> staticResponses;
    private final ILogger logger;

    public StaticFilesCache(ILogger logger) {
        staticResponses = new HashMap<>();
        this.logger = logger;
    }

    public Response getStaticResponse(String key) {
        return staticResponses.get(key);
    }

    /**
     * Given a file path inside the "static" directory,
     * if we find it we read it from disk, add it to the cache,
     * and return it.  If we don't find it, we return null.
     * <p>
     *     If an IOException is thrown, return null, since the
     *     callers above us know to return 404 or whatever
     *     with that.
     * </p>
     */
    Response loadStaticFile(String file) {
        // if someone tries unusual patterns or path traversal, return null
        if (badFilePathPatterns.matcher(file).find()) return null;

        try {
            String fullFileLocation = STATIC_FILES_DIRECTORY + file;
            byte[] fileContents = null;
            if (Set.of(".css",".js",".webp",".html",".htm").stream().anyMatch(x -> file.contains(x) )) {
                URL resource = StaticFilesCache.class.getClassLoader().getResource(fullFileLocation);

                if (resource == null) {
                    logger.logDebug(() -> "Did not find " + file + " in our resources, returning null Response");
                    return null;
                }

                URI uri = URI.create("");
                try {
                    uri = resource.toURI();
                } catch (URISyntaxException ex) {
                    logger.logDebug(() -> "Exception thrown when converting URI to URL for "+resource+": "+ex);
                }

                if (uri.getScheme().equals("jar")) {
                /*
                This part is necessary because it's the only way we can set up to loop
                through paths (files) later.  That is to say, when we getResource(path), it works fine,
                but if we want to get a list of all the files in a directory inside our jar file,
                we have to do it this way.
                 */
                    try (final var fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                        final var myPath = fileSystem.getPath(fullFileLocation);
                        fileContents = Files.readAllBytes(myPath);
                    }
                } else {
                    final var myPath = Paths.get(uri);
                    fileContents = Files.readAllBytes(myPath);
                }
            }
            if (fileContents == null) return null;

            Response result = createStaticFileResponse(file, fileContents);

            logger.logTrace(() -> "Storing in cache - filename: " + file);
            staticResponses.put(file, result);
            return result;
        } catch (IOException ex) {
            logger.logAsyncError(() -> "at getStaticResponse.  Returning null.  Error: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Given a file type (really, just a file suffix) and its contents, create
     * an appropritate {@link Response} object to be stored in the cache.
     */
    Response createStaticFileResponse(String path, byte[] fileContents) {
        Response result;
        if (path.toString().endsWith(".css")) {
            result = createOkResponseForStaticFiles(fileContents,"Content-Type: text/css");
        } else if (path.toString().endsWith(".js")) {
            result = createOkResponseForStaticFiles(fileContents, "Content-Type: application/javascript");
        } else if (path.toString().endsWith(".webp")) {
            result = createOkResponseForStaticFiles(fileContents, "Content-Type: image/webp");
        } else if (path.toString().endsWith(".html") || path.toString().endsWith(".htm")) {
            result = createOkResponseForStaticFiles(fileContents, "Content-Type: text/html; charset=UTF-8");
        } else {
            throw new RuntimeException("StaticFilesCache cannot handle this file: " + path.toString());
        }
        return result;
    }

    /**
     * All static responses will get a cache time of 60 seconds
     */
    private Response createOkResponseForStaticFiles(byte[] fileContents, String contentTypeHeader) {
        return new Response(
                StatusLine.StatusCode._200_OK,
                List.of(contentTypeHeader, "Cache-Control: max-age=60"),
                fileContents);
    }
}
