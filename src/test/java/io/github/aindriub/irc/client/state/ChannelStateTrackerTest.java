package io.github.aindriub.irc.client.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.message.IRCMessageParser;

public class ChannelStateTrackerTest {

    private ChannelStateTracker tracker;

    @Before
    public void setUp() {
        tracker = new ChannelStateTracker("bot");
    }

    @Test
    public void buildsAChannelFromItsNamesReply() {
        feed(":server 353 bot = #chan :@alice +bob carol");
        feed(":server 366 bot #chan :End of /NAMES list");

        ChannelState channel = tracker.getChannel("#chan");
        assertEquals(3, channel.size());
        assertEquals(Arrays.asList("alice", "bob", "carol"), channel.getNicks());
        assertTrue(channel.getUser("alice").hasStatus(UserStatus.OPERATOR));
        assertTrue(channel.getUser("bob").hasStatus(UserStatus.VOICE));
        assertTrue(channel.getUser("carol").getStatuses().isEmpty());
    }

    @Test
    public void namesArrivingOverSeveralLinesAccumulate() {
        feed(":server 353 bot = #chan :alice bob");
        feed(":server 353 bot = #chan :carol dave");
        feed(":server 366 bot #chan :End of /NAMES list");

        assertEquals(4, tracker.getChannel("#chan").size());
    }

    @Test
    public void aSecondNamesReplyReplacesTheFirstRatherThanAddingToIt() {
        feed(":server 353 bot = #chan :alice bob");
        feed(":server 366 bot #chan :End");

        // A rejoin after a reconnect: whoever left while we were gone must not linger.
        feed(":server 353 bot = #chan :alice carol");
        feed(":server 366 bot #chan :End");

        ChannelState channel = tracker.getChannel("#chan");
        assertEquals(Arrays.asList("alice", "carol"), channel.getNicks());
        assertFalse("bob left while we were disconnected", channel.contains("bob"));
    }

    @Test
    public void readsSeveralPrefixesOnOneName() {
        feed(":server 353 bot = #chan :@+alice");

        ChannelUser alice = tracker.getChannel("#chan").getUser("alice");
        assertTrue(alice.hasStatus(UserStatus.OPERATOR));
        assertTrue(alice.hasStatus(UserStatus.VOICE));
        assertEquals("the highest wins for display", UserStatus.OPERATOR,
                alice.getHighestStatus());
        assertEquals("@alice", alice.toString());
    }

    @Test
    public void followsJoinsAndParts() {
        feed(":server 353 bot = #chan :alice");
        feed(":dave!u@h JOIN #chan");
        assertTrue(tracker.getChannel("#chan").contains("dave"));

        feed(":dave!u@h PART #chan");
        assertFalse(tracker.getChannel("#chan").contains("dave"));
    }

    @Test
    public void forgetsAChannelTheClientItselfLeaves() {
        feed(":server 353 bot = #chan :alice bot");

        feed(":bot!u@h PART #chan");

        assertNull("we are not in it any more, so there is nothing to track",
                tracker.getChannel("#chan"));
    }

    @Test
    public void forgetsAChannelTheClientIsKickedFrom() {
        feed(":server 353 bot = #chan :alice bot");

        feed(":alice!u@h KICK #chan bot :out you go");

        assertNull(tracker.getChannel("#chan"));
    }

    @Test
    public void removesSomeoneElseWhoIsKicked() {
        feed(":server 353 bot = #chan :alice bot");

        feed(":bot!u@h KICK #chan alice :out you go");

        assertFalse(tracker.getChannel("#chan").contains("alice"));
        assertTrue("we are still here", tracker.getChannel("#chan").contains("bot"));
    }

    @Test
    public void aQuitRemovesTheUserFromEveryChannel() {
        feed(":server 353 bot = #one :alice bot");
        feed(":server 353 bot = #two :alice bot");

        // QUIT names no channel, so every one has to be checked.
        feed(":alice!u@h QUIT :Ping timeout");

        assertFalse(tracker.getChannel("#one").contains("alice"));
        assertFalse(tracker.getChannel("#two").contains("alice"));
    }

