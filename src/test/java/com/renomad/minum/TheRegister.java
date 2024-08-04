package com.renomad.minum;

import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.sampledomain.ListPhotos;
import com.renomad.minum.sampledomain.PersonName;
import com.renomad.minum.sampledomain.SampleDomain;
import com.renomad.minum.sampledomain.UploadPhoto;
import com.renomad.minum.sampledomain.auth.*;
import com.renomad.minum.sampledomain.photo.Photograph;
import com.renomad.minum.sampledomain.photo.Video;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.web.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.RequestLine.Method.POST;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_403_FORBIDDEN;

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
    private final ILogger logger;
    private final FileUtils fileUtils;

    public TheRegister(Context context, WebFramework wf) {
        this.context = context;
        this.logger = context.getLogger();
        this.webFramework = wf;
        this.fileUtils = new FileUtils(logger, context.getConstants());
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
        webFramework.registerPath(POST, "testform", sd::testform);
        webFramework.registerPath(GET, "hello", sd::helloName);

        // photos stuff
        webFramework.registerPath(GET, "photos", lp::ListPhotosPage);
        // it is necessary to register this image so explicitly because this is all in the test directory and the image
        // is not stored in the static files directory where it would normally be.
        webFramework.registerPath(GET, "video_poster.jpg", request -> Response.buildResponse(StatusLine.StatusCode.CODE_200_OK, Map.of("Content-Type", "image/jpg"), fileUtils.readBinaryFile("src/test/resources/video_poster.jpg")));
        webFramework.registerPath(GET, "upload", up::uploadPage);
        webFramework.registerPath(GET, "upload_video", up::uploadVideoPage);
        webFramework.registerPath(POST, "upload", up::uploadPageReceivePost);
        webFramework.registerPath(POST, "upload_video", up::uploadVideoReceivePost);
        webFramework.registerPath(GET, "photo", lp::grabPhoto);
        webFramework.registerPath(GET, "video", lp::grabVideo);

        // minum.auth stuff
        webFramework.registerPath(GET, "login", auth::login);
        webFramework.registerPath(GET, "register", auth::register);
        webFramework.registerPath(POST, "registeruser", auth::registerUser);
        webFramework.registerPath(POST, "loginuser", auth::loginUser);
        webFramework.registerPath(GET, "logout", auth::logout);
        webFramework.registerPath(GET, "auth", auth::authPage);

        // exception thrower
        webFramework.registerPath(GET, "throwexception", sd::throwException);

        // adding a section that is secured by dint of its path including the string "secure".
        // See preHandlerCode.
        webFramework.registerPath(GET, "secure/photos", lp::ListPhotosPage2);

        // register a function to run before all requests.  This is a *special*
        // behavior and is not necessarily a typical requirement for web apps. It
        // is used here for testing and demonstration only.
        webFramework.registerPreHandler(preHandlerInputs -> preHandlerCode(preHandlerInputs, auth));

        // register a custom handler to run afterwards.  In this case
        // it is being used to override the 404 and 500 error responses.
        webFramework.registerLastMinuteHandler(TheRegister::lastMinuteHandlerCode);
    }

    private static IResponse lastMinuteHandlerCode(LastMinuteHandlerInputs inputs) {
        switch (inputs.response().getStatusCode()) {
            case CODE_404_NOT_FOUND -> {
                return Response.buildResponse(
                        StatusLine.StatusCode.CODE_404_NOT_FOUND,
                        Map.of("Content-Type", "text/html; charset=UTF-8"),
                        "<p>No document was found</p>"
                        );
            }
            case CODE_500_INTERNAL_SERVER_ERROR -> {
                IResponse response = inputs.response();
                return Response.buildResponse(
                        StatusLine.StatusCode.CODE_500_INTERNAL_SERVER_ERROR,
                        Map.of("Content-Type", "text/html; charset=UTF-8"),
                        "<p>Server error occurred.  A log entry with further information has been added with the following code . " + new String(response.getBody(), StandardCharsets.UTF_8) + "</p>"
                        );
            }
            default -> {
                return inputs.response();
            }
        }
    }

    private IResponse preHandlerCode(PreHandlerInputs preHandlerInputs, AuthUtils auth) throws Exception {
        // log all requests
        IRequest request = preHandlerInputs.clientRequest();
        ThrowingFunction<IRequest, IResponse> endpoint = preHandlerInputs.endpoint();
        ISocketWrapper sw = preHandlerInputs.sw();

        logger.logTrace(() -> String.format("Request: %s by %s",
                request.getRequestLine().getRawValue(),
                request.getRemoteRequester()));

        String path = request.getRequestLine().getPathDetails().getIsolatedPath();

        // redirect to https if they are on the plain-text connection and the path is "whoops"
        if (path.contains("whoops") &&
                sw.getServerType().equals(HttpServerType.PLAIN_TEXT_HTTP)) {
            return Response.redirectTo("https://%s:%d/%s".formatted(sw.getHostName(), sw.getLocalPort(), path));
        }

        // adjust behavior if non-authenticated and path includes "secure/"

        if (path.contains("secure/")) {
            AuthResult authResult = auth.processAuth(request);
            if (authResult.isAuthenticated()) {
                return endpoint.apply(request);
            } else {
                return Response.buildLeanResponse(CODE_403_FORBIDDEN);
            }
        }

        // if the path does not include /secure, just move the request along unchanged.
        return endpoint.apply(request);
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
        var videoDb = context.getDb("videos", Video.EMPTY);
        return new UploadPhoto(photoDb, videoDb, auth, context);
    }

    private AuthUtils buildAuthDomain() {
        var sessionDb = context.getDb("sessions", SessionId.EMPTY);
        var userDb = context.getDb("users", User.EMPTY);
        var au = new AuthUtils(sessionDb, userDb, context);
        new LoopingSessionReviewing(context, au).initialize();
        return au;
    }
}
