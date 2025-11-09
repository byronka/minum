package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;

public class WebEngineTests {

    private static Context context;
    static private WebEngine webEngine;
    static private TestLogger logger;

    @BeforeClass
    public static void setUpClass() {
        context = buildTestingContext("WebEngine Tests");
        var webFramework = new WebFramework(context);
        webEngine = new WebEngine(context, webFramework);
        logger = (TestLogger)context.getLogger();
    }


    @AfterClass
    public static void tearDownClass() {
        shutdownTestingContext(context);
    }

    /**
     * This odd little test just examines what takes place when we
     * run the code that gets a keystore for SSL, and something fails.
     */
    @Test
    public void test_createSslSocketWithSpecificKeystore_EdgeCase() {
        var ex = assertThrows(WebServerException.class, () -> {
                webEngine.createSslSocketWithSpecificKeystore(
                        1234,
                        new URI("file:///does/not/exist").toURL(),
                        "badpass");
        });
        String adjustedMessage = ex.getMessage().replace('/', '.').replace('\\', '.');
        assertTrue(adjustedMessage.contains("Exception during creation of SSL socket with port 1234, keystore URL of file:.does.not.exist.  Exception message:"));
    }

    @Test
    public void test_isProvidedKeystoreProperties() {
        assertTrue (WebEngine.isProvidedKeystoreProperties("abc", "def", logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties("abc", "",    logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties("", "def",    logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties("", "",       logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties("", null,     logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties(null, "",     logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties(null, null,   logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties("abc", null,  logger));
        assertFalse(WebEngine.isProvidedKeystoreProperties(null, "def",  logger));
    }

    @Test
    public void test_getKeyStoreResult() throws MalformedURLException {
        assertEquals(WebEngine.getKeyStoreResult(true, "abc", "def", logger), new WebEngine.KeyStoreResult(Path.of("abc").toUri().toURL(), "def"));

        assertEquals(WebEngine.getKeyStoreResult(false, "abc", "def", logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, "abc", "",    logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, "", "def",    logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, "", "",       logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, "", null,     logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, null, "",     logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, null, null,   logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, "abc", null,  logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
        assertEquals(WebEngine.getKeyStoreResult(false, null, "def",  logger), new WebEngine.KeyStoreResult(WebEngine.class.getResource("/certs/keystore"), "passphrase"));
    }

    @Test
    public void test_MalformedUrl() {
        var ex = assertThrows(WebServerException.class, () -> WebEngine.getKeyStoreResult(true, null, "password", logger));
        assertEquals(ex.getMessage(), "Error while building keystoreUrl: java.lang.NullPointerException");
    }

    /**
     * If the user sets a port of -1 for the server (either normal or TLS-encrypted),
     * then that server simply won't start.
     */
    @Test
    public void test_DisabledServer() {
        Properties properties = new Properties();
        properties.put("SERVER_PORT", "-1");
        properties.put("SSL_SERVER_PORT", "-1");

        var localContext = buildTestingContext("test_DisabledServer", properties);
        var webFramework = new WebFramework(localContext);
        var myWebEngine = new WebEngine(localContext, webFramework);

        assertTrue(myWebEngine.startServer() == null);
        assertTrue(myWebEngine.startSslServer() == null);

        TestFramework.shutdownTestingContext(localContext);
    }

    /**
     * If the user sets a port of -1 for the server (either normal or TLS-encrypted),
     * then that server simply won't start.
     */
    @Test
    public void test_DisabledServer_AlternateCase() {
        Properties properties = new Properties();
        properties.put("SERVER_PORT", "-2");
        properties.put("SSL_SERVER_PORT", "-2");

        var localContext = buildTestingContext("test_DisabledServer_AlternateCase", properties);
        var webFramework = new WebFramework(localContext);
        var myWebEngine = new WebEngine(localContext, webFramework);

        var ex1 = assertThrows(WebServerException.class, () -> myWebEngine.startServer());
        assertEquals(ex1.getMessage(), "Failed to create serversocket on port -2");

        var ex2 = assertThrows(WebServerException.class, () -> myWebEngine.startSslServer());
        assertTrue(ex2.getMessage().contains("Exception during creation of SSL socket with port -2"));

        TestFramework.shutdownTestingContext(localContext);
    }
}
