package io.github.aindriub.irc.client.command;

import java.util.Collections;
import java.util.List;

import io.github.aindriub.irc.client.IRCText;

public class Part extends Command {

    private static final String BASE_COMMAND = "PART";

    public Part(String channel) {
        this(Collections.singletonList(channel));
    }

    public Part(List<String> channels) {
        super(BASE_COMMAND + SPACE + Channels.join(channels, "channel"));
    }

    /**
     * Parts with a reason, which is what other users see in the channel.
     */
    public Part(String channel, String reason) {
        this(Collections.singletonList(channel), reason);
    }

    public Part(List<String> channels, String reason) {
        super(BASE_COMMAND + SPACE + Channels.join(channels, "channel") + SPACE + COLON
                + IRCText.requireText(reason, "reason"));
    }
}
