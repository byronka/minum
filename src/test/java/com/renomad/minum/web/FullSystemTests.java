package com.renomad.minum.web;

import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.state.Constants;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.MyThread;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import static com.renomad.minum.testing.TestFramework.*;

public class FullSystemTests {

    @Test
    public void testFullSystem() {
        FullSystem fs = FullSystem.initialize();
        new Thread(() -> {
            MyThread.sleep(200);
            fs.shutdown();
        }).start();
        assertEquals(fs.getServer().getHost(), "0.0.0.0");
        assertEquals(fs.getServer().getPort(), 8080);
        assertEquals(fs.getSslServer().getPort(), 8443);
        assertEquals(fs.getContext().getConstants().dbDirectory, "out/simple_db");
        assertTrue(fs.getWebEngine().toString().contains("com.renomad.minum.web.WebEngine"));
        fs.block();
    }

    @Test
    public void testFullSystem_WithRedirect() {
        Properties properties = Constants.getConfiguredProperties();
        properties.setProperty("REDIRECT_TO_SECURE", "true");
        var context = buildTestingContext("testing redirect handler in FullSystem", properties);
        var fullSystem = new FullSystem(context);
        fullSystem.start();
        new Thread(() -> {
            MyThread.sleep(200);
            fullSystem.shutdown();
        }).start();

        assertEquals(fullSystem.getServer().getHost(), "0.0.0.0");
        assertEquals(fullSystem.getServer().getPort(), 8080);
        assertEquals(fullSystem.getSslServer().getPort(), 8443);
        assertEquals(fullSystem.getContext().getConstants().dbDirectory, "out/simple_db");
        assertTrue(fullSystem.getWebEngine().toString().contains("com.renomad.minum.web.WebEngine"));
        assertTrue(Files.exists(Path.of("SYSTEM_RUNNING")));

        fullSystem.block();

        TestFramework.shutdownTestingContext(context);
    }

    /**
     * It is possible to configure the system not to write a "system-running"
     * marker file to the disk.  Let's see that.
     */
    @Test
    public void testFullSystem_DisabledSystemRunningMarker() {
        Properties properties = Constants.getConfiguredProperties();
        properties.setProperty("ENABLE_SYSTEM_RUNNING_MARKER", "false");
        var context = buildTestingContext("testing disabled system running marker", properties);
        var fullSystem = new FullSystem(context);
        fullSystem.start();
        new Thread(() -> {
            MyThread.sleep(200);
            fullSystem.shutdown();
        }).start();
        assertFalse(Files.exists(Path.of("SYSTEM_RUNNING")));

        fullSystem.block();

        TestFramework.shutdownTestingContext(context);
    }

    /**
     * Close right after start
     */
    @Test
    public void testFullSystem_EdgeCase_InstantlyClosed() {
        FullSystem fs = FullSystem.initialize();
        fs.shutdown();
        assertEquals(fs.getServer().getHost(), "0.0.0.0");
        assertEquals(fs.getServer().getPort(), 8080);
        assertEquals(fs.getSslServer().getPort(), 8443);
        assertEquals(fs.getContext().getConstants().dbDirectory, "out/simple_db");
        assertTrue(fs.getWebEngine().toString().contains("com.renomad.minum.web.WebEngine"));
    }

    /**
     * When closing a full system, several steps take place,
     * any of which can throw exceptions.
     */
    @Test
    public void test_CloseCore() {
        Context context = buildTestingContext("Testing the closing core");
        TestLogger logger = (TestLogger)context.getLogger();
        assertThrows(WebServerException.class, "java.lang.RuntimeException: Just testing", () -> FullSystem.closeCore(logger, context, throwingServer, throwingServer, "my test system"));

        TestFramework.shutdownTestingContext(context);
    }

    /**
     * It is possible for users to disable servers, such as the plain-text or encrypted server,
     * by setting their port to -1 in the minum.config file.
     * <br>
     * This test examines the behavior of the closeCore method, which is the program which is
     * run when the program is being shut down.  Here, we will provide inputs for null servers
     * and ensure things operate as expected.
     */
    @Test
    public void test_CloseCore_NullServers() {
        Context context = buildTestingContext("Testing the closing core with null servers");
        TestLogger logger = (TestLogger) context.getLogger();
        FullSystem.closeCore(logger, context, null, null, "Born to fly");

        // should find that our server has said it is closed
        assertTrue(logger.doesMessageExist("Born to fly says: Goodbye world!"));

        // should not find any messages about stopping the servers
        assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("Stopping the"));

        TestFramework.shutdownTestingContext(context);
    }

    @Test
    public void test_BlockCore_RegularException() {
        Context context = buildTestingContext("test_BlockCore_RegularException");
        TestLogger logger = (TestLogger) context.getLogger();
        assertThrows(WebServerException.class, "java.util.concurrent.CancellationException: Just testing", () -> FullSystem.blockCore(throwingServer, throwingServer, logger));
        TestFramework.shutdownTestingContext(context);
    }

    /**
     * If the servers has been disabled (because the user set a port of -1 on SERVER_PORT or SSL_SERVER_PORT),
     * then blockCore may not have anything to block on at all!  That is just the price of advanced customization,
     * the user shouldn't go about modifying those settings unless they know what they're doing.
     */
    @Test
    public void test_BlockCore_NullServers() {
        Context context = buildTestingContext("test_BlockCore_NullServers");
        TestLogger logger = (TestLogger) context.getLogger();
        FullSystem.blockCore(null, null, logger);
        assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("Blocking on normal server"));
        assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("Blocking on encrypted server"));
        TestFramework.shutdownTestingContext(context);
    }

    IServer throwingServer = new IServer() {
        @Override
        public void start() {
        }

        @Override
        public String getHost() {
            return null;
        }

        @Override
        public int getPort() {
            return 0;
        }

        @Override
        public void removeMyRecord(ISocketWrapper socketWrapper) {
        }

        @Override
        public void addToSetOfSws(ISocketWrapper sw) {

        }

        @Override
        public Future<?> getCentralLoopFuture() {
            throw new CancellationException("Just testing");
        }

        @Override
        public HttpServerType getServerType() {
            return null;
        }

        @Override
        public void close() {
            throw new RuntimeException("Just testing");
        }
    };

}
