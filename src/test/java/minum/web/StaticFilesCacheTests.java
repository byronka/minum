package minum.web;

import minum.Context;
import minum.logging.TestLogger;
import minum.utils.InvariantException;

import java.io.IOException;
import java.util.List;

import static minum.testing.TestFramework.*;

public class StaticFilesCacheTests {
    private final TestLogger logger;
    private final Context context;

    public StaticFilesCacheTests(Context context) {
        this.context = context;
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("StaticFilesCacheTests");
    }

    public void tests() throws IOException {

        var staticFilesCache = new StaticFilesCache(context);

        logger.test("Happy path - someone requests a file we have in the static directory"); {
            // first, there should be no value for this in the cache.
            assertTrue(staticFilesCache.getStaticResponse("moon.webp") == null);

            Response response = staticFilesCache.loadStaticFile("moon.webp");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);

            // and then when we ask for the file again, it's in the cache.
            assertEquals(staticFilesCache.getStaticResponse("moon.webp"), response);
        }

        logger.test("Testing CSS"); {
            Response response = staticFilesCache.loadStaticFile("main.css");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
        }

        logger.test("Testing JS"); {
            Response response = staticFilesCache.loadStaticFile("index.js");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
        }

        logger.test("Testing HTM"); {
            Response response = staticFilesCache.loadStaticFile("index.html");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
        }

        /*
         If a user requests a file with .. in front, that means go up
         a directory - we don't really want that happening.
         */
        logger.test("Edge case - reading from outside the directory"); {
            Response response = staticFilesCache.loadStaticFile("../templates/auth/login_page_template.html");

            assertTrue(response == null);
        }

        logger.test("Edge case - forward slashes"); {
            Response response = staticFilesCache.loadStaticFile("//index.html");

            assertTrue(response == null);
        }

        logger.test("Edge case - colon"); {
            Response response = staticFilesCache.loadStaticFile(":");

            assertTrue(response == null);
        }

        logger.test("Edge case - a directory"); {
            Response response = staticFilesCache.loadStaticFile("./listphotos");

            assertTrue(response == null);
        }

        logger.test("Edge case - current directory"); {
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

        /*
        Users can add more mime types to our system by registering them
        in the app.config file in EXTRA_MIME_MAPPINGS.
         */
        logger.test("It can read the extra_mime_mappings"); {
            var input = List.of("png","image/png","wav","audio/wav");
            staticFilesCache.readExtraMappings(input);
            var mappings = staticFilesCache.getSuffixToMime();
            assertEquals(mappings.get("png"), "image/png");
            assertEquals(mappings.get("wav"), "audio/wav");
        }

        logger.test("while reading the extra mappings, bad syntax will cause a clear failure"); {
            var input = List.of("png","image/png","EXTRA_WORD_HERE","wav","audio/wav");
            var ex = assertThrows(InvariantException.class, () -> staticFilesCache.readExtraMappings(input));
            assertEquals(ex.getMessage(), "input must be even (key + value = 2 items). Your input: [png, image/png, EXTRA_WORD_HERE, wav, audio/wav]");
        }

        logger.test("If there's no values, it should work fine, it should simply not add any new mime mappings"); {
            var mappings = staticFilesCache.getSuffixToMime();
            int before = mappings.size();
            List<String> input = List.of();

            staticFilesCache.readExtraMappings(input);

            int after = mappings.size();
            assertEquals(before,after);
        }
    }
}
