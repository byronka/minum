package minum;

import minum.logging.ILogger;
import minum.testing.TestLogger;
import minum.utils.MyThread;
import minum.utils.StringUtils;
import minum.web.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.List;

import static minum.testing.RegexUtils.find;
import static minum.testing.TestFramework.assertEquals;
import static minum.testing.TestFramework.assertTrue;
import static minum.web.StatusLine.StatusCode.*;

/**
 * This test is called after the testing framework has started
 * the whole system.  We can now talk to the server like a regular user.
 */
public class FunctionalTests {

    private final ILogger logger;
    final Server primaryServer;
    final WebEngine webEngine;
    private final Context context;
    private final InputStreamUtils inputStreamUtils;
    private final WebFramework webFramework;

    public FunctionalTests(Context context) {
        this.webFramework = context.getFullSystem().getWebFramework();
        this.logger = context.getLogger();
        this.context = context;
        this.primaryServer = context.getFullSystem().getServer();
        this.webEngine = new WebEngine(context);
        this.inputStreamUtils = new InputStreamUtils(context);
    }

    public void test() throws Exception {
        System.out.println("First functional test"); {

            /* Request a static file.  First time it gets loaded from disk... */
            assertEquals(get("index.html").statusLine().status(), _200_OK);

            /* Second time, it gets loaded from cache */
            assertEquals(get("index.html").statusLine().status(), _200_OK);

            /* what if we ask for a file that doesn't exist? */
            assertEquals(get("DOES_NOT_EXIST.html").statusLine().status(), _404_NOT_FOUND);

            /*
            grab the photos page unauthenticated. We should be able
            to view the photos.
             */
            assertEquals(get("photos").statusLine().status(), _200_OK);

            // go to the page for registering a user, while unauthenticated.
            assertEquals(get("register").statusLine().status(), _200_OK);

            // register a user
            var registrationResponse = post("registeruser", "username=foo&password=bar");
            assertEquals(registrationResponse.statusLine().status(), _303_SEE_OTHER);
            assertEquals(registrationResponse.headers().valueByKey("location"), List.of("login"));

            // Go to the login page, unauthenticated
            assertEquals(get("login").statusLine().status(), _200_OK);

            // login as the user we registered
            var response = post("loginuser", "username=foo&password=bar");
            var cookieValue = String.join(";", response.headers().valueByKey("set-cookie"));

            // try visiting the registration page while authenticated (should get redirected)
            List<String> authHeader = List.of("Cookie: " + cookieValue);
            var registrationResponseAuthd = post("registeruser", "username=foo&password=bar", authHeader);
            assertEquals(registrationResponseAuthd.statusLine().status(), _303_SEE_OTHER);
            assertEquals(registrationResponseAuthd.headers().valueByKey("location"), List.of("index"));

            // try visiting the login page while authenticated (should get redirected)
            assertEquals(get("login", authHeader).statusLine().status(), _303_SEE_OTHER);

            // visit the page for uploading photos, authenticated
            get("upload", authHeader);

            // upload some content, authenticated
            post("upload", "image_uploads=123&short_descriptionbar=&long_description=foofoo", authHeader);

            // check out what's on the photos page now, unauthenticated
            var response2 = get("photos");
            var htmlResponse = response2.body().asString();
            String photoSrc = find("photo\\?name=[a-z0-9\\-]*", htmlResponse);

            // look at the contents of a particular photo, unauthenticated
            get(photoSrc, authHeader);

            // check out what's on the sample domain page, authenticated
            assertTrue(get("index", authHeader).body().asString().contains("Enter a name"));
            assertTrue(get("formEntry", authHeader).body().asString().contains("Name Entry"));
            assertEquals(post("testform", "name_entry=abc", authHeader).statusLine().status(), _303_SEE_OTHER);

            // logout
            assertEquals(get("logout", authHeader).statusLine().status(), _303_SEE_OTHER);

            // if we try to upload a photo unauth, we're prevented
            assertEquals(post("upload", "foo=bar").statusLine().status(), _401_UNAUTHORIZED);

            // if we try to upload a name on the sampledomain auth, we're prevented
            assertEquals(post("testform", "foo=bar").statusLine().status(), _401_UNAUTHORIZED);

            // request a static asset
            TestResponse staticResponse = get("main.css");
            assertEquals(staticResponse.headers.contentType(), "Content-Type: text/css");
            assertTrue(staticResponse.body().asString().contains("margin-left: 0;"));

            // *********** ERROR HANDLING SECTION *****************
            // if we try sending too many characters on a line, it should block us
            try (var client = webEngine.startClient(primaryServer)) {
                // send a GET request
                client.sendHttpLine("a".repeat(context.getConstants().MAX_READ_LINE_SIZE_BYTES + 1));
            }

            // remember, we're the client, we don't have immediate access to the server here.  So,
            // we have to wait for it to get through some processing before we check.
            MyThread.sleep(50);
            String failureMsg = ((TestLogger)logger).findFirstMessageThatContains("in readLine");
            assertEquals(failureMsg, "in readLine, client sent more bytes than allowed.  Current max: 500");

            // ******* IMPORTANT!!! This needs to be the last test, since it locks us out ************
            // ******* IMPORTANT!!! This needs to be the last test, since it locks us out ************
            // if we try sending a request that looks like an attack, immediately
            // block the client, don't even return a normal response
            assertEquals(get("version").statusLine().status(), NULL);
            MyThread.sleep(50);
            String vulnMsg = ((TestLogger)logger).findFirstMessageThatContains("looking for a vulnerability? true", 6);
            assertTrue(vulnMsg.contains("looking for a vulnerability? true"), "expect to find correct error in this: " + vulnMsg);

        }

    }

