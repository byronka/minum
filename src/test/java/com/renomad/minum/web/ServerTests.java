package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ITheBrig;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.net.SocketException;
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
        assertEquals("Forbidden!", logger.findFirstMessageThatContains("Forbidden!"));
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
        assertEquals("is looking for vulnerabilities, for this: no cipher suites in common", lookingForVulnerabilities.trim());
    }

    /**
     * SocketException is another thing that has a variety of messages,
     * but we don't do different things, we just log it.
     */
    @Test
    public void test_Server_SocketException() throws Exception  {
        Server server = new Server(null, context, "", null);
        var builtCore = server.buildExceptionHandlingInnerCore(x -> {
            throw new SocketException("Foo foo is a foo");
        }, new FakeSocketWrapper());
        builtCore.run();
        assertEquals("Foo foo is a foo - remote address: 0.0.0.0/0.0.0.0:12345", logger.findFirstMessageThatContains("Foo foo is a foo"));
    }

    /**
     * buildExceptionHandlingInnerCore only handles a few kinds of exception.
     * Everything else bubbles out of it, but if we don't recognize the
     * exception
     */
    @Test
    public void test_Server_OtherException() throws Exception  {
        Server server = new Server(null, context, "", null);
        var builtCore = server.buildExceptionHandlingInnerCore(x -> {
            throw new IOException();
        }, new FakeSocketWrapper());

        assertThrows(IOException.class, () -> builtCore.run());
    }


    ITheBrig theBrigMock = new ITheBrig() {

        @Override
        public ITheBrig initialize() {
            return null;
        }

        @Override
        public void stop() {

        }

        @Override
        public void sendToJail(String clientIdentifier, long sentenceDuration) {

        }

        @Override
        public boolean isInJail(String clientIdentifier) {
            return false;
        }

        @Override
        public List<Map.Entry<String, Long>> getInmates() {
            return null;
        }
    };
}
