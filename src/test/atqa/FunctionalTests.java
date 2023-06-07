package atqa;

import atqa.logging.ILogger;
import atqa.utils.MyThread;
import atqa.web.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static atqa.testing.RegexUtils.find;
import static atqa.testing.TestFramework.assertEquals;
import static atqa.testing.TestFramework.assertTrue;
import static atqa.web.InputStreamUtils.readLine;
import static atqa.web.StatusLine.StatusCode.*;

/**
 * This test is called after the testing framework has started
 * the whole system.  We can now talk to the server like a regular user.
 */
public class FunctionalTests {

    private final ILogger logger;
    final Server primaryServer;
    final WebEngine webEngine;

    public FunctionalTests(WebFramework wf) {
        this.logger = wf.getLogger();
        this.primaryServer = wf.getFullSystem().getServer();
        this.webEngine = new WebEngine(logger);
    }

    public void test() throws Exception {
        System.out.println("First functional test"); {

            // grab the photos page unauthenticated
            assertEquals(get("photos").statusLine().status(), _200_OK);

            // go to the page for registering a user, while unauthenticated.
            assertEquals(get("register").statusLine().status(), _200_OK);

            // register a user
            var registrationResponse = post("registeruser", "username=foo&password=bar");
            assertEquals(registrationResponse.statusLine().status(), _303_SEE_OTHER);
            assertEquals(registrationResponse.headers().headersAsMap().get("location"), List.of("login"));

            // Go to the login page, unauthenticated
            assertEquals(get("login").statusLine().status(), _200_OK);

            // login as the user we registered
            var response = post("loginuser", "username=foo&password=bar");
            var cookieValue = String.join(";", response.headers().headersAsMap().get("set-cookie"));

            // try visiting the registration page while authenticated (should get redirected)
            List<String> authHeader = List.of("Cookie: " + cookieValue);
            var registrationResponseAuthd = post("registeruser", "username=foo&password=bar", authHeader);
            assertEquals(registrationResponseAuthd.statusLine().status(), _303_SEE_OTHER);
            assertEquals(registrationResponseAuthd.headers().headersAsMap().get("location"), List.of("index"));

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

        }

    }

    record TestResponse(StatusLine statusLine, Headers headers, Body body) {}

    public TestResponse get(String path) throws IOException {
        return get(path, List.of());
    }

    public TestResponse get(String path, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY;
        Headers headers;
        StatusLine statusLine;
        try (SocketWrapper client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a GET request
            client.sendHttpLine("GET /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            for (String header : extraHeaders) {
                client.sendHttpLine(header);
            }
            client.sendHttpLine("");

            statusLine = StatusLine.extractStatusLine(readLine(is));

            headers = Headers.extractHeaderInformation(is);

            BodyProcessor bodyProcessor = new BodyProcessor(logger);


            // Determine whether there is a body (a block of data) in this request
            final var thereIsABody = !headers.contentType().isBlank();
            if (thereIsABody) {
                logger.logTrace(() -> "There is a body. Content-type is " + headers.contentType());
                body = bodyProcessor.extractData(is, headers);
            }

        }
        MyThread.sleep(100);
        return new TestResponse(statusLine, headers, body);
    }

    public TestResponse post(String path, String payload) throws IOException {
        return post(path, payload, List.of());
    }

    public TestResponse post(String path, String payload, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY;
        Headers headers;
        StatusLine statusLine;
        try (SocketWrapper client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a GET request
            client.sendHttpLine("POST /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            client.sendHttpLine("Content-Length: " + payload.length());
            client.sendHttpLine("Content-Type: application/x-www-form-urlencoded");
            for (String header : extraHeaders) {
                client.sendHttpLine(header);
            }
            client.sendHttpLine("");
            client.sendHttpLine(payload);

            statusLine = StatusLine.extractStatusLine(readLine(is));

            headers = Headers.extractHeaderInformation(is);

            BodyProcessor bodyProcessor = new BodyProcessor(logger);


            // Determine whether there is a body (a block of data) in this request
            final var thereIsABody = !headers.contentType().isBlank();
            if (thereIsABody) {
                logger.logTrace(() -> "There is a body. Content-type is " + headers.contentType());
                body = bodyProcessor.extractData(is, headers);
            }

        }
        MyThread.sleep(100);
        return new TestResponse(statusLine, headers, body);
    }

}
