package io.github.aindriub.irc.client.event;

import static org.junit.Assert.assertEquals;

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
}
