package primary;

import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A fake of {@link primary.Web.SocketWrapper} used in tests
 */
public class FakeSocketWrapper implements Web.ISocketWrapper {

    Consumer<String> sendAction;
    Consumer<String> sendHttpLineAction;
    Supplier<String> readLineAction;
    Supplier<String> getLocalAddrAction;
    Supplier<Integer> getLocalPortAction;
    Supplier<SocketAddress> getRemoteAddrAction;

    @Override
    public void send(String msg) {
        sendAction.accept(msg);
    }

    @Override
    public void sendHttpLine(String msg) {
        sendHttpLineAction.accept(msg);
    }

    @Override
    public String readLine() {
        return readLineAction.get();
    }

    @Override
    public String getLocalAddr() {
        return getLocalAddrAction.get();
    }

    @Override
    public int getLocalPort() {
        return getLocalPortAction.get();
    }

    @Override
    public SocketAddress getRemoteAddr() {
        return getRemoteAddrAction.get();
    }

    @Override
    public void close() {

    }
}
