package io.github.aindriub.irc.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class IRCTextTest {

    @Test
    public void acceptsAnOrdinaryParameter() {
        assertEquals("#channel", IRCText.requireParam("#channel", "channel"));
    }

    @Test
    public void acceptsSpacesInTrailingText() {
        assertEquals("hello there", IRCText.requireText("hello there", "message"));
    }

    @Test
    public void rejectsLineBreaksThatWouldInjectASecondMessage() {
        assertRejected("#chan\r\nQUIT", true);
        assertRejected("#chan\nQUIT", true);
        assertRejected("#chan\rQUIT", true);
        assertRejected("hello\r\nQUIT", false);
        assertRejected("hello\nQUIT", false);
    }

    @Test
    public void rejectsNul() {
        assertRejected("#chan\0", true);
        assertRejected("hello\0", false);
    }

    @Test
    public void rejectsWhitespaceInAParameterThatWouldSplitIt() {
        assertRejected("#chan extra", true);
        assertRejected("#chan\textra", true);
    }

    @Test
    public void rejectsALeadingColonInAParameter() {
        assertRejected(":#chan", true);
    }

    @Test
    public void rejectsNullAndEmptyParameters() {
        assertRejected(null, true);
        assertRejected("", true);
    }

    @Test
    public void reportsTheOffendingValueWithEscapedControlCharacters() {
        try {
            IRCText.requireText("hello\r\nQUIT", "message");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("hello\\r\\nQUIT"));
        }
    }

    private static void assertRejected(String value, boolean asParam) {
        try {
            if (asParam) {
                IRCText.requireParam(value, "value");
            } else {
                IRCText.requireText(value, "value");
            }
            fail("expected IllegalArgumentException for: " + value);
        } catch (IllegalArgumentException expected) {
            // as expected
        }
    }
}
