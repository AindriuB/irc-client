package io.github.aindriub.irc.client.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Join extends Command {

    private static final String BASE_COMMAND = "JOIN";

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
    }

    /**
     * Keys are positional: the nth key belongs to the nth channel.
     */
    public Join(List<String> channels, List<String> keys) {
        super(BASE_COMMAND + SPACE + Channels.join(channels, "channel") + SPACE
                + Channels.join(requireMatching(channels, keys), "key"));
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
        return new Join(Arrays.asList("0"));
    }
}
