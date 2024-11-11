package com.renomad.minum.web;

import com.renomad.minum.state.Constants;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.state.Context;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Path;
import java.security.*;
import java.util.concurrent.ExecutorService;

import static com.renomad.minum.web.HttpServerType.ENCRYPTED_HTTP;
import static com.renomad.minum.web.HttpServerType.PLAIN_TEXT_HTTP;

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
  private final ExecutorService executorService;
  private final WebFramework webFramework;

  WebEngine(Context context, WebFramework webFramework) {
    this.logger = context.getLogger();
    this.logger.logDebug(() -> "Using a supplied logger in WebEngine");
    this.theBrig = context.getFullSystem() != null ? context.getFullSystem().getTheBrig() : null;
    this.constants = context.getConstants();
    this.context = context;
    this.executorService = context.getExecutorService();
    this.webFramework = webFramework;
  }

  private final ILogger logger;
  static final String HTTP_CRLF = "\r\n";

  IServer startServer() {
    int port = constants.serverPort;
      ServerSocket ss;
      try {
          ss = new ServerSocket(port);
      } catch (Exception e) {
          throw new WebServerException(e);
      }
      logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    IServer server = new Server(ss, context, "http server", theBrig, webFramework, executorService, PLAIN_TEXT_HTTP);
    logger.logDebug(() -> String.format("Just created a new Server: %s", server));
    server.start();
    String hostname = constants.hostName;
    logger.logDebug(() -> String.format("%s started at http://%s:%s", server, hostname, port));
    return server;
  }

  IServer startSslServer() {

    /*
     * If we find the keystore and pass in the system properties
     */
    final var useExternalKeystore = isProvidedKeystoreProperties(constants.keystorePath, constants.keystorePassword, logger);
    KeyStoreResult keystoreResult = getKeyStoreResult(useExternalKeystore, constants.keystorePath, constants.keystorePassword, logger);

    int port = constants.secureServerPort;
    ServerSocket ss = createSslSocketWithSpecificKeystore(port, keystoreResult.keystoreUrl(), keystoreResult.keystorePassword());
    logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    IServer server = new Server(ss, context, "https server", theBrig, webFramework, executorService, ENCRYPTED_HTTP);
    logger.logDebug(() -> String.format("Just created a new SSL Server: %s", server));
    server.start();
    String hostname = constants.hostName;
    logger.logDebug(() -> String.format("%s started at https://%s:%s", server, hostname, port));
    return server;
  }

  static KeyStoreResult getKeyStoreResult(
          boolean useExternalKeystore,
          String keystorePath,
          String keystorePassword,
          ILogger logger) {
    if (useExternalKeystore) {
      logger.logDebug(() -> "Using keystore and password referenced in minum.config");
    } else {
      logger.logDebug(() -> "Using the default (self-signed / testing-only) certificate");
    }

    final URL keystoreUrl;
    try {
        keystoreUrl = useExternalKeystore ?
                Path.of(keystorePath).toUri().toURL() :
                WebEngine.class.getResource("/certs/keystore");
    } catch (Exception e) {
        throw new WebServerException("Error while building keystoreUrl: " + e);
    }
    final String keystorePasswordFinal = useExternalKeystore ?
          keystorePassword :
          "passphrase";
    return new KeyStoreResult(keystoreUrl, keystorePasswordFinal);
  }

  record KeyStoreResult(URL keystoreUrl, String keystorePassword) { }


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
  static Boolean isProvidedKeystoreProperties(String keystorePath, String keystorePassword, ILogger logger) {

    // get the directory to the keystore from a system property
    boolean hasKeystore = ! (keystorePath == null || keystorePath.isBlank());
    if (! hasKeystore) {
      logger.logDebug(() -> "Keystore system property was not set");
    }

    // get the password to that keystore from a system property
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
      throw new WebServerException(ex);
    }
  }

  /**
   * Create a client {@link ISocketWrapper} connected to the running host server
   */
  ISocketWrapper startClient(Socket socket) throws IOException {
    logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
    return new SocketWrapper(socket, null, logger, constants.socketTimeoutMillis, constants.hostName);
  }

  /**
   * Intentionally return just the default object toString, this is only used
   * to differentiate between multiple instances in memory.
   */
  @Override
  public String toString() {
    return super.toString();
  }

}
