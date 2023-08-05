package minum.web;

import minum.Context;
import minum.logging.ILogger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static minum.utils.Invariants.mustBeTrue;

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
    private final Map<String, String> fileSuffixToMime;

    public StaticFilesCache(Context context) {
        staticResponses = new HashMap<>();
        this.logger = context.getLogger();
        fileSuffixToMime = new HashMap<>();
        addDefaultValuesForMimeMap();
        readExtraMappings(context.getConstants().EXTRA_MIME_MAPPINGS);
    }

    /**
     * These are the default starting values for mappings
     * between file suffixes and appropriate mime types
     */
    private void addDefaultValuesForMimeMap() {
        fileSuffixToMime.put("css", "text/css");
        fileSuffixToMime.put("js", "application/javascript");
        fileSuffixToMime.put("webp", "image/webp");
        fileSuffixToMime.put("jpg", "image/jpeg");
        fileSuffixToMime.put("jpeg", "image/jpeg");
        fileSuffixToMime.put("htm", "text/html; charset=UTF-8");
        fileSuffixToMime.put("html", "text/html; charset=UTF-8");
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
            byte[] fileContents;
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
            if (fileContents == null || fileContents.length == 0) return null;

            // if we do get bytes, figure out the best response headers for it.
            Response result = createStaticFileResponse(file, fileContents);

            logger.logTrace(() -> "Storing in cache - filename: " + file);
            staticResponses.put(file, result);
            return result;
        } catch (IOException ex) {
            logger.logAsyncError(() -> "at getStaticResponse.  Returning null.  Error: " + ex);
            return null;
        }
    }

    /**
     * Given a file type (really, just a file suffix) and its contents, create
     * an appropritate {@link Response} object to be stored in the cache.
     */
    Response createStaticFileResponse(String path, byte[] fileContents) {
        String mimeType = null;

        // if the provided path has a dot in it, use that
        // to obtain a suffix for determining file type
        String fileName = path.toString();
        int suffixBeginIndex = fileName.lastIndexOf('.');
        if (suffixBeginIndex > 0) {
            String suffix = fileName.substring(suffixBeginIndex+1);
            mimeType = fileSuffixToMime.get(suffix);
        }

        // if we don't find any registered mime types for this
        // suffix, or if it doesn't have a suffix, set the mime type
        // to application/octet-stream
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        return createOkResponseForStaticFiles(fileContents, mimeType);
    }

    /**
     * All static responses will get a cache time of 60 seconds
     */
    private Response createOkResponseForStaticFiles(byte[] fileContents, String mimeType) {
        var headers = Map.of("Cache-Control", "max-age=60",
                "Content-Type", mimeType);

        return new Response(
                StatusLine.StatusCode._200_OK,
                headers,
                fileContents);
    }

    /**
     * This getter allows users to add extra mappings
     * between file suffixes and mime types, in case
     * a user needs one that was not provided.
     */
    Map<String,String> getSuffixToMime() {
        return fileSuffixToMime;
    }

    void readExtraMappings(List<String> input) {
        mustBeTrue(input.size() % 2 == 0, "input must be even (key + value = 2 items). Your input: " + input);

        for (int i = 0; i < input.size(); i += 2) {
            String fileSuffix = input.get(i);
            String mime = input.get(i+1);
            logger.logTrace(() -> "Adding mime mapping: " + fileSuffix + " -> " + mime);
            fileSuffixToMime.put(fileSuffix, mime);
        }
    }
}
