package io.github.aindriub.irc.client.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class IRCFormattingTest {

    @Test
    public void returnsNullForNull() {
        assertNull(IRCFormatting.strip(null));
    }

    @Test
    public void returnsPlainTextUnchanged() {
        assertEquals("plain !cmd", IRCFormatting.strip("plain !cmd"));
    }

    @Test
    public void doesNotRemoveTheCtcpDelimiter() {
        assertEquals("\u0001ACTION waves\u0001", IRCFormatting.strip("\u0001ACTION waves\u0001"));
    }

    @Test
    public void removesBold() {
        assertEquals("hi", IRCFormatting.strip("\u0002hi\u0002"));
    }

    @Test
    public void removesItalic() {
        assertEquals("hi", IRCFormatting.strip("\u001Dhi\u001D"));
    }

    @Test
    public void removesUnderline() {
        assertEquals("hi", IRCFormatting.strip("\u001Fhi\u001F"));
    }

    @Test
    public void removesStrikethrough() {
        assertEquals("hi", IRCFormatting.strip("\u001Ehi\u001E"));
    }

    @Test
    public void removesMonospace() {
        assertEquals("hi", IRCFormatting.strip("\u0011hi\u0011"));
    }

    @Test
    public void removesReverse() {
        assertEquals("hi", IRCFormatting.strip("\u0016hi\u0016"));
    }

    @Test
    public void removesReset() {
        assertEquals("hi", IRCFormatting.strip("\u000Fhi\u000F"));
    }

    @Test
    public void removesColourWithForegroundAndBackground() {
        assertEquals("hi", IRCFormatting.strip("\u000304,12hi"));
    }

    @Test
    public void removesColourWithForegroundOnly() {
        assertEquals("hi", IRCFormatting.strip("\u00034hi"));
    }

    @Test
    public void leavesABareCommaWhenThereIsNoForegroundDigit() {
        assertEquals(",hi", IRCFormatting.strip("\u0003,hi"));
    }

    @Test
    public void leavesACommaThatIsNotFollowedByABackgroundDigit() {
        assertEquals(",x", IRCFormatting.strip("\u000304,x"));
    }

    @Test
    public void takesAtMostTwoForegroundDigits() {
        assertEquals("3", IRCFormatting.strip("\u0003123"));
    }

    @Test
    public void removesHexColourWithForegroundAndBackground() {
        assertEquals("hi", IRCFormatting.strip("\u0004FF0000,00FF00hi"));
    }

    @Test
    public void removesHexColourWithForegroundOnly() {
        assertEquals("hi", IRCFormatting.strip("\u0004FF0000hi"));
    }

    @Test
    public void takesAtMostSixHexDigits() {
        assertEquals("7hi", IRCFormatting.strip("\u0004FF00007hi"));
    }

    @Test
    public void leavesABareCommaForHexColourWhenThereIsNoBackgroundDigit() {
        assertEquals(",hi", IRCFormatting.strip("\u0004,hi"));
    }

    @Test
    public void handlesColourCodeAtEndOfString() {
        assertEquals("", IRCFormatting.strip("\u0003"));
        assertEquals("", IRCFormatting.strip("\u000304"));
    }

    @Test
    public void handlesMultipleCodesInOneString() {
        assertEquals("bold and colour", IRCFormatting.strip("\u0002bold\u0002 and \u00034colour\u000F"));
    }
}
