package com.renomad.minum.testing;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;

public class TestFrameworkTests {

    /*
    If the inner method doesn't throw an exception, assertThrows should throw one.
     */
    @Test
    public void test_AssertThrows_NoException() {
        assertThrows(RuntimeException.class, () -> assertThrows(IOException.class, () -> {}));
    }

    @Test
    public void test_AssertThrows_AssertOnMessage() {
        assertThrows(RuntimeException.class, "Did not get expected message (I am foo). Instead, got: Failed to throw exception", () -> assertThrows(RuntimeException.class, "I am foo", () -> {}));
    }

    @Test
    public void test_assertEquals_Basic() {
        assertEquals(true, true);
        assertThrows(RuntimeException.class, () -> assertEquals(true, false));
    }

    @Test
    public void test_assertEquals_Lists() {
        assertEquals(List.of("a","b"), List.of("a","b"));
        assertThrows(RuntimeException.class, () -> assertEquals(List.of("a","b", "c"), List.of("a","b")));
        assertThrows(RuntimeException.class, () -> assertEquals(List.of("a","b", "c"), List.of("a","b", "d")));
    }

    @Test
    public void test_assertEquals_ListsDifferentOrders() {
        assertEqualsDisregardOrder(List.of("a", "b"), List.of("b", "a"));
        assertThrows(RuntimeException.class, () -> assertEqualsDisregardOrder(List.of("a", "b", "c"), List.of("b", "a")));
        assertThrows(RuntimeException.class, () -> assertEqualsDisregardOrder(List.of("a", "b", "c"), List.of("b", "a", "d")));
    }

    @Test
    public void test_assertTrue_Basic() {
        assertTrue(true);
        assertThrows(RuntimeException.class, () -> assertTrue(false));
        assertThrows(RuntimeException.class, () -> assertTrue(false, "Here is a message shown during failure"));
    }

    @Test
    public void test_assertFalse_Basic() {
        assertFalse(false);
        assertThrows(RuntimeException.class, () -> assertFalse(true));
    }

}
