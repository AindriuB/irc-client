package io.github.aindriub.irc.client.command;

import java.util.Collections;
import java.util.List;

/**
 * Asks who is in a channel. The server answers with RPL_NAMREPLY (353) lines and
 * then RPL_ENDOFNAMES (366).
 */
public class Names extends Command {

    private static final String BASE_COMMAND = "NAMES";

    public Names(String channel) {
        this(Collections.singletonList(channel));
    }

    public Names(List<String> channels) {
        super(BASE_COMMAND + SPACE + Channels.join(channels, "channel"));
    }
}
