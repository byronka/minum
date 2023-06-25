package minum.web;

import minum.Constants;
import minum.exceptions.ForbiddenUseException;
import minum.logging.ILogger;
import minum.security.TheBrig;
import minum.security.UnderInvestigation;
import minum.utils.ConcurrentSet;
import minum.utils.StacktraceUtils;
import minum.utils.ThrowingConsumer;
import minum.utils.ThrowingRunnable;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
public class Server implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final SetOfSws setOfSWs;
    private final ILogger logger;
    private final String serverName;
    private final TheBrig theBrig;

    /**
     * This is the future returned when we submitted the
     * thread for the central server loop to the ExecutorService
     */
    public Future<?> centralLoopFuture;

    public Server(ServerSocket ss, ILogger logger, String serverName, TheBrig theBrig) {
        this.serverSocket = ss;
        this.logger = logger;
        this.serverName = serverName;
        this.theBrig = theBrig;
        setOfSWs = new SetOfSws(new ConcurrentSet<>(), logger, serverName);
    }

    /**
     * This is the infinite loop running inside the basic socket server code.  Every time we
     * {@link ServerSocket#accept()} a new incoming socket connection, we attach our "end
     * of the phone line" to the code that is "handler", which mainly handles it from there.
     * @param handler the commonest handler will be found at {@link WebFramework#makePrimaryHttpHandler}
     */
    public void start(ExecutorService es, ThrowingConsumer<ISocketWrapper, IOException> handler) {
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
                    SocketWrapper sw = new SocketWrapper(freshSocket, this, logger);
                    logger.logTrace(() -> String.format("client connected from %s", sw.getRemoteAddrWithPort()));
                    setOfSWs.add(sw);
                    if (handler != null) {
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
                                logger.logDebug(() -> ex.getMessage() + " - remote address: " + sw.getRemoteAddrWithPort());
                            } catch (ForbiddenUseException ex) {
                                logger.logDebug(ex::getMessage);
                            } catch (SSLException ex) {
                                logger.logDebug(() -> ex.getMessage() + " (at Server.start)");
                                boolean isLookingForVulnerabilities = UnderInvestigation.isClientLookingForVulnerabilities(ex.getMessage());
                                logger.logDebug(() -> "is " + sw.getRemoteAddr() + " looking for vulnerabilities? " + isLookingForVulnerabilities);
                                if (isLookingForVulnerabilities) {
                                    if (theBrig != null && Constants.IS_THE_BRIG_ENABLED)
                                        theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", Constants.VULN_SEEKING_JAIL_DURATION);
                                }
                            } catch (Exception ex) {
                                // if we hit here, someone has carried out an action down in the gears that we consider hacking-like.
                                // ban hammer.
                                if (theBrig != null && Constants.IS_THE_BRIG_ENABLED)
                                    theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", Constants.VULN_SEEKING_JAIL_DURATION);
                            }
                        };
                        es.submit(ThrowingRunnable.throwingRunnableWrapper(innerServerCode, logger));
                    }
                }
            } catch (SocketException ex) {
                if (!(ex.getMessage().contains("Socket closed") || ex.getMessage().contains("Socket is closed"))) {
                    logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
                }
            }
        };
        Runnable t = ThrowingRunnable.throwingRunnableWrapper(serverCode, logger);
        this.centralLoopFuture = es.submit(t);
    }

    public void close() throws IOException {
        // close all the running sockets
        setOfSWs.stopAllServers();
        logger.logTrace(() -> "close called on " + this + ". Stacktrace:" + StacktraceUtils.stackTraceToString(Thread.currentThread().getStackTrace()));
        // close the primary server socket
        serverSocket.close();
    }

    public String getHost() {
        return serverSocket.getInetAddress().getHostAddress();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * This is a helper method to find the server SocketWrapper
     * connected to a client SocketWrapper.
     */
    public SocketWrapper getServer(SocketWrapper sw) {
        return setOfSWs.getSocketWrapperByRemoteAddr(sw.getLocalAddr(), sw.getLocalPort());
    }

    /**
     * When we first create a SocketWrapper in Server, we provide it
     * a reference back to this object, so that it can call
     * this command.  This class maintains a list of open
     * sockets in setOfSWs, and allows generated SocketWrappers
     * to deregister themselves from this list by using this method.
     */
    public void removeMyRecord(SocketWrapper socketWrapper) {
        setOfSWs.remove(socketWrapper);
    }

    @Override
    public String toString() {
        return this.serverName;
    }
}
