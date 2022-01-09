package primary;

import java.net.SocketAddress;

public interface ISocketWrapper extends AutoCloseable {
    void send(String msg);

    void sendHttpLine(String msg);

    String readLine();

    String getLocalAddr();

    int getLocalPort();

    SocketAddress getRemoteAddr();

    void close();
}
