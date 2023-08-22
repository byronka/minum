package minum.utils;

import minum.Context;
import minum.logging.TestLogger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static minum.testing.TestFramework.assertEquals;

public class StringUtilsTests {

    private final TestLogger logger;

    public StringUtilsTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("StringUtilsTests");
    }

    public void tests() {
        logger.test("See that our code to clean html does its work"); {
            final var cleanedHtml = StringUtils.safeHtml("<script>alert(1)</script>");
            final var expectedHtml = "&lt;script&gt;alert(1)&lt;/script&gt;";
            assertEquals(expectedHtml, cleanedHtml);
        }

        logger.test("Cleaning html should return an empty string if given null"); {
            final var cleanedHtml = StringUtils.safeHtml(null);
            assertEquals("", cleanedHtml);
        }

        logger.test("Our code to clean strings used in attributes should work"); {
            final var cleanedHtml = StringUtils.safeAttr("alert('XSS Attack')");
            final var expectedHtml = "alert(&apos;XSS Attack&apos;)";
            assertEquals(expectedHtml, cleanedHtml);
        }

        logger.test("safeAttr should return an empty string if given null"); {
            final var cleanedHtml = StringUtils.safeAttr(null);
            assertEquals("", cleanedHtml);
        }

        logger.test("Can convert a list of bytes to a string"); {
            byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
            List<Byte> bytesList = IntStream.range(0, bytes.length).mapToObj(i -> bytes[i]).collect(Collectors.toList());
            String s = StringUtils.byteListToString(bytesList);
            assertEquals(s, "hello");
        }
    }
}
