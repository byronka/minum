package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.HttpVersion.ONE_DOT_ONE;
import static com.renomad.minum.web.RequestLine.Method.GET;

public class EndpointTests {


    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("EndpointTests");
        logger = (TestLogger) context.getLogger();
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
     * An example of testing a simple endpoint.
     * <br>
     * An endpoint consists of a method that takes a Request and
     * returns a Response.  With this contract in hand, testing
     * becomes manageable
     */
    @Test
    public void test_Endpoint_HappyPath() {
        var request = new FakeRequest();
        PathDetails pathDetails = new PathDetails("", "", Map.of("name", "foo"));
        request.requestLine = new RequestLine(GET, pathDetails, ONE_DOT_ONE, "", logger);

        var response = helloName(request);

        String bodyOfResponse = new String(response.getBody(), StandardCharsets.UTF_8);
        assertEquals(bodyOfResponse, "hello foo");
    }

    /**
     * a GET request, at /hello?name=foo
     * <p>
     *     Replies "hello foo"
     * </p>
     */
    public IResponse helloName(IRequest request) {
        String name = request.getRequestLine().queryString().get("name");

        return Response.htmlOk("hello " + name);
    }
}
