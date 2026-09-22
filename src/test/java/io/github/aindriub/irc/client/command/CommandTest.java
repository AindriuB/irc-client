package io.github.aindriub.irc.client.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class CommandTest {

    @Test
    public void joinRendersASingleChannel() {
        assertEquals("JOIN #channel", new Join("#channel").render());
    }

    @Test
    public void joinRendersACommaSeparatedChannelList() {
        assertEquals("JOIN #one,#two,#three",
                new Join(Arrays.asList("#one", "#two", "#three")).render());
    }

    @Test
    public void joinRendersChannelKeys() {
        assertEquals("JOIN #channel secret", new Join("#channel", "secret").render());
        assertEquals("JOIN #one,#two key1,key2",
                new Join(Arrays.asList("#one", "#two"), Arrays.asList("key1", "key2")).render());
    }

    @Test(expected = IllegalArgumentException.class)
    public void joinRejectsAKeyListThatDoesNotLineUpWithTheChannels() {
        new Join(Arrays.asList("#one", "#two"), Collections.singletonList("key1"));
    }

    @Test
    public void joinZeroPartsEveryChannel() {
        assertEquals("JOIN 0", Join.partAll().render());
    }

    @Test
    public void partRendersPartAndNotJoin() {
        assertEquals("PART #channel", new Part("#channel").render());
        assertEquals("PART #one,#two", new Part(Arrays.asList("#one", "#two")).render());
    }

    @Test
    public void partRendersAReason() {
        assertEquals("PART #channel :back later", new Part("#channel", "back later").render());
    }

    @Test
    public void nickRendersNickAndNotPart() {
        assertEquals("NICK someone", new Nick("someone").render());
    }

    @Test
    public void whoSeparatesTheMaskWithASpace() {
        assertEquals("WHO someone", new Who("someone").render());
        assertEquals("WHO #channel o", new Who("#channel", true).render());
        assertEquals("WHO #channel", new Who("#channel", false).render());
    }

    @Test
    public void quitRendersWithAndWithoutAReason() {
        assertEquals("QUIT", new Quit().render());
        assertEquals("QUIT :goodbye now", new Quit("goodbye now").render());
    }

    @Test
    public void privMsgRendersATrailingMessage() {
        assertEquals("PRIVMSG #chan :hello there",
                new PrivMsg("#chan", "hello there").render());
    }

    @Test
    public void noticeRendersATrailingMessage() {
        assertEquals("NOTICE nick :automated reply",
                new Notice("nick", "automated reply").render());
    }

    @Test
    public void userRendersTheRegistrationForm() {
        assertEquals("USER bot 0 * :A Bot", new User("bot", "A Bot").render());
    }

    @Test
    public void passRendersThePasswordButNeverLogsIt() {
        Pass pass = new Pass("oauth:secret-token");

        assertEquals("PASS oauth:secret-token", pass.render());
        assertEquals("PASS <redacted>", pass.toString());
        assertFalse("the password must not reach a log line",
                pass.toString().contains("secret-token"));
    }

    @Test
    public void pingAndPongRenderTheirToken() {
        assertEquals("PING :tmi.twitch.tv", new Ping("tmi.twitch.tv").render());
        assertEquals("PONG :tmi.twitch.tv", new Pong("tmi.twitch.tv").render());
    }

    @Test
    public void capNegotiationForms() {
        assertEquals("CAP LS 302", Cap.ls().render());
        assertEquals("CAP REQ :twitch.tv/tags twitch.tv/commands",
                Cap.req(Arrays.asList("twitch.tv/tags", "twitch.tv/commands")).render());
        assertEquals("CAP END", Cap.end().render());
    }

    @Test(expected = IllegalArgumentException.class)
    public void capRejectsAnEmptyRequest() {
        Cap.req(Collections.<String>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void capRejectsANullRequest() {
        Cap.req(null);
    }

    @Test
    public void capRequestsASingleCapabilityWithoutATrailingSpace() {
        assertEquals("CAP REQ :sasl", Cap.req(Collections.singletonList("sasl")).render());
    }

    @Test(expected = IllegalArgumentException.class)
    public void joinRejectsANullChannelList() {
        new Join((java.util.List<String>) null);
    }

    @Test
    public void joinExposesItsChannelsAndKeys() {
        Join keyless = new Join(Arrays.asList("#one", "#two"));
        assertEquals(Arrays.asList("#one", "#two"), keyless.getChannels());
        assertNull("no key means no key", keyless.getKey("#one"));
        assertNull("an unknown channel has no key", keyless.getKey("#nope"));
        assertFalse(keyless.isPartAll());

        Join keyed = new Join(Arrays.asList("#one", "#two"), Arrays.asList("k1", "k2"));
        assertEquals("k1", keyed.getKey("#one"));
        assertEquals("k2", keyed.getKey("#two"));
        assertNull(keyed.getKey("#nope"));
    }

    @Test
    public void partAllIsRecognisableAsSuch() {
        assertTrue(Join.partAll().isPartAll());
        assertFalse(new Join("#zero").isPartAll());
        assertFalse(new Join(Arrays.asList("0", "#other")).isPartAll());
    }

    @Test
    public void partExposesItsChannels() {
        assertEquals(Arrays.asList("#one"), new Part("#one").getChannels());
        assertEquals(Arrays.asList("#one", "#two"),
                new Part(Arrays.asList("#one", "#two"), "bye").getChannels());
    }

    @Test
    public void channelListsAreImmutable() {
        try {
            new Join("#one").getChannels().add("#two");
            throw new AssertionError("expected the channel list to be immutable");
        } catch (UnsupportedOperationException expected) {
            // as expected
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void joinRejectsANullKeyList() {
        new Join(Arrays.asList("#one"), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void partRejectsAnEmptyChannelList() {
        new Part(Collections.<String>emptyList());
    }

    @Test
    public void toStringMatchesRenderForEverythingExceptPass() {
        Command command = new PrivMsg("#chan", "hello");
        assertEquals(command.render(), command.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void joinRejectsAnEmptyChannelList() {
        new Join(Collections.<String>emptyList());
    }

    @Test
    public void commandsRejectInjectedLineBreaks() {
        // A bot echoing chat input into a command is the path that makes this reachable.
        assertRejects(new Runnable() {
            public void run() {
                new Join("#chan\r\nQUIT");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new PrivMsg("#chan", "hi\r\nQUIT");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Notice("#chan", "hi\r\nQUIT");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Quit("bye\r\nJOIN #evil");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Nick("bot\r\nQUIT");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Pass("pw\r\nQUIT");
            }
        });
    }

    private static void assertRejects(Runnable construction) {
        try {
            construction.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected the command to reject an injected line break");
    }

    @Test
    public void everyCommandIsASingleLine() {
        for (Command command : Arrays.asList(new Join("#a"), new Part("#a", "bye"),
                new Nick("n"), new Who("#a"), new Quit("bye"), new PrivMsg("#a", "hi"),
                new Notice("#a", "hi"), new User("u", "Real Name"), new Pass("pw"),
                new Ping("t"), new Pong("t"), Cap.ls(), Cap.end())) {
            String rendered = command.render();
            assertFalse(rendered, rendered.contains("\r"));
            assertFalse(rendered, rendered.contains("\n"));
            assertTrue(rendered, rendered.length() > 0);
        }
    }
}
