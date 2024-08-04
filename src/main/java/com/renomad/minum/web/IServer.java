package com.renomad.minum.web;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.Future;

/**
 * An interface for the {@link Server} implementation.
 * Solely created to provide better testing access
 */
public interface IServer extends Closeable {

    /**
     * This is where the central loop of our server is started
     */
    void start();

    /**
     * Get the string version of the address of this
     * server.  See {@link InetAddress#getHostAddress()}
     */
    String getHost();

    /**
     * See {@link ServerSocket#getLocalPort()}
     */
    int getPort();

    /**
     * When we first create a SocketWrapper in Server, we provide it
     * a reference back to this object, so that it can call
     * this command.  This class maintains a list of open
     * sockets in setOfSWs, and allows generated SocketWrappers
     * to deregister themselves from this list by using this method.
     */
    void removeMyRecord(ISocketWrapper socketWrapper);

    /**
     * Obtain the {@link Future} of the central loop of this
     * server object
     */
    Future<?> getCentralLoopFuture();

    HttpServerType getServerType();
}
