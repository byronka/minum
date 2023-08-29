package com.renomad.minum.testing;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;

import java.io.IOException;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;

public class TestFrameworkTests {
    private final TestLogger logger;

    public TestFrameworkTests(Context context) {
        this.logger = (TestLogger) context.getLogger();
        logger.testSuite("TestFrameworkTests");
    }

    public void tests() {
        /*
        If the inner method doesn't throw an exception, assertThrows should throw one.
         */
        logger.test("Testing that assertThrows behaves properly when no internal exception is thrown"); {
            assertThrows(RuntimeException.class, () -> assertThrows(IOException.class, () -> {}));
        }

        logger.test("We should be able to assert on the exception message"); {
            assertThrows(RuntimeException.class, "Did not get expected message (I am foo). Instead, got: Failed to throw exception", () -> assertThrows(RuntimeException.class, "I am foo", () -> {}));
        }

        logger.test("assertEquals should assert that two items are equal"); {
            assertEquals(true, true);
            assertThrows(RuntimeException.class, () -> assertEquals(true, false));
        }

        logger.test("assertEquals should assert that lists in the same order are equal"); {
            assertEquals(List.of("a","b"), List.of("a","b"));
            assertThrows(RuntimeException.class, () -> assertEquals(List.of("a","b", "c"), List.of("a","b")));
            assertThrows(RuntimeException.class, () -> assertEquals(List.of("a","b", "c"), List.of("a","b", "d")));
        }

        logger.test("assert that two lists with items in different orders but otherwise equal can be compared"); {
            assertEqualsDisregardOrder(List.of("a", "b"), List.of("b", "a"));
            assertThrows(RuntimeException.class, () -> assertEqualsDisregardOrder(List.of("a", "b", "c"), List.of("b", "a")));
            assertThrows(RuntimeException.class, () -> assertEqualsDisregardOrder(List.of("a", "b", "c"), List.of("b", "a", "d")));
        }

        logger.test("assertTrue should do its job"); {
            assertTrue(true);
            assertThrows(RuntimeException.class, () -> assertTrue(false));
            assertThrows(RuntimeException.class, () -> assertTrue(false, "Here is a message shown during failure"));
        }

        logger.test("assertFalse should do its job"); {
            assertFalse(false);
            assertThrows(RuntimeException.class, () -> assertFalse(true));
        }

    }
}
