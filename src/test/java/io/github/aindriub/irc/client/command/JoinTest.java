package io.github.aindriub.irc.client.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class JoinTest {

    @Test
    public void rendersTheKeysOnTheWire() {
        Join join = new Join(Arrays.asList("#a", "#b"), Arrays.asList("secretkey1", "secretkey2"));

        assertEquals("JOIN #a,#b secretkey1,secretkey2", join.render());
    }

    @Test
    public void redactsTheKeysInToString() {
        Join join = new Join(Arrays.asList("#a", "#b"), Arrays.asList("secretkey1", "secretkey2"));

        String logged = join.toString();

        assertFalse(logged, logged.contains("secretkey1"));
        assertFalse(logged, logged.contains("secretkey2"));
        assertTrue(logged, logged.contains("JOIN #a,#b"));
        assertTrue(logged, logged.contains("2 keys redacted"));
    }

    @Test
    public void toStringIsUnchangedWhenThereAreNoKeys() {
        Join join = new Join("#a");

        assertEquals("JOIN #a", join.toString());
        assertEquals(join.render(), join.toString());
    }
}
