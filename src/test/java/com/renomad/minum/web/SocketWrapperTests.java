package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static com.renomad.minum.testing.TestFramework.*;

public class SocketWrapperTests {

    private TestLogger logger;

    @Before
    public void init() {
        Context context = buildTestingContext("testing socket wrapper");
        this.logger = (TestLogger)context.getLogger();
    }

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
        var ex = assertThrows(WebServerException.class, () -> new SocketWrapper(new Socket(), null, logger, -1, ""));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertEquals(ex.getCause().getMessage(), "timeout can't be negative");
    }

    /**
     * Just asserting what happens when an exception occurs
     */
    @Test
    public void test_SendString_Exception() {
        BufferedOutputStream fakeBufferedOutputStream = createExceptionThrowingBufferedOutputStream();
        SocketWrapper socketWrapper = new SocketWrapper(
                new Socket(), null, logger, 0, "", true, fakeBufferedOutputStream, null);

        var ex = assertThrows(WebServerException.class, () -> socketWrapper.send("DOES_NOT_MATTER"));

        assertTrue(ex.getCause() instanceof IOException);
        assertEquals(ex.getCause().getMessage(), "This is a test IOException");
    }


    /**
     * Just asserting what happens when an exception occurs
     */
    @Test
    public void test_SendMultiBytes_Exception() {
        BufferedOutputStream fakeBufferedOutputStream = createExceptionThrowingBufferedOutputStream();
        SocketWrapper socketWrapper = new SocketWrapper(
                new Socket(), null, logger, 0, "", true, fakeBufferedOutputStream, null);

        var ex = assertThrows(WebServerException.class, () -> socketWrapper.send("DOES_NOT_MATTER".getBytes(StandardCharsets.UTF_8)));

        assertTrue(ex.getCause() instanceof IOException);
        assertEquals(ex.getCause().getMessage(), "This is a test IOException");
    }

    /**
     * Just asserting what happens when an exception occurs
     */
    @Test
    public void test_SendMultiBytesWithOffsetAndLength_Exception() {
        BufferedOutputStream fakeBufferedOutputStream = createExceptionThrowingBufferedOutputStream();
        SocketWrapper socketWrapper = new SocketWrapper(
                new Socket(), null, logger, 0, "", true, fakeBufferedOutputStream, null);

        var ex = assertThrows(WebServerException.class, () -> socketWrapper.send("DOES_NOT_MATTER".getBytes(StandardCharsets.UTF_8), 0, 5));

        assertTrue(ex.getCause() instanceof IOException);
        assertEquals(ex.getCause().getMessage(), "This is a test IOException");
    }

    /**
     * Just asserting what happens when an exception occurs
     */
    @Test
    public void test_SendInteger_Exception() {
        BufferedOutputStream fakeBufferedOutputStream = createExceptionThrowingBufferedOutputStream();
        SocketWrapper socketWrapper = new SocketWrapper(
                new Socket(), null, logger, 0, "", true, fakeBufferedOutputStream, null);

        var ex = assertThrows(WebServerException.class, () -> socketWrapper.send(1));

        assertTrue(ex.getCause() instanceof IOException);
        assertEquals(ex.getCause().getMessage(), "This is a test IOException");
    }

    /**
     * Just asserting what happens when an exception occurs
     */
    @Test
    public void test_SendInteger_ExceptionOnFlush() {
        BufferedOutputStream fakeBufferedOutputStream = createExceptionThrowingBufferedOutputStreamOnFlush();
        SocketWrapper socketWrapper = new SocketWrapper(
                new Socket(), null, logger, 0, "", true, fakeBufferedOutputStream, null);

        socketWrapper.send(1);
        var ex = assertThrows(WebServerException.class, () -> socketWrapper.flush());

        assertTrue(ex.getCause() instanceof IOException);
        assertEquals(ex.getCause().getMessage(), "This is a test IOException");
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
