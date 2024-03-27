package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.Inmate;
import com.renomad.minum.security.UnderInvestigation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class ServerTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * ForbiddenUseException gets thrown when the client has done
     * something we prohibit, usually on the basis that it's a
     * potential attack.  For example, trying to send us an
     * absolutely huge number of characters, trying to overflow
     * our stack or some such nonsense.
     */
    @Test
    public void test_Server_ForbiddenUse() throws Exception {
        Server server = new Server(null, context, "", null);
        var builtCore = server.buildExceptionHandlingInnerCore(x -> {
            throw new ForbiddenUseException("Forbidden!");
        }, new FakeSocketWrapper());
        builtCore.run();
        assertEquals("tester is looking for vulnerabilities, for this: Forbidden!", logger.findFirstMessageThatContains("Forbidden!"));
    }

    /**
     * There are many possible exceptions under the heading of SSLException.
     * In our code, we examine the exception message and perform different
     * actions based on it.
     * Here, the phrase "no cipher suites in common" indicates that the
     * client is trying to force the server to use a less-secure encryption,
     * as a form of attack.
     */
    @Test
    public void test_Server_VulnerabilityAttack() throws Exception {
        Server server = new Server(null, context, "", theBrigMock);
        var builtCore = server.buildExceptionHandlingInnerCore(x -> {
            throw new SSLException("no cipher suites in common");
        }, new FakeSocketWrapper());
        builtCore.run();
        String lookingForVulnerabilities = logger.findFirstMessageThatContains("looking for vulnerabilities");
        assertEquals("tester is looking for vulnerabilities, for this: no cipher suites in common", lookingForVulnerabilities.trim());
    }

    /**
     * SocketException is another thing that has a variety of messages,
     * but we don't do different things, we just log it.
     */
    @Test
    public void test_Server_SocketException() throws Exception {
        Server server = new Server(null, context, "", null);
        var builtCore = server.buildExceptionHandlingInnerCore(x -> {
            throw new SocketException("Foo foo is a foo");
        }, new FakeSocketWrapper());
        builtCore.run();
        assertTrue(logger.doesMessageExist("Foo foo is a foo - remote address: /123.123.123.123:1234"));
    }

    /**
     * buildExceptionHandlingInnerCore only handles a few kinds of exception.
     * Everything else bubbles out of it, but if we don't recognize the
     * exception
     */
    @Test
    public void test_Server_OtherException() {
        Server server = new Server(null, context, "", null);
        var builtCore = server.buildExceptionHandlingInnerCore(x -> {
            throw new RuntimeException();
        }, new FakeSocketWrapper());

        assertThrows(RuntimeException.class, () -> builtCore.run());
    }



    ITheBrig theBrigMock = new ITheBrig() {

        final Map<String, Long> jail = new HashMap<>();

        @Override
        public ITheBrig initialize() {
            return null;
        }

        @Override
        public void stop() {

        }

        @Override
        public boolean sendToJail(String clientIdentifier, long sentenceDuration) {
            jail.put(clientIdentifier, sentenceDuration);
            return true;
        }

        @Override
        public boolean isInJail(String clientIdentifier) {
            return jail.containsKey(clientIdentifier);
        }

        @Override
        public List<Inmate> getInmates() {
            return null;
        }
    };


    @Test
    public void testServerExceptionHandling() {
        Server.handleServerException(new IOException("foo closed"), logger);
        assertTrue(logger.doesMessageExist("java.io.IOException: foo closed"));

        Server.handleServerException(new IOException("Socket closed"), logger);
        var ex1 = assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("java.io.IOException: Socket closed"));
        assertTrue(ex1.getMessage().contains("java.io.IOException: Socket closed was not found"));

        Server.handleServerException(new IOException("Socket is closed"), logger);
        var ex2 = assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("java.io.IOException: Socket is closed"));
        assertTrue(ex2.getMessage().contains("java.io.IOException: Socket is closed was not found"));

        Server.handleServerException(new IOException("Socket is closed Socket closed"), logger);
        var ex3 = assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("java.io.IOException: Socket is closed Socket closed"));
        assertTrue(ex3.getMessage().contains("java.io.IOException: Socket is closed Socket closed was not found"));
    }

    @Test
    public void testReadTimedOut() {
        var fakeSocketWrapper = new FakeSocketWrapper();
        Server.handleReadTimedOut(fakeSocketWrapper, new IOException("Read timed out"), logger);
        assertTrue(logger.doesMessageExist("Read timed out - remote address"));
    }

    /**
     * two booleans here:
     * suspicious clues is empty, and whether the brig is null
     * true true
     * true false
     * false true
     * false false
     *
     * or...
     *
     * suspicousClues   brig
     * --------------   ----
     * empty             null
     * notEmpty          null
     * empty             nonNull
     * notEmpty          nonNull
     *
     * Only if the brig is non-null, and underInvestigation finds
     * something suspicious, will it add a value to the brig
     */
    @Test
    public void testHandleIoException() {
        var fakeSocketWrapper1 = new FakeSocketWrapper();
        fakeSocketWrapper1.getRemoteAddrAction = () -> "11.11.11.11";
        var fakeSocketWrapper2 = new FakeSocketWrapper();
        fakeSocketWrapper2.getRemoteAddrAction = () -> "22.22.22.22";
        var fakeSocketWrapper3 = new FakeSocketWrapper();
        fakeSocketWrapper3.getRemoteAddrAction = () -> "33.33.33.33";
        var fakeSocketWrapper4 = new FakeSocketWrapper();
        fakeSocketWrapper4.getRemoteAddrAction = () -> "44.44.44.44";

        var underInvestigation = new UnderInvestigation(context.getConstants());

        // empty, null - nothing added to brig
        Server.handleIOException(fakeSocketWrapper1, new IOException(""), logger, null, underInvestigation, 10);
        assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("is looking for vulnerabilities, for this", 1));
        // notEmpty, null - nothing added to brig
        Server.handleIOException(fakeSocketWrapper2, new IOException("The client supported protocol versions"), logger, null, underInvestigation, 10);
        assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("is looking for vulnerabilities, for this", 1));
        // empty, nonNull - nothing added to brig
        Server.handleIOException(fakeSocketWrapper3, new IOException(""), logger, theBrigMock, underInvestigation, 10);
        assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("is looking for vulnerabilities, for this", 1));
        // notEmpty, nonNull - added, and logged
        Server.handleIOException(fakeSocketWrapper4, new IOException("The client supported protocol versions"), logger, theBrigMock, underInvestigation, 10);
        assertTrue(logger.doesMessageExist("is looking for vulnerabilities, for this"));
        assertTrue(theBrigMock.isInJail("44.44.44.44_vuln_seeking"));
    }

}
