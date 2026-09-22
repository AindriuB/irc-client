package io.github.aindriub.irc.client.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

public class EventTest {

    @Test
    public void timestampsItselfWhenNoneIsGiven() {
        long before = System.currentTimeMillis();

        Event<String> event = new Event<>("payload");

        assertTrue(event.getTimestamp().getTime() >= before);
        assertTrue(event.getTimestamp().getTime() <= System.currentTimeMillis());
    }

    @Test
    public void carriesAnExplicitTimestamp() {
        Date when = new Date(1234567890L);

        assertEquals(when, new Event<>("payload", when).getTimestamp());
    }

    @Test
    public void doesNotShareItsTimestampWithTheCaller() {
        Date when = new Date(1234567890L);
        Event<String> event = new Event<>("payload", when);

        when.setTime(0);
        assertEquals(1234567890L, event.getTimestamp().getTime());

        event.getTimestamp().setTime(0);
        assertEquals("a caller must not be able to mutate it either", 1234567890L,
                event.getTimestamp().getTime());
        assertNotSame(event.getTimestamp(), event.getTimestamp());
    }

    @Test(expected = NullPointerException.class)
    public void rejectsANullPayload() {
        new Event<String>(null);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsANullTimestamp() {
        new Event<>("payload", null);
    }
}
