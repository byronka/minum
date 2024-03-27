package com.renomad.minum.web;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public interface IServer extends Closeable {
    /**
     * This is the infinite loop running inside the basic socket server code.  Every time we
     * {@link ServerSocket#accept()} a new incoming socket connection, we attach our "end
     * of the phone line" to the code that is "handler", which mainly handles it from there.
     *
     * @param handler the commonest handler will be found at {@link WebFramework#makePrimaryHttpHandler}
     */
    void start(ExecutorService es, ThrowingConsumer<ISocketWrapper> handler);

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
     * This is a helper method to find the server SocketWrapper
     * connected to a client SocketWrapper.
     */
    ISocketWrapper getServer(ISocketWrapper sw);

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
     * server object, which is obtained during {@link #start(ExecutorService, ThrowingConsumer)}
     */
    Future<?> getCentralLoopFuture();
}
