package minum.web;

import minum.logging.ILogger;
import minum.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * This wraps Sockets to make them more particular to our use case
 */
public class SocketWrapper implements ISocketWrapper, AutoCloseable {

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream writer;
    private final ILogger logger;
    private final Server server;

    /**
     * Constructor
     * @param socket a socket we intend to wrap with methods applicable to our use cases
     * @param logger not much more to say on this param
     * @param timeoutMillis we'll configure the socket to timeout after this many milliseconds.
     */
    public SocketWrapper(Socket socket, ILogger logger, int timeoutMillis) throws IOException {
        this(socket, null, logger, timeoutMillis);
    }

    public SocketWrapper(Socket socket, Server server, ILogger logger, int timeoutMillis) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(timeoutMillis);
        this.inputStream = socket.getInputStream();
        writer = socket.getOutputStream();
        this.logger = logger;
        this.server = server;
    }

    @Override
    public void send(String msg) throws IOException {
        writer.write(msg.getBytes());
    }

    @Override
    public void send(byte[] bodyContents) throws IOException {
        writer.write(bodyContents);
    }

    @Override
    public void sendHttpLine(String msg) throws IOException {
        logger.logTrace(() -> String.format("%s sending: \"%s\"", this, Logger.showWhiteSpace(msg)));
        send(msg + WebEngine.HTTP_CRLF);
    }

    @Override
    public String getLocalAddr() {
        return socket.getLocalAddress().getHostAddress();
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
}
