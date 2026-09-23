package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

/**
 * Reading a server's refusal out of a failed TLS handshake.
 *
 * <p>The hex in these tests is the real thing: EFnet answering a connection it
 * had decided was too soon after the last one.
 */
public class PlaintextReplyTest {

    /** "ERROR :Reconnecting too fast, throttled.\r\n" as EFnet sent it. */
    private static final String THROTTLED =
            "4552524f52203a5265636f6e6e656374696e6720746f6f20666173742c207468726f74746c65642e0d0a";

    private static Throwable notSslRecord(String hex) {
        return new IllegalArgumentException("not an SSL/TLS record: " + hex);
    }

    @Test
    public void readsTheServersRefusal() {
        assertEquals("ERROR :Reconnecting too fast, throttled.",
                PlaintextReply.from(notSslRecord(THROTTLED)));
    }

    @Test
    public void looksThroughWrappedCauses() {
        // The exception arrives wrapped by the time anything useful can read it.
        Throwable wrapped = new IOException("connection failed", notSslRecord(THROTTLED));
        assertTrue(PlaintextReply.from(wrapped).contains("throttled"));
    }

    @Test
    public void ignoresFailuresThatAreNotThis() {
        assertNull(PlaintextReply.from(new IOException("connection reset by peer")));
        assertNull(PlaintextReply.from(new IllegalStateException()));
    }

    @Test
    public void ignoresBytesThatAreNotText() {
        // A genuine TLS record from something mismatched, not a message to quote.
        assertNull(PlaintextReply.from(notSslRecord("160301004a0100")));
    }

    @Test
    public void ignoresHexThatIsNotHex() {
        assertNull(PlaintextReply.from(notSslRecord("zzzz")));
        assertNull(PlaintextReply.from(notSslRecord("abc")));
        assertNull(PlaintextReply.from(notSslRecord("")));
    }

    @Test
    public void truncatesSomethingTooLongToBeALine() {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            hex.append("41");
        }
        String reply = PlaintextReply.from(notSslRecord(hex.toString()));
        assertTrue("expected it to stop quoting somewhere: " + reply.length(),
                reply.length() < 400);
        assertTrue(reply.endsWith("..."));
    }
}
