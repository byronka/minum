package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;

/**
 * This wraps Sockets to make them more particular to our use case
 */
public final class SocketWrapper implements ISocketWrapper {

    private final Socket socket;
    private final String hostName;
    private final BufferedOutputStream bufferedOutputStream;
    private final ILogger logger;
    private final IServer server;
    private final BufferedInputStream bufferedInputStream;

    /**
     * Constructor
     * @param socket a socket we intend to wrap with methods applicable to our use cases
     * @param logger not much more to say on this param
     * @param timeoutMillis we'll configure the socket to timeout after this many milliseconds.
     */
    public SocketWrapper(Socket socket, IServer server, ILogger logger, int timeoutMillis, String hostName) {
        this(socket, server, logger, timeoutMillis, hostName, false, null, null);
    }

    /**
     * This constructor has extra parameters used for testing
     * @param ignoreSocket if true, the socket won't be inspected at all.  This is useful for test
     *                     scenarios, if we want to set the input/output for testing this class.
     */
    SocketWrapper(Socket socket, IServer server, ILogger logger,
                  int timeoutMillis, String hostName, boolean ignoreSocket,
                  BufferedOutputStream testOutputStream, BufferedInputStream testInputStream) {
        this.socket = socket;
        this.hostName = hostName;
        logger.logTrace(() -> String.format("Setting timeout of %d milliseconds on socket %s", timeoutMillis, socket));

        // if we ignore the socket, then this is a test SocketWrapper
        if (!ignoreSocket) {
            try {
                this.socket.setSoTimeout(timeoutMillis);
                this.bufferedInputStream = new BufferedInputStream(socket.getInputStream());
                this.bufferedOutputStream = new BufferedOutputStream(socket.getOutputStream());
            } catch (Exception ex) {
                throw new WebServerException("Error in SocketWrapper constructor", ex);
            }
        } else {
            this.bufferedInputStream = testInputStream;
            this.bufferedOutputStream = testOutputStream;
        }

        this.logger = logger;
        this.server = server;
    }

    @Override
    public void send(String msg) {
        try {
            bufferedOutputStream.write(msg.getBytes(Charset.defaultCharset()));
        } catch (IOException e) {
            throw new WebServerException(e);
        }
    }

    @Override
    public void send(byte[] bodyContents) {
        try {
            bufferedOutputStream.write(bodyContents);
        } catch (IOException e) {
            throw new WebServerException(e);
        }
    }

    @Override
    public void send(byte[] bodyContents, int off, int len) {
        try {
            bufferedOutputStream.write(bodyContents, off, len);
        } catch (IOException e) {
            throw new WebServerException(e);
        }
    }

    @Override
    public void send(int b) {
        try {
            bufferedOutputStream.write(b);
        } catch (IOException e) {
            throw new WebServerException(e);
        }
    }

    @Override
    public void sendHttpLine(String msg) {
        logger.logTrace(() -> String.format("%s sending: \"%s\"", this, msg));
        send(msg + WebEngine.HTTP_CRLF);
    }

    @Override
    public int getLocalPort() {
        return socket.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteAddrWithPort() {
        return socket.getRemoteSocketAddress();
    }

    @Override
    public String getRemoteAddr() {
        return socket.getInetAddress().getHostAddress();
    }

    @Override
    public HttpServerType getServerType() {
        return server.getServerType();
    }

    @Override
    public void close() {
        logger.logTrace(() -> "close called on " + this);
        try {
            socket.close();
        } catch (IOException e) {
            throw new WebServerException(e);
        }
        if (server != null) server.removeMyRecord(this);
    }

    @Override
    public InputStream getInputStream() {
        return bufferedInputStream;
    }

    /**
     * Note that since we are indicating just the remote address
     * as the unique value, in cases like tests where we are operating as
     * sometimes server or client, you might see the server as the remote.
     */
    @Override
    public String toString() {
        return "(SocketWrapper for remote address: " + this.getRemoteAddrWithPort().toString() + ")";
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public void flush() {
        try {
            this.bufferedOutputStream.flush();
        } catch (IOException e) {
            throw new WebServerException(e);
        }
    }
}
