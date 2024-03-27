package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.UnderInvestigation;
import com.renomad.minum.utils.ConcurrentSet;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.ThrowingRunnable;

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
final class Server implements IServer {
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

    @Override
    public void start(ExecutorService es, ThrowingConsumer<ISocketWrapper> handler) {
        ThrowingRunnable serverCode = buildMainServerLoop(es, handler);
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
    private ThrowingRunnable buildMainServerLoop(ExecutorService es, ThrowingConsumer<ISocketWrapper> handler) {
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
                    ISocketWrapper sw = new SocketWrapper(freshSocket, this, logger, constants.socketTimeoutMillis);
                    logger.logTrace(() -> String.format("client connected from %s", sw.getRemoteAddrWithPort()));
                    setOfSWs.add(sw);
                    if (handler != null) {
                        ThrowingRunnable innerServerCode = buildExceptionHandlingInnerCore(handler, sw);
                        es.submit(ThrowingRunnable.throwingRunnableWrapper(innerServerCode, logger));
                    }
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

    /**
     * By volume of code, this method is primarily focused on handling the kinds
     * of exceptional situations that can arise from handling the HTTP communication
     * with a client.
     * <p>
     *     Note that this method *builds* a function but does not run it.
     * </p>
     *
     */
    ThrowingRunnable buildExceptionHandlingInnerCore(ThrowingConsumer<ISocketWrapper> handler, ISocketWrapper sw) {
        return () -> {
            Thread.currentThread().setName("SocketWrapper thread for " + sw.getRemoteAddr());
            // wrap socketwrapper in try-with-resources so leaving this block
            // closes the socket.
            try (sw) {
                handler.accept(sw);
            } catch (SocketException | SocketTimeoutException ex) {
                handleReadTimedOut(sw, ex, logger);
            } catch (ForbiddenUseException ex) {
                handleForbiddenUse(sw, ex, logger, theBrig, constants.vulnSeekingJailDuration);
            } catch (IOException ex) {
                handleIOException(sw, ex, logger, theBrig, underInvestigation, constants.vulnSeekingJailDuration);
            }
        };
    }

    static void handleIOException(ISocketWrapper sw, IOException ex, ILogger logger, ITheBrig theBrig, UnderInvestigation underInvestigation, int vulnSeekingJailDuration ) {
        logger.logDebug(() -> ex.getMessage() + " (at Server.start)");
        String suspiciousClues = underInvestigation.isClientLookingForVulnerabilities(ex.getMessage());

        if (!suspiciousClues.isEmpty() && theBrig != null) {
            logger.logDebug(() -> sw.getRemoteAddr() + " is looking for vulnerabilities, for this: " + suspiciousClues);
            theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", vulnSeekingJailDuration);
        }
    }

    static void handleForbiddenUse(ISocketWrapper sw, ForbiddenUseException ex, ILogger logger, ITheBrig theBrig, int vulnSeekingJailDuration) {
        logger.logDebug(() -> sw.getRemoteAddr() + " is looking for vulnerabilities, for this: " + ex.getMessage());
        if (theBrig != null) {
            theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", vulnSeekingJailDuration);
        }
    }

    static void handleReadTimedOut(ISocketWrapper sw, IOException ex, ILogger logger) {
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
    }

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
    public ISocketWrapper getServer(ISocketWrapper sw) {
        return setOfSWs.getSocketWrapperByRemoteAddr(sw.getLocalAddr(), sw.getLocalPort());
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
}
