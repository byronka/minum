package com.renomad.minum.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A fake of {@link ISocketWrapper} used in tests
 */
public class FakeSocketWrapper implements ISocketWrapper {

    public Consumer<String> sendHttpLineAction;
    public Supplier<String> getLocalAddrAction;
    public Supplier<Integer> getLocalPortAction;
    public ByteArrayOutputStream baos;
    public ByteArrayInputStream bais;

    public FakeSocketWrapper() {
        bais = new ByteArrayInputStream(new byte[0]);
        baos = new ByteArrayOutputStream();
    }

    @Override
    public void send(String msg) throws IOException {
        baos.write(msg.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void send(byte[] bodyContents) throws IOException {
        baos.write(bodyContents);
    }

    @Override
    public void sendHttpLine(String msg) {
        sendHttpLineAction.accept(msg);
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
    public SocketAddress getRemoteAddrWithPort() {
        return new InetSocketAddress(12345);
    }

    @Override
    public String getRemoteAddr() {
        return "";
    }

    @Override
    public void close() {}

    @Override
    public InputStream getInputStream() {
        return bais;
    }
}
