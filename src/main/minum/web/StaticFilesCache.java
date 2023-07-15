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

import static minum.utils.Invariants.mustNotBeNull;

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

    /**
     * Let's put a nice cap on this - no reason to go crazy with it
     */
    public static final int MAXIMUM_FILE_SEARCH_DEPTH = 5;
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
     * This provides all the static files (e.g. .html, .css) in the resources/static directory
     */
    public StaticFilesCache loadStaticFiles() throws IOException {
        final List<URL> urls = mustNotBeNull(
                Collections.list(
                        StaticFilesCache.class.getClassLoader().getResources(STATIC_FILES_DIRECTORY)));
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

    /**
     * Given a path to scan, walk through the files (up to MAXIMUM_FILE_SEARCH_DEPTH deep)
     * and add them to our cache.
     */
    void processPath(Path myPath) throws IOException {
        try (final var pathsStream = Files.walk(myPath, MAXIMUM_FILE_SEARCH_DEPTH)) {
            for (var path : pathsStream.toList()) {
                byte[] fileContents = null;
                if (Set.of(".css",".js",".webp",".html",".htm").stream().anyMatch(x -> path.getFileName().toString().contains(x) )) {
                    fileContents = Files.readAllBytes(path);
                }
                if (fileContents == null) continue;

                Response result = createStaticFileResponse(path, fileContents);

                String route = getRoute(path);

                logger.logTrace(() -> "Storing in cache - filename: " + route);
                staticResponses.put(route, result);
            }
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
