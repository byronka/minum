package com.renomad.minum.web;

import com.renomad.minum.state.Constants;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.ConcurrentSet;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.ThrowingRunnable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * The purpose here is to make it marginally easier to
 * work with a ServerSocket.
 * <p>
 * First, instantiate this class using a running serverSocket
 * Then, by running the start method, we gain access to
 * the server's socket.  This way we can easily test / control
 * the server side but also tie it in with an ExecutorService
 * for controlling lots of server threads.
 */
final class Server implements IServer {
    private final ServerSocket serverSocket;
    private final SetOfSws setOfSWs;
    private final ExecutorService es;
    private final HttpServerType serverType;
    private final ILogger logger;
    private final String serverName;
    private final ITheBrig theBrig;
    private final Constants constants;
    private final WebFramework webFramework;

    /**
     * This is the future returned when we submitted the
     * thread for the central server loop to the ExecutorService
     */
    private Future<?> centralLoopFuture;

    Server(ServerSocket ss, Context context, String serverName, ITheBrig theBrig, WebFramework webFramework, ExecutorService es, HttpServerType serverType) {
        this.serverSocket = ss;
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.webFramework = webFramework;
        this.serverName = serverName;
        this.theBrig = theBrig;
        setOfSWs = new SetOfSws(new ConcurrentSet<>(), logger, serverName);
        this.es = es;
        this.serverType = serverType;
    }

    @Override
    public void start() {
        ThrowingRunnable serverCode = buildMainServerLoop(es);
        Runnable t = ThrowingRunnable.throwingRunnableWrapper(serverCode, logger);
        this.centralLoopFuture = es.submit(t);
    }

    /**
     * This code is the innermost loop of the server, waiting for incoming
     * connections and then delegating their handling off to a handler.
     *
     * @param es         The ExecutorService helping us with the threads
     */
    private ThrowingRunnable buildMainServerLoop(ExecutorService es) {
        return () -> {
            Thread.currentThread().setName("Main Server");
            try {
                // yes, this infinite loop can only exit by an exception.  But this is
                // the beating heart of a server, and to the best of my current knowledge,
                // when a server socket is force-closed it's going to throw an exception, and
                // that's just part of its life cycle
                //noinspection InfiniteLoopStatement
                while (true) {
                    logger.logTrace(() -> serverName + " waiting to accept connection");
                    Socket freshSocket = serverSocket.accept();
                    ISocketWrapper sw = new SocketWrapper(freshSocket, this, logger, constants.socketTimeoutMillis, constants.hostName);
                    logger.logTrace(() -> String.format("client connected from %s", sw.getRemoteAddrWithPort()));
                    setOfSWs.add(sw);
                    ThrowingRunnable innerServerCode = this.webFramework.makePrimaryHttpHandler(sw, theBrig);
                    Runnable task = ThrowingRunnable.throwingRunnableWrapper(innerServerCode, logger);
                    es.submit(task);
                }
            } catch (IOException ex) {
                handleServerException(ex, logger);
            }
        };
    }

    static void handleServerException(IOException ex, ILogger logger) {
        if (!(ex.getMessage().contains("Socket closed") || ex.getMessage().contains("Socket is closed"))) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
        }
    }

    @Override
    public void close() throws IOException {
        // close all the running sockets
        setOfSWs.stopAllServers();
        logger.logTrace(() -> "close called on " + this);
        // close the primary server socket
        serverSocket.close();
    }

    @Override
    public String getHost() {
        return serverSocket.getInetAddress().getHostAddress();
    }

    @Override
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void removeMyRecord(ISocketWrapper socketWrapper) {
        setOfSWs.remove(socketWrapper);
    }

    /**
     * Returns the name of this server, which is set
     * when the server is instantiated.
     */
    @Override
    public String toString() {
        return this.serverName;
    }

    @Override
    public Future<?> getCentralLoopFuture() {
        return centralLoopFuture;
    }

    @Override
    public HttpServerType getServerType() {
        return serverType;
    }
}
