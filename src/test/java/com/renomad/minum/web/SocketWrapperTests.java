package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static com.renomad.minum.testing.TestFramework.*;

public class SocketWrapperTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("SocketWrapperTests");
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

    @Test
    public void testSendingSingleByte() throws IOException {
        ServerSocket serverSocket = new ServerSocket(6000);
        Thread.ofVirtual().start(() -> {
            try {
                Socket mySocket = serverSocket.accept();
                InputStream inputStream = mySocket.getInputStream();
                int value = inputStream.read();
                assertEquals(value, 123);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        var socket = new Socket("localhost", 6000);
        SocketWrapper testSocketWrapper = new SocketWrapper(socket, null, logger, 0, "test host");
        testSocketWrapper.send(123);
        testSocketWrapper.flush();
    }

    /**
     * It is possible for an exception to be thrown while
     * constructing the SocketWrapper if certain invalid
     * values are provided.
     */
    @Test
    public void test_ConstructorException() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new SocketWrapper(new Socket(), null, logger, -1, ""));
        assertEquals(ex.getMessage(), "timeout can't be negative");
    }

    private static class FakeBufferedOutputStream extends BufferedOutputStream {

        public FakeBufferedOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            throw new IOException("This is a test IOException");
        }

        @Override
        public void write(byte[] b) throws IOException {
            throw new IOException("This is a test IOException");
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException("This is a test IOException");
        }
    }


    private static class FakeBufferedOutputStream2 extends BufferedOutputStream {

        public FakeBufferedOutputStream2(OutputStream out) {
            super(out);
        }
    }


    /**
     * Creates a {@link BufferedOutputStream} that will throw IOExceptions
     * on several methods.
     */
    private FakeBufferedOutputStream createExceptionThrowingBufferedOutputStream() {
        return new FakeBufferedOutputStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("This is a test IOException");
            }
        });
    }

    /**
     * Creates a {@link BufferedOutputStream} that will throw IOExceptions
     * on flush
     */
    private FakeBufferedOutputStream2 createExceptionThrowingBufferedOutputStreamOnFlush() {
        return new FakeBufferedOutputStream2(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("This is a test IOException");
            }
        });
    }
}
