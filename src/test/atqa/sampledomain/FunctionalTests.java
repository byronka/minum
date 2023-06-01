package atqa.sampledomain;

import atqa.testing.TestLogger;
import atqa.utils.MyThread;
import atqa.web.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static atqa.testing.RegexUtils.find;
import static atqa.testing.TestFramework.assertEquals;
import static atqa.web.InputStreamUtils.readLine;
import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._303_SEE_OTHER;

/**
 * This test is called after the testing framework has started
 * the whole system.  We can now talk to the server like a regular user.
 */
public class FunctionalTests {

    private final TestLogger logger;
    final Server primaryServer;
    final WebEngine webEngine;

    public FunctionalTests(TestLogger logger, Server primaryServer) {
        this.logger = logger;
        this.primaryServer = primaryServer;
        this.webEngine = new WebEngine(logger);
    }

    public void test() throws Exception {
        logger.test("First functional test"); {

            get("photos");
            get("login");
            post("registeruser", "username=foo&password=bar");
            var response = post("loginuser", "username=foo&password=bar");
            var cookieValue = String.join(";", response.headers().headersAsMap().get("Set-Cookie"));
            get("upload", List.of("Cookie: " + cookieValue));
            post("upload", "image_uploads=123&short_descriptionbar=&long_description=foofoo", List.of("Cookie: " + cookieValue));
            var response2 = get("photos");
            var htmlResponse = response2.body().asString();
            String photoSrc = find("photo\\?name=[a-z0-9\\-]*", htmlResponse);
            get(photoSrc, List.of("Cookie: " + cookieValue));
        }

    }

    record TestResponse(Headers headers, Body body) {}

    public TestResponse get(String path) throws IOException {
        return get(path, List.of());
    }

    public TestResponse get(String path, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY;
        Headers headers;
        try (SocketWrapper client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a GET request
            client.sendHttpLine("GET /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            for (String header : extraHeaders) {
                client.sendHttpLine(header);
            }
            client.sendHttpLine("");

            StatusLine statusLine = StatusLine.extractStatusLine(readLine(is));
            assertEquals(statusLine.status(), _200_OK);

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
        return new TestResponse(headers, body);
    }

    public TestResponse post(String path, String payload) throws IOException {
        return post(path, payload, List.of());
    }

    public TestResponse post(String path, String payload, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY;
        Headers headers;
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

            StatusLine statusLine = StatusLine.extractStatusLine(readLine(is));
            assertEquals(statusLine.status(), _303_SEE_OTHER);

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
        return new TestResponse(headers, body);
    }

}
