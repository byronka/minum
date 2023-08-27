package minum.web;

import minum.Constants;
import minum.Context;
import minum.exceptions.ForbiddenUseException;
import minum.logging.ILogger;
import minum.security.ITheBrig;
import minum.security.UnderInvestigation;
import minum.utils.ConcurrentSet;
import minum.utils.StacktraceUtils;
import minum.utils.ThrowingRunnable;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.*;
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
final class Server implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final SetOfSws setOfSWs;
    private final ILogger logger;
    private final String serverName;
    private final ITheBrig theBrig;
    private final Constants constants;
    private final UnderInvestigation underInvestigation;

    /**
     * This is the future returned when we submitted the
     * thread for the central server loop to the ExecutorService
     */
    private Future<?> centralLoopFuture;

    Server(ServerSocket ss, Context context, String serverName, ITheBrig theBrig) {
        this.serverSocket = ss;
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.serverName = serverName;
        this.theBrig = theBrig;
        setOfSWs = new SetOfSws(new ConcurrentSet<>(), logger, serverName);
        this.underInvestigation = new UnderInvestigation(constants);
    }

    /**
     * This is the infinite loop running inside the basic socket server code.  Every time we
     * {@link ServerSocket#accept()} a new incoming socket connection, we attach our "end
     * of the phone line" to the code that is "handler", which mainly handles it from there.
     * @param handler the commonest handler will be found at {@link WebFramework#makePrimaryHttpHandler}
     */
    void start(ExecutorService es, ThrowingConsumer<ISocketWrapper, IOException> handler) {
        ThrowingRunnable<Exception> serverCode = buildMainServerLoop(es, handler);
        Runnable t = ThrowingRunnable.throwingRunnableWrapper(serverCode, logger);
        this.centralLoopFuture = es.submit(t);
    }

    /**
     * This code is the innermost loop of the server, waiting for incoming
     * connections and then delegating their handling off to a handler.
     * @param es The ExecutorService helping us with the threads
     * @param handler the handler that will take charge immediately after
     *                a client makes a connection.
     */
    private ThrowingRunnable<Exception> buildMainServerLoop(ExecutorService es, ThrowingConsumer<ISocketWrapper, IOException> handler) {
        ThrowingRunnable<Exception> serverCode = () -> {
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
                    ISocketWrapper sw = new SocketWrapper(freshSocket, this, logger, constants.SOCKET_TIMEOUT_MILLIS);
                    logger.logTrace(() -> String.format("client connected from %s", sw.getRemoteAddrWithPort()));
                    setOfSWs.add(sw);
                    if (handler != null) {
                        ThrowingRunnable<Exception> innerServerCode = buildExceptionHandlingInnerCore(handler, sw);
                        es.submit(ThrowingRunnable.throwingRunnableWrapper(innerServerCode, logger));
                    }
                }
            } catch (SocketException ex) {
                if (!(ex.getMessage().contains("Socket closed") || ex.getMessage().contains("Socket is closed"))) {
                    logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
                }
            }
        };
        return serverCode;
    }

    /**
     * By volume of code, this method is primarily focused on handling the kinds
     * of exceptional situations that can arise from handling the HTTP communication
     * with a client.
     * <p>
     *     Note that this method *builds* a function but does not run it.
     * </p>
     *
     */
    ThrowingRunnable<Exception> buildExceptionHandlingInnerCore(ThrowingConsumer<ISocketWrapper, IOException> handler, ISocketWrapper sw) {
        ThrowingRunnable<Exception> innerServerCode = () -> {
            Thread.currentThread().setName("SocketWrapper thread for " + sw.getRemoteAddr());
            try {
                handler.accept(sw);
            } catch (SocketException | SocketTimeoutException ex) {
                 /*
                 if we close the application on the server side, there's a good
                 likelihood a SocketException will come bubbling through here.
                 NOTE:
                   it seems that Socket closed is what we get when the client closes the connection in non-SSL, and conversely,
                   if we are operating in secure (i.e. SSL/TLS) mode, we get "an established connection..."
                 */
                if (ex.getMessage().equals("Read timed out")) {
                    logger.logTrace(() -> "Read timed out - remote address: " + sw.getRemoteAddrWithPort());
                } else {
                    logger.logDebug(() -> ex.getMessage() + " - remote address: " + sw.getRemoteAddrWithPort());
                }

            } catch (ForbiddenUseException ex) {
                logger.logDebug(ex::getMessage);
            } catch (SSLException ex) {
                logger.logDebug(() -> ex.getMessage() + " (at Server.start)");
                String suspiciousClues = underInvestigation.isClientLookingForVulnerabilities(ex.getMessage());

                if (suspiciousClues.length() > 0) {
                    if (theBrig != null) {
                        logger.logDebug(() -> sw.getRemoteAddr() + " is looking for vulnerabilities, for this: " + suspiciousClues);
                        theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", constants.VULN_SEEKING_JAIL_DURATION);
                    }
                }
            }
        };
        return innerServerCode;
    }

    public void close() throws IOException {
        // close all the running sockets
        setOfSWs.stopAllServers();
        logger.logTrace(() -> "close called on " + this);
        // close the primary server socket
        serverSocket.close();
    }

    /**
     * Get the string version of the address of this
     * server.  See {@link InetAddress#getHostAddress()}
     */
    String getHost() {
        return serverSocket.getInetAddress().getHostAddress();
    }

    /**
     * See {@link ServerSocket#getLocalPort()}
     */
    int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * This is a helper method to find the server SocketWrapper
     * connected to a client SocketWrapper.
     */
    ISocketWrapper getServer(ISocketWrapper sw) {
        return setOfSWs.getSocketWrapperByRemoteAddr(sw.getLocalAddr(), sw.getLocalPort());
    }

    /**
     * When we first create a SocketWrapper in Server, we provide it
     * a reference back to this object, so that it can call
     * this command.  This class maintains a list of open
     * sockets in setOfSWs, and allows generated SocketWrappers
     * to deregister themselves from this list by using this method.
     */
    void removeMyRecord(ISocketWrapper socketWrapper) {
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

    /**
     * Obtain the {@link Future} of the central loop of this
     * server object, which is obtained during {@link #start(ExecutorService, ThrowingConsumer)}
     */
    Future<?> getCentralLoopFuture() {
        return centralLoopFuture;
    }
}
