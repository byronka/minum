package com.renomad.minum;

import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.web.*;
import com.renomad.minum.web.FunctionalTesting.TestResponse;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.MyThread;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.SearchHelpers.*;
import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

/**
 * This test is called after the testing framework has started
 * the whole system.  We can now talk to the server like a regular user.
 */
public class FunctionalTests {

    private static TestLogger logger;
    private static Context context;
    private static FunctionalTesting ft;
    private static FullSystem fullSystem;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("_integration_test");
        logger = (TestLogger) context.getLogger();
        var fileUtils = new FileUtils(logger, context.getConstants());
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        fullSystem = new FullSystem(context);
        fullSystem.start();
        new TheRegister(context, fullSystem.getWebFramework()).registerDomains();

        ft = new FunctionalTesting(
                context,
                context.getConstants().hostName,
                context.getConstants().serverPort);
    }

    @AfterClass
    public static void cleanup() {
        // delay a sec so our system has time to finish before we start deleting files
        MyThread.sleep(500);
        fullSystem.shutdown();
        context.getLogger().stop();
        context.getExecutorService().shutdownNow();
    }

    @Test
    public void testEndToEnd_Functional() throws IOException {
        fullSystem.getWebFramework().addMimeForSuffix("png", "image/png");

        logger.test("Request a static png image that needed a mime type we just provided");
        assertEquals(ft.get("moon.png").statusLine().status(), CODE_200_OK);
        assertEquals(ft.get("moon.png").headers().valueByKey("content-type"), List.of("image/png"));
        checkForClosedSocket();

        logger.test("Request a static file.  First time it gets loaded from disk... ");
        assertEquals(ft.get("index.html").statusLine().status(), CODE_200_OK);

        logger.test("Get just the headers from the index file");
        TestResponse headResponse = ft.send(RequestLine.Method.HEAD, "index.html");
        assertEquals(headResponse.statusLine().status(), CODE_200_OK);
        assertFalse(headResponse.headers().equals(new Headers(List.of())));
        assertEquals(headResponse.body(), Body.EMPTY);

        logger.test("Second time, it gets loaded from cache");
        assertEquals(ft.get("index.html").statusLine().status(), CODE_200_OK);
        checkForClosedSocket();

        logger.test("what if we ask for a file that doesn't exist?");
        TestResponse notFoundResponse = ft.get("DOES_NOT_EXIST.html");
        assertEquals(notFoundResponse.statusLine().status(), CODE_404_NOT_FOUND);
        assertEquals(notFoundResponse.body().asString(), "<p>No document was found</p>");

        logger.test("If we ask for a path of \"whoops\", we should get redirected to the https endpoint");
        TestResponse redirectedResponse = ft.get("whoops");
        assertEquals(redirectedResponse.statusLine().status(), CODE_303_SEE_OTHER);
        assertEquals(redirectedResponse.headers().valueByKey("location").getFirst(), "https://localhost:8080/whoops");

        logger.test("What about when business logic fails and an exception is thrown?");
        TestResponse serverErrorResponse = ft.get("throwexception");
        assertEquals(serverErrorResponse.statusLine().status(), CODE_500_INTERNAL_SERVER_ERROR);
        assertTrue(serverErrorResponse.body().asString().contains(
                "Server error occurred.  A log entry with further information has been added with the following code"));

        /*
         * If the user uses the WebFramework.registerPreHandler to register some code to
         * be run before all requests, it is possible to add some interesting extra behavior.
         *
         * In this case, code has been written that is looking for a path string of /secure. If
         * found, and if the request contains a proper cookie, the system will allow the request
         * to proceed.  Otherwise, it will disallow the request.
         *
         */
        logger.test("What if we ask for a page with a string meant for our preHandler?");
        TestResponse securePhotoResponse = ft.get("secure/photos");
        assertEquals(securePhotoResponse.statusLine().status(),  CODE_403_FORBIDDEN);

        logger.test("grab the photos page unauthenticated. We should be able to view the photos.");
        TestResponse photos = ft.get("photos");
        assertEquals(photos.statusLine().status(), CODE_200_OK);
        var pNode1 = photos.searchOne(TagName.A, Map.of("href", "index.html"));
        assertEquals(pNode1.innerText(), "Index");
        var pNode2 = searchOne(photos.body(), TagName.A, Map.of("href", "index.html"));
        assertEquals(innerText(pNode2), "Index");

        logger.test("go to the page for registering a user, while unauthenticated.");
        assertEquals(ft.get("register").statusLine().status(), CODE_200_OK);

        logger.test("register a user");
        var registrationResponse = ft.post("registeruser", "username=foo&password=bar");
        assertEquals(registrationResponse.statusLine().status(), CODE_303_SEE_OTHER);
        assertEquals(registrationResponse.headers().valueByKey("location"), List.of("login"));

        logger.test("Go to the login page, unauthenticated");
        assertEquals(ft.get("login").statusLine().status(), CODE_200_OK);

        logger.test("login as the user we registered");
        var response = ft.post("loginuser", "username=foo&password=bar");
        checkForClosedSocket();
        var cookieValue = String.join(";", response.headers().valueByKey("set-cookie"));

        logger.test("try visiting the registration page while authenticated (should get redirected)");
        List<String> authHeader = List.of("Cookie: " + cookieValue);
        var registrationResponseAuthd = ft.post("registeruser", "username=foo&password=bar", authHeader);
        assertEquals(registrationResponseAuthd.statusLine().status(), CODE_303_SEE_OTHER);
        assertEquals(registrationResponseAuthd.headers().valueByKey("location"), List.of("index"));

        logger.test("try visiting the login page while authenticated (should get redirected)");
        assertEquals(ft.get("login", authHeader).statusLine().status(), CODE_303_SEE_OTHER);

        logger.test("visit the page for uploading photos, authenticated");
        HtmlParseNode uploadNodeFound1 = ft.get("upload", authHeader).searchOne(TagName.LABEL, Map.of("for", "image_uploads"));
        assertTrue(uploadNodeFound1.innerText().contains("Choose images to upload (PNG, JPG)"));
        HtmlParseNode uploadNodeFound2 = searchOne(ft.get("upload", authHeader).body(), TagName.LABEL, Map.of("for", "image_uploads"));
        assertTrue(innerText(uploadNodeFound2).contains("Choose images to upload (PNG, JPG)"));

        logger.test("upload some content, authenticated");
        ft.post("upload", "image_uploads=123&short_description=bar&long_description=foofoo", authHeader);
        // this will create a photo file that is large enough to test out the fileChannel streaming
        // capability of Response.
        byte[] fakePhotoByteArray = new byte[5000];
        Arrays.fill(fakePhotoByteArray, (byte)3);
        String urlEncodedBinaryData = URLEncoder.encode(Arrays.toString(fakePhotoByteArray), StandardCharsets.UTF_8);
        ft.post("upload", "image_uploads="+urlEncodedBinaryData+"&short_description=fooz&long_description=foofoo", authHeader);
        checkForClosedSocket();

        logger.test("check out what's on the photos page now, unauthenticated");
        TestResponse response1 = ft.get("photos");
        var photoResponses = search(response1.body(), TagName.IMG, Map.of("alt", "photo alt text"));

        var firstPhotoResponse = photoResponses.getFirst();
        String firstPhotoUrl = firstPhotoResponse.getTagInfo().getAttribute("src");
        assertTrue(firstPhotoUrl.contains("photo?name="));

        var secondPhotoResponse = photoResponses.getLast();
        String secondPhotoUrl = secondPhotoResponse.getTagInfo().getAttribute("src");
        assertTrue(secondPhotoUrl.contains("photo?name="));

        logger.test("look at the contents of a particular photo, unauthenticated");
        var photoOne = ft.get(firstPhotoUrl);
        assertEquals(photoOne.body().asBytes().length, 3);

        logger.test("look at the contents of a larger photo, unauthenticated");
        var photoTwo = ft.get(secondPhotoUrl);
        assertEquals(photoTwo.body().asBytes().length, 15000);

        logger.test("look at the contents of a larger photo with a range header, unauthenticated");
        var photoThree = ft.get(secondPhotoUrl, List.of("Range: bytes=0-"));
        assertEquals(photoThree.body().asBytes().length, 15000);

        logger.test("look at the contents of a larger photo with a range header, unauthenticated");
        var photoThreePartial = ft.get(secondPhotoUrl, List.of("Range: bytes=1-9000"));
        assertEquals(photoThreePartial.body().asBytes().length, 9000);

        logger.test("ask for the photo again, looking for the cached value");
        var photoOneCached = ft.get(firstPhotoUrl);
        assertEquals(photoOneCached.body().asBytes().length, 3);

        logger.test("ask for the large photo again, looking for the cached value");
        var photoTwoCached = ft.get(secondPhotoUrl);
        assertEquals(photoTwoCached.body().asBytes().length, 15000);

        logger.test("check out what's on the sample domain page, authenticated");
        assertTrue(ft.get("index", authHeader).body().asString().contains("Enter a name"));
        assertTrue(ft.get("formEntry", authHeader).body().asString().contains("Name Entry"));
        assertEquals(ft.post("testform", "name_entry=abc", authHeader).statusLine().status(), CODE_303_SEE_OTHER);

        logger.test("logout");
        assertEquals(ft.get("logout", authHeader).statusLine().status(), CODE_303_SEE_OTHER);
        checkForClosedSocket();

        logger.test("if we try to upload a photo unauth, we're prevented");
        assertEquals(ft.post("upload", "foo=bar").statusLine().status(), CODE_401_UNAUTHORIZED);

        logger.test("if we try to upload a name on the sampledomain auth, we're prevented");
        assertEquals(ft.post("testform", "foo=bar").statusLine().status(), CODE_401_UNAUTHORIZED);

        logger.test("request a static asset");
        TestResponse staticResponse = ft.get("main.css");
        assertEquals(staticResponse.headers().contentType(), "content-type: text/css");
        assertTrue(staticResponse.body().asString().contains("margin-left: 0;"));

        /*
         * If a large static asset (more than a million bytes) is requested by the client, it will switch to use
         * a streaming mechanism, specifically it will read the file using FileChannel.
         */
        logger.test("Request a large static asset");
        Files.writeString(Path.of("src/test/webapp/static/largefile.txt"), "a".repeat(1_000_000 + 1));
        MyThread.sleep(20);
        TestResponse largeResponse = ft.get("largefile.txt");
        assertEquals(largeResponse.body().asString().length(), 1_000_000 + 1);
        MyThread.sleep(20);
        Files.delete(Path.of("src/test/webapp/static/largefile.txt"));
        MyThread.sleep(20);

        // testing some edge cases of #searchOne

        TestResponse responseForSearching = ft.get("index.html");

        // find nothing
        HtmlParseNode htmlParseNode = responseForSearching.searchOne(TagName.A, Map.of("foo", "bar"));
        assertEquals(htmlParseNode, HtmlParseNode.EMPTY);

        // find nothing
        List<HtmlParseNode> searchResult = responseForSearching.search(TagName.A, Map.of("foo", "bar"));
        assertTrue(searchResult.isEmpty());

        // find too much
        var searchException = assertThrows(InvariantException.class, () -> responseForSearching.searchOne(TagName.A, Map.of()));
        assertTrue(searchException.getMessage().contains("More than 1 node found.  Here they are"), searchException.getMessage());

        // *********** ERROR HANDLING SECTION *****************

        logger.test("if we try sending too many characters on a line, it should block us and lock us out");

        // ******* IMPORTANT!!! This needs to be the last test, since it locks us out ************
        // ******* IMPORTANT!!! This needs to be the last test, since it locks us out ************

        try {
            ft.get("a".repeat(context.getConstants().maxReadLineSizeBytes + 1));
        } catch (Exception ex) {
            // this is kind of a weird case I'm looking into.
            // On Windows, this runs right through without failing, but on Mac,
            // an exception is thrown here because of a connection reset. A
            // connection reset happens when a host sends packets to a closed
            // socket connection.  I debugged through here on the Windows machine
            // and it's like nothing is wrong.

            // so in the meantime, since I just need to cause the log to be entered,
            // this try-catch that eats the exception will have to do.
        }

        // remember, we're the client, we don't have immediate access to the server here.  So,
        // we have to wait for it to get through some processing before we check.
        MyThread.sleep(70);
        assertTrue(logger.doesMessageExist("127.0.0.1 is looking for vulnerabilities, for this: client sent more bytes than allowed for a single line.  max: 1024", 10));
    }

    /**
     * This method asserts we see "from SetOfSws" in the logs.  The full
     * context is to see messages like "http server removed (SocketWrapper for remote address: /127.0.0.1:53533) from SetOfSws. size: 0",
     * which indicates the server is closing sockets as soon as they're finished.
     * <br>
     * This test was needed to guarantee the system will clean up its sockets, that is,
     * it will not have a leak.
     */
    private static void checkForClosedSocket() {
        MyThread.sleep(15);
        assertTrue(logger.doesMessageExist("from SetOfSws", 15));
    }

}
