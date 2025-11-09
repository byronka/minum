package com.renomad.minum.web;

import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.HttpVersion.ONE_DOT_ONE;
import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.RequestLine.Method.NONE;

public class RequestLineTests {

    private Context context;
    private RequestLine emptyRequestLine;

    @Before
    public void init() {
        context = buildTestingContext("RequestLine tests");
        this.emptyRequestLine = new RequestLine(
                NONE,
                PathDetails.empty,
                HttpVersion.NONE,
                "",
                context.getLogger());
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

    /**
     * It was noticed that if the incoming request was an empty string
     * path, then a query string would be misinterpreted as the path. This
     * test examines that and ensure correct behavior, which is for that
     * case to be a path of empty string and the query string to be just that.
     */
    @Test
    public void test_QueryStringOnOtherwiseEmpty() {
        RequestLine requestLine = emptyRequestLine.extractRequestLine("GET /?foo=bar HTTP/1.1");

        assertEquals(requestLine.getPathDetails().toString(), "PathDetails{isolatedPath='', rawQueryString='foo=bar', queryString={foo=bar}}");
    }

    /**
     * A test to examine how long it takes to analyze an HTTP
     * method in a request, comparing between the older naive
     * approach and the current approach, which is more cumbersome
     * but handles the edge cases better.
     */
    @Test
    public void test_Perf_GetMethod() throws IOException {

        /*
        In the new approach we are taking, it is actually *very* slightly slower in
        the happy path case.
         */
        {
            // new approach
            StopwatchUtils stopwatch1 = new StopwatchUtils().startTimer();
            for (int i = 0; i < 1000; i++) {
                RequestLine.Method result = RequestLine.Method.getMethod("GET");
                assertEquals(result, GET);
            }
            long time1 = stopwatch1.stopTimer();
            System.out.println("happy path new approach timing is " + time1 + " milliseconds");

            // old approach
            StopwatchUtils stopwatch2 = new StopwatchUtils().startTimer();
            for (int i = 0; i < 1000; i++) {
                RequestLine.Method result2 = RequestLine.Method.valueOf("GET".toUpperCase(Locale.ROOT));
                assertEquals(result2, GET);
            }
            long time2 = stopwatch2.stopTimer();
            System.out.println("happy path old approach timing is " + time2 + " milliseconds");
        }

        /*
        However, the new approach handles exception cases much faster.  So, if we
        were to receive requests intentionally built to slow us down, like with badly
        misformed requests, our new approach will breeze through.
         */
        {
            List<String> reallyEccentricData = Files.readAllLines(Path.of("src/test/resources/unusualStrings.txt"));

            // new approach
            StopwatchUtils stopwatch1 = new StopwatchUtils().startTimer();
            for (int i = 0; i < 100; i++) {
                for (String unusualString : reallyEccentricData) {
                    RequestLine.Method result = RequestLine.Method.getMethod(unusualString);
                    assertEquals(result, NONE);
                }
            }
            long time1 = stopwatch1.stopTimer();
            System.out.println("edge case new approach timing is " + time1 + " milliseconds");

            // old approach
            StopwatchUtils stopwatch2 = new StopwatchUtils().startTimer();
            for (int i = 0; i < 100; i++) {
                for (String unusualString : reallyEccentricData) {
                    try {
                        RequestLine.Method result2 = RequestLine.Method.valueOf(unusualString.toUpperCase(Locale.ROOT));
                    } catch (Exception ex) {
                        continue;
                    }
                    throw new RuntimeException("Should not get here");
                }
            }
            long time2 = stopwatch2.stopTimer();
            System.out.println("edge case old approach timing is " + time2 + " milliseconds");
        }
    }

    /**
     * The HTTP method might have eccentric capitalization, anything
     * from get to GET to gET to GeT, etc.  We might also receive some
     * unusual characters or lengths in an attempt to attack the server.
     * This will test some of those cases and show expected outcomes.
     * In short: if the value is out of bounds, the result will be Method.NONE.
     */
    @Test
    public void test_GetMethod_UnusualCapitalization() throws IOException {
        assertEquals(RequestLine.Method.getMethod("get"), RequestLine.Method.GET);
        assertEquals(RequestLine.Method.getMethod("gET"), RequestLine.Method.GET);
        assertEquals(RequestLine.Method.getMethod("GET"), RequestLine.Method.GET);
        assertEquals(RequestLine.Method.getMethod("GEt"), RequestLine.Method.GET);

        assertEquals(RequestLine.Method.getMethod("DELETE"), RequestLine.Method.DELETE);
        assertEquals(RequestLine.Method.getMethod("delete"), RequestLine.Method.DELETE);
        assertEquals(RequestLine.Method.getMethod("deLEte"), RequestLine.Method.DELETE);
        assertEquals(RequestLine.Method.getMethod("DELEte"), RequestLine.Method.DELETE);

        assertEquals(RequestLine.Method.getMethod("PUT"), RequestLine.Method.PUT);
        assertEquals(RequestLine.Method.getMethod("put"), RequestLine.Method.PUT);

        assertEquals(RequestLine.Method.getMethod("post"), RequestLine.Method.POST);
        assertEquals(RequestLine.Method.getMethod("POST"), RequestLine.Method.POST);

        assertEquals(RequestLine.Method.getMethod("trace"), RequestLine.Method.TRACE);
        assertEquals(RequestLine.Method.getMethod("TRACE"), RequestLine.Method.TRACE);

        assertEquals(RequestLine.Method.getMethod("patch"), RequestLine.Method.PATCH);
        assertEquals(RequestLine.Method.getMethod("PATCH"), RequestLine.Method.PATCH);

        // invalid data of various sorts

        assertEquals(RequestLine.Method.getMethod("FOOFOO"), RequestLine.Method.NONE);

        // the following four tests are for the characters on either side of the alphabet, upper
        // and lower-case, in ascii.

        assertEquals(RequestLine.Method.getMethod("@"), RequestLine.Method.NONE);
        assertEquals(RequestLine.Method.getMethod("["), RequestLine.Method.NONE);
        assertEquals(RequestLine.Method.getMethod("`"), RequestLine.Method.NONE);
        assertEquals(RequestLine.Method.getMethod("{"), RequestLine.Method.NONE);

        // now we will just run some wild stuff through just to ensure we get NONE each time:

        List<String> reallyEccentricData = Files.readAllLines(Path.of("src/test/resources/unusualStrings.txt"));

        for (String unusualString : reallyEccentricData) {
            assertEquals(RequestLine.Method.getMethod(unusualString), RequestLine.Method.NONE);
        }

    }


}
