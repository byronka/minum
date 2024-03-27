package com.renomad.minum.utils;

import org.junit.Test;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.Invariants.*;

public class InvariantsTests {

    @Test
    public void test_MustBeTrue() {
        mustBeTrue(true, "testing...");
        var ex = assertThrows(InvariantException.class, () -> mustBeTrue(false, "testing..."));
        assertEquals(ex.getMessage(), "testing...");
    }

    @Test
    public void test_MustBeFalse() {
        mustBeFalse(false, "testing...");
        var ex = assertThrows(InvariantException.class, () -> mustBeFalse(true, "testing..."));
        assertEquals(ex.getMessage(), "testing...");
    }

    @Test
    public void test_MustNotBeNull() {
        Map<Object, Object> mapObject = Map.of();
        var object = mustNotBeNull(mapObject);
        assertTrue(object == object);
        var ex = assertThrows(InvariantException.class, () -> mustNotBeNull(null));
        assertEquals(ex.getMessage(), "value must not be null");
    }
}
