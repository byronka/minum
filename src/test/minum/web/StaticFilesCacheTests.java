package minum.web;

import minum.Context;
import minum.testing.TestLogger;
import minum.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

import static minum.testing.TestFramework.*;

public class StaticFilesCacheTests {
    private final TestLogger logger;

    public StaticFilesCacheTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("StaticFilesCacheTests");
    }

    public void tests() throws IOException {

        var staticFilesCache = new StaticFilesCache(logger);

        logger.test("Happy path - someone requests a file we have in the static directory"); {
            // first, there should be no value for this in the cache.
            assertTrue(staticFilesCache.getStaticResponse("moon.webp") == null);

            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("moon.webp");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);

            // and then when we ask for the file again, it's in the cache.
            assertEquals(staticFilesCache.getStaticResponse("moon.webp"), response);
        }

        logger.test("Testing CSS"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("main.css");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
        }

        logger.test("Testing JS"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("index.js");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
        }

        logger.test("Testing HTM"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("index.html");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
        }

        /*
         If a user requests a file with .. in front, that means go up
         a directory - we don't really want that happening.
         */
        logger.test("Edge case - reading from outside the directory"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("../templates/auth/login_page_template.html");

            assertTrue(response == null);
        }

        logger.test("Edge case - forward slashes"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("//index.html");

            assertTrue(response == null);
        }

        logger.test("Edge case - colon"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile(":");

            assertTrue(response == null);
        }

        logger.test("Edge case - a directory"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("./listphotos");

            assertTrue(response == null);
        }

        logger.test("Edge case - current directory"); {
            // When we run the load command, we get back a response
            Response response = staticFilesCache.loadStaticFile("./");

            assertTrue(response == null);
        }

        /*
         If we encounter a file we don't recognize, we'll label it as application/octet-stream.  Browsers
         won't know what to do with this, so they will treat it as if the Content-Disposition header was set
        to attachment, and propose a "Save As" dialog.  This will make it clearer when data has not
        been labeled with a proper mime.
         */
        logger.test("If the static cache is given something that it can't handle, it returns application/octet-stream"); {
            var response = staticFilesCache.createStaticFileResponse("Foo", new byte[0]);
            assertEquals(response.extraHeaders().get("Content-Type"), "application/octet-stream");
        }

    }
}
