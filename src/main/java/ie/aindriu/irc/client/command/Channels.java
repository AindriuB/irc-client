package ie.aindriu.irc.client.command;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders the comma separated channel list shared by JOIN and PART.
 */
final class Channels {

    private Channels() {
    }

    static String join(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("at least one channel is required");
        }
        return channels.stream().collect(Collectors.joining(","));
    }
}
