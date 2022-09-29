package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.ConcurrentSet;
import atqa.utils.ThrowingConsumer;
import atqa.utils.ThrowingRunnable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
    private final SetOfServers setOfServers;
    private final ILogger logger;

    /**
     * This is the future returned when we submitted the
     * thread for the central server loop to the ExecutorService
     */
    public Future<?> centralLoopFuture;

    public Server(ServerSocket ss, ILogger logger) {
        this.serverSocket = ss;
        this.logger = logger;
        setOfServers = new SetOfServers(new ConcurrentSet<>(), logger);
    }

    public void start(ExecutorService es, ThrowingConsumer<SocketWrapper, IOException> handler) {
        Runnable t = ThrowingRunnable.throwingRunnableWrapper(() -> {
            try {
                // yes, this infinite loop can only exit by an exception.  But this is
                // the beating heart of a server, and to the best of my current knowledge,
                // when a server socket is force-closed it's going to throw an exception, and
                // that's just part of its life cycle
                //noinspection InfiniteLoopStatement
                while (true) {
                    logger.logDebug(() -> "server waiting to accept connection");
                    Socket freshSocket = serverSocket.accept();
                    SocketWrapper sw = new SocketWrapper(freshSocket, setOfServers, logger);
                    logger.logDebug(() -> String.format("server accepted connection: remote: %s", sw.getRemoteAddr()));
                    setOfServers.add(sw);
                    if (handler != null) {
                        es.submit(ThrowingRunnable.throwingRunnableWrapper(() -> handler.accept(sw)));
                    }
                }
            } catch (SocketException ex) {
                if (!ex.getMessage().contains("Socket closed")) {
                    throw new RuntimeException(ex);
                }
            }
        });
        this.centralLoopFuture = es.submit(t);
    }

    public void close() throws IOException {
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
        return setOfServers.getSocketWrapperByRemoteAddr(sw.getLocalAddr(), sw.getLocalPort());
    }

    public void stop() throws IOException {
        // close all the running sockets
        setOfServers.stopAllServers();

        // close the primary server socket
        serverSocket.close();
    }

}
