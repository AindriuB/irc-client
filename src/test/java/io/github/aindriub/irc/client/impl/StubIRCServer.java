package io.github.aindriub.irc.client.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal in-process IRC server for tests: enough of the handshake to get a
 * client registered, plus the ability to drop a connection on demand.
 */
final class StubIRCServer implements AutoCloseable {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final ServerSocket serverSocket;
    private final List<String> received = new ArrayList<>();
    private final AtomicInteger connections = new AtomicInteger();
    private final Thread acceptLoop;

    private volatile Socket currentSocket;
    private volatile boolean running = true;
    private volatile boolean refuseRegistration = false;

    StubIRCServer() throws IOException {
        serverSocket = new ServerSocket(0);
        acceptLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                accept();
            }
        }, "stub-irc-accept");
        acceptLoop.setDaemon(true);
        acceptLoop.start();
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    int getConnectionCount() {
        return connections.get();
    }

    private void accept() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                currentSocket = socket;
                connections.incrementAndGet();
                Thread session = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        serve(socket);
                    }
                }, "stub-irc-session");
                session.setDaemon(true);
                session.start();
            } catch (IOException e) {
                if (running) {
                    throw new IllegalStateException("stub server accept failed", e);
                }
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), UTF_8));
            OutputStream out = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(out, UTF_8), true);
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (received) {
                    received.add(line);
                    received.notifyAll();
                }
                respond(writer, line);
            }
        } catch (IOException e) {
            // The client or the test closed the socket; nothing to do.
        }
    }

    private void respond(PrintWriter writer, String line) {
        if (line.startsWith("CAP LS")) {
            writer.print(":stub CAP * LS :multi-prefix\r\n");
            writer.flush();
        } else if (line.startsWith("USER ")) {
            if (refuseRegistration) {
                writer.print(":stub 464 * :Password incorrect\r\n");
            } else {
                writer.print(":stub 001 bot :Welcome to the stub\r\n");
            }
            writer.flush();
        }
    }

    /**
     * Pushes a line to the connected client.
     */
    void push(String line) throws IOException {
        Socket socket = currentSocket;
        if (socket == null) {
            throw new IllegalStateException("no client is connected");
        }
        OutputStream out = socket.getOutputStream();
        out.write((line + "\r\n").getBytes(UTF_8));
        out.flush();
    }

    /**
     * Drops the live connection the way a network failure or a server restart would,
     * without the client having asked for it.
     */
    void dropConnection() throws IOException {
        Socket socket = currentSocket;
        if (socket != null) {
            socket.close();
        }
    }

    void refuseRegistration(boolean refuse) {
        this.refuseRegistration = refuse;
    }

    /**
     * Waits for a line starting with the given prefix, counting only those that
     * arrive at or after {@code fromIndex} in the received log.
     */
    boolean awaitLine(String prefix, int fromIndex, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (received) {
            while (true) {
                for (int i = Math.max(0, fromIndex); i < received.size(); i++) {
                    if (received.get(i).startsWith(prefix)) {
                        return true;
                    }
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                received.wait(remaining);
            }
        }
    }

    boolean awaitLine(String prefix, long timeoutMillis) throws InterruptedException {
        return awaitLine(prefix, 0, timeoutMillis);
    }

    int receivedCount() {
        synchronized (received) {
            return received.size();
        }
    }

    List<String> getReceived() {
        synchronized (received) {
            return new ArrayList<>(received);
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        Socket socket = currentSocket;
        if (socket != null) {
            socket.close();
        }
        serverSocket.close();
    }
}
