package io.github.aindriub.irc.client.state;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One person in a channel, and what status they hold there.
 */
public final class ChannelUser {

    private final String nick;
    private final Set<UserStatus> statuses;

    ChannelUser(String nick, Set<UserStatus> statuses) {
        this.nick = Objects.requireNonNull(nick, "nick");
        this.statuses = Collections.unmodifiableSet(new LinkedHashSet<>(statuses));
    }

    public String getNick() {
        return nick;
    }

    /**
     * Every status held, since a user can be more than one thing at once.
     */
    public Set<UserStatus> getStatuses() {
        return statuses;
    }

    /**
     * The highest status held, or null when the user holds none.
     */
    public UserStatus getHighestStatus() {
        UserStatus highest = null;
        for (UserStatus status : statuses) {
            if (highest == null || status.ordinal() < highest.ordinal()) {
                highest = status;
            }
        }
        return highest;
    }

    public boolean hasStatus(UserStatus status) {
        return statuses.contains(status);
    }

    /**
     * True for anyone who can moderate the channel: operator or above.
     */
    public boolean isOperator() {
        return hasStatus(UserStatus.OPERATOR) || hasStatus(UserStatus.ADMIN)
                || hasStatus(UserStatus.OWNER);
    }

    ChannelUser withStatus(UserStatus status) {
        Set<UserStatus> updated = new LinkedHashSet<>(statuses);
        updated.add(status);
        return new ChannelUser(nick, updated);
    }

    ChannelUser withoutStatus(UserStatus status) {
        Set<UserStatus> updated = new LinkedHashSet<>(statuses);
        updated.remove(status);
        return new ChannelUser(nick, updated);
    }

    ChannelUser renamedTo(String newNick) {
        return new ChannelUser(newNick, statuses);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChannelUser)) {
            return false;
        }
        ChannelUser that = (ChannelUser) other;
        return nick.equalsIgnoreCase(that.nick) && statuses.equals(that.statuses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nick.toLowerCase(java.util.Locale.ROOT), statuses);
    }

    @Override
    public String toString() {
        UserStatus highest = getHighestStatus();
        return (highest == null ? "" : highest.getPrefix()) + nick;
    }
}