    record TestResponse(StatusLine statusLine, Headers headers, Body body) {}

    public TestResponse get(String path) throws IOException {
        return get(path, List.of());
    }

    public TestResponse get(String path, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY(context);
        Headers headers = null;
        StatusLine statusLine = null;
        try (var client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a GET request
            client.sendHttpLine("GET /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            for (String header : extraHeaders) {
                client.sendHttpLine(header);
            }
            client.sendHttpLine("");

            statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));

            headers = Headers.make(context, inputStreamUtils).extractHeaderInformation(is);

            BodyProcessor bodyProcessor = new BodyProcessor(context);


            // Determine whether there is a body (a block of data) in this request
            if (webFramework.isThereIsABody(headers)) {
                Headers finalHeaders = headers;
                logger.logTrace(() -> "There is a body. Content-type is " + finalHeaders.contentType());
                body = bodyProcessor.extractData(is, headers);
            }

        } catch (SocketException ex) {
            if (ex.getMessage().equals("An established connection was aborted by the software in your host machine")) {
                /*
                When the server is done communicating with us, it will close
                the socket on its side, which will cause a SocketException on our side.
                If the message matches what we have here, it's just that happening, no
                need to pass it up further, it's expected and ok.
                 */
            } else {
                throw ex;
            }
        }
        MyThread.sleep(100);
        return new TestResponse(statusLine, headers, body);
    }

    public TestResponse post(String path, String payload) throws IOException {
        return post(path, payload, List.of());
    }

    public TestResponse post(String path, String payload, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY(context);
        Headers headers = null;
        StatusLine statusLine = null;
        try (var client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a POST request
            client.sendHttpLine("POST /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            client.sendHttpLine("Content-Length: " + payload.length());
            client.sendHttpLine("Content-Type: application/x-www-form-urlencoded");
            for (String header : extraHeaders) {
                client.sendHttpLine(header);
            }
            client.sendHttpLine("");
            client.sendHttpLine(payload);

            statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));

            headers = Headers.make(context, inputStreamUtils).extractHeaderInformation(is);

            BodyProcessor bodyProcessor = new BodyProcessor(context);


            if (webFramework.isThereIsABody(headers)) {
                Headers finalHeaders = headers;
                logger.logTrace(() -> "There is a body. Content-type is " + finalHeaders.contentType());
                body = bodyProcessor.extractData(is, headers);
            }

        } catch (SocketException ex) {
            if (ex.getMessage().equals("An established connection was aborted by the software in your host machine")) {
                /*
                When the server is done communicating with us, it will close
                the socket on its side, which will cause a SocketException on our side.
                If the message matches what we have here, it's just that happening, no
                need to pass it up further, it's expected and ok.
                 */
            } else {
                throw ex;
            }
        }
        MyThread.sleep(100);
        return new TestResponse(statusLine, headers, body);
    }

}
