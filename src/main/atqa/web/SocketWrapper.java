package atqa.web;

import atqa.logging.ILogger;
import atqa.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * This wraps Sockets to make them simpler / more particular to our use case
 */
public class SocketWrapper implements ISocketWrapper {

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream writer;
    private final ILogger logger;
    private final SetOfServers setOfServers;

    public SocketWrapper(Socket socket, ILogger logger) throws IOException {
        this(socket, null, logger);
    }

    public SocketWrapper(Socket socket, SetOfServers sos, ILogger logger) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        writer = socket.getOutputStream();
        this.logger = logger;
        this.setOfServers = sos;
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
        logger.logTrace(() -> String.format("socket sending: %s", Logger.showWhiteSpace(msg)));
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
    public SocketAddress getRemoteAddr() {
        return socket.getRemoteSocketAddress();
    }

    @Override
    public void close() throws IOException {
        logger.logTrace(() -> "close called on " + this);
        socket.close();
        if (setOfServers != null) {
            setOfServers.remove(this);
        }
    }

    @Override
    public InputStream getInputStream() {
        return this.inputStream;
    }

}
