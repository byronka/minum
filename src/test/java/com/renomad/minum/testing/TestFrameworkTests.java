package com.renomad.minum.testing;

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
        assertThrows(TestFailureException.class, () -> assertThrows(IOException.class, () -> {}));
    }

    @Test
    public void test_AssertThrows_AssertOnMessage() {
        assertThrows(TestFailureException.class, "Did not get expected message (I am foo). Instead, got: Failed to throw exception", () -> assertThrows(RuntimeException.class, "I am foo", () -> {}));
    }

    @Test
    public void test_assertEquals_Basic() {
        assertEquals(true, true);
        assertThrows(TestFailureException.class, () -> assertEquals(true, false));
    }

    @Test
    public void test_assertEquals_Lists() {
        assertEquals(List.of("a","b"), List.of("a","b"));
        assertThrows(TestFailureException.class, () -> assertEquals(List.of("a","b", "c"), List.of("a","b")));
        assertThrows(TestFailureException.class, () -> assertEquals(List.of("a","b", "c"), List.of("a","b", "d")));
    }

    @Test
    public void test_assertEquals_ListsDifferentOrders() {
        assertEqualsDisregardOrder(List.of("a", "b"), List.of("b", "a"));
        assertThrows(TestFailureException.class, () -> assertEqualsDisregardOrder(List.of("a", "b", "c"), List.of("b", "a")));
        assertThrows(TestFailureException.class, () -> assertEqualsDisregardOrder(List.of("a", "b", "c"), List.of("b", "a", "d")));
        var ex = assertThrows(TestFailureException.class, () -> assertEqualsDisregardOrder(List.of("a", "b", "c"), List.of("b", "a", "d"), "Should be in order"));
        assertTrue(ex.getMessage().contains("Should be in order"));
    }

    @Test
    public void test_assertTrue_Basic() {
        assertTrue(true);
        assertThrows(TestFailureException.class, () -> assertTrue(false));
        assertThrows(TestFailureException.class, "Here is a message shown during failure", () -> assertTrue(false, "Here is a message shown during failure"));
    }

    @Test
    public void test_assertFalse_Basic() {
        assertFalse(false);
        assertThrows(TestFailureException.class, () -> assertFalse(true));
    }

    @Test
    public void test_assertFalse_WithMessage() {
        assertThrows(TestFailureException.class, "should be false", () -> assertFalse(true, "should be false"));
    }

    @Test
    public void test_assertEqualsByteArray() {
        assertEqualByteArray(new byte[]{1,2,3}, new byte[]{1,2,3});
        var ex = assertThrows(TestFailureException.class, () -> assertEqualByteArray(new byte[]{1,2,3}, new byte[]{4,5,6}, "should be equal arrays"));
        assertTrue(ex.getMessage().contains("should be equal arrays"));
    }

}
