package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.testing.TestFramework.shutdownTestingContext;

public class StringUtilsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("StringUtilsTests");
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    /**
     * See that our code to clean html does its work
     */
    @Test
    public void test_CleanHtml() {
        final var cleanedHtml = StringUtils.safeHtml("<script>alert(1)</script>");
        final var expectedHtml = "&lt;script&gt;alert(1)&lt;/script&gt;";
        assertEquals(expectedHtml, cleanedHtml);
    }

    /**
     * Cleaning html should return an empty string if given null
     */
    @Test
    public void test_CleanHtml_Null() {
        final var cleanedHtml = StringUtils.safeHtml(null);
        assertEquals("", cleanedHtml);
    }

    /**
     * Our code to clean strings used in attributes should work
     */
    @Test
    public void test_CleanAttributes() {
        final var cleanedHtml = StringUtils.safeAttr("alert('XSS Attack')");
        final var expectedHtml = "alert(&apos;XSS Attack&apos;)";
        assertEquals(expectedHtml, cleanedHtml);
    }

    /**
     * safeAttr should return an empty string if given null
     */
    @Test
    public void test_CleanAttributes_Null() {
        final var cleanedHtml = StringUtils.safeAttr(null);
        assertEquals("", cleanedHtml);
    }

    /**
     * Can convert a list of bytes to a string
     */
    @Test
    public void test_BytesListToString() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        List<Byte> bytesList = IntStream.range(0, bytes.length).mapToObj(i -> bytes[i]).collect(Collectors.toList());
        String s = StringUtils.byteListToString(bytesList);
        assertEquals(s, "hello");
        String shouldBeNull = StringUtils.byteListToString(null);
        assertTrue(shouldBeNull == null);
    }
    
    @Test
    public void test_ByteArrayToString() {
        String shouldBeNull = StringUtils.byteArrayToString(null);
        assertTrue(shouldBeNull == null);
    }

    /**
     * Some basic behaviors of the {@link StringUtils#isPureDigits(String)} method
     */
    @Test
    public void testIsPureDigits() {
        // valid
        assertTrue(StringUtils.isPureDigits("0"));
        assertTrue(StringUtils.isPureDigits("123"));

        // invalid
        assertFalse(StringUtils.isPureDigits("+5"));
        assertFalse(StringUtils.isPureDigits("12.34"));
        assertFalse(StringUtils.isPureDigits("abc"));
        assertFalse(StringUtils.isPureDigits("123a"));
        assertFalse(StringUtils.isPureDigits(""));
        assertFalse(StringUtils.isPureDigits(null));
    }

    /**
     * What happens when we ask for a large random number?
     * We allow users to request up to a thousand characters, which
     * is gargantuan, probably far more than would ever be necessary
     * for this one-off utility method's purposes.  Setting to a maximum
     * value just provides basic range control.
     */
    @Test
    public void testGeneratingSecureRandomNumber_EdgeCase_Large() {
        assertEquals(StringUtils.generateSecureRandomString(999).length(), 999);
        assertEquals(StringUtils.generateSecureRandomString(1000).length(), 1000);
        var ex = assertThrows(UtilsException.class, () -> StringUtils.generateSecureRandomString(1001));
        assertEquals(ex.getMessage(), "Input must be less than 1000");
    }


    /**
     * If the user requests zero or negative length values, we'll throw an exception
     */
    @Test
    public void testGeneratingSecureRandomNumber_EdgeCase_Small() {
        var ex = assertThrows(UtilsException.class, () -> StringUtils.generateSecureRandomString(0));
        assertEquals(ex.getMessage(), "Input must be a positive non-zero number");

        var ex2 = assertThrows(UtilsException.class, () -> StringUtils.generateSecureRandomString(-1));
        assertEquals(ex2.getMessage(), "Input must be a positive non-zero number");
    }

}
