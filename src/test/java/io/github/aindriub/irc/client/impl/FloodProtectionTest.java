package io.github.aindriub.irc.client.impl;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.command.PrivMsg;
import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;
import io.github.aindriub.irc.client.testsupport.StubIRCServer;

/**
 * The limiter is placed below the ping and registration handlers precisely so that
 * protocol traffic the server is waiting on is never delayed. These are the tests
 * that hold that placement in place.
 */
public class FloodProtectionTest {

    private static final long TIMEOUT = 5000;

    private StubIRCServer server;
    private BasicIRCClient client;

    @Before
    public void setUp() throws IOException {
        server = new StubIRCServer();
    }

    @After
    public void tearDown() throws IOException {
        if (client != null) {
            try {
                client.disconnect();
            } catch (RuntimeException e) {
                // Already down.
            }
        }
        server.close();
    }

    @Test
    public void registrationIsNotThrottled() throws Exception {
        // One message per 800ms. The handshake is several messages, so if it went
        // through the limiter it could not finish inside the registration timeout.
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .capability("multi-prefix")
                .reconnect(false)
                .floodProtection(1, 800)
                .registrationTimeout(600)
                .build());

        long start = System.currentTimeMillis();
        client.connect();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(client.isRegistered());
        assertTrue("the handshake took " + elapsed + "ms, so it was throttled",
                elapsed < 600);
    }

    @Test
    public void pongIsNotThrottledEvenWithTheLimiterSaturated() throws Exception {
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .floodProtection(1, 800)
                .build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // Spend the single token and queue more behind it.
        client.sendCommand(new PrivMsg("#chan", "one"));
        sendInBackground("two");
        sendInBackground("three");
        Thread.sleep(100);
        int before = server.receivedCount();

        server.push("PING :are-you-there");

        // Well inside the 800ms the limiter would have imposed.
        assertTrue("a throttled PONG would risk the ping timeout it exists to prevent",
                server.awaitLine("PONG :are-you-there", before, 400));
    }

    /**
     * send() blocks until the message is actually released, which is the point, so a
     * throttled one cannot be sent from the test thread.
     */
    private void sendInBackground(final String text) {
        Thread sender = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.sendCommand(new PrivMsg("#chan", text));
                } catch (RuntimeException e) {
                    // The test may finish and disconnect first.
                }
            }
        });
        sender.setDaemon(true);
        sender.start();
    }
}
