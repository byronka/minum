package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

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
    public void test_EdgeCase_ExceptionIn_doHttpWork() throws IOException {
        try (Server server = new Server(new ServerSocket(8080), context, "fake_server_for_testing", null, null, HttpServerType.UNKNOWN)) {
            server.doHttpWork(new Socket("localhost", 8080));
            assertTrue(logger.doesMessageExist("NullPointerException"));
        }
    }

}