    @Test
    public void followsANickChangeAcrossChannelsAndKeepsStatus() {
        feed(":server 353 bot = #one :@alice");
        feed(":server 353 bot = #two :@alice");

        feed(":alice!u@h NICK :alice2");

        assertFalse(tracker.getChannel("#one").contains("alice"));
        assertTrue(tracker.getChannel("#one").contains("alice2"));
        assertTrue("status survives a rename",
                tracker.getChannel("#two").getUser("alice2").isOperator());
    }

    @Test
    public void followsTheClientsOwnNickChange() {
        feed(":bot!u@h NICK :bot2");

        assertEquals("bot2", tracker.getSelfNick());

        // And the new nick is what decides whether a PART is ours.
        feed(":server 353 bot2 = #chan :bot2");
        feed(":bot2!u@h PART #chan");
        assertNull(tracker.getChannel("#chan"));
    }

    @Test
    public void appliesStatusModes() {
        feed(":server 353 bot = #chan :alice bob");

        feed(":bot!u@h MODE #chan +o alice");
        assertTrue(tracker.getChannel("#chan").getUser("alice").isOperator());

        feed(":bot!u@h MODE #chan -o alice");
        assertFalse(tracker.getChannel("#chan").getUser("alice").isOperator());
    }

    @Test
    public void appliesSeveralModesInOneMessage() {
        feed(":server 353 bot = #chan :alice bob");

        feed(":bot!u@h MODE #chan +ov alice bob");

        assertTrue(tracker.getChannel("#chan").getUser("alice").isOperator());
        assertTrue(tracker.getChannel("#chan").getUser("bob").hasStatus(UserStatus.VOICE));
    }

    @Test
    public void handlesAMixOfAddedAndRemovedModes() {
        feed(":server 353 bot = #chan :@alice bob");

        feed(":bot!u@h MODE #chan +v-o bob alice");

        assertTrue(tracker.getChannel("#chan").getUser("bob").hasStatus(UserStatus.VOICE));
        assertFalse(tracker.getChannel("#chan").getUser("alice").isOperator());
    }

    @Test
    public void stopsAtAModeItCannotInterpret() {
        feed(":server 353 bot = #chan :alice");

        // Whether +k takes an argument needs the server's ISUPPORT, so guessing would
        // misalign everything after it. Better to apply nothing than the wrong thing.
        feed(":bot!u@h MODE #chan +ko secret alice");

        assertFalse("alice must not be given op from a misread argument",
                tracker.getChannel("#chan").getUser("alice").isOperator());
    }

    @Test
    public void ignoresUserModesOnTheClientItself() {
        feed(":server 353 bot = #chan :alice");

        feed(":bot!u@h MODE bot +i");

        assertEquals("a user mode is not channel state", 1,
                tracker.getChannel("#chan").size());
    }

    @Test
    public void tracksTheTopic() {
        feed(":server 353 bot = #chan :alice");

        feed(":server 332 bot #chan :the current topic");
        assertEquals("the current topic", tracker.getChannel("#chan").getTopic());

        feed(":alice!u@h TOPIC #chan :a newer topic");
        assertEquals("a newer topic", tracker.getChannel("#chan").getTopic());

        feed(":server 331 bot #chan :No topic is set");
        assertNull(tracker.getChannel("#chan").getTopic());
    }

    @Test
    public void listsOperators() {
        feed(":server 353 bot = #chan :@alice +bob ~carol dave");

        assertEquals(Arrays.asList("alice", "carol"), nicksOf(tracker.getChannel("#chan")
                .getOperators()));
    }

    @Test
    public void treatsNicksCaseInsensitively() {
        // Servers are inconsistent about case between a NAMES reply and later events.
        feed(":server 353 bot = #chan :Alice");

        assertTrue(tracker.getChannel("#chan").contains("alice"));
        feed(":ALICE!u@h PART #chan");
        assertFalse(tracker.getChannel("#chan").contains("Alice"));
    }

