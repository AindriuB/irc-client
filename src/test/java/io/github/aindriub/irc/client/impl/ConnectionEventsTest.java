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
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

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
    private final List<ILoggingEvent> logged = new CopyOnWriteArrayList<>();
    private final AppenderBase<ILoggingEvent> logAppender = new AppenderBase<ILoggingEvent>() {
        @Override
        protected void append(ILoggingEvent event) {
            logged.add(event);
        }
    };
    private Logger clientLogger;

    @Before
    public void setUp() throws IOException {
        server = new StubIRCServer();
        clientLogger = (Logger) LoggerFactory.getLogger(AbstractClient.class);
        logAppender.start();
        clientLogger.addAppender(logAppender);
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
        clientLogger.detachAppender(logAppender);
        logAppender.stop();
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
        // Nothing further should arrive once given up: the reconnect chain has
        // already stopped scheduling by the time GAVE_UP was observed, so
        // disconnect() (which blocks until the connection-event thread has
        // drained, see AbstractClient.disconnect()) is a deterministic point past
        // which no more events could have snuck in, without an arbitrary sleep.
        client.disconnect();

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
        assertTrue("expected DISCONNECTED", awaitEventCount(1, TIMEOUT));
        // Reconnection is disabled, so nothing further is ever scheduled; calling
        // disconnect() here is a deterministic marker (it blocks until the
        // connection-event thread has drained, see AbstractClient.disconnect())
        // rather than an arbitrary sleep hoping nothing else turns up.
        client.disconnect();

        assertEquals(1, events.size());
        assertEquals(ConnectionEvent.Type.DISCONNECTED, events.get(0).getType());
    }

    @Test
    public void deliberateDisconnectPublishesNothing() throws Exception {
        client = client(true, 0);
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // disconnect() blocks until the connection-event thread has drained (see
        // AbstractClient.disconnect()), so nothing more can arrive once it
        // returns; no sleep needed to prove a negative.
        client.disconnect();

        assertTrue("disconnect() must publish no ConnectionEvent at all", events.isEmpty());
    }

    @Test
    public void aHandlerThatDisconnectsFromDisconnectedReturnsPromptlyAndPublishesNothingMore()
            throws Exception {
        // disconnect() called from inside a handler runs on the very thread that
        // is trying to shut down and drain, so it must recognise that (rather
        // than block on itself for up to the 5s drain cap) and return quickly.
        final long[] disconnectDurationMillis = {-1};
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(false)
                .registrationTimeout(3000)
                .connectionListener(new EventHandler<ConnectionEvent>() {
                    @Override
                    public void publishEvent(Event<ConnectionEvent> event) {
                        events.add(event.getPayload());
                        if (event.getPayload().getType() == ConnectionEvent.Type.DISCONNECTED) {
                            long start = System.nanoTime();
                            client.disconnect();
                            disconnectDurationMillis[0] =
                                    (System.nanoTime() - start) / 1_000_000;
                        }
                    }
                })
                .build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.dropConnection();

        assertTrue("expected DISCONNECTED", awaitEventCount(1, TIMEOUT));
        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (disconnectDurationMillis[0] < 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("disconnect() from within the DISCONNECTED handler must not hang",
                disconnectDurationMillis[0] >= 0);
        assertTrue("expected it to return well under the 5s drain cap, took "
                + disconnectDurationMillis[0] + "ms", disconnectDurationMillis[0] < 1000);
        assertEquals("nothing must be published once that disconnect() has run", 1,
                events.size());
        for (ILoggingEvent entry : logged) {
            assertFalse("the handler calling disconnect() on itself must not be reported "
                    + "as a failed handler: " + entry.getFormattedMessage(),
                    entry.getFormattedMessage().contains("Connection handler"));
        }
    }

    @Test
    public void aHandlerThatDisconnectsFromGaveUpReturnsPromptlyAndPublishesNothingMore()
            throws Exception {
        final long[] disconnectDurationMillis = {-1};
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(true)
                .reconnectBackoff(10, 10, 1)
                .registrationTimeout(1000)
                .connectionListener(new EventHandler<ConnectionEvent>() {
                    @Override
                    public void publishEvent(Event<ConnectionEvent> event) {
                        events.add(event.getPayload());
                        if (event.getPayload().getType() == ConnectionEvent.Type.GAVE_UP) {
                            long start = System.nanoTime();
                            client.disconnect();
                            disconnectDurationMillis[0] =
                                    (System.nanoTime() - start) / 1_000_000;
                        }
                    }
                })
                .build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.close();

        assertTrue("expected a GAVE_UP event", awaitType(ConnectionEvent.Type.GAVE_UP, TIMEOUT));
        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (disconnectDurationMillis[0] < 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("disconnect() from within the GAVE_UP handler must not hang",
                disconnectDurationMillis[0] >= 0);
        assertTrue("expected it to return well under the 5s drain cap, took "
                + disconnectDurationMillis[0] + "ms", disconnectDurationMillis[0] < 1000);
        int gaveUpCount = 0;
        for (ConnectionEvent event : events) {
            if (event.getType() == ConnectionEvent.Type.GAVE_UP) {
                gaveUpCount++;
            }
        }
        assertEquals("nothing must be published once that disconnect() has run", 1, gaveUpCount);
        for (ILoggingEvent entry : logged) {
            assertFalse("the handler calling disconnect() on itself must not be reported "
                    + "as a failed handler: " + entry.getFormattedMessage(),
                    entry.getFormattedMessage().contains("Connection handler"));
        }
    }

    @Test
    public void reconnectAttemptsArriveStrictlyOrderedAndNeverOverlap() throws Exception {
        // No backoff at all: with a naive implementation, attempt 2 could be
        // scheduled and published before attempt 1's own publish has returned.
        // A small sleep inside the handler below widens that race window so a
        // regression would actually be caught rather than get lucky.
        final java.util.concurrent.atomic.AtomicBoolean handlerRunning =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean overlapDetected =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        client = new BasicIRCClient(new ClientConfigurationBuilder()
                .host("127.0.0.1")
                .port(server.getPort())
                .secure(false)
                .nick("bot")
                .reconnect(true)
                .reconnectBackoff(0, 0, 0)
                .registrationTimeout(1000)
                .connectionListener(new EventHandler<ConnectionEvent>() {
                    @Override
                    public void publishEvent(Event<ConnectionEvent> event) {
                        if (handlerRunning.getAndSet(true)) {
                            overlapDetected.set(true);
                        }
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        events.add(event.getPayload());
                        handlerRunning.set(false);
                    }
                })
                .build());
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        // Close the port outright, rather than just dropping the connection, so
        // every reconnect attempt after this one is refused at the TCP level.
        server.close();

        assertTrue("expected several RECONNECTING attempts against the closed port",
                awaitAttempt(5, TIMEOUT));

        int lastAttempt = 0;
        for (ConnectionEvent event : events) {
            if (event.getType() == ConnectionEvent.Type.RECONNECTING) {
                assertTrue("attempt numbers must arrive strictly increasing: saw "
                        + event.getAttempt() + " after " + lastAttempt,
                        event.getAttempt() > lastAttempt);
                lastAttempt = event.getAttempt();
            }
        }
        assertFalse("the connection handler must never be invoked concurrently with itself",
                overlapDetected.get());
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
        // loop callback below takes the only token, and every send after it
        // overflows the limiter's queue outright, refusing it.
        final int refusedSends = 100;
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
                        // One send takes the only token; the rest, back to back from
                        // the event loop, are all refused, and none may throw since
                        // nothing here could catch it.
                        for (int i = 0; i < 1 + refusedSends; i++) {
                            holder[0].sendCommand(new PrivMsg(target, "msg" + i));
                        }
                    }
                })
                .build());
        holder[0] = client;
        client.connect();
        assertTrue(server.awaitLine("NICK bot", TIMEOUT));

        server.push(":someone!u@h PRIVMSG #chan :ping");

        assertTrue("expected the refused writes to be logged, not silently dropped",
                awaitLogCount(1, TIMEOUT));
        // A burst of refusals must not turn into a burst of WARN lines: exactly one
        // report, not one per refusal.
        assertEquals("100 refusals must give one WARN with a count, not one per refusal", 1,
                warnLogs().size());
        ILoggingEvent report = warnLogs().get(0);
        assertEquals(Level.WARN, report.getLevel());
        String rendered = report.getFormattedMessage();
        assertFalse("must never log the payload", rendered.contains("msg"));

        assertTrue("the client must still be usable afterwards", client.isConnected());
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

    private List<ILoggingEvent> warnLogs() {
        List<ILoggingEvent> warnings = new CopyOnWriteArrayList<>();
        for (ILoggingEvent event : logged) {
            if (event.getLevel() == Level.WARN) {
                warnings.add(event);
            }
        }
        return warnings;
    }

    private boolean awaitLogCount(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (warnLogs().size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return warnLogs().size() >= expected;
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
