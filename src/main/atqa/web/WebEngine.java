package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.ThrowingConsumer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
public class WebEngine {

  public WebEngine(ILogger logger) {
      this.logger = logger;
      this.logger.logDebug(() -> "Using a supplied logger in WebEngine");
  }

  public enum HttpVersion {
    ONE_DOT_ZERO, ONE_DOT_ONE
  }

  private final ILogger logger;
  public static final String HTTP_CRLF = "\r\n";

  public Server startServer(ExecutorService es, ThrowingConsumer<SocketWrapper, IOException> handler) throws IOException {
    int port = 8080;
    ServerSocket ss = new ServerSocket(port);
    logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    Server server = new Server(ss, logger);
    server.start(es, handler);
    return server;
  }

  public Server startSslServer(ExecutorService es, ThrowingConsumer<SocketWrapper, IOException> handler) throws IOException {
    int port = 8443;

    /*
     * If we find the keystore and pass in the system properties
     */
    final var hasKeystoreAndPasswordOnSystemProperties = checkSystemPropertiesForKeystore();

    ServerSocket ss;
    if (hasKeystoreAndPasswordOnSystemProperties) {
      logger.logDebug(() -> "Using keystore and password provided in system properties");
      ss = SSLServerSocketFactory.getDefault().createServerSocket(port);
    } else {
      logger.logDebug(() -> "Using the default (self-signed / testing-only) certificate");
      ss = createSslSocketWithInternalKeystore(port);
    }

    logger.logDebug(() -> String.format("Just created a new ServerSocket: %s", ss));
    Server server = new Server(ss, logger);
    server.start(es, handler);
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
    final var props = System.getProperties();

    // get the directory to the keystore from a system property
    final var keystore = props.getProperty("javax.net.ssl.keyStore");
    if (keystore == null) {
      logger.logDebug(() -> "Keystore system property was not set");
    }

    // get the password to that keystore from a system property
    final var keystorePassword = props.getProperty("javax.net.ssl.keyStorePassword");
    if (keystorePassword == null) {
      logger.logDebug(() -> "keystorePassword system property was not set");
    }

    return keystore != null && keystorePassword != null;
  }

  /**
   * Creates an SSL Socket using a keystore we have internally, meant
   * for ssl testing and other situations where a self-signed keystore will suffice
   * <p>
   * It's so convoluted just because the interface to this stuff on the standard library
   * doesn't expect the typical user to read a keystore programmatically.  Realistic use
   * of SSL sockets typically (strongly) suggests using a real key signed by a certificate
   * authority.
   */
  public ServerSocket createSslSocketWithInternalKeystore(int sslPort) {
    try {
      final var keyPassword = "passphrase".toCharArray();
      final var keystoreFile = WebEngine.class.getClassLoader().getResource("resources/certs/keystore").openStream();

      final var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(keystoreFile, keyPassword);

      final var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, keyPassword);

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
  public Server startServer(ExecutorService es) throws IOException {
    return startServer(es, null);
  }

  public SocketWrapper startClient(Server server) throws IOException {
    Socket socket = new Socket(server.getHost(), server.getPort());
    logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
    return new SocketWrapper(socket, logger);
  }

}
