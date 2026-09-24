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
}
