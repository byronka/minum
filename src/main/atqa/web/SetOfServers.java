package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.ConcurrentSet;
import atqa.utils.MyThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static atqa.utils.Invariants.mustBeFalse;

public record SetOfServers(ConcurrentSet<SocketWrapper> data, ILogger logger) {

    public void add(SocketWrapper sw) {
        data().add(sw);
        int size = data().size();
        logger.logDebug(() -> "added " + sw + " to setOfServers. size: " + size);
    }

    public void remove(SocketWrapper sw) {
        data().remove(sw);
        int size = data().size();
        logger.logDebug(() -> "removed " + sw + " from setOfServers. size: " + size);
    }

    /**
     * This is a program used during testing so we can find the server
     * socket that corresponds to a particular client socket.
     * <p>
     * Due to the circumstances of the TCP handshake, there's a bit of
     * time where the server might not have finished initialization,
     * and been put into the list of current server sockets.
     * <p>
     * For that reason, if we come in here and don't find it initially, we'll
     * sleep and then try again, up to three times.
     */
    public SocketWrapper getSocketWrapperByRemoteAddr(String address, int port) {
        int maxLoops = 3;
        for (int loopCount = 0; loopCount < maxLoops; loopCount++ ) {
            List<SocketWrapper> servers = data()
                    .asStream()
                    .filter((x) -> x.getRemoteAddr().equals(new InetSocketAddress(address, port)))
                    .toList();
            mustBeFalse(servers.size() > 1, "Too many sockets found with that address");
            if (servers.size() == 1) {
                return servers.get(0);
            }

            // if we got here, we didn't find a server in the list - probably because the TCP
            // initialization has not completed.  Retry after a bit.  The TCP process is dependent on
            // a whole lot of variables outside our control - downed lines, slow routers, etc.
            //
            // on the other hand, this code should probably only be called in testing, so maybe fewer
            // out-of-bounds problems?
            int finalLoopCount = loopCount;
            logger.logDebug(() -> String.format("no server found, sleeping on it... (attempt %d)", finalLoopCount + 1));
            MyThread.sleep(10);
        }
        throw new RuntimeException("No socket found with that address");
    }

    public void stopAllServers() throws IOException {
        for(var s : data()) {
            s.close();
        }
    }
}
