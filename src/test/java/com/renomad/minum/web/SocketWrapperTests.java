package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;

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
    }
}
