package io.github.aindriub.irc.client.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReconnectConfigurationTest {

    @Test
    public void backsOffExponentiallyFromTheInitialDelay() {
        ReconnectConfiguration reconnect = new ReconnectConfiguration();
        reconnect.setInitialDelay(1000);
        reconnect.setMultiplier(2.0);
        reconnect.setMaxDelay(60000);

        assertEquals(1000, reconnect.delayFor(1));
        assertEquals(2000, reconnect.delayFor(2));
        assertEquals(4000, reconnect.delayFor(3));
        assertEquals(8000, reconnect.delayFor(4));
    }

    @Test
    public void settlesAtTheCeilingRatherThanGrowingForever() {
        ReconnectConfiguration reconnect = new ReconnectConfiguration();
        reconnect.setInitialDelay(1000);
        reconnect.setMultiplier(2.0);
        reconnect.setMaxDelay(10000);

        assertEquals(10000, reconnect.delayFor(20));
        assertEquals(10000, reconnect.delayFor(1000));
    }

    @Test
    public void theFirstAttemptIsNeverDelayedLongerThanTheInitialDelay() {
        ReconnectConfiguration reconnect = new ReconnectConfiguration();
        reconnect.setInitialDelay(500);

        assertEquals(500, reconnect.delayFor(1));
        // Guards against an off-by-one that would skip the first, shortest wait.
        assertEquals(500, reconnect.delayFor(0));
    }

    @Test
    public void defaultsAreSensible() {
        ReconnectConfiguration reconnect = new ReconnectConfiguration();

        assertTrue("reconnection should be on by default", reconnect.isEnabled());
        assertEquals("unlimited attempts by default", 0, reconnect.getMaxAttempts());
        assertTrue(reconnect.delayFor(1) <= reconnect.getMaxDelay());
    }
}
