package io.github.aindriub.irc.client.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class IRCMessageParserTest {

    @Test
    public void parsesACommandWithNoPrefix() {
        IRCMessage message = IRCMessageParser.parse("PING :tmi.twitch.tv");

        assertNull(message.getPrefix());
        assertEquals("PING", message.getCommand());
        assertEquals(Collections.singletonList("tmi.twitch.tv"), message.getParams());
        assertEquals("tmi.twitch.tv", message.getTrailing());
    }

    @Test
    public void parsesAPrivmsgFromAUser() {
        IRCMessage message = IRCMessageParser
                .parse(":nick!user@host.example PRIVMSG #chan :hello there, world");

        assertEquals("nick!user@host.example", message.getPrefix());
        assertEquals("nick", message.getNick());
        assertEquals("PRIVMSG", message.getCommand());
        assertEquals("#chan", message.getParam(0));
        assertEquals("hello there, world", message.getTrailing());
    }

    @Test
    public void keepsSpacesAndColonsInsideTheTrailingParameter() {
        IRCMessage message = IRCMessageParser.parse("PRIVMSG #chan :look: a b c");

        assertEquals("look: a b c", message.getTrailing());
        assertEquals(Arrays.asList("#chan", "look: a b c"), message.getParams());
    }

    @Test
    public void parsesAnEmptyTrailingParameter() {
        IRCMessage message = IRCMessageParser.parse("PRIVMSG #chan :");

        assertEquals("", message.getTrailing());
        assertEquals(2, message.getParams().size());
    }

    @Test
    public void parsesMiddleParametersWithNoTrailing() {
        IRCMessage message = IRCMessageParser.parse(":server 353 bot = #chan");

        assertEquals("353", message.getCommand());
        assertEquals(Arrays.asList("bot", "=", "#chan"), message.getParams());
    }

    @Test
    public void recognisesNumericReplies() {
        assertTrue(IRCMessageParser.parse(":server 001 bot :Welcome").isNumeric());
        assertFalse(IRCMessageParser.parse("PING :x").isNumeric());
        assertFalse(IRCMessageParser.parse(":server ABC bot").isNumeric());
    }

    @Test
    public void uppercasesTheCommandSinceTheWireIsCaseInsensitive() {
        assertEquals("PRIVMSG", IRCMessageParser.parse(":n!u@h privmsg #c :hi").getCommand());
    }

    @Test
    public void returnsNoNickForAServerPrefix() {
        assertNull(IRCMessageParser.parse(":tmi.twitch.tv 001 bot :Welcome").getNick());
    }

    @Test
    public void parsesIrcV3Tags() {
        IRCMessage message = IRCMessageParser.parse(
                "@badge-info=;color=#1E90FF;display-name=Someone;mod=1 "
                        + ":someone!someone@someone.tmi.twitch.tv PRIVMSG #chan :hi");

        assertEquals("#1E90FF", message.getTags().get("color"));
        assertEquals("Someone", message.getTags().get("display-name"));
        assertEquals("1", message.getTags().get("mod"));
        assertEquals("", message.getTags().get("badge-info"));
        assertEquals("PRIVMSG", message.getCommand());
        assertEquals("hi", message.getTrailing());
    }

    @Test
    public void treatsAValuelessTagAsPresentAndEmpty() {
        assertEquals("", IRCMessageParser.parse("@flag PING :x").getTags().get("flag"));
    }

    @Test
    public void unescapesTagValues() {
        IRCMessage message = IRCMessageParser
                .parse("@msg=a\\sb\\:c\\\\d\\nend PING :x");

        assertEquals("a b;c\\d\nend", message.getTags().get("msg"));
    }

    @Test
    public void toleratesTrailingLineBreaksAndRepeatedSpaces() {
        IRCMessage message = IRCMessageParser.parse(":server  001   bot  :Welcome\r\n");

        assertEquals("001", message.getCommand());
        assertEquals("bot", message.getParam(0));
        assertEquals("Welcome", message.getTrailing());
    }

    @Test
    public void parsesABareCommand() {
        IRCMessage message = IRCMessageParser.parse("QUIT");

        assertEquals("QUIT", message.getCommand());
        assertTrue(message.getParams().isEmpty());
        assertNull(message.getTrailing());
    }

    @Test
    public void paramsAndTagsAreImmutable() {
        IRCMessage message = IRCMessageParser.parse("@a=b PRIVMSG #c :hi");
        try {
            message.getParams().add("nope");
            throw new AssertionError("params should be immutable");
        } catch (UnsupportedOperationException expected) {
            // as expected
        }
        try {
            message.getTags().put("nope", "nope");
            throw new AssertionError("tags should be immutable");
        } catch (UnsupportedOperationException expected) {
            // as expected
        }
    }

    @Test
    public void getParamIsBoundsSafe() {
        IRCMessage message = IRCMessageParser.parse("PING :x");

        assertNull(message.getParam(9));
        assertNull(message.getParam(-1));
    }

    @Test(expected = IRCParseException.class)
    public void rejectsAPrefixWithNoCommand() {
        IRCMessageParser.parse(":server");
    }

    @Test(expected = IRCParseException.class)
    public void rejectsAnEmptyLine() {
        IRCMessageParser.parse("");
    }

    @Test(expected = IRCParseException.class)
    public void rejectsNull() {
        IRCMessageParser.parse(null);
    }
}
