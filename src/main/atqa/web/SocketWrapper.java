package atqa.web;

import atqa.logging.ILogger;
import atqa.logging.Logger;
import atqa.utils.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;

import static atqa.utils.Invariants.mustBeFalse;

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
    public String readLine() throws IOException {
        final int NEWLINE_DECIMAL = 10;
        final int CARRIAGE_RETURN_DECIMAL = 13;

        final var result = new ArrayList<Byte>();
        while (true) {
            int a = inputStream.read();

            if (a == -1) return null;

            if (a == CARRIAGE_RETURN_DECIMAL) {
                continue;
            }

            if (a == NEWLINE_DECIMAL) break;

            result.add((byte) a);

        }
        return StringUtils.bytesToString(result);
    }

    @Override
    public byte[] read(int length) throws IOException {
        final var buf = new byte[length];
        final var lengthRead = this.inputStream.read(buf);
        mustBeFalse(lengthRead == -1, "end of file hit");
        mustBeFalse(lengthRead != length, String.format("length of bytes read (%d) wasn't equal to what we specified (%d)", lengthRead, length));
        return buf;
    }
}
