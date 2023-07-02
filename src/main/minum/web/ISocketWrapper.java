package minum.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;

/**
 * This is the public interface to {@link ISocketWrapper}, whose
 * purpose is to make our lives easier when working with {@link java.net.Socket}.
 */
public interface ISocketWrapper extends AutoCloseable {

    void send(String msg) throws IOException;

    void send(byte[] bodyContents) throws IOException;

    void sendHttpLine(String msg) throws IOException;

    /**
     * A live running socket connects a local address and port to a
     * remote address and port. This returns the local address.
     */
    String getLocalAddr();

    int getLocalPort();

    /**
     * Returns a {@link SocketAddress}, which includes
     * the host address and port
     */
    SocketAddress getRemoteAddrWithPort();

    /**
     * Returns a string of the remote host address without port
     */
    String getRemoteAddr();

    void close() throws IOException;

    /**
     * Returns this socket's input stream for more granular access
     */
    InputStream getInputStream();
}
