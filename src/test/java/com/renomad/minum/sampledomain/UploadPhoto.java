package com.renomad.minum.sampledomain;

import com.renomad.minum.sampledomain.photo.Video;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.sampledomain.auth.AuthResult;
import com.renomad.minum.sampledomain.auth.AuthUtils;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.sampledomain.photo.Photograph;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StreamingMultipartPartition;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.renomad.minum.web.StatusLine.StatusCode.*;


public class UploadPhoto {

    private final String uploadPhotoTemplateHtml;
    private final String uploadVideoTemplateHtml;
    private final Db<Photograph> db;
    private final Db<Video> videoDb;
    private final ILogger logger;
    private final Path dbDir;
    private final AuthUtils auth;

    public UploadPhoto(Db<Photograph> db, Db<Video> videoDb, AuthUtils auth, Context context) {
        Constants constants = context.getConstants();
        this.auth = auth;
        this.logger = context.getLogger();
        FileUtils fileUtils = new FileUtils(logger, constants);
        this.dbDir = Path.of(constants.dbDirectory);
        uploadPhotoTemplateHtml = fileUtils.readTextFile("src/test/webapp/templates/uploadphoto/upload_photo_template.html");
        uploadVideoTemplateHtml = fileUtils.readTextFile("src/test/webapp/templates/uploadphoto/upload_video_template.html");
        this.db = db;
        this.videoDb = videoDb;
    }

    public IResponse uploadPage(IRequest r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_401_UNAUTHORIZED);
        }
        return Response.htmlOk(uploadPhotoTemplateHtml);
    }

    public IResponse uploadVideoPage(IRequest r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_401_UNAUTHORIZED);
        }
        return Response.htmlOk(uploadVideoTemplateHtml);
    }

    public IResponse uploadPageReceivePost(IRequest request) {
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_401_UNAUTHORIZED);
        }
        // we expect to get a photo by url-encoding because one of our tests sends to the
        // endpoint that way.
        byte[] photoBytes;
        String shortDescription;
        String description;
        try {
            photoBytes = request.getBody().asBytes("image_uploads");
            shortDescription = request.getBody().asString("short_description");
            description = request.getBody().asString("long_description");
        } catch (Exception ex) {
            // if we are running our sample domain, the browser will typically send the data in multipart form.
            photoBytes = request.getBody().getPartitionByName("image_uploads").getFirst().getContent();
            shortDescription = request.getBody().getPartitionByName("short_description").getFirst().getContentAsString();
            description = request.getBody().getPartitionByName("long_description").getFirst().getContentAsString();
        }

        var newFilename = UUID.nameUUIDFromBytes(photoBytes).toString();
        final var newPhotograph = new Photograph(0, newFilename, shortDescription, description);
        Path photoDirectory = dbDir.resolve("photo_files");
        Path photoPath = photoDirectory.resolve(newFilename);
        try {
            logger.logDebug(() -> "Creating a directory for photo_files");
            boolean directoryExists = Files.exists(photoDirectory);
            logger.logDebug(() -> "Directory: " + photoDirectory + ". Already exists: " + directoryExists);
            if (!directoryExists) {
                logger.logDebug(() -> "Creating directory, since it does not already exist: " + photoDirectory);
                Files.createDirectories(photoDirectory);
                logger.logDebug(() -> "Directory: " + photoDirectory + " created");
            }

            Files.write(photoPath, photoBytes);
        } catch (IOException e) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
            return Response.buildResponse(CODE_500_INTERNAL_SERVER_ERROR, Map.of("Content-Type", "text/plain;charset=UTF-8"), e.toString());
        }
        db.write(newPhotograph);
        return Response.redirectTo("photos");
    }


    public IResponse uploadVideoReceivePost(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        // we are guaranteed to get the partitions in the same order as in the HTML document, per
        // my understanding of the specification.  So we can just pull one partition at a time, in
        // a particular order, knowing what each partition will have.
        Iterator<StreamingMultipartPartition> iterator = request.getMultipartIterable().iterator();

        // get the whole damn video.  Could be huge so we'll stream this into a file as we read it.

        long countOfVideoBytes = 0;
        var newFilename = UUID.randomUUID() + ".mp4";
        final Path videoFilesDirectory = dbDir.resolve("video_files");

        try {
            try {
                logger.logDebug(() -> "Creating a directory for video_files");
                boolean directoryExists = Files.exists(videoFilesDirectory);
                logger.logDebug(() -> "Directory: " + videoFilesDirectory + ". Already exists: " + directoryExists);
                if (!directoryExists) {
                    logger.logDebug(() -> "Creating directory, since it does not already exist: " + videoFilesDirectory);
                    Files.createDirectories(videoFilesDirectory);
                    logger.logDebug(() -> "Directory: " + videoFilesDirectory + " created");
                }

            } catch (IOException e) {
                logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
                return Response.buildResponse(CODE_500_INTERNAL_SERVER_ERROR, Map.of("Content-Type", "text/plain;charset=UTF-8"), e.toString());
            }

            StreamingMultipartPartition videoPartition = iterator.next();
            Path videoFile = videoFilesDirectory.resolve(newFilename);
            Files.createFile(videoFile);

            FileChannel fc = FileChannel.open(videoFile, StandardOpenOption.WRITE);
            OutputStream outputStream = Channels.newOutputStream(fc);
            copy(videoPartition, outputStream);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        // the video short description
        String shortDescription = new String(iterator.next().readAllBytes(), StandardCharsets.UTF_8);

        // the video long description
        String longDescription = new String(iterator.next().readAllBytes(), StandardCharsets.UTF_8);

        final var newVideo = new Video(0L, newFilename, shortDescription, longDescription);

        videoDb.write(newVideo);

        logger.logAudit(() -> String.format("%s has posted a new video, %s, with short description of %s, size of %d",
                authResult.user().getUsername(),
                newFilename,
                shortDescription,
                countOfVideoBytes
        ));

        return Response.redirectTo("photos");
    }

    void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }

    public List<Photograph> getPhotographs() {
        return db.values().stream().toList();
    }

    public List<Video> getVideos() {
        return videoDb.values().stream().toList();
    }

}
