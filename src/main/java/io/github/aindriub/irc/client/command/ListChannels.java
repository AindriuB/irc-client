package io.github.aindriub.irc.client.command;

import java.util.List;

/**
 * Lists channels and their topics. Named ListChannels rather than List so that it
 * does not collide with {@link java.util.List} in every file that uses both.
 */
public class ListChannels extends Command {

    private static final String BASE_COMMAND = "LIST";

    private ListChannels(String rendered) {
        super(rendered);
    }

    /**
     * Lists every channel. On a large network this is a lot of traffic, and some
     * servers refuse it outright.
     */
    public static ListChannels all() {
        return new ListChannels(BASE_COMMAND);
    }

    public static ListChannels of(List<String> channels) {
        return new ListChannels(BASE_COMMAND + SPACE + Channels.join(channels, "channel"));
    }

    public static ListChannels of(String channel) {
        return of(java.util.Collections.singletonList(channel));
    }
}
