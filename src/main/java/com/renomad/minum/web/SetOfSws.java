package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.ConcurrentSet;
import com.renomad.minum.utils.MyThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static com.renomad.minum.utils.Invariants.mustBeFalse;

/**
 * This is a data structure of the live set of {@link ISocketWrapper}
 * in our system.  It exists so that we can keep tabs on how many
 * open sockets we have, and can then find them all in one place
 * when we need to kill them at server shutdown.
 * @param nameOfSet This parameter is used to distinguish different servers'
 *        list of sockets (e.g.
 *        the server for 80 vs 443)
 */
record SetOfSws(
        ConcurrentSet<ISocketWrapper> data,
        ILogger logger,
        String nameOfSet) {

    void add(ISocketWrapper sw) {
        data().add(sw);
        int size = data().size();
        logger.logTrace(() -> nameOfSet + " added " + sw + " to SetOfSws. size: " + size);
    }

    void remove(ISocketWrapper sw) {
        data().remove(sw);
        int size = data().size();
        logger.logTrace(() -> nameOfSet +" removed " + sw + " from SetOfSws. size: " + size);
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
    ISocketWrapper getSocketWrapperByRemoteAddr(String address, int port) {
        int maxLoops = 3;
        for (int loopCount = 0; loopCount < maxLoops; loopCount++ ) {
            List<ISocketWrapper> servers = data()
                    .asStream()
                    .filter(x -> x.getRemoteAddrWithPort().equals(new InetSocketAddress(address, port)))
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

    void stopAllServers() throws IOException {
        for(var s : data()) {
            s.close();
        }
    }
}
