package minum.web;

import minum.logging.ILogger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
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
     * and return it.  If we don't fine it, we return null.
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
            Path path = Path.of(STATIC_FILES_DIRECTORY).resolve(file);
            byte[] fileContents = null;
            if (Set.of(".css",".js",".webp",".html",".htm").stream().anyMatch(x -> path.getFileName().toString().contains(x) )) {
                URL resource = StaticFilesCache.class.getClassLoader().getResource(path.toString());
                fileContents = Files.readAllBytes(Paths.get(resource.toURI()));
            }
            if (fileContents == null) return null;

            Response result = createStaticFileResponse(path, fileContents);

            String route = getRoute(path);

            logger.logTrace(() -> "Storing in cache - filename: " + route);
            staticResponses.put(route, result);
            return result;
        } catch (IOException | URISyntaxException ex) {
            logger.logAsyncError(() -> "at getStaticResponse.  Returning null.  Error: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Given a file type (really, just a file suffix) and its contents, create
     * an appropritate {@link Response} object to be stored in the cache.
     */
    Response createStaticFileResponse(Path path, byte[] fileContents) {
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
     * This crappy little method exists to get a consistent route to a
     * static file, regardless of if we are running in a Zipfile, on a
     * Windows machine, on Unix, etc.
     */
    private static String getRoute(Path path) {
        // I imagine this function will be an endless source of mirth
        String path2 = path.toUri().getPath();
        String path1 = path2 == null ? path.toString() : path2;
        int indexToStartSubstring = path1.indexOf("static/") + "static/".length();
        return path1.substring(indexToStartSubstring);
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
