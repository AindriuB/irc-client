package io.github.aindriub.irc.client.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import org.junit.Test;

import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.message.IRCMessageParser;

public class ChannelStateTest {

    @Test
    public void statusesMapBothWays() {
        for (UserStatus status : UserStatus.values()) {
            assertEquals(status, UserStatus.fromPrefix(status.getPrefix()));
            assertEquals(status, UserStatus.fromMode(status.getMode()));
        }
        assertNull("an ordinary nick character is not a prefix", UserStatus.fromPrefix('x'));
        assertNull("not every mode letter is a status", UserStatus.fromMode('k'));
    }

    @Test
    public void statusesAreOrderedByPrivilege() {
        assertTrue(UserStatus.OWNER.ordinal() < UserStatus.OPERATOR.ordinal());
        assertTrue(UserStatus.OPERATOR.ordinal() < UserStatus.VOICE.ordinal());
    }

    @Test
    public void aUserWithNoStatusHasNoHighestStatus() {
        ChannelUser user = new ChannelUser("someone", Collections.<UserStatus>emptySet());

        assertNull(user.getHighestStatus());
        assertFalse(user.isOperator());
        assertEquals("someone", user.toString());
    }

    @Test
    public void halfOperatorAndVoiceDoNotCountAsOperator() {
        assertFalse(user("someone", UserStatus.HALF_OPERATOR).isOperator());
        assertFalse(user("someone", UserStatus.VOICE).isOperator());
        assertTrue(user("someone", UserStatus.OPERATOR).isOperator());
        assertTrue(user("someone", UserStatus.ADMIN).isOperator());
        assertTrue(user("someone", UserStatus.OWNER).isOperator());
    }

    @Test
    public void usersCompareByNickCaseInsensitivelyAndByStatus() {
        assertEquals(user("someone", UserStatus.VOICE), user("SOMEONE", UserStatus.VOICE));
        assertEquals(user("someone", UserStatus.VOICE).hashCode(),
                user("SOMEONE", UserStatus.VOICE).hashCode());
        assertNotEquals(user("someone", UserStatus.VOICE),
                user("someone", UserStatus.OPERATOR));
        assertNotEquals(user("someone", UserStatus.VOICE), user("other", UserStatus.VOICE));
        assertNotEquals(user("someone", UserStatus.VOICE), "not a user");
        assertEquals(user("someone", UserStatus.VOICE), user("someone", UserStatus.VOICE));
    }

    @Test
    public void aUsersStatusSetIsImmutable() {
        ChannelUser user = user("someone", UserStatus.VOICE);
        try {
            user.getStatuses().add(UserStatus.OPERATOR);
            throw new AssertionError("expected the status set to be immutable");
        } catch (UnsupportedOperationException expected) {
            // as expected
        }
    }

    @Test
    public void theUserAndChannelListsAreImmutable() {
        ChannelStateTracker tracker = tracker(":server 353 bot = #chan :@alice bob");
        ChannelState channel = tracker.getChannel("#chan");

        assertEquals(Arrays.asList("alice", "bob"), channel.getNicks());
        assertEquals(2, channel.getUsers().size());
        try {
            channel.getUsers().add(user("intruder", UserStatus.OPERATOR));
            throw new AssertionError("expected the user list to be immutable");
        } catch (UnsupportedOperationException expected) {
            // as expected
        }
        try {
            tracker.getChannelNames().add("#nope");
            throw new AssertionError("expected the channel list to be immutable");
        } catch (UnsupportedOperationException expected) {
            // as expected
        }
    }

    @Test
    public void describesItself() {
        ChannelState channel = tracker(":server 353 bot = #chan :alice bob")
                .getChannel("#chan");

        assertEquals("#chan (2 users)", channel.toString());
        assertEquals("@alice", user("alice", UserStatus.OPERATOR).toString());
    }

    @Test
    public void reportsNothingForAnUnknownNick() {
        ChannelState channel = tracker(":server 353 bot = #chan :alice").getChannel("#chan");

        assertNull(channel.getUser("nobody"));
        assertNull(channel.getUser(null));
        assertFalse(channel.contains("nobody"));
        assertTrue(channel.getOperators().isEmpty());
    }

    private static ChannelUser user(String nick, UserStatus status) {
        return new ChannelUser(nick, EnumSet.of(status));
    }

    private static ChannelStateTracker tracker(String... lines) {
        ChannelStateTracker tracker = new ChannelStateTracker("bot");
        for (String line : lines) {
            tracker.publishEvent(new Event<>(IRCMessageParser.parse(line)));
        }
        return tracker;
    }
}
