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
import io.github.aindriub.irc.client.event.MessageListener;
import io.github.aindriub.irc.client.message.IRCMessage;

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
