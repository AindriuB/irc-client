package io.github.aindriub.irc.client.bot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CtcpTest {

    /**
     * Every CTCP input used across this test class, kept in one place so the
     * round-trip invariant test below exercises them all.
     */
    private static final String[] CTCP_INPUTS = {
        "\u0001ACTION waves\u0001",
        "\u0001version\u0001",
        "\u0001PING 123",
        "\u0001PING a\u0001VERSION\u0001",
        "\u0001PING \u0001",
        "\u0001PING ",
    };

    @Test
    public void actionWithArgumentIsCtcp() {
        String text = "\u0001ACTION waves\u0001";
        assertTrue(Ctcp.isCtcp(text));
        assertEquals("ACTION", Ctcp.command(text));
        assertEquals("waves", Ctcp.argument(text));
    }

    @Test
    public void lowerCaseCommandIsUpperCased() {
        String text = "\u0001version\u0001";
        assertTrue(Ctcp.isCtcp(text));
        assertEquals("VERSION", Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void missingClosingDelimiterIsTolerated() {
        String text = "\u0001PING 123";
        assertTrue(Ctcp.isCtcp(text));
        assertEquals("PING", Ctcp.command(text));
        assertEquals("123", Ctcp.argument(text));
    }

    @Test
    public void doubleDelimiterOnlyIsNotCtcp() {
        String text = "\u0001\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void singleDelimiterIsNotCtcp() {
        String text = "\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void plainTextIsNotCtcp() {
        String text = "hello";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void emptyStringIsNotCtcp() {
        String text = "";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void nullIsNotCtcp() {
        assertFalse(Ctcp.isCtcp(null));
        assertNull(Ctcp.command(null));
        assertNull(Ctcp.argument(null));
    }

    @Test
    public void buildsPayloadWithArgument() {
        assertEquals("\u0001ACTION waves\u0001", Ctcp.build("ACTION", "waves"));
    }

    @Test
    public void buildsPayloadWithoutArgument() {
        assertEquals("\u0001VERSION\u0001", Ctcp.build("VERSION", null));
    }

    @Test
    public void argumentStopsAtFirstEmbeddedDelimiter() {
        String text = "\u0001PING a\u0001VERSION\u0001";
        assertEquals("PING", Ctcp.command(text));
        assertEquals("a", Ctcp.argument(text));
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsNullCommand() {
        Ctcp.build(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsEmptyCommand() {
        Ctcp.build("", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsCommandWithSpace() {
        Ctcp.build("BAD COMMAND", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsCommandWithDelimiter() {
        Ctcp.build("ACTION\u0001VERSION", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsCommandWithCr() {
        Ctcp.build("ACTION\r", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsCommandWithLf() {
        Ctcp.build("ACTION\n", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsCommandWithNul() {
        Ctcp.build("ACTION\u0000", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsArgumentWithEmbeddedDelimiter() {
        Ctcp.build("ACTION", "hi\u0001VERSION");
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsArgumentWithCr() {
        Ctcp.build("ACTION", "hi\r");
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsArgumentWithLf() {
        Ctcp.build("ACTION", "hi\n");
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRejectsArgumentWithNul() {
        Ctcp.build("ACTION", "hi\u0000");
    }

    @Test
    public void buildAllowsArgumentWithSpace() {
        assertEquals("\u0001ACTION waves at you\u0001", Ctcp.build("ACTION", "waves at you"));
    }

    @Test
    public void emptyCommandFollowedByArgumentIsNotCtcp() {
        String text = "\u0001 x\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void emptyCommandWithOnlySpaceIsNotCtcp() {
        String text = "\u0001 \u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void argumentIsNullNotEmptyWhenNothingFollowsCommandBeforeDelimiter() {
        String text = "\u0001PING \u0001";
        assertTrue(Ctcp.isCtcp(text));
        assertEquals("PING", Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void argumentIsNullNotEmptyWhenNothingFollowsCommandWithoutDelimiter() {
        String text = "\u0001PING ";
        assertTrue(Ctcp.isCtcp(text));
        assertEquals("PING", Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void commandWithNulIsNotCtcp() {
        String text = "\u0001PI\u0000NG\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void commandWithCrIsNotCtcp() {
        String text = "\u0001PI\rNG\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void commandWithLfIsNotCtcp() {
        String text = "\u0001PI\nNG\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void argumentWithNulIsNotCtcp() {
        String text = "\u0001PING a\u0000b\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void argumentWithCrIsNotCtcp() {
        String text = "\u0001PING a\rb\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void argumentWithLfIsNotCtcp() {
        String text = "\u0001PING a\nb\u0001";
        assertFalse(Ctcp.isCtcp(text));
        assertNull(Ctcp.command(text));
        assertNull(Ctcp.argument(text));
    }

    @Test
    public void roundTripNeverThrowsForKnownCtcpInputs() {
        for (String text : CTCP_INPUTS) {
            assertRoundTripInvariant(text);
        }
    }

    @Test
    public void roundTripNeverThrowsForCommandWithNul() {
        assertRoundTripInvariant("\u0001PI\u0000NG\u0001");
    }

    @Test
    public void roundTripNeverThrowsForArgumentWithNul() {
        assertRoundTripInvariant("\u0001PING a\u0000b\u0001");
    }

    @Test
    public void roundTripNeverThrowsForMissingArgument() {
        assertRoundTripInvariant("\u0001PING \u0001");
        assertRoundTripInvariant("\u0001PING ");
    }

    /**
     * Asserts the invariant task 03 exists to guarantee: if {@code text} is
     * CTCP, building from its parsed command and argument must never throw,
     * because task 05 echoes stranger-controlled CTCP arguments straight
     * through {@link Ctcp#build}.
     */
    private static void assertRoundTripInvariant(String text) {
        if (!Ctcp.isCtcp(text)) {
            return;
        }
        Ctcp.build(Ctcp.command(text), Ctcp.argument(text));
    }
}
