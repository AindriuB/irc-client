package io.github.aindriub.irc.client.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.event.EventHandler;
import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.message.Numerics;

/**
 * Keeps track of who is in each channel the client is in, and what status they hold.
 *
 * <p>Built from what the server sends rather than by asking: the NAMES reply that
 * follows a JOIN seeds a channel, and JOIN, PART, QUIT, KICK, NICK and MODE keep it
 * current. Register it as a message listener and read it from any thread.
 *
 * <p>A channel is rebuilt from scratch on each NAMES reply, so a reconnect that
 * rejoins leaves no stale members behind.
 */
public class ChannelStateTracker implements EventHandler<IRCMessage> {

    private final Map<String, ChannelState> channels = new ConcurrentHashMap<>();

    /**
     * Channels whose NAMES reply is still arriving; the first line of a new reply
     * clears whatever was there before.
     */
    private final Set<String> receivingNames = Collections.synchronizedSet(new LinkedHashSet<>());

    private volatile String selfNick;

    /**
     * @param selfNick the client's own nick, so the tracker can tell its own PART or
     *                 KICK (forget the channel) from someone else's (forget the user)
     */
    public ChannelStateTracker(String selfNick) {
        this.selfNick = selfNick;
    }

    /**
     * Follows the client's own nick changes, which the client may not control: a
     * server can hand back a modified nick after a collision.
     */
    public void setSelfNick(String selfNick) {
        this.selfNick = selfNick;
    }

    public String getSelfNick() {
        return selfNick;
    }

    /**
     * @return the tracked state for a channel, or null if the client is not in it
     */
    public ChannelState getChannel(String name) {
        return name == null ? null : channels.get(key(name));
    }

    public List<String> getChannelNames() {
        List<String> names = new ArrayList<>();
        for (ChannelState channel : channels.values()) {
            names.add(channel.getName());
        }
        return Collections.unmodifiableList(names);
    }

    @Override
    public void publishEvent(Event<IRCMessage> event) {
        IRCMessage message = event.getPayload();
        switch (message.getCommand()) {
        case "JOIN":
            onJoin(message);
            break;
        case "PART":
            onPart(message);
            break;
        case "KICK":
            onKick(message);
            break;
        case "QUIT":
            onQuit(message);
            break;
        case "NICK":
            onNick(message);
            break;
        case "MODE":
            onMode(message);
            break;
        case "TOPIC":
            onTopic(message);
            break;
        default:
            onNumeric(message);
            break;
        }
    }

    private void onNumeric(IRCMessage message) {
        String command = message.getCommand();
        if (Numerics.RPL_NAMREPLY.equals(command)) {
            onNamesReply(message);
        } else if (Numerics.RPL_ENDOFNAMES.equals(command)) {
            receivingNames.remove(key(nullToEmpty(message.getParam(1))));
        } else if (Numerics.RPL_TOPIC.equals(command)) {
            ChannelState channel = channels.get(key(nullToEmpty(message.getParam(1))));
            if (channel != null) {
                channel.setTopic(message.getParam(2));
            }
        } else if (Numerics.RPL_NOTOPIC.equals(command)) {
            ChannelState channel = channels.get(key(nullToEmpty(message.getParam(1))));
            if (channel != null) {
                channel.setTopic(null);
            }
        }
    }

    /**
     * {@code 353 <nick> = #chan :@one +two three}. The names arrive over several
     * lines, so only the first clears the channel.
     */
    private void onNamesReply(IRCMessage message) {
        String name = message.getParam(2);
        // getParam(3), not getTrailing(): a truncated reply with no name list would
        // otherwise hand back the channel name and add it as a member.
        String names = message.getParam(3);
        if (name == null || names == null) {
            return;
        }
        ChannelState channel = channelFor(name);
        if (receivingNames.add(key(name))) {
            // First line of this reply: start clean so a rejoin cannot leave ghosts.
            channel.clearUsers();
        }
        for (String entry : names.trim().split("\\s+")) {
            if (!entry.isEmpty()) {
                channel.add(parseName(entry));
            }
        }
    }

