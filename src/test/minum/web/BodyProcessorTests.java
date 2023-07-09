package minum.web;

import minum.Context;
import minum.htmlparsing.ParsingException;
import minum.logging.ILogger;
import minum.testing.TestLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static minum.testing.TestFramework.*;

public class BodyProcessorTests {

    private final TestLogger logger;
    private final Context context;

    public BodyProcessorTests(Context context) {
        this.context = context;
        this.logger = (TestLogger)context.getLogger();
        logger.testSuite("BodyProcessorTests");
    }

    public void tests() {

        logger.test("Edge case - if a multipart form body is missing a valid name value in its headers"); {
            String body = """
                    --i_am_a_boundary\r
                    Content-Type: text/plain\r
                    Content-Disposition: form-data; NO_NAME="text1"\r
                    \r
                    I am a value that is text\r
                    --i_am_a_boundary\r
                    """.stripIndent();
            var bodyProcessor = new BodyProcessor(context);

            var exception = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                    body.length(),
                    "content-type: multipart/form-data; boundary=i_am_a_boundary",
                    body.getBytes(StandardCharsets.UTF_8)
                    ));

            assertEquals(exception.getMessage(), "Unable to parse this body");
            assertEquals(exception.getCause().getMessage(), "No name value found in the headers of a partition. Data: --i_am_a_boundary\nContent-Type: text/plain\nContent-Disposition: form-data; NO_NAME=\"text1\"\n\nI am a value that is text\n--i_am_a_boundary\n");
        }

        /*
        If the data has a content type of url-encoded form, and we
        hit malformed data, return useful information in the exception.
         */
        logger.test("Edge case - malformed urlencoded form"); {
            String body = """
                    Foo
                    """.strip();
            var bodyProcessor = new BodyProcessor(context);

            ParsingException parsingException = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                    body.length(),
                    "content-type: application/x-www-form-urlencoded",
                    body.getBytes(StandardCharsets.UTF_8)
            ));

            assertEquals(parsingException.getMessage(), "Unable to parse this body");
            assertEquals(parsingException.getCause().getMessage(), "Unable to parse this body as application/x-www-form-urlencoded. Data: Foo");
        }

        /*
        If the data has a content type of url-encoded form, and we
        hit malformed data, return useful information in the exception.
         */
        logger.test("Edge case - malformed urlencoded form with large data"); {
            String body = "a".repeat(BodyProcessor.MAX_SIZE_DATA_RETURNED_IN_EXCEPTION + 1);
            var bodyProcessor = new BodyProcessor(context);

            ParsingException parsingException = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                    body.length(),
                    "content-type: application/x-www-form-urlencoded",
                    body.getBytes(StandardCharsets.UTF_8)
            ));

            assertEquals(parsingException.getMessage(), "Unable to parse this body");
            assertTrue(parsingException.getCause().getMessage().contains("aaaaaaaaaa ... (remainder of data trimmed)"));
        }


        logger.test("Edge case - if a multipart form body is missing a valid name value in its headers, with large data"); {
            String body =
                    """
                    --i_am_a_boundary\r
                    Content-Type: text/plain\r
                    Content-Disposition: form-data; NO_NAME="text1"\r
                    \r
                    I am a value that is
                    """ +
                            "a".repeat(BodyProcessor.MAX_SIZE_DATA_RETURNED_IN_EXCEPTION+1) +
                    """
                    text\r
                    --i_am_a_boundary\r
                    """.stripIndent();
            var bodyProcessor = new BodyProcessor(context);

            var exception = assertThrows(ParsingException.class, () -> bodyProcessor.extractBodyFromBytes(
                    body.length(),
                    "content-type: multipart/form-data; boundary=i_am_a_boundary",
                    body.getBytes(StandardCharsets.UTF_8)
            ));

            assertEquals(exception.getMessage(), "Unable to parse this body");
            assertTrue(exception.getCause().getMessage().contains("aaaaaaaaaaaaaa ... (remainder of data trimmed)"));
        }

    }
}
