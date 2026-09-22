package io.github.aindriub.irc.client.command;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class Tier2CommandTest {

    @Test
    public void modeQueriesWhenGivenNoModes() {
        assertEquals("MODE #chan", new Mode("#chan").render());
    }

    @Test
    public void modeSetsChannelAndUserModes() {
        assertEquals("MODE #chan +o someone", new Mode("#chan", "+o", "someone").render());
        assertEquals("MODE #chan +mi", new Mode("#chan", "+mi").render());
        assertEquals("MODE mynick +i", new Mode("mynick", "+i").render());
    }

    @Test
    public void modeTakesSeveralArguments() {
        assertEquals("MODE #chan +oo one two",
                new Mode("#chan", "+oo", Arrays.asList("one", "two")).render());
        assertEquals("MODE #chan +k secret",
                new Mode("#chan", "+k", Collections.singletonList("secret")).render());
    }

    @Test
    public void modeToleratesAnEmptyArgumentList() {
        assertEquals("MODE #chan +m",
                new Mode("#chan", "+m", Collections.<String>emptyList()).render());
        assertEquals("MODE #chan +m", new Mode("#chan", "+m", (java.util.List<String>) null)
                .render());
    }

    @Test
    public void topicQueriesAndSets() {
        assertEquals("TOPIC #chan", new Topic("#chan").render());
        assertEquals("TOPIC #chan :the new topic",
                new Topic("#chan", "the new topic").render());
        assertEquals("an empty topic clears it", "TOPIC #chan :",
                new Topic("#chan", "").render());
    }

    @Test
    public void kickWithAndWithoutAReason() {
        assertEquals("KICK #chan someone", new Kick("#chan", "someone").render());
        assertEquals("KICK #chan someone :behave",
                new Kick("#chan", "someone", "behave").render());
    }

    @Test
    public void inviteTakesTheNickBeforeTheChannel() {
        assertEquals("INVITE someone #chan", new Invite("someone", "#chan").render());
    }

    @Test
    public void namesForOneOrManyChannels() {
        assertEquals("NAMES #chan", new Names("#chan").render());
        assertEquals("NAMES #one,#two", new Names(Arrays.asList("#one", "#two")).render());
    }

    @Test
    public void awayAndBack() {
        assertEquals("AWAY :making tea", new Away("making tea").render());
        assertEquals("AWAY", Away.back().render());
    }

    @Test
    public void whoisAndWhowas() {
        assertEquals("WHOIS someone", new Whois("someone").render());
        assertEquals("WHOWAS someone", new Whowas("someone").render());
        assertEquals("WHOWAS someone 5", new Whowas("someone", 5).render());
    }

    @Test(expected = IllegalArgumentException.class)
    public void whowasCountMustBePositive() {
        new Whowas("someone", 0);
    }

    @Test
    public void isonTakesSeveralNicksSeparatedBySpaces() {
        assertEquals("ISON one two three", new Ison("one", "two", "three").render());
        assertEquals("ISON one", new Ison(Collections.singletonList("one")).render());
    }

    @Test(expected = IllegalArgumentException.class)
    public void isonNeedsAtLeastOneNick() {
        new Ison(Collections.<String>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void isonRejectsANullNickList() {
        new Ison((java.util.List<String>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void userhostRejectsANullNickList() {
        new Userhost((java.util.List<String>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void namesRejectsAnEmptyChannelList() {
        new Names(Collections.<String>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void listRejectsAnEmptyChannelList() {
        ListChannels.of(Collections.<String>emptyList());
    }

    @Test
    public void userhostTakesUpToFiveNicks() {
        assertEquals("USERHOST a b c d e", new Userhost("a", "b", "c", "d", "e").render());
    }

    @Test(expected = IllegalArgumentException.class)
    public void userhostRefusesMoreThanFiveNicks() {
        // RFC 2812 section 3.6.4; a sixth would be silently ignored by the server.
        new Userhost("a", "b", "c", "d", "e", "f");
    }

    @Test
    public void listAllOrSpecificChannels() {
        assertEquals("LIST", ListChannels.all().render());
        assertEquals("LIST #chan", ListChannels.of("#chan").render());
        assertEquals("LIST #one,#two",
                ListChannels.of(Arrays.asList("#one", "#two")).render());
    }

    @Test
    public void everyTier2CommandRejectsInjectedLineBreaks() {
        assertRejects(new Runnable() {
            public void run() {
                new Topic("#chan", "hi\r\nQUIT");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Kick("#chan", "nick", "bye\r\nJOIN #evil");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Mode("#chan", "+o", "nick\r\nQUIT");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Away("back soon\r\nQUIT");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Invite("nick\r\nQUIT", "#chan");
            }
        });
        assertRejects(new Runnable() {
            public void run() {
                new Ison("one\r\nQUIT");
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
}