    /**
     * A name may carry several prefixes at once, such as {@code @+someone}.
     */
    private static ChannelUser parseName(String entry) {
        Set<UserStatus> statuses = new LinkedHashSet<>();
        int index = 0;
        while (index < entry.length()) {
            UserStatus status = UserStatus.fromPrefix(entry.charAt(index));
            if (status == null) {
                break;
            }
            statuses.add(status);
            index++;
        }
        return new ChannelUser(entry.substring(index), statuses);
    }

    private void onJoin(IRCMessage message) {
        String name = message.getParam(0);
        String nick = message.getNick();
        if (name == null || nick == null) {
            return;
        }
        ChannelState channel = channelFor(name);
        channel.add(new ChannelUser(nick, Collections.<UserStatus>emptySet()));
    }

    private void onPart(IRCMessage message) {
        String name = message.getParam(0);
        String nick = message.getNick();
        if (name == null || nick == null) {
            return;
        }
        if (isSelf(nick)) {
            channels.remove(key(name));
        } else {
            ChannelState channel = channels.get(key(name));
            if (channel != null) {
                channel.remove(nick);
            }
        }
    }

    private void onKick(IRCMessage message) {
        String name = message.getParam(0);
        String kicked = message.getParam(1);
        if (name == null || kicked == null) {
            return;
        }
        if (isSelf(kicked)) {
            channels.remove(key(name));
        } else {
            ChannelState channel = channels.get(key(name));
            if (channel != null) {
                channel.remove(kicked);
            }
        }
    }

    /**
     * A QUIT names no channel, so the user has to be removed from all of them.
     */
    private void onQuit(IRCMessage message) {
        String nick = message.getNick();
        if (nick == null) {
            return;
        }
        for (ChannelState channel : channels.values()) {
            channel.remove(nick);
        }
    }

    private void onNick(IRCMessage message) {
        String oldNick = message.getNick();
        String newNick = message.getParam(0);
        if (oldNick == null || newNick == null) {
            return;
        }
        if (isSelf(oldNick)) {
            selfNick = newNick;
        }
        for (ChannelState channel : channels.values()) {
            channel.rename(oldNick, newNick);
        }
    }

    /**
     * {@code MODE #chan +oo one two}: only the status modes are tracked, and only
     * those consume an argument here.
     */
    private void onMode(IRCMessage message) {
        String name = message.getParam(0);
        String modes = message.getParam(1);
        if (name == null || modes == null || !IRCMessage.isChannel(name)) {
            return;
        }
        ChannelState channel = channels.get(key(name));
        if (channel == null) {
            return;
        }
        boolean adding = true;
        int argument = 2;
        for (int i = 0; i < modes.length(); i++) {
            char c = modes.charAt(i);
            if (c == '+') {
                adding = true;
                continue;
            }
            if (c == '-') {
                adding = false;
                continue;
            }
            UserStatus status = UserStatus.fromMode(c);
            if (status == null) {
                // Not a status mode. Some of these take an argument and some do not,
                // and working out which needs the server's ISUPPORT, so stop here
                // rather than misalign the remaining arguments.
                return;
            }
            String nick = message.getParam(argument++);
            if (nick != null) {
                channel.applyStatus(nick, status, adding);
            }
        }
    }

    private void onTopic(IRCMessage message) {
        String name = message.getParam(0);
        if (name == null) {
            return;
        }
        ChannelState channel = channels.get(key(name));
        if (channel != null) {
            channel.setTopic(message.getParam(1));
        }
    }

    private ChannelState channelFor(String name) {
        ChannelState existing = channels.get(key(name));
        if (existing != null) {
            return existing;
        }
        ChannelState created = new ChannelState(name);
        ChannelState raced = channels.putIfAbsent(key(name), created);
        return raced == null ? created : raced;
    }

    private boolean isSelf(String nick) {
        String self = selfNick;
        return self != null && self.equalsIgnoreCase(nick);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
