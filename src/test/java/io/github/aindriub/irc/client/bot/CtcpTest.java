package io.github.aindriub.irc.client.bot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CtcpTest {

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
}
