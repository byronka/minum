package minum.testing;

import minum.Context;

import static minum.testing.RegexUtils.find;
import static minum.testing.TestFramework.*;

public class RegexUtilsTests {

    private final TestLogger logger;
    private final Context context;

    public RegexUtilsTests(Context context) {
        this.context = context;
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("RegexUtilsTests");
    }

    public void tests() {

        logger.test("We should be able to do a quick search by regex"); {
            var result = find("photo\\?name=[a-z0-9\\-]*", "src=photo?name=abc123");
            assertEquals(result, "photo?name=abc123");
        }

        logger.test("We should also have the option to search using a named matching group"); {
            var result = find("photo\\?name=(?<somecoolname>[a-z0-9\\-]*)", "somecoolname", "src=photo?name=abc123");
            assertEquals(result, "abc123");
        }

        logger.test("The named matching group must be all alphanumerics - no special chars, please"); {
            var ex = assertThrows(java.util.regex.PatternSyntaxException.class, () -> find("photo\\?name=(?<some_cool_name>[a-z0-9\\-]*)", "some_cool_name", "src=photo?name=abc123"));
            assertTrue(ex.getMessage().contains("named capturing group is missing trailing '>' near index 19"));
        }
    }
}
