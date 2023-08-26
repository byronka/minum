package minum.utils;

import minum.Context;
import minum.logging.TestLogger;
import minum.web.Response;
import minum.web.StatusLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static minum.testing.TestFramework.*;
import static minum.web.StatusLine.StatusCode._400_BAD_REQUEST;
import static minum.web.StatusLine.StatusCode._404_NOT_FOUND;

public class FileUtilsTests {
    private final TestLogger logger;
    private final Context context;

    public FileUtilsTests(Context context) {
        this.context = context;
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("FileUtilsTests");
    }

    public void tests() throws IOException {

        var fileUtils = new FileUtils(logger, context.getConstants());

        logger.test("Testing CSS"); {
            Response response = fileUtils.readStaticFile("main.css");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
            assertEquals(response.extraHeaders().get("content-type"), "text/css");
        }

        logger.test("Testing JS"); {
            Response response = fileUtils.readStaticFile("index.js");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
            assertEquals(response.extraHeaders().get("content-type"), "application/javascript");
        }

        logger.test("Testing HTML"); {
            Response response = fileUtils.readStaticFile("index.html");

            assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
            assertTrue(response.body().length > 0);
            assertEquals(response.extraHeaders().get("content-type"), "text/html");
        }

        /*
         If a user requests a file with .. in front, that means go up
         a directory - we don't really want that happening.
         */
        logger.test("Edge case - reading from outside the directory"); {
            Response response = fileUtils.readStaticFile("../templates/auth/login_page_template.html");

            assertEquals(response.statusCode(), _400_BAD_REQUEST);
        }

        logger.test("Edge case - forward slashes"); {
            Response response = fileUtils.readStaticFile("//index.html");

            assertEquals(response.statusCode(), _400_BAD_REQUEST);
        }

        logger.test("Edge case - colon"); {
            Response response = fileUtils.readStaticFile(":");

            assertEquals(response.statusCode(), _400_BAD_REQUEST);
        }

        logger.test("Edge case - a directory"); {
            Response response = fileUtils.readStaticFile(".out/templates/listphotos");

            assertEquals(response.statusCode(), _404_NOT_FOUND);
        }

        logger.test("Edge case - current directory"); {
            Response response = fileUtils.readStaticFile("./");

            assertEquals(response.statusCode(), _400_BAD_REQUEST);
        }

        /*
         If we encounter a file we don't recognize, we'll label it as application/octet-stream.  Browsers
         won't know what to do with this, so they will treat it as if the Content-Disposition header was set
        to attachment, and propose a "Save As" dialog.  This will make it clearer when data has not
        been labeled with a proper mime.
         */
        logger.test("If the static cache is given something that it can't handle, it returns application/octet-stream"); {
            var response = fileUtils.readStaticFile("Foo");
            assertEquals(response.extraHeaders().get("content-type"), "application/octet-stream");
        }

        /*
        Users can add more mime types to our system by registering them
        in the minum.config file in EXTRA_MIME_MAPPINGS.
         */
        logger.test("It can read the extra_mime_mappings"); {
            var input = List.of("png","image/png","wav","audio/wav");
            fileUtils.readExtraMappings(input);
            var mappings = fileUtils.getSuffixToMime();
            assertEquals(mappings.get("png"), "image/png");
            assertEquals(mappings.get("wav"), "audio/wav");
        }

        logger.test("while reading the extra mappings, bad syntax will cause a clear failure"); {
            var input = List.of("png","image/png","EXTRA_WORD_HERE","wav","audio/wav");
            var ex = assertThrows(InvariantException.class, () -> fileUtils.readExtraMappings(input));
            assertEquals(ex.getMessage(), "input must be even (key + value = 2 items). Your input: [png, image/png, EXTRA_WORD_HERE, wav, audio/wav]");
        }

        logger.test("If there's no values, it should work fine, it should simply not add any new mime mappings"); {
            var mappings = fileUtils.getSuffixToMime();
            int before = mappings.size();
            List<String> input = List.of();

            fileUtils.readExtraMappings(input);

            int after = mappings.size();
            assertEquals(before,after);
        }
    }
}
