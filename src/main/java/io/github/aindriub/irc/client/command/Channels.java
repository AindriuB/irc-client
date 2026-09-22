package io.github.aindriub.irc.client.command;

import java.util.List;

import io.github.aindriub.irc.client.IRCText;

/**
 * Renders the comma separated channel and key lists shared by JOIN and PART.
 */
final class Channels {

    private Channels() {
    }

    static String join(List<String> channels, String name) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("at least one " + name + " is required");
        }
        StringBuilder rendered = new StringBuilder();
        for (String channel : channels) {
            if (rendered.length() > 0) {
                rendered.append(',');
            }
            rendered.append(IRCText.requireParam(channel, name));
        }
        return rendered.toString();
    }
}
