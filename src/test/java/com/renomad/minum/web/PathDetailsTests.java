package com.renomad.minum.web;

import org.junit.Test;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class PathDetailsTests {

    @Test
    public void happyPath() {
        PathDetails pathDetails = new PathDetails("foo?biz=baz", "biz=baz", Map.of("biz", "baz"));
        assertEquals(pathDetails.getRawQueryString(), "biz=baz");
    }
}
