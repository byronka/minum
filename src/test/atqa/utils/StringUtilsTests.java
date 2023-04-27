package atqa.utils;

import atqa.logging.TestLogger;
import static atqa.testing.TestFramework.assertEquals;

public class StringUtilsTests {

    private final TestLogger logger;

    public StringUtilsTests(TestLogger logger) {

        this.logger = logger;
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
    }
}
