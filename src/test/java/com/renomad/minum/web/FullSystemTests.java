package com.renomad.minum.web;

import com.renomad.minum.state.Constants;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.MyThread;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import static com.renomad.minum.testing.TestFramework.*;

public class FullSystemTests {

    @Test
    public void testFullSystem() {
        FullSystem fs = FullSystem.initialize();
        new Thread(() -> {
            MyThread.sleep(100);
            fs.shutdown();
        }).start();
        assertEquals(fs.getServer().getHost(), "0.0.0.0");
        assertEquals(fs.getServer().getPort(), 8080);
        assertEquals(fs.getSslServer().getPort(), 8443);
        assertEquals(fs.getContext().getConstants().dbDirectory, "out/simple_db");
        assertTrue(fs.getWebEngine().toString().contains("com.renomad.minum.web.WebEngine"));
        fs.block();
        fs.shutdown();
    }

    @Test
    public void testFullSystem_WithRedirect() {
        Properties properties = Constants.getConfiguredProperties();
        properties.setProperty("REDIRECT_TO_SECURE", "true");
        var context = buildTestingContext("testing redirect handler in FullSystem", properties);
        var fullSystem = new FullSystem(context);
        fullSystem.start();
        new Thread(() -> {
            MyThread.sleep(100);
            fullSystem.shutdown();
        }).start();
        assertEquals(fullSystem.getServer().getHost(), "0.0.0.0");
        assertEquals(fullSystem.getServer().getPort(), 8080);
        assertEquals(fullSystem.getSslServer().getPort(), 8443);
        assertEquals(fullSystem.getContext().getConstants().dbDirectory, "out/simple_db");
        assertTrue(fullSystem.getWebEngine().toString().contains("com.renomad.minum.web.WebEngine"));
        fullSystem.block();
        fullSystem.shutdown();
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
    }

    @Test
    public void test_BlockCore_RegularException() {
        assertThrows(WebServerException.class, "java.util.concurrent.CancellationException: Just testing", () -> FullSystem.blockCore(throwingServer, throwingServer));
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
