package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;

/**
 * This class contains the basic internet capabilities.
 * <br><br>
 * Think of this class as managing some of the lowest-level internet
 * communications we need to handle to support a web application. Sockets,
 * Servers, Threads, that kind of stuff.
 */
final class WebEngine {

  private final ITheBrig theBrig;
  private final Constants constants;
  private final Context context;

  WebEngine(Context context) {
    this.logger = context.getLogger();
    this.logger.logDebug(() -> "Using a supplied logger in WebEngine");
    this.theBrig = context.getFullSystem() != null ? context.getFullSystem().getTheBrig() : null;
    this.constants = context.getConstants();
    this.context = context;
  }

  private final ILogger logger;
  static final String HTTP_CRLF = "\r\n";

  Server startServer(ExecutorService es, ThrowingConsumer<ISocketWrapper, IOException> handler) throws IOException {
    int port = constants.SERVER_PORT;
    ServerSocket ss = new ServerSocket(port);
    logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    Server server = new Server(ss, context, "http server", theBrig);
    logger.logDebug(() -> String.format("Just created a new Server: %s", server));
    server.start(es, handler);
    String hostname = constants.HOST_NAME;
    logger.logDebug(() -> String.format("%s started at http://%s:%s", server, hostname, port));
    return server;
  }

  Server startSslServer(ExecutorService es, ThrowingConsumer<ISocketWrapper, IOException> handler) throws IOException {

    /*
     * If we find the keystore and pass in the system properties
     */
    final var useExternalKeystore = Boolean.TRUE.equals(checkSystemPropertiesForKeystore());

    ServerSocket ss;

    if (useExternalKeystore) {
      logger.logDebug(() -> "Using keystore and password referenced in minum.config");
    } else {
      logger.logDebug(() -> "Using the default (self-signed / testing-only) certificate");
    }

    final URL keystoreUrl = useExternalKeystore ?
            Path.of(constants.KEYSTORE_PATH).toUri().toURL() :
            WebEngine.class.getClassLoader().getResource("certs/keystore");
    final String keystorePassword = useExternalKeystore ?
            constants.KEYSTORE_PASSWORD :
            "passphrase";

    int port = constants.SECURE_SERVER_PORT;
    ss = createSslSocketWithSpecificKeystore(port, keystoreUrl, keystorePassword);
    logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    Server server = new Server(ss, context, "https server", theBrig);
    logger.logDebug(() -> String.format("Just created a new SSL Server: %s", server));
    server.start(es, handler);
    String hostname = constants.HOST_NAME;
    logger.logDebug(() -> String.format("%s started at https://%s:%s", server, hostname, port));
    return server;
  }


  /**
   * Look into the system properties to see whether values have been
   * set for the keystore and keystorePassword keys.
   * <p>
   * the key for keystore is:  javax.net.ssl.keyStore
   * the key for keystorePassword is: javax.net.ssl.keyStorePassword
   * <p>
   * It's smart, if you are creating a server that will run
   * with a genuine signed certificate, to have those files
   * stored somewhere and then set these system properties.  That
   * way, it's a characteristic of a particular server - it's not
   * needed to bundle the certificate with the actual server in
   * any way.
   * <p>
   * We *do* bundle a cert, but it's for testing and is self-signed.
   */
  private Boolean checkSystemPropertiesForKeystore() {

    // get the directory to the keystore from a system property
    final var keystore = constants.KEYSTORE_PATH;
    boolean hasKeystore = ! (keystore == null || keystore.isBlank());
    if (! hasKeystore) {
      logger.logDebug(() -> "Keystore system property was not set");
    }

    // get the password to that keystore from a system property
    final var keystorePassword = constants.KEYSTORE_PASSWORD;
    boolean hasKeystorePassword = ! (keystorePassword == null || keystorePassword.isBlank());
    if (! hasKeystorePassword) {
      logger.logDebug(() -> "keystorePassword system property was not set");
    }

    return hasKeystore && hasKeystorePassword;
  }


  /**
   * Create an SSL Socket using a specified keystore
   */
  ServerSocket createSslSocketWithSpecificKeystore(int sslPort, URL keystoreUrl, String keystorePassword) {
    try (InputStream keystoreInputStream = keystoreUrl.openStream()) {
      final var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      char[] passwordCharArray = keystorePassword.toCharArray();
      keyStore.load(keystoreInputStream, passwordCharArray);

      final var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, passwordCharArray);

      final var keyManagers = keyManagerFactory.getKeyManagers();

      final var sslContext = SSLContext.getInstance("TLSv1.3");
      sslContext.init(keyManagers, null, new SecureRandom());

      final var socketFactory = sslContext.getServerSocketFactory();
      return socketFactory.createServerSocket(sslPort);
    } catch (Exception ex) {
      logger.logDebug(ex::getMessage);
      throw new RuntimeException(ex);
    }
  }

  /**
   * Create a listening server with no handler.
   * Mostly used to test the server very manually.
   */
  Server startServer(ExecutorService es) throws IOException {
    return startServer(es, null);
  }

  /**
   * Create a client {@link ISocketWrapper} connected to the running host server
   */
  ISocketWrapper startClient(Socket socket) throws IOException {
    logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
    return new SocketWrapper(socket, logger, constants.SOCKET_TIMEOUT_MILLIS);
  }

}
