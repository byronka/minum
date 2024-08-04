package com.renomad.minum.web;

import com.renomad.minum.state.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.HttpVersion.ONE_DOT_ONE;
import static com.renomad.minum.web.RequestLine.Method.GET;

public class RequestLineTests {

    private Context context;

    @Before
    public void init() {
        context = buildTestingContext("RequestLine tests");
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }


    @Test
    public void test_GetRawValue() {
        var startLine = new RequestLine(
                GET,
                new PathDetails("mypath", "", Map.of()),
                ONE_DOT_ONE,
                "testing test",
                context.getLogger());
        String rawValue = startLine.getRawValue();
        assertEquals(rawValue, "testing test");
    }
}
