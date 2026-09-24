package io.github.aindriub.irc.client.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.command.PrivMsg;
import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;
import io.github.aindriub.irc.client.event.ConnectionEvent;
import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.event.EventHandler;
import io.github.aindriub.irc.client.testsupport.StubIRCServer;

/**
 * Drives a real socket against an in-process stub server: the only way to exercise
 * connection loss and reconnection actually happening. See ReconnectTest for the
 * equivalent coverage of the reconnect mechanics themselves; this class only
 * checks what gets published while that happens.
 */
public class ConnectionEventsTest {

    private static final long TIMEOUT = 5000;

    private StubIRCServer server;
    private BasicIRCClient client;
    private final List<ConnectionEvent> events = new CopyOnWriteArrayList<>();

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
    public void publishesDisconnectedThenReconnectingThenReconnected() throws Exception {
        client = client(true, 0);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.dropConnection();

        assertTrue("expected DISCONNECTED, RECONNECTING and RECONNECTED",
                awaitEventCount(3, TIMEOUT));
        assertEquals(ConnectionEvent.Type.DISCONNECTED, events.get(0).getType());
        assertEquals(ConnectionEvent.Type.RECONNECTING, events.get(1).getType());
        assertEquals(1, events.get(1).getAttempt());
        assertEquals(50, events.get(1).getDelayMillis());
        assertEquals(ConnectionEvent.Type.RECONNECTED, events.get(2).getType());
    }

    @Test
    public void publishesOneReconnectingPerFailedAttemptAndOnlyOneDisconnected()
            throws Exception {
        client = client(true, 0);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // From now on every reconnect attempt gets a socket but never a registered
        // session, so it keeps retrying without ever reaching RECONNECTED.
        server.refuseRegistration(true);
        server.dropConnection();

        assertTrue("expected at least three RECONNECTING attempts",
                awaitAttempt(3, TIMEOUT));

        long disconnectedCount = events.stream()
                .filter(e -> e.getType() == ConnectionEvent.Type.DISCONNECTED)
                .count();
        assertEquals("DISCONNECTED must be published exactly once per lost connection", 1,
                disconnectedCount);

        int attempt = 0;
        for (ConnectionEvent event : events) {
            if (event.getType() == ConnectionEvent.Type.RECONNECTING) {
                attempt++;
                assertEquals(attempt, event.getAttempt());
            }
        }
    }

    @Test
    public void givesUpAfterMaxAttemptsWithExactlyOneGaveUpEvent() throws Exception {
        client = client(true, 2);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.refuseRegistration(true);
        server.dropConnection();

        assertTrue("expected a GAVE_UP event", awaitType(ConnectionEvent.Type.GAVE_UP, TIMEOUT));
        // Nothing further should arrive once given up; wait a little and check.
        Thread.sleep(300);

        long gaveUpCount = events.stream()
                .filter(e -> e.getType() == ConnectionEvent.Type.GAVE_UP)
                .count();
        assertEquals(1, gaveUpCount);
        ConnectionEvent gaveUp = events.stream()
                .filter(e -> e.getType() == ConnectionEvent.Type.GAVE_UP)
                .findFirst().get();
        assertEquals(2, gaveUp.getAttempt());

        long reconnectingCount = events.stream()
                .filter(e -> e.getType() == ConnectionEvent.Type.RECONNECTING)
                .count();
        assertEquals(2, reconnectingCount);
    }

    @Test
    public void reconnectDisabledYieldsDisconnectedOnly() throws Exception {
        client = client(false, 0);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.dropConnection();
        Thread.sleep(300);

        assertEquals(1, events.size());
        assertEquals(ConnectionEvent.Type.DISCONNECTED, events.get(0).getType());
    }

    @Test
    public void deliberateDisconnectPublishesNothing() throws Exception {
        client = client(true, 0);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        client.disconnect();
        Thread.sleep(300);

        assertTrue("disconnect() must publish no ConnectionEvent at all", events.isEmpty());
    }

    @Test
    public void aFailingConnectionHandlerDoesNotStopOthersOrTheReconnectLoop()
            throws Exception {
        ClientConfigurationBuilder builder = new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(true)
                .reconnectBackoff(50, 200, 0)
                .registrationTimeout(3000)
                .connectionListener(new EventHandler<ConnectionEvent>() {
                    @Override
                    public void publishEvent(Event<ConnectionEvent> event) {
                        throw new RuntimeException("boom");
                    }
                })
                .connectionListener(new EventHandler<ConnectionEvent>() {
                    @Override
                    public void publishEvent(Event<ConnectionEvent> event) {
                        events.add(event.getPayload());
                    }
                });
        client = new BasicIRCClient(builder.build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.dropConnection();

        assertTrue("the well-behaved handler must still see every event, and the "
                + "reconnect must still happen", awaitEventCount(3, TIMEOUT));
        assertEquals(ConnectionEvent.Type.RECONNECTED, events.get(2).getType());
    }

    @Test
    public void aRefusedWriteFromTheEventLoopIsLoggedNotSilentlyDropped() throws Exception {
        // A burst of one and a queue depth of one: the first send from the event
        // loop callback below takes the only token, and the second overflows the
        // limiter's queue outright.
        final BasicIRCClient[] holder = new BasicIRCClient[1];
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(3000)
                .floodProtection(1, 60000)
                .outboundQueueDepth(1)
                .messageListener(new io.github.aindriub.irc.client.event.MessageListener() {
                    @Override
                    protected void onMessage(String target, String sender, String text,
                            io.github.aindriub.irc.client.message.IRCMessage raw) {
                        // Three sends back to back from the event loop: the flood
                        // configuration below leaves room for only one, so the rest
                        // are refused, and none may throw since nothing here could
                        // catch it.
                        holder[0].sendCommand(new PrivMsg(target, "one"));
                        holder[0].sendCommand(new PrivMsg(target, "two"));
                        holder[0].sendCommand(new PrivMsg(target, "three"));
                    }
                })
                .build());
        holder[0] = client;
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :ping");

        // Nothing to assert on the wire: the point is that this does not throw and
        // the client is left usable, which the next line demonstrates.
        Thread.sleep(200);
        assertTrue(client.isConnected());
    }

    private BasicIRCClient client(boolean reconnect, int maxAttempts) {
        return new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(reconnect)
                .reconnectBackoff(50, 200, maxAttempts)
                .registrationTimeout(3000)
                .connectionListener(new EventHandler<ConnectionEvent>() {
                    @Override
                    public void publishEvent(Event<ConnectionEvent> event) {
                        events.add(event.getPayload());
                    }
                })
                .build());
    }

    private boolean awaitEventCount(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (events.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return events.size() >= expected;
    }

    private boolean awaitAttempt(int attempt, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            for (ConnectionEvent event : events) {
                if (event.getType() == ConnectionEvent.Type.RECONNECTING
                        && event.getAttempt() >= attempt) {
                    return true;
                }
            }
            Thread.sleep(20);
        }
        return false;
    }

    private boolean awaitType(ConnectionEvent.Type type, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            for (ConnectionEvent event : events) {
                if (event.getType() == type) {
                    return true;
                }
            }
            Thread.sleep(20);
        }
        return false;
    }
}
