package minum;

import minum.htmlparsing.HtmlParseNode;
import minum.htmlparsing.HtmlParser;
import minum.htmlparsing.TagName;
import minum.logging.ILogger;
import minum.testing.FunctionalTesting;
import minum.testing.FunctionalTesting.TestResponse;
import minum.testing.TestLogger;
import minum.utils.MyThread;
import minum.utils.StringUtils;
import minum.web.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
    private final HtmlParser htmlParser;
    private final FunctionalTesting ft;

    public FunctionalTests(Context context) {
        this.logger = context.getLogger();
        this.context = context;
        this.primaryServer = context.getFullSystem().getServer();
        this.webEngine = new WebEngine(context);
        this.htmlParser = new HtmlParser();
        this.ft = new FunctionalTesting(context);
    }

    public void test() throws Exception {
        System.out.println("First functional test"); {

            /* Request a static file.  First time it gets loaded from disk... */
            assertEquals(ft.get("index.html").statusLine().status(), _200_OK);

            /* Second time, it gets loaded from cache */
            assertEquals(ft.get("index.html").statusLine().status(), _200_OK);

            /* what if we ask for a file that doesn't exist? */
            assertEquals(ft.get("DOES_NOT_EXIST.html").statusLine().status(), _404_NOT_FOUND);

            /*
            grab the photos page unauthenticated. We should be able
            to view the photos.
             */
            TestResponse photos = ft.get("photos");
            assertEquals(photos.statusLine().status(), _200_OK);
            var nodes = htmlParser.parse(photos.body().asString());
            var pNode = htmlParser.searchOne(nodes, TagName.A, Map.of("href", "index.html"));
            assertEquals(pNode.innerText(), "Index");

            // go to the page for registering a user, while unauthenticated.
            assertEquals(ft.get("register").statusLine().status(), _200_OK);

            // register a user
            var registrationResponse = ft.post("registeruser", "username=foo&password=bar");
            assertEquals(registrationResponse.statusLine().status(), _303_SEE_OTHER);
            assertEquals(registrationResponse.headers().valueByKey("location"), List.of("login"));

            // Go to the login page, unauthenticated
            assertEquals(ft.get("login").statusLine().status(), _200_OK);

            // login as the user we registered
            var response = ft.post("loginuser", "username=foo&password=bar");
            var cookieValue = String.join(";", response.headers().valueByKey("set-cookie"));

            // try visiting the registration page while authenticated (should get redirected)
            List<String> authHeader = List.of("Cookie: " + cookieValue);
            var registrationResponseAuthd = ft.post("registeruser", "username=foo&password=bar", authHeader);
            assertEquals(registrationResponseAuthd.statusLine().status(), _303_SEE_OTHER);
            assertEquals(registrationResponseAuthd.headers().valueByKey("location"), List.of("index"));

            // try visiting the login page while authenticated (should get redirected)
            assertEquals(ft.get("login", authHeader).statusLine().status(), _303_SEE_OTHER);

            // visit the page for uploading photos, authenticated
            HtmlParseNode uploadNodeFound1 = ft.get("upload", authHeader).searchOne(TagName.LABEL, Map.of("for", "image_uploads"));
            assertEquals(uploadNodeFound1.innerText(), "Choose images to upload (PNG, JPG)");

            // upload some content, authenticated
            ft.post("upload", "image_uploads=123&short_descriptionbar=&long_description=foofoo", authHeader);

            // check out what's on the photos page now, unauthenticated
            var response2 = ft.get("photos").searchOne(TagName.IMG, Map.of("alt", "photo alt text"));
            String photoUrl = response2.tagInfo().attributes().get("src");
            assertTrue(photoUrl.contains("photo?name="));

            // look at the contents of a particular photo, unauthenticated
            var photoResponse = ft.get(photoUrl);
            assertTrue(photoResponse.body().asBytes().length > 2);

            // check out what's on the sample domain page, authenticated
            assertTrue(ft.get("index", authHeader).body().asString().contains("Enter a name"));
            assertTrue(ft.get("formEntry", authHeader).body().asString().contains("Name Entry"));
            assertEquals(ft.post("testform", "name_entry=abc", authHeader).statusLine().status(), _303_SEE_OTHER);

            // logout
            assertEquals(ft.get("logout", authHeader).statusLine().status(), _303_SEE_OTHER);

            // if we try to upload a photo unauth, we're prevented
            assertEquals(ft.post("upload", "foo=bar").statusLine().status(), _401_UNAUTHORIZED);

            // if we try to upload a name on the sampledomain auth, we're prevented
            assertEquals(ft.post("testform", "foo=bar").statusLine().status(), _401_UNAUTHORIZED);

            // request a static asset
            TestResponse staticResponse = ft.get("main.css");
            assertEquals(staticResponse.headers().contentType(), "Content-Type: text/css");
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
            assertEquals(ft.get("version").statusLine().status(), NULL);
            MyThread.sleep(50);
            String vulnMsg = ((TestLogger)logger).findFirstMessageThatContains("looking for a vulnerability? true", 6);
            assertTrue(vulnMsg.contains("looking for a vulnerability? true"), "expect to find correct error in this: " + vulnMsg);

        }

    }

}
