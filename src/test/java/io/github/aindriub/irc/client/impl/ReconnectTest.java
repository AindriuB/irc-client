package io.github.aindriub.irc.client.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.IRCClientException;
import io.github.aindriub.irc.client.command.Join;
import io.github.aindriub.irc.client.command.Part;
import io.github.aindriub.irc.client.command.PrivMsg;
import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;
import io.github.aindriub.irc.client.testsupport.StubIRCServer;
import io.github.aindriub.irc.client.event.MessageListener;
import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.state.CaseMapping;

/**
 * Drives a real socket against an in-process stub server, which is the only way to
 * exercise a connection actually going away.
 */
public class ReconnectTest {

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
                // Already down; the test has made its point.
            }
        }
        server.close();
    }

    @Test
    public void registersOnConnect() throws Exception {
        client = client(true);
        client.connect();

        assertTrue(client.isConnected());
        assertTrue(client.isRegistered());
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        assertTrue(server.awaitLine("USER bot 0 * :bot", TIMEOUT));
    }

    @Test
    public void reconnectsAndReregistersAfterAnUnexpectedDrop() throws Exception {
        client = client(true);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.dropConnection();

        // A second NICK means a second handshake, not just a second socket.
        assertTrue("expected the client to re-register",
                server.awaitLine("NICK bot", before, TIMEOUT));
        assertEquals(2, server.getConnectionCount());
    }

    @Test
    public void rejoinsChannelsAfterReconnecting() throws Exception {
        client = client(true);
        client.connect();
        client.sendCommand(new Join("#one"));
        client.sendCommand(new Join("#two", "secret"));
        assertTrue(server.awaitLine("JOIN #two secret", TIMEOUT));
        int before = server.receivedCount();

        server.dropConnection();

        assertTrue("expected #one to be rejoined",
                server.awaitLine("JOIN #one", before, TIMEOUT));
        assertTrue("expected #two to be rejoined with its key",
                server.awaitLine("JOIN #two secret", before, TIMEOUT));
    }

    @Test
    public void doesNotRejoinAChannelItHasParted() throws Exception {
        client = client(true);
        client.connect();
        client.sendCommand(new Join(Arrays.asList("#one", "#two")));
        client.sendCommand(new Part("#one"));
        assertEquals(Arrays.asList("#two"), client.getJoinedChannels());
        assertTrue(server.awaitLine("PART #one", TIMEOUT));
        int before = server.receivedCount();

        server.dropConnection();

        assertTrue(server.awaitLine("JOIN ", before, TIMEOUT));
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("should not rejoin a parted channel: " + line, line.contains("#one"));
        }
    }

    @Test
    public void doesNotRejoinAChannelItWasKickedFrom() throws Exception {
        client = client(true);
        client.connect();
        client.sendCommand(new Join(Arrays.asList("#one", "#two")));
        assertTrue(server.awaitLine("JOIN #one,#two", TIMEOUT));

        server.push(":op!u@h KICK #one bot :reason");

        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (client.getJoinedChannels().contains("#one")
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(Arrays.asList("#two"), client.getJoinedChannels());
        int before = server.receivedCount();

        server.dropConnection();

        assertTrue(server.awaitLine("JOIN ", before, TIMEOUT));
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("should not rejoin a channel it was kicked from: " + line,
                    line.contains("#one"));
        }
    }

    @Test
    public void aDeliberateDisconnectDoesNotReconnect() throws Exception {
        client = client(true);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        client.disconnect();
        Thread.sleep(300);

        assertEquals("disconnect() must not be undone by the reconnect logic", 1,
                server.getConnectionCount());
    }

    @Test
    public void doesNotReconnectWhenDisabled() throws Exception {
        client = client(false);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.dropConnection();
        Thread.sleep(300);

        assertEquals(1, server.getConnectionCount());
    }

    @Test
    public void refusesToSendBeforeConnecting() {
        client = client(true);
        try {
            client.sendCommand(new PrivMsg("#chan", "hi"));
            fail("expected send to be refused");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not connected"));
        }
    }

    @Test
    public void refusesToReconnectADisconnectedClient() throws Exception {
        client = client(true);
        client.connect();
        client.disconnect();

        try {
            client.connect();
            fail("expected a disconnected client to refuse reuse");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cannot be reused"));
        }
    }

    @Test
    public void failsConnectWhenTheServerRejectsRegistration() throws Exception {
        server.refuseRegistration(true);
        client = client(false);

        try {
            client.connect();
            fail("expected registration to fail");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("rejected by the server"));
        }
    }

    @Test
    public void aListenerCanReplyFromItsCallbackWithoutDeadlocking() throws Exception {
        // The callback runs on the event loop, and blocking there would stop the
        // very thread that has to perform the write.
        final BasicIRCClient[] holder = new BasicIRCClient[1];
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(3000)
                .messageListener(new MessageListener() {
                    @Override
                    protected void onMessage(String target, String sender, String text,
                            IRCMessage raw) {
                        holder[0].sendCommand(new PrivMsg(target, "pong: " + text));
                    }
                })
                .build());
        holder[0] = client;
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        int before = server.receivedCount();

        server.push(":someone!u@h PRIVMSG #chan :ping");

        assertTrue("expected the listener's reply to reach the server",
                server.awaitLine("PRIVMSG #chan :pong: ping", before, TIMEOUT));
    }

    @Test
    public void failsConnectWhenTheServerNeverFinishesTheHandshake() throws Exception {
        server.withholdWelcome(true);
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(300)
                .build());

        try {
            client.connect();
            fail("expected connect to time out waiting for registration");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("did not complete registration"));
        }
        assertFalse(client.isRegistered());
    }

    @Test
    public void keepsRetryingWhenRegistrationFailsOnEveryReconnect() throws Exception {
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(true)
                .reconnectBackoff(30, 60, 2)
                .registrationTimeout(3000)
                .build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // From now on the server rejects the handshake, so each reconnect gets a
        // socket but never a registered session.
        server.refuseRegistration(true);
        server.dropConnection();

        // The original connection plus exactly maxAttempts retries, then it stops.
        waitForConnections(3, TIMEOUT);
        Thread.sleep(400);
        assertEquals("should give up after maxAttempts", 3, server.getConnectionCount());
        assertFalse(client.isRegistered());
    }

    @Test
    public void givesUpWhenTheServerCannotBeReachedAtAll() throws Exception {
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(true)
                .reconnectBackoff(30, 60, 2)
                .registrationTimeout(1000)
                .build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // The whole server goes away, so the reconnect attempts cannot even connect.
        server.close();
        Thread.sleep(600);

        assertFalse(client.isConnected());
        assertFalse(client.isRegistered());
    }

    @Test
    public void joinZeroForgetsEveryTrackedChannel() throws Exception {
        client = client(true);
        client.connect();
        client.sendCommand(new Join(Arrays.asList("#one", "#two")));
        assertEquals(Arrays.asList("#one", "#two"), client.getJoinedChannels());

        client.sendCommand(Join.partAll());

        assertTrue("JOIN 0 leaves everything", client.getJoinedChannels().isEmpty());
        // Wait for the server to log it, or the window below starts too early and
        // catches the JOIN 0 this test just sent.
        assertTrue(server.awaitLine("JOIN 0", TIMEOUT));
        int before = server.receivedCount();
        server.dropConnection();
        assertTrue(server.awaitLine("NICK bot", before, TIMEOUT));
        Thread.sleep(200);
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            assertFalse("nothing should be rejoined: " + line, line.startsWith("JOIN"));
        }
    }

    @Test
    public void reconnectsWithoutRegistrationWhenNoNickIsSet() throws Exception {
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .reconnect(true)
                .reconnectBackoff(30, 60, 2)
                .build());
        client.connect();
        assertTrue(client.isConnected());
        assertFalse("no nick means no handshake to complete", client.isRegistered());

        server.dropConnection();

        waitForConnections(2, TIMEOUT);
        assertEquals(2, server.getConnectionCount());
    }

    @Test
    public void reconnectingWithoutIsupportResetsCaseMappingToTheDefault() throws Exception {
        client = client(true);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        server.push(":stub 005 bot CASEMAPPING=ascii :are supported by this server");
        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (client.getCaseMapping() != CaseMapping.ASCII
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(CaseMapping.ASCII,
                client.getCaseMapping());

        // The stub never sends ISUPPORT on its own, so the reconnected session gets
        // none: the ASCII learned from the previous connection must not survive.
        int before = server.receivedCount();
        server.dropConnection();
        assertTrue(server.awaitLine("NICK bot", before, TIMEOUT));

        deadline = System.currentTimeMillis() + TIMEOUT;
        while (client.getCaseMapping() != CaseMapping.RFC1459
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("stale CASEMAPPING must not survive a reconnect",
                CaseMapping.RFC1459, client.getCaseMapping());
    }

    @Test
    public void aCaseFoldedDuplicateJoinRejoinsOnceRatherThanTwice() throws Exception {
        client = client(true);
        client.connect();
        client.sendCommand(new Join("#A"));
        assertTrue(server.awaitLine("JOIN #A", TIMEOUT));

        client.sendCommand(new Join("#a"));
        assertEquals(Arrays.asList("#a"), client.getJoinedChannels());
        int before = server.receivedCount();

        server.dropConnection();

        // Once the rejoin for #a has arrived, the (synchronous) rejoin of every
        // tracked channel has already finished, so checking the lines sent since is
        // safe without sleeping blind.
        assertTrue("expected #a to be rejoined", server.awaitLine("JOIN #a", before, TIMEOUT));
        int joinLines = 0;
        for (String line : server.getReceived().subList(before, server.receivedCount())) {
            if (line.startsWith("JOIN")) {
                joinLines++;
            }
        }
        assertEquals("a case-folded duplicate must replace the tracked key, not add a "
                + "second rejoin", 1, joinLines);
    }

    @Test
    public void reportsNotRegisteredBeforeConnecting() {
        client = client(true);

        assertFalse(client.isRegistered());
        assertFalse(client.isConnected());
    }

    @Test
    public void refusesToSendOnceTheConnectionHasGoneAway() throws Exception {
        client = client(false);
        client.connect();
        server.dropConnection();
        Thread.sleep(200);

        try {
            client.sendCommand(new PrivMsg("#chan", "hi"));
            fail("expected send to be refused on a dead connection");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not connected"));
        }
    }

    @Test
    public void addsTheDebugLoggingHandlerWhenAsked() throws Exception {
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .debug(true)
                .reconnect(false)
                .build());

        client.connect();

        assertTrue(server.awaitLine("NICK bot", TIMEOUT));
        assertTrue("debug should be usable end to end", client.isRegistered());
    }

    @Test
    public void sendIsInterruptible() throws Exception {
        client = client(false);
        client.connect();

        Thread.currentThread().interrupt();
        try {
            client.sendCommand(new PrivMsg("#chan", "hi"));
            fail("expected the interrupt to surface");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Interrupted while sending"));
            assertTrue("the interrupt must be restored for the caller",
                    Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void disconnectCompletesEvenWhenTheServerHasGoneAway() throws Exception {
        client = client(false);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.close();
        Thread.sleep(200);

        // The QUIT cannot land, but that is no reason to fail a disconnect.
        client.disconnect();

        assertFalse(client.isConnected());
    }

    @Test
    public void anInterruptWhileWaitingForRegistrationSurfaces() throws Exception {
        server.withholdWelcome(true);
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(10000)
                .build());

        final Thread connecting = Thread.currentThread();
        Thread interrupter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Long enough that the TCP connect has finished and the wait for
                    // RPL_WELCOME is what gets interrupted.
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                connecting.interrupt();
            }
        });
        interrupter.setDaemon(true);
        interrupter.start();

        try {
            client.connect();
            fail("expected the interrupt to surface");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Interrupted while registering"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void connectIsInterruptible() throws Exception {
        client = client(false);

        Thread.currentThread().interrupt();
        try {
            client.connect();
            fail("expected the interrupt to surface");
        } catch (IRCClientException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Interrupted"));
        } finally {
            Thread.interrupted();
        }
    }

    private void waitForConnections(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (server.getConnectionCount() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    public void waitsLongerAfterTheServerRefusesThanAfterADrop() throws Exception {
        // The backoff starts at 50ms and tops out at 2s, so the two cases are
        // far enough apart to tell one from the other without a tight race.
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(true)
                .reconnectBackoff(50, 2000, 0)
                .registrationTimeout(3000)
                .build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // From here the server turns every connection away, the way one
        // throttling reconnects does.
        server.throttle(true);
        server.dropConnection();

        // The first retry is on the short backoff and gets refused.
        long start = System.currentTimeMillis();
        waitForConnections(2, TIMEOUT);
        assertTrue("expected a prompt retry after an ordinary drop",
                System.currentTimeMillis() - start < 1500);

        // The next one must not be on the short backoff: knocking again
        // immediately is what keeps a throttle alive.
        Thread.sleep(900);
        assertEquals("a refusal should not be retried within a second",
                2, server.getConnectionCount());
    }

    private BasicIRCClient client(boolean reconnect) {
        return new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(reconnect)
                .reconnectBackoff(50, 200, 0)
                .registrationTimeout(3000)
                .build());
    }
}
