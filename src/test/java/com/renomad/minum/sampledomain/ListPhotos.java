package com.renomad.minum.sampledomain;

import com.renomad.minum.sampledomain.auth.AuthUtils;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.FileReader;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.LRUCache;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.FileUtils.badFilePathPatterns;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class ListPhotos {

    private final TemplateProcessor listPhotosTemplateProcessor;
    private final ILogger logger;
    private final Path dbDir;

    private final UploadPhoto up;
    private final AuthUtils auth;
    private final Map<String, byte[]> lruCache;
    private final FileReader fileReader;
    private final long staticFileCacheTime;

    public ListPhotos(Context context, UploadPhoto up, AuthUtils auth) {
        this.logger = context.getLogger();
        Constants constants = context.getConstants();
        FileUtils fileUtils = new FileUtils(logger, constants);
        this.dbDir = Path.of(constants.dbDirectory);
        this.staticFileCacheTime = constants.staticFileCacheTime;
        listPhotosTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/listphotos/list_photos_template.html"));
        this.up = up;
        this.auth = auth;
        this.lruCache = LRUCache.getLruCache();
        this.fileReader = new FileReader(lruCache, true, logger);
    }

    public Response ListPhotosPage(Request r) {

        String photoHtml = up.getPhotographs().stream().map(x ->
        """
        <li>
            <img src="photo?name=%s" alt="photo alt text" title="photo title text" />
            <p>%s</p>
            <p>%s</p>
        </li>
        """.formatted(x.getPhotoUrl(), x.getShortDescription(), x.getDescription())).collect(Collectors.joining ("\n"));

        String navBar = auth.processAuth(r).isAuthenticated() ? "<p><a href=\"logout\">Logout</a></p><p><a href=\"upload\">Upload</a></p>" : "";

        String listPhotosHtml = listPhotosTemplateProcessor.renderTemplate(Map.of(
                "nav_bar", navBar,
                "photo_html", photoHtml
        ));
        return Response.htmlOk(listPhotosHtml);
    }

    public Response ListPhotosPage2(Request r) {

        String photoHtml = up.getPhotographs().stream().map(x ->
        """
        <li>
            <img src="/photo?name=%s"
                alt="photo alt text"
                title="photo title text" />
            <p>%s</p>
            <p>%s</p>
        </li>
        """.formatted(x.getPhotoUrl(), x.getShortDescription(), x.getDescription())).collect(Collectors.joining ("\n"));

        String navBar;
        List<String> headerStrings = r.headers().valueByKey("x-preapproved");
        if (headerStrings != null && headerStrings.contains("true")) {
            navBar = "<p><a href=\"/logout\">Logout</a></p><p><a href=\"/upload\">Upload</a></p>";
        } else {
            navBar = "";
        }

        String listPhotosHtml = listPhotosTemplateProcessor.renderTemplate(Map.of(
                "nav_bar", navBar,
                "photo_html", photoHtml
        ));
        return Response.htmlOk(listPhotosHtml);
    }

    /**
     * Like you would think - a way to read a photo from disk to put on the wire
     */
    public Response grabPhoto(Request r) {
        String filename = r.requestLine().queryString().get("name");
        logger.logAudit(() -> r.remoteRequester() + " is looking for a photo named " + filename);

        // if the name query is null or blank, return 404
        if (filename == null || filename.isBlank()) {
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // first, is it already in our cache?
        Path photoPath = dbDir.resolve("photo_files").resolve(filename);
        if (lruCache.containsKey(photoPath.toString())) {
            logger.logDebug(() -> "Found " + filename + " in the cache. Serving.");
            return Response.buildResponse(CODE_200_OK,
                    Map.of(
                            "Cache-Control","max-age=604800",
                            "Content-Type", "image/jpeg"
                    ),
                    lruCache.get(photoPath.toString()));
        }

        // if it's not in our cache, let's check to see whether the file is even there.
        boolean doesFileExist = Files.exists(photoPath);

        // if it's not there, return 404
        if (! doesFileExist) {
            logger.logDebug(() -> "filename of " + filename + " does not exist in the directory");
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // otherwise, read the bytes
        logger.logDebug(() -> "about to read file at " + photoPath);

        return readStaticFile(photoPath.toString());

    }


    /**
     * Get a file from a path and create a response for it with a mime type.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link FileUtils#badFilePathPatterns}
     * </p>
     *
     * @return a response with the file contents and caching headers and mime if valid.
     *  if the path has invalid characters, we'll return a "bad request" response.
     */
    Response readStaticFile(String path) {
        if (badFilePathPatterns.matcher(path).find()) {
            logger.logDebug(() -> String.format("Bad path requested at readStaticFile: %s", path));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }

        try {
            // convert from a string to a path object for some valuable methods
            Path staticFilePath = Path.of(path);
            if (!Files.isRegularFile(staticFilePath)) {
                logger.logDebug(() -> String.format("No readable file found at %s", path));
                return Response.buildLeanResponse(CODE_404_NOT_FOUND);
            }

            long fileSize = Files.size(staticFilePath);
            if (fileSize == 0) {
                logger.logDebug(() -> path + " photo filesize was 0.  Sending 404");
                return Response.buildLeanResponse(CODE_404_NOT_FOUND);
            } else {
                // note that the fileSize is set very low here just so we can test reading a file
                // with a filechannel, with files that aren't very large, so they don't make our
                // code repo huge.
                if (fileSize < 3000) {
                    logger.logDebug(() -> String.format("File: (%s) was smaller than 3000 bytes, reading into cache.", staticFilePath));
                    return createOkResponseForStaticFiles(staticFilePath);
                } else {
                    logger.logDebug(() -> String.format("File: (%s) was larger than 3000 bytes, reading directly from disk", staticFilePath));
                    return createOkResponseForLargeStaticFiles(staticFilePath);
                }
            }
        } catch (IOException e) {
            logger.logAsyncError(() -> String.format("Error while reading file: %s. %s", path, StacktraceUtils.stackTraceToString(e)));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }
    }


    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     */
    private Response createOkResponseForLargeStaticFiles(Path staticFilePath) throws IOException {
        var headers = Map.of(
                "Content-Type", "image/jpeg",
                "cache-control", "max-age=" + staticFileCacheTime);

        return Response.buildLargeFileResponse(
                CODE_200_OK,
                headers,
                staticFilePath.toString());
    }


    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     */
    private Response createOkResponseForStaticFiles(Path staticFilePath) throws IOException {
        var fileContents = fileReader.readFile(staticFilePath.toString());
        var headers = Map.of(
                "Content-Type", "image/jpeg",
                "cache-control", "max-age=" + staticFileCacheTime);

        return Response.buildResponse(
                CODE_200_OK,
                headers,
                fileContents);
    }


}
