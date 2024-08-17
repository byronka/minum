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
import com.renomad.minum.web.Headers;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
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
    private final TemplateProcessor videoHtmlTemplateProcessor;
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
        videoHtmlTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTextFile("src/test/webapp/templates/listphotos/video_element_template.html"));
        this.up = up;
        this.auth = auth;
        this.lruCache = LRUCache.getLruCache();
        this.fileReader = new FileReader(lruCache, true, logger);
    }

    public IResponse ListPhotosPage(IRequest r) {

        String photoHtml = up.getPhotographs().stream().map(x ->
        """
        <li>
            <img src="photo?name=%s" alt="photo alt text" title="photo title text" />
            <p>%s</p>
            <p>%s</p>
        </li>
        """.formatted(x.getPhotoUrl(), x.getShortDescription(), x.getDescription())).collect(Collectors.joining ("\n"));

        String videoHtml = up.getVideos().stream()
                .map(x -> videoHtmlTemplateProcessor.renderTemplate(Map.of("video_id", x.getVideoUrl(), "short_description", x.getShortDescription(), "long_description", x.getDescription())))
                .collect(Collectors.joining ("\n"));

        String navBar = auth.processAuth(r).isAuthenticated() ? "<p><a href=\"logout\">Logout</a></p><p><a href=\"upload\">Upload Photo</a></p><p><a href=\"upload_video\">Upload Video</a></p>" : "";

        String listPhotosHtml = listPhotosTemplateProcessor.renderTemplate(Map.of(
                "nav_bar", navBar,
                "photo_html", photoHtml,
                "video_html", videoHtml
        ));
        return Response.htmlOk(listPhotosHtml);
    }

    public IResponse ListPhotosPage2(IRequest r) {

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
        List<String> headerStrings = r.getHeaders().valueByKey("x-preapproved");
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
    public IResponse grabPhoto(IRequest r) {
        String filename = r.getRequestLine().queryString().get("name");
        logger.logAudit(() -> r.getRemoteRequester() + " is looking for a photo named " + filename);

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

        return readStaticFile(photoPath.toString(), "image/jpeg", r.getHeaders());

    }

    /**
     * Returns videos we have stored
     */
    public IResponse grabVideo(IRequest r) {
        String filename = r.getRequestLine().queryString().get("name");
        logger.logAudit(() -> r.getRemoteRequester() + " is looking for a video named " + filename);

        // if the name query is null or blank, return 404
        if (filename == null || filename.isBlank()) {
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // let's check to see whether the file is even there.
        Path videoPath = dbDir.resolve("video_files").resolve(filename);
        boolean doesFileExist = Files.exists(videoPath);

        // if it's not there, return 404
        if (! doesFileExist) {
            logger.logDebug(() -> "filename of " + filename + " does not exist in the directory");
            return Response.buildLeanResponse(CODE_404_NOT_FOUND);
        }

        // otherwise, read the bytes
        logger.logDebug(() -> "about to read file at " + videoPath);

        return readStaticFile(videoPath.toString(), "video/mp4", r.getHeaders());

    }


    /**
     * Get a file from a path and create a response for it with a mime type.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link FileUtils#badFilePathPatterns}
     * </p>
     *
     * @param path the path to the file on disk.
     * @param mimeType a mime type e.g. "image/jpg" or "video/mp4"
     * @return a response with the file contents and caching headers and mime if valid.
     *  if the path has invalid characters, we'll return a "bad request" response.
     */
    IResponse readStaticFile(String path, String mimeType, Headers requestHeaders) {
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
                    return createOkResponseForStaticFiles(staticFilePath, mimeType);
                } else {
                    logger.logDebug(() -> String.format("File: (%s) was larger than 3000 bytes, reading directly from disk", staticFilePath));
                    return createOkResponseForLargeStaticFiles(staticFilePath, mimeType, requestHeaders);
                }
            }
        } catch (IOException e) {
            logger.logAsyncError(() -> String.format("Error while reading file: %s. %s", path, StacktraceUtils.stackTraceToString(e)));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }
    }


    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     *
     * @param mimeType       a mime type e.g. "image/jpg" or "video/mp4"
     */
    private IResponse createOkResponseForLargeStaticFiles(Path staticFilePath, String mimeType, Headers requestHeaders) throws IOException {
        var extraHeaders = Map.of(
                "Content-Type", mimeType,
                "cache-control", "max-age=" + staticFileCacheTime);

        return Response.buildLargeFileResponse(
                extraHeaders,
                staticFilePath.toString(),
                requestHeaders);
    }

    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     * @param mimeType a mime type e.g. "image/jpg" or "video/mp4"
     */
    private IResponse createOkResponseForStaticFiles(Path staticFilePath, String mimeType) throws IOException {
        // this mild-looking method, "readFile", will cache the file contents.
        var fileContents = fileReader.readFile(staticFilePath.toString());
        var headers = Map.of(
                "Content-Type", mimeType,
                "cache-control", "max-age=" + staticFileCacheTime);

        return Response.buildResponse(
                CODE_200_OK,
                headers,
                fileContents);
    }


}
