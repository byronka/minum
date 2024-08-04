package com.renomad.minum.web;

import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class BodyTests {

    @Test
    public void testGettingValue_EdgeCase_WhenNotFound() {
        Body empty = Body.EMPTY;
        assertEquals(empty.asString("foo"), "");
        assertEquals(empty.getBodyType(), BodyType.NONE);
    }
}
