package minum;

import minum.auth.AuthUtils;
import minum.auth.LoopingSessionReviewing;
import minum.auth.SessionId;
import minum.auth.User;
import minum.database.DatabaseDiskPersistenceSimpler;
import minum.sampledomain.ListPhotos;
import minum.sampledomain.PersonName;
import minum.sampledomain.SampleDomain;
import minum.sampledomain.UploadPhoto;
import minum.sampledomain.photo.Photograph;
import minum.web.Response;
import minum.web.StartLine;
import minum.web.WebFramework;

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

    public static void registerDomains(WebFramework wf) {
        var auth = buildAuthDomain(wf);
        var up = setupUploadPhotos(wf, auth);
        var lp = setupListPhotos(wf, auth, up);
        var sd = setupSampleDomain(wf, auth);

        // homepage
        wf.registerPath(StartLine.Verb.GET, "", r -> Response.redirectTo("index.html"));
        wf.registerPath(StartLine.Verb.GET, "index", sd::sampleDomainIndex);

        // sample domain stuff
        wf.registerPath(StartLine.Verb.GET, "formentry", sd::formEntry);
        wf.registerPath(StartLine.Verb.POST, "testform", sd::testform);

        // photos stuff
        wf.registerPath(StartLine.Verb.GET, "photos", lp::ListPhotosPage);
        wf.registerPath(StartLine.Verb.GET, "upload", up::uploadPage);
        wf.registerPath(StartLine.Verb.POST, "upload", up::uploadPageReceivePost);
        wf.registerPath(StartLine.Verb.GET, "photo", lp::grabPhoto);

        // minum.auth stuff
        wf.registerPath(StartLine.Verb.GET, "login", auth::login);
        wf.registerPath(StartLine.Verb.GET, "register", auth::register);
        wf.registerPath(StartLine.Verb.POST, "registeruser", auth::registerUser);
        wf.registerPath(StartLine.Verb.POST, "loginuser", auth::loginUser);
        wf.registerPath(StartLine.Verb.GET, "logout", auth::logout);
        wf.registerPath(StartLine.Verb.GET, "auth", auth::authPage);

    }

    private static SampleDomain setupSampleDomain(WebFramework wf, AuthUtils auth) {
        DatabaseDiskPersistenceSimpler<PersonName> sampleDomainDdps = wf.getDdps("names");
        return new SampleDomain(sampleDomainDdps, auth);
    }

    private static ListPhotos setupListPhotos(WebFramework wf, AuthUtils auth, UploadPhoto up) {
        return new ListPhotos(wf, up, auth);
    }

    private static UploadPhoto setupUploadPhotos(WebFramework wf, AuthUtils auth) {
        DatabaseDiskPersistenceSimpler<Photograph> photoDdps = wf.getDdps("photos");
        return new UploadPhoto(photoDdps, wf.getLogger(), auth);
    }

    private static AuthUtils buildAuthDomain(WebFramework wf) {
        DatabaseDiskPersistenceSimpler<SessionId> sessionDdps = wf.getDdps("sessions");
        DatabaseDiskPersistenceSimpler<User> userDdps = wf.getDdps("users");
        var au = new AuthUtils(sessionDdps, userDdps, wf);
        var sessionLooper = new LoopingSessionReviewing(wf.getFullSystem().getExecutorService(), wf.getLogger(), au).initialize();
        au.setSessionLooper(sessionLooper);
        return au;
    }
}
