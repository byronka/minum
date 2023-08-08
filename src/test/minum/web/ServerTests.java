package minum.web;

import minum.Context;
import minum.exceptions.ForbiddenUseException;
import minum.logging.TestLogger;

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.net.SocketException;

import static minum.testing.TestFramework.assertEquals;
import static minum.testing.TestFramework.assertThrows;

public class ServerTests {

    private final Context context;
    private final TestLogger logger;

    public ServerTests(Context context) {
        this.context = context;
        this.logger = (TestLogger)context.getLogger();
        logger.testSuite("ServerTests");
    }

    public void tests() throws Exception {

        /*
        ForbiddenUseException gets thrown when the client has done
        something we prohibit, usually on the basis that it's a
        potential attack.  For example, trying to send us an
        absolutely huge number of characters, trying to overflow
        our stack or some such nonsense.
         */
        logger.test("Handling ForbiddenUseException"); {
            Server server = new Server(null, context, "", null);
            var builtCore = server.buildExceptionHandlingInnerCore(x -> {
                throw new ForbiddenUseException("Forbidden!");
            }, new FakeSocketWrapper());
            builtCore.run();
            assertEquals("Forbidden!", logger.findFirstMessageThatContains("Forbidden!"));
        }

        /*
          There are many possible exceptions under the heading of SSLException.
          In our code, we examine the exception message and perform different
          actions based on it.

          Here, the phrase "no cipher suites in common" indicates that the
          client is trying to force the server to use a less-secure encryption,
          as a form of attack.
         */
        logger.test("Handling SSLException - vulnerability attack"); {
            Server server = new Server(null, context, "", null);
            var builtCore = server.buildExceptionHandlingInnerCore(x -> {
                throw new SSLException("no cipher suites in common");
            }, new FakeSocketWrapper());
            builtCore.run();
            assertEquals("is  looking for vulnerabilities? true", logger.findFirstMessageThatContains("looking for vulnerabilities?"));
        }

        /*
         SocketException is another thing that has a variety of messages,
         but we don't do different things, we just log it.
         */
        logger.test("Handling SocketException"); {
            Server server = new Server(null, context, "", null);
            var builtCore = server.buildExceptionHandlingInnerCore(x -> {
                throw new SocketException("Foo foo is a foo");
            }, new FakeSocketWrapper());
            builtCore.run();
            assertEquals("Foo foo is a foo - remote address: 0.0.0.0/0.0.0.0:12345", logger.findFirstMessageThatContains("Foo foo is a foo"));
        }

        /*
        buildExceptionHandlingInnerCore only handles a few kinds of exception.
        Everything else bubbles out of it, but if we don't recognize the
        exception
         */
        logger.test("Handling other exception"); {
            Server server = new Server(null, context, "", null);
            var builtCore = server.buildExceptionHandlingInnerCore(x -> {
                throw new IOException();
            }, new FakeSocketWrapper());

            assertThrows(IOException.class, () -> builtCore.run());
        }


    }
}
