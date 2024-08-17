package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;

/**
 * This wraps Sockets to make them more particular to our use case
 */
final class SocketWrapper implements ISocketWrapper {

    private final Socket socket;
    private final String hostName;
    private final InputStream inputStream;
    private final OutputStream writer;
    private final ILogger logger;
    private final IServer server;

    /**
     * Constructor
     * @param socket a socket we intend to wrap with methods applicable to our use cases
     * @param logger not much more to say on this param
     * @param timeoutMillis we'll configure the socket to timeout after this many milliseconds.
     */
    SocketWrapper(Socket socket, IServer server, ILogger logger, int timeoutMillis, String hostName) throws IOException {
        this.socket = socket;
        this.hostName = hostName;
        logger.logTrace(() -> String.format("Setting timeout of %d milliseconds on socket %s", timeoutMillis, socket));
        this.socket.setSoTimeout(timeoutMillis);
        this.inputStream = socket.getInputStream();
        writer = socket.getOutputStream();
        this.logger = logger;
        this.server = server;
    }

    @Override
    public void send(String msg) throws IOException {
        writer.write(msg.getBytes(Charset.defaultCharset()));
    }

    @Override
    public void send(byte[] bodyContents) throws IOException {
        writer.write(bodyContents);
    }

    @Override
    public void send(byte[] bodyContents, int off, int len) throws IOException {
        writer.write(bodyContents, off, len);
    }

    @Override
    public void send(int b) throws IOException {
        writer.write(b);
    }

    @Override
    public void sendHttpLine(String msg) throws IOException {
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
    public void close() throws IOException {
        logger.logTrace(() -> "close called on " + this);
        socket.close();
        if (server != null) server.removeMyRecord(this);
    }

    @Override
    public InputStream getInputStream() {
        return this.inputStream;
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
}
