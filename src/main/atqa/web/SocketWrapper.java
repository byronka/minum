package atqa.web;

import atqa.logging.ILogger;
import atqa.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;

import static atqa.utils.Invariants.mustBeFalse;

/**
 * This wraps Sockets to make them simpler / more particular to our use case
 */
public class SocketWrapper implements ISocketWrapper {

    private final Socket socket;
    private final OutputStream writer;
    private final BufferedReader reader;
    private final ILogger logger;
    private final SetOfServers setOfServers;

    public SocketWrapper(Socket socket, ILogger logger) throws IOException {
        this(socket, null, logger);
    }

    public SocketWrapper(Socket socket, SetOfServers sos, ILogger logger) throws IOException {
        this.socket = socket;
        writer = socket.getOutputStream();
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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
    public String readLine() throws IOException {
        return reader.readLine();
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
    public String readByLength(int length) throws IOException {
        char[] cb = new char[length];
        int countOfBytesRead = reader.read(cb, 0, length);
        mustBeFalse(countOfBytesRead == -1, "end of file hit");
        mustBeFalse(countOfBytesRead != length, String.format("length of bytes read (%d) wasn't equal to what we specified (%d)", countOfBytesRead, length));
        return new String(cb);
    }

    @Override
    public String read(int length) throws IOException {
        final var buf = new char[length];
        final var lengthRead = reader.read(buf, 0, length);
        char[] newArray = Arrays.copyOfRange(buf, 0, lengthRead);
        return new String(newArray);
    }
}