    @Test
    public void treatsChannelNamesCaseInsensitively() {
        feed(":server 353 bot = #Chan :alice");

        assertEquals("#Chan", tracker.getChannel("#chan").getName());
    }

    @Test
    public void reportsTheChannelsItKnowsAbout() {
        feed(":server 353 bot = #one :alice");
        feed(":server 353 bot = #two :alice");

        assertEquals(Arrays.asList("#one", "#two"), tracker.getChannelNames());
        assertNull(tracker.getChannel("#nope"));
        assertNull(tracker.getChannel(null));
    }

    @Test
    public void ignoresMalformedOrIrrelevantMessages() {
        feed(":server 353 bot = #chan :alice");

        feed("JOIN #chan");
        feed(":alice!u@h PART");
        feed(":alice!u@h KICK #chan");
        feed(":server 353 bot = #chan");
        feed(":server 332 bot #chan");
        feed(":alice!u@h MODE #nosuchchannel +o alice");
        feed(":alice!u@h TOPIC #nosuchchannel :hi");
        feed(":server 372 bot :message of the day");
        feed("PING :x");

        assertEquals("none of that should have disturbed the channel", 1,
                tracker.getChannel("#chan").size());
        assertNull("a truncated topic reply is not a topic",
                tracker.getChannel("#chan").getTopic());
    }

    @Test
    public void ignoresEventsForChannelsItIsNotIn() {
        feed(":server 353 bot = #chan :alice");

        // A server can legitimately send these for a channel we have just left.
        feed(":server 332 bot #elsewhere :a topic");
        feed(":server 331 bot #elsewhere :No topic is set");
        feed(":alice!u@h PART #elsewhere");
        feed(":alice!u@h KICK #elsewhere bob");
        feed(":alice!u@h MODE #elsewhere +o bob");

        assertNull(tracker.getChannel("#elsewhere"));
        assertEquals(1, tracker.getChannel("#chan").size());
    }

    @Test
    public void ignoresTruncatedMessagesOfEveryKind() {
        feed(":server 353 bot");
        feed(":server 366 bot");
        feed(":server 332 bot");
        feed(":alice!u@h JOIN");
        feed("QUIT :no prefix so no nick");
        feed("NICK :no prefix either");
        feed(":alice!u@h NICK");
        feed(":alice!u@h MODE");
        feed(":alice!u@h MODE #chan");
        feed(":alice!u@h TOPIC");

        assertTrue("nothing should have been created from any of that",
                tracker.getChannelNames().isEmpty());
    }

    @Test
    public void ignoresAModeWithFewerArgumentsThanModes() {
        feed(":server 353 bot = #chan :alice");

        // "+oo" promises two nicks and supplies one.
        feed(":bot!u@h MODE #chan +oo alice");

        assertTrue("the mode that did have an argument still applies",
                tracker.getChannel("#chan").getUser("alice").isOperator());
        assertEquals(1, tracker.getChannel("#chan").size());
    }

    @Test
    public void copesWithNoNickOfItsOwn() {
        ChannelStateTracker anonymous = new ChannelStateTracker(null);
        anonymous.publishEvent(new Event<>(IRCMessageParser.parse(
                ":server 353 nobody = #chan :alice")));

        // With no self nick, nobody's PART can be our own.
        anonymous.publishEvent(new Event<>(IRCMessageParser.parse(":alice!u@h PART #chan")));

        assertNull(anonymous.getSelfNick());
        assertEquals(0, anonymous.getChannel("#chan").size());
    }

    @Test
    public void aUserNotInTheChannelCannotBeGivenStatus() {
        feed(":server 353 bot = #chan :alice");

        feed(":bot!u@h MODE #chan +o stranger");

        assertNull(tracker.getChannel("#chan").getUser("stranger"));
    }

    private static java.util.List<String> nicksOf(java.util.List<ChannelUser> users) {
        java.util.List<String> nicks = new java.util.ArrayList<>();
        for (ChannelUser user : users) {
            nicks.add(user.getNick());
        }
        return nicks;
    }

    private void feed(String line) {
        tracker.publishEvent(new Event<>(IRCMessageParser.parse(line)));
    }
}
