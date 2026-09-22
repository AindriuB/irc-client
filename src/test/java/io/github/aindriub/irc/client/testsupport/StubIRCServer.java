package io.github.aindriub.irc.client.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;


/**
 * A minimal in-process IRC server for tests: enough of the handshake to get a
 * client registered, plus the ability to drop a connection on demand.
 */
public final class StubIRCServer implements AutoCloseable {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final ServerSocket serverSocket;
    private final List<String> received = new ArrayList<>();
    private final AtomicInteger connections = new AtomicInteger();
    private final Thread acceptLoop;

    private volatile Socket currentSocket;
    private volatile boolean running = true;
    private volatile boolean refuseRegistration = false;
    private volatile boolean withholdWelcome = false;

    /**
     * A TLS server presenting the self-signed certificate in
     * src/test/resources/stub-server.p12, so that both the happy path with
     * trustAllCertificates and the rejection without it can be exercised.
     *
     * <p>The keystore is checked in rather than generated: Netty's
     * SelfSignedCertificate cannot generate one on a modern JDK without
     * BouncyCastle, and this keeps the tests dependency free.
     */
    public static StubIRCServer tls() throws Exception {
        return new StubIRCServer(sslContext());
    }

    private static SSLContext sslContext() throws Exception {
        char[] password = "stubstub".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (java.io.InputStream in = StubIRCServer.class
                .getResourceAsStream("/stub-server.p12")) {
            if (in == null) {
                throw new IllegalStateException("stub-server.p12 is missing from test resources");
            }
            keyStore.load(in, password);
        }

        KeyManagerFactory keyManagers = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    public StubIRCServer() throws IOException {
        this((SSLContext) null);
    }

    public StubIRCServer(SSLContext sslContext) throws IOException {
        serverSocket = sslContext == null ? new ServerSocket(0)
                : sslContext.getServerSocketFactory().createServerSocket(0);
        acceptLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                accept();
            }
        }, "stub-irc-accept");
        acceptLoop.setDaemon(true);
        acceptLoop.start();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public int getConnectionCount() {
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
            if (withholdWelcome) {
                // Accept the connection but never finish the handshake.
                return;
            }
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
    public void push(String line) throws IOException {
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
    public void dropConnection() throws IOException {
        Socket socket = currentSocket;
        if (socket != null) {
            socket.close();
        }
    }

    public void refuseRegistration(boolean refuse) {
        this.refuseRegistration = refuse;
    }

    /**
     * Stay connected but never send RPL_WELCOME, the way a wedged server would.
     */
    public void withholdWelcome(boolean withhold) {
        this.withholdWelcome = withhold;
    }

    /**
     * Waits for a line starting with the given prefix, counting only those that
     * arrive at or after {@code fromIndex} in the received log.
     */
    public boolean awaitLine(String prefix, int fromIndex, long timeoutMillis)
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

    public boolean awaitLine(String prefix, long timeoutMillis) throws InterruptedException {
        return awaitLine(prefix, 0, timeoutMillis);
    }

    public int receivedCount() {
        synchronized (received) {
            return received.size();
        }
    }

    public List<String> getReceived() {
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
