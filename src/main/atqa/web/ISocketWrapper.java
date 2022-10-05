package atqa.web;

import java.io.IOException;
import java.net.SocketAddress;

public interface ISocketWrapper extends AutoCloseable {
    void send(String msg) throws IOException;

    void send(byte[] bodyContents) throws IOException;

    void sendHttpLine(String msg) throws IOException;

    String readLine() throws IOException;

    String getLocalAddr();

    int getLocalPort();

    SocketAddress getRemoteAddr();

    void close() throws IOException;

    String readByLength(int length) throws IOException;

    String read(int length) throws IOException;
}
