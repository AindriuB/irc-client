package io.github.aindriub.irc.client.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What is known about one channel: who is in it, what status they hold, and the
 * topic.
 *
 * <p>Nicks are compared under the server's {@link CaseMapping}, because IRC treats
 * them that way and a server will happily send {@code Someone} in a NAMES reply and
 * {@code someone} in the PART that follows.
 */
public final class ChannelState {

    private final String name;
    private final CaseMapping caseMapping;
    private final Map<String, ChannelUser> users = new LinkedHashMap<>();

    private String topic;

    ChannelState(String name, CaseMapping caseMapping) {
        this.name = name;
        this.caseMapping = caseMapping;
    }

    public String getName() {
        return name;
    }

    /**
     * The topic, or null when the channel has none or it has not been seen yet.
     */
    public synchronized String getTopic() {
        return topic;
    }

    synchronized void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * Everyone currently in the channel, in the order they were first seen.
     */
    public synchronized List<ChannelUser> getUsers() {
        return Collections.unmodifiableList(new ArrayList<>(users.values()));
    }

    public synchronized List<String> getNicks() {
        return Collections.unmodifiableList(new ArrayList<>(
                users.values().stream().map(ChannelUser::getNick)
                        .collect(java.util.stream.Collectors.toList())));
    }

    /**
     * @return what is known about a nick in this channel, or null if they are not in it
     */
    public synchronized ChannelUser getUser(String nick) {
        return nick == null ? null : users.get(key(nick));
    }

    public synchronized boolean contains(String nick) {
        return getUser(nick) != null;
    }

    public synchronized int size() {
        return users.size();
    }

    /**
     * Everyone who can moderate: operator or above.
     */
    public synchronized List<ChannelUser> getOperators() {
        List<ChannelUser> operators = new ArrayList<>();
        for (ChannelUser user : users.values()) {
            if (user.isOperator()) {
                operators.add(user);
            }
        }
        return Collections.unmodifiableList(operators);
    }

    synchronized void add(ChannelUser user) {
        users.put(key(user.getNick()), user);
    }

    synchronized void remove(String nick) {
        users.remove(key(nick));
    }

    synchronized void clearUsers() {
        users.clear();
    }

    synchronized void rename(String oldNick, String newNick) {
        ChannelUser user = users.remove(key(oldNick));
        if (user != null) {
            // Reinserted rather than mutated, so a caller holding the old object
            // still sees a consistent snapshot.
            users.put(key(newNick), user.renamedTo(newNick));
        }
    }

    synchronized void applyStatus(String nick, UserStatus status, boolean adding) {
        ChannelUser user = users.get(key(nick));
        if (user == null) {
            return;
        }
        users.put(key(nick), adding ? user.withStatus(status) : user.withoutStatus(status));
    }

    private String key(String nick) {
        return caseMapping.fold(nick);
    }

    @Override
    public synchronized String toString() {
        return name + " (" + users.size() + " users)";
    }
}
