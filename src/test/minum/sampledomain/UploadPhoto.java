package minum.sampledomain;

import minum.Constants;
import minum.Context;
import minum.auth.AuthResult;
import minum.auth.AuthUtils;
import minum.database.AlternateDatabaseDiskPersistenceSimpler;
import minum.logging.ILogger;
import minum.sampledomain.photo.Photograph;
import minum.utils.FileUtils;
import minum.utils.StacktraceUtils;
import minum.web.Request;
import minum.web.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static minum.web.StatusLine.StatusCode.*;


public class UploadPhoto {

    private final String uploadPhotoTemplateHtml;
    private final AlternateDatabaseDiskPersistenceSimpler<Photograph> ddps;
    private final ILogger logger;
    private final Path dbDir;
    private final AuthUtils auth;
    private final Constants constants;

    public UploadPhoto(AlternateDatabaseDiskPersistenceSimpler<Photograph> ddps, AuthUtils auth, Context context) {
        this.constants = context.getConstants();
        this.auth = auth;
        this.logger = context.getLogger();
        this.dbDir = Path.of(constants.DB_DIRECTORY);
        uploadPhotoTemplateHtml = FileUtils.readTemplate("uploadphoto/upload_photo_template.html");
        this.ddps = ddps;
    }

    public Response uploadPage(Request r) {
        AuthResult authResult = auth.processAuth(r);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }
        return Response.htmlOk(uploadPhotoTemplateHtml);
    }

    public Response uploadPageReceivePost(Request request) {
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return new Response(_401_UNAUTHORIZED);
        }
        var photoBytes = request.body().asBytes("image_uploads");
        var shortDescription = request.body().asString("short_description");
        var description = request.body().asString("long_description");

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
            return new Response(_500_INTERNAL_SERVER_ERROR, e.toString());
        }
        ddps.persistToDisk(newPhotograph);
        return Response.redirectTo("photos");
    }

    public List<Photograph> getPhotographs() {
        return ddps.stream().toList();
    }

}
