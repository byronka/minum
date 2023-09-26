package com.renomad.minum;

import com.renomad.minum.auth.AuthUtils;
import com.renomad.minum.auth.LoopingSessionReviewing;
import com.renomad.minum.auth.SessionId;
import com.renomad.minum.auth.User;
import com.renomad.minum.database.Db;
import com.renomad.minum.sampledomain.ListPhotos;
import com.renomad.minum.sampledomain.PersonName;
import com.renomad.minum.sampledomain.SampleDomain;
import com.renomad.minum.sampledomain.UploadPhoto;
import com.renomad.minum.sampledomain.photo.Photograph;
import com.renomad.minum.web.RequestLine;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.WebFramework;

import static com.renomad.minum.web.RequestLine.Method.GET;

/**
 * This class is where all code gets registered to work
 * with our web testing.
 * <br><br>
 * example:
 * <pre>{@code
 *     wf.registerPath(StartLine.Method.GET, "formentry", sd::formEntry);
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
        webFramework.registerPath(GET, "", r -> Response.redirectTo("index.html"));
        webFramework.registerPath(GET, "index", sd::sampleDomainIndex);

        // sample domain stuff
        webFramework.registerPath(GET, "formentry", sd::formEntry);
        webFramework.registerPath(RequestLine.Method.POST, "testform", sd::testform);

        // photos stuff
        webFramework.registerPath(GET, "photos", lp::ListPhotosPage);
        webFramework.registerPath(GET, "upload", up::uploadPage);
        webFramework.registerPath(RequestLine.Method.POST, "upload", up::uploadPageReceivePost);
        webFramework.registerPath(GET, "photo", lp::grabPhoto);

        // minum.auth stuff
        webFramework.registerPath(GET, "login", auth::login);
        webFramework.registerPath(GET, "register", auth::register);
        webFramework.registerPath(RequestLine.Method.POST, "registeruser", auth::registerUser);
        webFramework.registerPath(RequestLine.Method.POST, "loginuser", auth::loginUser);
        webFramework.registerPath(GET, "logout", auth::logout);
        webFramework.registerPath(GET, "auth", auth::authPage);

    }

    private SampleDomain setupSampleDomain(AuthUtils auth) {
        Db<PersonName> sampleDomainDb = context.getDb("names", PersonName.EMPTY);
        return new SampleDomain(sampleDomainDb, auth, context);
    }

    private ListPhotos setupListPhotos(AuthUtils auth, UploadPhoto up) {
        return new ListPhotos(context, up, auth);
    }

    private UploadPhoto setupUploadPhotos(AuthUtils auth) {
        var photoDb = context.getDb("photos", Photograph.EMPTY);
        return new UploadPhoto(photoDb, auth, context);
    }

    private AuthUtils buildAuthDomain() {
        var sessionDb = context.getDb("sessions", SessionId.EMPTY);
        var userDb = context.getDb("users", User.EMPTY);
        var au = new AuthUtils(sessionDb, userDb, context);
        new LoopingSessionReviewing(context, au).initialize();
        return au;
    }
}
