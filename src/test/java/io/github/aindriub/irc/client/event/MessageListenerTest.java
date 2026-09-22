package io.github.aindriub.irc.client.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.message.IRCMessageParser;

public class MessageListenerTest {

    private final List<String> calls = new ArrayList<>();

    private final MessageListener listener = new MessageListener() {
        @Override
        protected void onMessage(String target, String sender, String text, IRCMessage raw) {
            calls.add("message " + target + " " + sender + " " + text);
        }

        @Override
        protected void onNotice(String target, String sender, String text, IRCMessage raw) {
            calls.add("notice " + target + " " + sender + " " + text);
        }

        @Override
        protected void onJoin(String channel, String nick, IRCMessage raw) {
            calls.add("join " + channel + " " + nick);
        }

        @Override
        protected void onPart(String channel, String nick, IRCMessage raw) {
            calls.add("part " + channel + " " + nick);
        }

        @Override
        protected void onQuit(String nick, String reason, IRCMessage raw) {
            calls.add("quit " + nick + " " + reason);
        }

        @Override
        protected void onKick(String channel, String kicked, String by, IRCMessage raw) {
            calls.add("kick " + channel + " " + kicked + " " + by);
        }

        @Override
        protected void onNickChange(String oldNick, String newNick, IRCMessage raw) {
            calls.add("nick " + oldNick + " " + newNick);
        }

        @Override
        protected void onNumeric(String numeric, IRCMessage raw) {
            calls.add("numeric " + numeric);
        }

        @Override
        protected void onOther(IRCMessage raw) {
            calls.add("other " + raw.getCommand());
        }
    };

    @Test
    public void dispatchesAChannelMessage() {
        publish(":nick!user@host PRIVMSG #chan :hello there");

        assertEquals("message #chan nick hello there", only());
    }

    @Test
    public void dispatchesADirectMessage() {
        publish(":nick!user@host PRIVMSG mybot :psst");

        assertEquals("message mybot nick psst", only());
    }

    @Test
    public void dispatchesNoticeSeparatelyFromMessage() {
        publish(":nick!user@host NOTICE #chan :heads up");

        assertEquals("notice #chan nick heads up", only());
    }

    @Test
    public void dispatchesJoinAndPart() {
        publish(":nick!user@host JOIN #chan");
        publish(":nick!user@host PART #chan");

        assertEquals("join #chan nick", calls.get(0));
        assertEquals("part #chan nick", calls.get(1));
    }

    @Test
    public void dispatchesJoinSentAsATrailingParameter() {
        // Some servers send JOIN with the channel as a trailing parameter.
        publish(":nick!user@host JOIN :#chan");

        assertEquals("join #chan nick", only());
    }

    @Test
    public void dispatchesQuitWithItsReason() {
        publish(":nick!user@host QUIT :Ping timeout");

        assertEquals("quit nick Ping timeout", only());
    }

    @Test
    public void dispatchesKick() {
        publish(":op!user@host KICK #chan baduser :behave");

        assertEquals("kick #chan baduser op", only());
    }

    @Test
    public void dispatchesNickChange() {
        publish(":old!user@host NICK :new");

        assertEquals("nick old new", only());
    }

    @Test
    public void dispatchesNumericReplies() {
        publish(":server 001 bot :Welcome");

        assertEquals("numeric 001", only());
    }

    @Test
    public void sendsAnythingElseToOnOther() {
        publish("PING :tmi.twitch.tv");
        publish(":server CAP * ACK :twitch.tv/tags");

        assertEquals("other PING", calls.get(0));
        assertEquals("other CAP", calls.get(1));
    }

    @Test
    public void exposesTheRawMessageIncludingTags() {
        final List<IRCMessage> raws = new ArrayList<>();
        MessageListener capturing = new MessageListener() {
            @Override
            protected void onMessage(String target, String sender, String text, IRCMessage raw) {
                raws.add(raw);
            }
        };
        capturing.publishEvent(new Event<>(IRCMessageParser
                .parse("@mod=1;display-name=Someone :n!u@h PRIVMSG #chan :hi")));

        assertEquals("1", raws.get(0).getTags().get("mod"));
        assertEquals("Someone", raws.get(0).getTags().get("display-name"));
    }

    @Test
    public void unoverriddenCallbacksAreHarmless() {
        // Every default does nothing, so a listener can override only what it wants
        // without the others throwing on a message it never asked about.
        MessageListener bare = new MessageListener() {
        };
        for (String line : new String[] {
                ":n!u@h PRIVMSG #c :hi",
                ":n!u@h NOTICE #c :hi",
                ":n!u@h JOIN #c",
                ":n!u@h PART #c",
                ":n!u@h QUIT :bye",
                ":o!u@h KICK #c n :out",
                ":n!u@h NICK :other",
                ":server 001 bot :Welcome",
                "PING :x" }) {
            bare.publishEvent(new Event<>(IRCMessageParser.parse(line)));
        }
    }

    @Test
    public void identifiesChannelTargets() {
        assertTrue(IRCMessage.isChannel("#chan"));
        assertTrue(IRCMessage.isChannel("&local"));
        assertTrue(IRCMessage.isChannel("!12345chan"));
        assertTrue(IRCMessage.isChannel("+modeless"));
        assertFalse(IRCMessage.isChannel("mybot"));
        assertFalse(IRCMessage.isChannel(""));
        assertFalse(IRCMessage.isChannel(null));
    }

    @Test
    public void reportsNoSenderForAServerMessage() {
        publish(":tmi.twitch.tv PRIVMSG #chan :server said so");

        assertEquals("message #chan null server said so", only());
    }

    @Test
    public void handlesAMessageWithNoPrefixAtAll() {
        publish("PRIVMSG #chan :hi");

        assertEquals("message #chan null hi", only());
        assertNull(IRCMessageParser.parse("PRIVMSG #chan :hi").getNick());
    }

    private void publish(String line) {
        listener.publishEvent(new Event<>(IRCMessageParser.parse(line)));
    }

    private String only() {
        assertEquals("expected exactly one callback, got " + calls, 1, calls.size());
        return calls.get(0);
    }
}
