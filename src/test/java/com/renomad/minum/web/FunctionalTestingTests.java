package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.MyThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static com.renomad.minum.testing.TestFramework.*;

public class FunctionalTestingTests {

    private Context context;
    private TestLogger logger;
    private IInputStreamUtils inputStreamUtils;
    private WebEngine webEngine;
    private ExecutorService es;

    @Before
    public void init() {
        var properties = new Properties();
        properties.setProperty("SERVER_PORT", "7878");
        properties.setProperty("HOST_NAME", "localhost");
        context = buildTestingContext("Functional Testing Tests", properties);
        logger = (TestLogger)context.getLogger();
        inputStreamUtils = context.getInputStreamUtils();
        webEngine = new WebEngine(context);
        es = context.getExecutorService();
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void test_FunctionalTesting() {
        ThrowingConsumer<ISocketWrapper> handler = (sw) -> {
            InputStream is = sw.getInputStream();
            logger.logDebug(() -> inputStreamUtils.readLine(is));
            sw.close();
            assertTrue(logger.doesMessageExist("Host: localhost:7878",8));
        };

        webEngine.startServer(es, handler);
        var functionalTesting = new FunctionalTesting(context, "localhost", 7878);
        functionalTesting.send(RequestLine.Method.GET, "foo");
        MyThread.sleep(30);

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
