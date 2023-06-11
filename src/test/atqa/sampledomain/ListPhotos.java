package atqa.sampledomain;

import atqa.auth.AuthUtils;
import atqa.logging.ILogger;
import atqa.utils.FileUtils;
import atqa.utils.StacktraceUtils;
import atqa.utils.TemplateProcessor;
import atqa.web.FullSystem;
import atqa.web.Request;
import atqa.web.Response;
import atqa.web.WebFramework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static atqa.web.StatusLine.StatusCode.*;

public class ListPhotos {

    private final TemplateProcessor listPhotosTemplateProcessor;
    private final ILogger logger;
    private final Path dbDir;

    private final UploadPhoto up;
    private final AuthUtils auth;
    private final Map<String, byte[]> lruCache;

    public ListPhotos(WebFramework wf, UploadPhoto up, AuthUtils auth) {
        this.logger = wf.getLogger();
        this.dbDir = Path.of(FullSystem.getConfiguredProperties().getProperty("dbdir", "out/simple_db/"));
        listPhotosTemplateProcessor = TemplateProcessor.makeTemplateList(FileUtils.readTemplate("listphotos/list_photos_template.html"));
        this.up = up;
        this.auth = auth;
        this.lruCache = LRUCache.getLruCache();
    }

    public Response ListPhotosPage(Request r) {

        String photoHtml = up.getPhotographs().stream().map(x ->
        """
        <li>
            <img src="photo?name=%s" alt="photo alt text" title="photo title text" />
            <p>%s</p>
            <p>%s</p>
        </li>
        """.formatted(x.photoUrl(), x.shortDescription(), x.description())).collect(Collectors.joining ("\n"));

        String navBar = auth.processAuth(r).isAuthenticated() ? "<p><a href=\"logout\">Logout</a></p><p><a href=\"upload\">Upload</a></p>" : "";

        String listPhotosHtml = listPhotosTemplateProcessor.renderTemplate(Map.of(
                "nav_bar", navBar,
                "photo_html", photoHtml
        ));
        return new Response(_200_OK, List.of("Content-Type: text/html; charset=UTF-8"),listPhotosHtml);
    }

    /**
     * Like you would think - a way to read a photo from disk to put on the wire
     */
    public Response grabPhoto(Request r) {
        String filename = r.startLine().queryString().get("name");
        logger.logAudit(() -> r.remoteRequester() + " is looking for a photo named " + filename);

        // if the name query is null or blank, return 404
        if (filename == null || filename.isBlank()) {
            return new Response(_404_NOT_FOUND);
        }

        // first, is it already in our cache?
        if (lruCache.containsKey(filename)) {
            logger.logDebug(() -> "Found " + filename + " in the cache. Serving.");
            return new Response(_200_OK, lruCache.get(filename),
                    List.of(
                            "Cache-Control: max-age=604800",
                            "Content-Type: image/jpeg"
                    ));
        }
        // first let's check to see whether the file is even there.
        Path photoPath = dbDir.resolve("photo_files").resolve(filename);
        boolean doesFileExist = Files.exists(photoPath);

        // if it's not there, return 404
        if (! doesFileExist) {
            logger.logDebug(() -> "filename of " + filename + " does not exist in the directory");
            return new Response(_404_NOT_FOUND);
        }

        // otherwise, read the bytes
        logger.logDebug(() -> "about to read file at " + photoPath);

        try (RandomAccessFile reader = new RandomAccessFile(photoPath.toString(), "r");
             FileChannel channel = reader.getChannel();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            int bufferSize = 1024;
            if (bufferSize > channel.size()) {
                bufferSize = (int) channel.size();
            }
            ByteBuffer buff = ByteBuffer.allocate(bufferSize);

            while (channel.read(buff) > 0) {
                out.write(buff.array(), 0, buff.position());
                buff.clear();
            }

            byte[] bytes = out.toByteArray();
            if (bytes.length == 0) {
                logger.logDebug(() -> filename + " photo filesize was 0.  Sending 404");
                return new Response(_404_NOT_FOUND);
            } else {
                String s = filename + "photo filesize was " + bytes.length + " bytes.";
                logger.logDebug(() -> s);

                logger.logDebug(() -> "Storing " + filename + " in the cache");
                lruCache.put(filename, bytes);

                return new Response(_200_OK, bytes,
                        List.of(
                                "Cache-Control: max-age=604800",
                                "Content-Type: image/jpeg"
                        ));

            }
        } catch (IOException e){
            logger.logAsyncError(() -> "There was an issue reading a file at " + photoPath + ". " + StacktraceUtils.stackTraceToString(e));
            return new Response(_500_INTERNAL_SERVER_ERROR);
        }


    }

}
