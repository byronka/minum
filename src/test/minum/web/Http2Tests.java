package minum.web;

import minum.testing.TestLogger;
import minum.web.http2.Http2Frame;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static minum.testing.TestFramework.assertEqualByteArray;

public class Http2Tests {

    private final TestLogger logger;

    public Http2Tests(TestLogger logger) {
        this.logger = logger;
    }

    public void test(ExecutorService es) {

        /*
        We will use this sample request in a few tests to follow
         */
        var request = new Request(
                new Headers(
                        List.of(
                                "Host: example.org",
                                "Accept: image/jpeg")),
                new StartLine(
                        StartLine.Verb.GET,
                        new StartLine.PathDetails("resource", "", Map.of()),
                        WebEngine.HttpVersion.ONE_DOT_ONE, "GET /resource HTTP/1.1"),
                Body.EMPTY,
                "REMOTE_REQUESTER");

        /*
            From the RFC 9113, here is an example of a
            basic GET request conversion

          GET /resource HTTP/1.1       HEADERS
          Host: example.org      ==>   + END_STREAM
          Accept: image/jpeg           + END_HEADERS
                                       :method = GET
                                       :scheme = https
                                       :authority = example.org
                                       :path = /resource
                                       host = example.org
                                       accept = image/jpeg

        This is a gentle test.  There's really a lot more to delve into,
        but since we're just starting, we're going to use one of the examples
        from the HTTP/2 RFC 9113 as the jumping board.

        The gentleness, in this case, refers to how little of a gap I am creating
        between the expected and actual.  The RFC for HTTP/2 makes it seems like
        it's TCP on TCP.  That's a heck of a lot heavier than HTTP/1.1, which is
        far simpler - just send strings - where HTTP/2 requires managing a larger
        state engine and sophisticated patterns.
         */
        logger.test("encode convert HTTP/1.1 GET to HTTP/2 frames"); {
            List<Http2Frame> frames = Http2Frame.encode(request, "https", "myhost.com");

            assertEqualByteArray(
                    frames.get(0).toBytes(),
                    new byte[]{0, 0, 0, 49, 1, 0, 0, 0, 1, -126, 68, 8, 114, 101, 115,
                            111, 117, 114, 99, 101, -121, 65, 10, 109, 121, 104, 111,
                            115, 116, 46, 99, 111, 109, 102, 11, 101, 120, 97, 109,
                            112, 108, 101, 46, 111, 114, 103, 83, 10, 105, 109,
                            97, 103, 101, 47, 106, 112, 101, 103});


            /*
             * Let's go the opposite direction of the previous test
             */
//            logger.test("decode HTTP/2 HEADER frame to HTTP/1.1"); {
//                byte[] incomingHeaderFrame =
//                        new byte[]{0, 0, 0, 24, 1, 0, 0, 0, 1, -126, 68, 8, 114,
//                                101, 115, 111, 117, 114, 99, 101, -121, 65, 10,
//                                109, 121, 104, 111, 115, 116, 46, 99, 111, 109};
//
//                Http2Frame result = Http2Frame.decode(new ByteArrayInputStream(incomingHeaderFrame));
//
//                assertEquals(result, frames.get(0));
//            }
        }

    }


}
