package com.renomad.minum.web;

import com.renomad.minum.state.Constants;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;

/**
 * This is the public interface to {@link ISocketWrapper}, whose
 * purpose is to make our lives easier when working with {@link java.net.Socket}.
 * Created to provide better testing access
 */
public interface ISocketWrapper extends Closeable {

    /**
     * Convert the provided string value into bytes
     * using the default charset, and send on the socket.
     */
    void send(String msg) throws IOException;

    /**
     * Simply send the bytes on the socket, simple as that.
     */
    void send(byte[] bodyContents) throws IOException;

    void send(byte[] bodyContents, int off, int len) throws IOException;

    void send(int b) throws IOException;

    /**
     * Sends a line of text, with carriage-return and line-feed
     * appended to the end, required for the HTTP protocol.
     */
    void sendHttpLine(String msg) throws IOException;

    /**
     * Get the port of the server
     */
    int getLocalPort();

    /**
     * Returns a {@link SocketAddress}, which includes
     * the client's address and port
     */
    SocketAddress getRemoteAddrWithPort();

    /**
     * Returns a string of the remote host address without port
     */
    String getRemoteAddr();

    HttpServerType getServerType();

    @Override
    void close() throws IOException;

    /**
     * Returns this socket's input stream for more granular access
     */
    InputStream getInputStream();

    /**
     * The hostname of the server, as set in the configuration
     * file of key HOST_NAME in {@link Constants#hostName}
     */
    String getHostName();
}
