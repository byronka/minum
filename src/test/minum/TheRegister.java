package minum;

import minum.auth.AuthUtils;
import minum.auth.LoopingSessionReviewing;
import minum.auth.SessionId;
import minum.auth.User;
import minum.database.AlternateDatabaseDiskPersistenceSimpler;
import minum.database.DatabaseDiskPersistenceSimpler;
import minum.sampledomain.ListPhotos;
import minum.sampledomain.PersonName;
import minum.sampledomain.SampleDomain;
import minum.sampledomain.UploadPhoto;
import minum.sampledomain.photo.Photograph;
import minum.web.Response;
import minum.web.StartLine;
import minum.web.WebFramework;

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

    private final Context context;
    private final WebFramework webFramework;

    public TheRegister(Context context) {
        this.context = context;
        this.webFramework = context.getFullSystem().getWebFramework();
    }

    public void registerDomains() {
        var auth = buildAuthDomain();
        var up = setupUploadPhotos(auth);
        var lp = setupListPhotos(auth, up);
        var sd = setupSampleDomain(auth);

        // homepage
        webFramework.registerPath(StartLine.Verb.GET, "", r -> Response.redirectTo("index.html"));
        webFramework.registerPath(StartLine.Verb.GET, "index", sd::sampleDomainIndex);

        // sample domain stuff
        webFramework.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        webFramework.registerPath(StartLine.Verb.POST, "testform", sd::testform);

        // photos stuff
        webFramework.registerPath(StartLine.Verb.GET, "photos", lp::ListPhotosPage);
        webFramework.registerPath(StartLine.Verb.GET, "upload", up::uploadPage);
        webFramework.registerPath(StartLine.Verb.POST, "upload", up::uploadPageReceivePost);
        webFramework.registerPath(StartLine.Verb.GET, "photo", lp::grabPhoto);

        // minum.auth stuff
        webFramework.registerPath(StartLine.Verb.GET, "login", auth::login);
        webFramework.registerPath(StartLine.Verb.GET, "register", auth::register);
        webFramework.registerPath(StartLine.Verb.POST, "registeruser", auth::registerUser);
        webFramework.registerPath(StartLine.Verb.POST, "loginuser", auth::loginUser);
        webFramework.registerPath(StartLine.Verb.GET, "logout", auth::logout);
        webFramework.registerPath(StartLine.Verb.GET, "auth", auth::authPage);

    }

    private SampleDomain setupSampleDomain(AuthUtils auth) {
        DatabaseDiskPersistenceSimpler<PersonName> sampleDomainDdps = webFramework.getDdps("names");
        return new SampleDomain(sampleDomainDdps, auth);
    }

    private ListPhotos setupListPhotos(AuthUtils auth, UploadPhoto up) {
        return new ListPhotos(context, up, auth);
    }

    private UploadPhoto setupUploadPhotos(AuthUtils auth) {
        DatabaseDiskPersistenceSimpler<Photograph> photoDdps = webFramework.getDdps("photos");
        return new UploadPhoto(photoDdps, auth, context);
    }

    private AuthUtils buildAuthDomain() {
        DatabaseDiskPersistenceSimpler<SessionId> sessionDdps = webFramework.getDdps("sessions");
        AlternateDatabaseDiskPersistenceSimpler<User> userDdps = new AlternateDatabaseDiskPersistenceSimpler<>(Path.of(context.getConstants().DB_DIRECTORY, "users"), context);
        var au = new AuthUtils(sessionDdps, userDdps, context);
        new LoopingSessionReviewing(context, au).initialize();
        return au;
    }
}
