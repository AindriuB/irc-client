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
    public void reportsALeadingColonWithoutTheValue() {
        try {
            IRCText.requireParam(":#chan", "value");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("value must not start with ':', which would make it a "
                    + "trailing parameter", e.getMessage());
            assertTrue(e.getMessage(), !e.getMessage().contains("#chan"));
        }
    }

    @Test
    public void reportsWhitespaceByIndexWithoutTheValue() {
        try {
            IRCText.requireParam("#chan extra", "value");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("value must not contain whitespace (at index 5 of 11)", e.getMessage());
            assertTrue(e.getMessage(), !e.getMessage().contains("extra"));
        }
    }

    @Test
    public void reportsCarriageReturnByCodePointAndIndexWithoutTheValue() {
        try {
            IRCText.requireText("hello\rQUIT", "value");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("value must not contain CR, LF or NUL, which would let it inject a "
                    + "separate IRC message (U+000D at index 5)", e.getMessage());
            assertTrue(e.getMessage(), !e.getMessage().contains("QUIT"));
        }
    }

    @Test
    public void reportsLineFeedByCodePointAndIndexWithoutTheValue() {
        try {
            IRCText.requireText("hello\nQUIT", "value");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("value must not contain CR, LF or NUL, which would let it inject a "
                    + "separate IRC message (U+000A at index 5)", e.getMessage());
            assertTrue(e.getMessage(), !e.getMessage().contains("QUIT"));
        }
    }

    @Test
    public void reportsNulByCodePointAndIndexWithoutTheValue() {
        try {
            IRCText.requireText("hello\0QUIT", "value");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("value must not contain CR, LF or NUL, which would let it inject a "
                    + "separate IRC message (U+0000 at index 5)", e.getMessage());
            assertTrue(e.getMessage(), !e.getMessage().contains("QUIT"));
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
