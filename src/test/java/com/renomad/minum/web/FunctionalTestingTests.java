package com.renomad.minum.web;

import com.renomad.minum.state.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;

public class FunctionalTestingTests {

    private Context context;

    @Before
    public void init() {
        var properties = new Properties();
        properties.setProperty("SERVER_PORT", "7878");
        properties.setProperty("HOST_NAME", "localhost");
        context = buildTestingContext("Functional Testing Tests", properties);
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * If an exception takes place in {@link FunctionalTesting#send(RequestLine.Method, String, byte[], List)},
     * it will return an empty TestResponse
     */
    @Test
    public void test_sendDealsWithException() {
        var functionalTesting = new FunctionalTesting(context, "localhost", 6000);
        var result = functionalTesting.send(RequestLine.Method.HEAD, "foo", new byte[0], List.of());
        assertEquals(result, FunctionalTesting.TestResponse.EMPTY);
    }

}
