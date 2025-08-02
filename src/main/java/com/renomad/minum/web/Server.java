package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.ConcurrentSet;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.ThrowingRunnable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static com.renomad.minum.utils.ThrowingRunnable.throwingRunnableWrapper;

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
    private final WebFramework webFramework;
    private final BlockingQueue<Socket> socketQueue;
    private final Constants constants;

    /**
     * This is the future returned when we submitted the
     * thread for the central server loop to the ExecutorService
     */
    private Future<?> centralLoopFuture;

    Server(ServerSocket ss, Context context, String serverName, WebFramework webFramework, ExecutorService es, HttpServerType serverType) {
        this.serverSocket = ss;
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.webFramework = webFramework;
        this.serverName = serverName;
        setOfSWs = new SetOfSws(new ConcurrentSet<>(), logger, serverName);
        this.es = es;
        this.serverType = serverType;
        this.socketQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void start() {
        ThrowingRunnable serverCode = this::outermostLoop;
        this.centralLoopFuture = es.submit(throwingRunnableWrapper(serverCode, logger));

        ThrowingRunnable socketHandler = this::takeOffDequeForProcessing;
        es.submit(throwingRunnableWrapper(socketHandler, logger));
    }

    /**
     * This code is the outermost loop of the server, waiting for incoming
     * connections and then delegating their handling off to a handler.
     */
    private void outermostLoop() {
        Thread.currentThread().setName("Main Server");
        try {
            // yes, this infinite loop can only exit by an exception.  But this is
            // the beating heart of a server, and to the best of my current knowledge,
            // when a server socket is force-closed it's going to throw an exception, and
            // that's just part of its life cycle
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket freshSocket = serverSocket.accept();
                // see takeOffDeque for the code that pulls sockets out of this queue
                // and sends them for processing
                socketQueue.add(freshSocket);
            }
        } catch (IOException ex) {
            handleServerException(ex, logger);
        }
    }

    /**
     * An infinite loop that pulls connected sockets out of the
     * deque for processing
     */
    private void takeOffDequeForProcessing() throws InterruptedException {
        Thread.currentThread().setName("socket queue handler");

        // this is a known infinite loop, meant to keep running all during the runtime
        //noinspection InfiniteLoopStatement
        while(true) {
            Socket socket = socketQueue.take();
            es.submit(() -> this.doHttpWork(socket));
        }
    }


    void doHttpWork(Socket freshSocket) {
        // provide a name for this thread for easier debugging
        Thread.currentThread().setName("SocketWrapper thread for " + freshSocket.getInetAddress().getHostAddress());

        try {
            // prepare the socket for later processing
            ISocketWrapper socketWrapper = new SocketWrapper(freshSocket, this, logger, constants.socketTimeoutMillis, constants.hostName);
            logger.logTrace(() -> String.format("client connected from %s", socketWrapper.getRemoteAddrWithPort()));

            // add to a set of wrapped sockets so we can precisely close them all at shutdown
            addToSetOfSws(socketWrapper);

            webFramework.httpProcessing(socketWrapper);
        } catch (Exception ex) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
        }
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

    @Override
    public void addToSetOfSws(ISocketWrapper sw) {
        this.setOfSWs.add(sw);
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
