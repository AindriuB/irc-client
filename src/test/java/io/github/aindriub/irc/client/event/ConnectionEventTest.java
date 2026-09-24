package io.github.aindriub.irc.client.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ConnectionEventTest {

    @Test
    public void disconnectedHasNoAttemptOrDelay() {
        ConnectionEvent event = ConnectionEvent.disconnected();

        assertEquals(ConnectionEvent.Type.DISCONNECTED, event.getType());
        assertEquals(0, event.getAttempt());
        assertEquals(0, event.getDelayMillis());
    }

    @Test
    public void reconnectingCarriesTheAttemptAndDelay() {
        ConnectionEvent event = ConnectionEvent.reconnecting(3, 4000);

        assertEquals(ConnectionEvent.Type.RECONNECTING, event.getType());
        assertEquals(3, event.getAttempt());
        assertEquals(4000, event.getDelayMillis());
    }

    @Test
    public void reconnectedHasNoAttemptOrDelay() {
        ConnectionEvent event = ConnectionEvent.reconnected();

        assertEquals(ConnectionEvent.Type.RECONNECTED, event.getType());
        assertEquals(0, event.getAttempt());
        assertEquals(0, event.getDelayMillis());
    }

    @Test
    public void gaveUpCarriesTheAttemptCountAndNoDelay() {
        ConnectionEvent event = ConnectionEvent.gaveUp(5);

        assertEquals(ConnectionEvent.Type.GAVE_UP, event.getType());
        assertEquals(5, event.getAttempt());
        assertEquals(0, event.getDelayMillis());
    }

    @Test
    public void reconnectingRejectsANegativeAttempt() {
        try {
            ConnectionEvent.reconnecting(-1, 100);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("attempt"));
        }
    }

    @Test
    public void reconnectingRejectsANegativeDelay() {
        try {
            ConnectionEvent.reconnecting(1, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("delayMillis"));
        }
    }

    @Test
    public void gaveUpRejectsANegativeAttemptCount() {
        try {
            ConnectionEvent.gaveUp(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("attempt"));
        }
    }

    @Test
    public void toStringIncludesTypeAttemptAndDelay() {
        String rendered = ConnectionEvent.reconnecting(3, 4000).toString();

        assertTrue(rendered, rendered.contains("RECONNECTING"));
        assertTrue(rendered, rendered.contains("3"));
        assertTrue(rendered, rendered.contains("4000"));
    }
}
