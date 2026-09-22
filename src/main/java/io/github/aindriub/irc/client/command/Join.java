package io.github.aindriub.irc.client.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Join extends Command {

    private static final String BASE_COMMAND = "JOIN";

    private static final String ALL_CHANNELS = "0";

    private final List<String> channels;

    /** Positional keys, empty when the channels need none. */
    private final List<String> keys;

    public Join(String channel) {
        this(Collections.singletonList(channel));
    }

    /**
     * Joins a key protected channel: {@code JOIN #channel key}.
     */
    public Join(String channel, String key) {
        this(Collections.singletonList(channel), Collections.singletonList(key));
    }

    public Join(List<String> channels) {
        super(BASE_COMMAND + SPACE + Channels.join(channels, "channel"));
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.keys = Collections.emptyList();
    }

    /**
     * Keys are positional: the nth key belongs to the nth channel.
     */
    public Join(List<String> channels, List<String> keys) {
        super(BASE_COMMAND + SPACE + Channels.join(channels, "channel") + SPACE
                + Channels.join(requireMatching(channels, keys), "key"));
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    }

    /**
     * The key for a channel this command joins, or null when it needs none.
     */
    public String getKey(String channel) {
        int index = channels.indexOf(channel);
        return index < 0 || index >= keys.size() ? null : keys.get(index);
    }

    /**
     * The channels this command joins, so a client can track what it is in and
     * restore them after a reconnect.
     */
    public List<String> getChannels() {
        return channels;
    }

    /**
     * True for {@code JOIN 0}, which leaves every channel rather than joining one.
     */
    public boolean isPartAll() {
        return channels.size() == 1 && ALL_CHANNELS.equals(channels.get(0));
    }

    private static List<String> requireMatching(List<String> channels, List<String> keys) {
        if (keys == null || channels == null || keys.size() != channels.size()) {
            throw new IllegalArgumentException("a key is required for each channel: "
                    + (channels == null ? 0 : channels.size()) + " channels, "
                    + (keys == null ? 0 : keys.size()) + " keys");
        }
        return keys;
    }

    /**
     * {@code JOIN 0} parts every channel at once (RFC 2812 section 3.2.1).
     */
    public static Join partAll() {
        return new Join(Arrays.asList(ALL_CHANNELS));
    }
}
