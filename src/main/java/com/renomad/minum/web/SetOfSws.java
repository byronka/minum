package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.ConcurrentSet;

import java.io.IOException;

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
        ConcurrentSet<ISocketWrapper> socketWrappers,
        ILogger logger,
        String nameOfSet) {

    void add(ISocketWrapper sw) {
        socketWrappers().add(sw);
        int size = socketWrappers().size();
        logger.logTrace(() -> nameOfSet + " added " + sw + " to SetOfSws. size: " + size);
    }

    void remove(ISocketWrapper sw) {
        socketWrappers().remove(sw);
        int size = socketWrappers().size();
        logger.logTrace(() -> nameOfSet +" removed " + sw + " from SetOfSws. size: " + size);
    }

    void stopAllServers() throws IOException {
        for(ISocketWrapper s : socketWrappers()) {
            s.close();
        }
    }
}
