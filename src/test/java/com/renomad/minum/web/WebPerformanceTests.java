package com.renomad.minum.web;

import com.renomad.minum.logging.Logger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.StacktraceUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.web.FunctionalTesting.extractStatusLine;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_200_OK;

/**
 * These test the performance with a full initialization and communication
 * through sockets.
 */
public class WebPerformanceTests {

    private static Logger logger;
    private static Context context;
    private static FunctionalTesting ft;
    private static FullSystem fullSystem;
    private String host;
    private int port;
    InputStreamUtils inputStreamUtils;

    /**
     * Used as a secondary check to confirm the count of requests
     */
    AtomicInteger requestCounter;
    AtomicInteger testLoopCounter;

    @Before
    public void init() {
        var constants = new Constants();
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        logger = new Logger(constants, executorService, "WebPerformanceTestLogger");
        context = new Context(executorService, constants, logger);
        var fileUtils = new FileUtils(logger, context.getConstants());
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        fullSystem = new FullSystem(context);
        fullSystem.start();
        fullSystem.getWebFramework().registerPath(RequestLine.Method.GET, "hello", this::helloName);
        this.host = context.getConstants().hostName;
        this.port = context.getConstants().serverPort;
        ft = new FunctionalTesting(context, host, port);
        inputStreamUtils = new InputStreamUtils(context.getConstants().maxReadLineSizeBytes);
    }

    private IResponse helloName(IRequest request) {
        String name = request.getRequestLine().queryString().get("name");
        return Response.htmlOk("hello " + name);
    }

    @After
    public void cleanup() {
        // delay a sec so our system has time to finish before we start deleting files
        MyThread.sleep(500);
        fullSystem.shutdown();
        context.getLogger().stop();
        context.getExecutorService().shutdownNow();
    }

    /**
     * A plain performance test - just GET a bunch of times to an endpoint that hardly does anything.
     */
    @Test
    public void test1() {
        int countParallelism = 5;
        int countRequestsToSend = 5;
        requestCounter = new AtomicInteger();

        // disable all logging
        for (var key : logger.getActiveLogLevels().keySet()) {
            logger.getActiveLogLevels().put(key, false);
        }
        StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();

        IntStream.range(0, countParallelism).boxed().parallel().forEach(
                x -> {
                    for (int i = 0; i < countRequestsToSend; i++) {
                        StatusLine.StatusCode status = ft.get("hello?name=byron").statusLine().status();
                        requestCounter.incrementAndGet();
                        assertEquals(status, CODE_200_OK);
                    }
                }
        );

        long l = stopwatchUtils.stopTimer();
        System.out.println("Took this many millis for test1: " + l + ", or " + (countParallelism * countRequestsToSend / (l / 1000.0)) + " requests per second");
        System.out.println("Ran this many iterations: " + (countParallelism * countRequestsToSend) + " and the counter is at " + requestCounter.get());

    }

    /*
     * This tests the performance of a small number of sockets, with many
     * requests being handled on each.  To see a test where each request has
     * its own socket, see {@link #test1}
     */
    @Test
    public void test2() {
        int countParallelism = 5;
        int countRequests = 5;
        requestCounter = new AtomicInteger();

        // disable all logging
        for (var key : logger.getActiveLogLevels().keySet()) {
            logger.getActiveLogLevels().put(key, false);
        }
        StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();
        String host = context.getConstants().hostName;
        int port = context.getConstants().serverPort;
        IntStream.range(0, countParallelism).boxed().parallel().forEach(
                x -> {
                    try (Socket socket = new Socket(host, port)) {
                        try (ISocketWrapper client = new SocketWrapper(socket, null, logger, 0, "localhost")) {
                            for (int i = 0; i < countRequests; i++) {
                                FunctionalTesting.TestResponse testResponse = ft.innerClientSend(
                                        client,
                                        RequestLine.Method.GET,
                                        "hello?name=byron", new byte[0], List.of());
                                requestCounter.incrementAndGet();
                                assertEquals(testResponse.body().asString(), "hello byron");
                            }
                        }
                    } catch (Exception e) {
                        logger.logDebug(() -> "Error during client send: " + StacktraceUtils.stackTraceToString(e));
                    }
                }
        );

        long l = stopwatchUtils.stopTimer();
        System.out.println("Took this many millis for test2: " + l + ", or " + (countParallelism * countRequests / (l / 1000.0)) + " requests per second");
        System.out.println("Ran this many iterations: " + (countParallelism * countRequests) + " and the counter is at " + requestCounter.get());

    }

    /*
     * A third kind of performance test - this one creates a bunch of virtual threads
     * and submits them using an {@link ExecutorService} using virtual threads
     */
    @Test
    public void test3() throws ExecutionException, InterruptedException {
        int countParallelism = 5;
        int countRequests = 5;
        requestCounter = new AtomicInteger();
        testLoopCounter = new AtomicInteger();

        // disable all logging
        for (var key : logger.getActiveLogLevels().keySet()) {
            logger.getActiveLogLevels().put(key, false);
        }
        StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();


        ExecutorService executorService = context.getExecutorService();

        List<Future<?>> centralLoopFutures = new ArrayList<>();

        for (int i = 0; i < countParallelism; i++) {
            Future<?> future = executorService.submit(() -> testLoop(countRequests));
            centralLoopFutures.add(future);
        }

        for (Future<?> future : centralLoopFutures) {
            future.get();
        }
        long l = stopwatchUtils.stopTimer();

        System.out.println("Took this many millis for test3: " + l + ", or " + (countParallelism * countRequests / (l / 1000.0)) + " requests per second");
        System.out.println("Ran this many iterations: " + (countParallelism * countRequests) +
                " and the counter is at " + requestCounter.get() + " and the test loop counter is at " + testLoopCounter.get());
    }

    private void testLoop(int countLoops) {
        testLoopCounter.incrementAndGet();
        try (Socket socket = new Socket(host, port)) {
            ISocketWrapper clientSocketWrapper = new SocketWrapper(socket, null, logger, 0, "localhost");
            for (int i = 0; i < countLoops; i++) {

                requestCounter.incrementAndGet();
                MyResponse myResponse = clientSend(clientSocketWrapper);
                assertEquals(myResponse.statusLine.status(), CODE_200_OK);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MyResponse clientSend (ISocketWrapper socketWrapper) throws IOException {

        socketWrapper.send("""
                            GET /hello?name=byron HTTP/1.1\r
                            Host: localhost:8080\r
                            \r
                            """.getBytes(StandardCharsets.UTF_8));
        socketWrapper.flush();

        InputStream is = socketWrapper.getInputStream();
        StatusLine statusLine = extractStatusLine(inputStreamUtils.readLine(is));
        List<String> allHeaders = Headers.getAllHeaders(is, inputStreamUtils);
        Headers headers = new Headers(allHeaders);
        int bodyLength = headers.contentLength();
        byte[] body = inputStreamUtils.read(bodyLength, is);
        return new MyResponse(statusLine, headers, body);
    }

    record MyResponse(StatusLine statusLine, Headers headers, byte[] body) {}
}
