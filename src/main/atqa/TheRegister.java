package atqa;

import atqa.auth.AuthUtils;
import atqa.auth.LoopingSessionReviewing;
import atqa.auth.SessionId;
import atqa.auth.User;
import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.sampledomain.ListPhotos;
import atqa.sampledomain.UploadPhoto;
import atqa.sampledomain.photo.Photograph;
import atqa.sampledomain.PersonName;
import atqa.sampledomain.SampleDomain;
import atqa.web.StartLine;
import atqa.web.WebFramework;

import java.nio.file.Path;

/**
 * This class is where all code gets registered to work
 * with our web testing.
 * <br><br>
 * example:
 * <pre>{@code
 *     wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
 * }</pre>
 */
public class TheRegister {

    public void registerDomains(WebFramework wf) {
        var auth = buildAuthDomain(wf);
        var up = setupUploadPhotos(wf, auth);
        var lp = setupListPhotos(wf, auth, up);
        var sd = setupSampleDomain(wf, auth);

        // homepage
        wf.registerPath(StartLine.Verb.GET, "", WebFramework.redirectTo("index.html"));
        wf.registerPath(StartLine.Verb.GET, "index", sd::sampleDomainIndex);

        // sample domain stuff
        wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        wf.registerPath(StartLine.Verb.POST, "testform", sd::testform);

        // photos stuff
        wf.registerPath(StartLine.Verb.GET, "photos", lp::ListPhotosPage);
        wf.registerPath(StartLine.Verb.GET, "upload", up::uploadPage);
        wf.registerPath(StartLine.Verb.POST, "upload", up::uploadPageReceivePost);
        wf.registerPath(StartLine.Verb.GET, "photo", lp::grabPhoto);

        // auth stuff
        wf.registerPath(StartLine.Verb.GET, "login", auth::login);
        wf.registerPath(StartLine.Verb.GET, "register", auth::register);
        wf.registerPath(StartLine.Verb.POST, "registeruser", auth::registerUser);
        wf.registerPath(StartLine.Verb.POST, "loginuser", auth::loginUser);
        wf.registerPath(StartLine.Verb.GET, "logout", auth::logout);
        wf.registerPath(StartLine.Verb.GET, "auth", auth::authPage);

    }

    private static SampleDomain setupSampleDomain(WebFramework wf, AuthUtils auth) {
        final var sampleDomainDdps = new DatabaseDiskPersistenceSimpler<PersonName>(Path.of("out/simple_db/names"), wf.executorService, wf.logger);
        return new SampleDomain(sampleDomainDdps, auth);
    }

    private ListPhotos setupListPhotos(WebFramework wf, AuthUtils auth, UploadPhoto up) {
        return new ListPhotos(wf, up, auth);
    }

    private UploadPhoto setupUploadPhotos(WebFramework wf, AuthUtils auth) {
        final var photoDdps = new DatabaseDiskPersistenceSimpler<Photograph>(wf.dbDir.resolve("photos"), wf.executorService, wf.logger);
        return new UploadPhoto(photoDdps, wf.logger, wf.dbDir, auth);
    }

    private AuthUtils buildAuthDomain(WebFramework wf) {

        wf.logger.logDebug(() -> "using a database directory of " + wf.dbDir);
        final var sessionDdps = new DatabaseDiskPersistenceSimpler<SessionId>(wf.dbDir.resolve("sessions"), wf.executorService, wf.logger);
        final var userDdps = new DatabaseDiskPersistenceSimpler<User>(wf.dbDir.resolve("users"), wf.executorService, wf.logger);
        var au = new AuthUtils(sessionDdps, userDdps, wf);
        var sessionLooper = new LoopingSessionReviewing(wf.executorService, wf.logger, au).initialize();
        au.setSessionLooper(sessionLooper);
        return au;
    }
}
