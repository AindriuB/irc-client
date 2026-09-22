package io.github.aindriub.irc.client.command;

import java.util.List;

public class Join extends Command {

    private static final String BASE_COMMAND = "JOIN";

    public Join(String channel) {
        super(BASE_COMMAND + SPACE + channel);
    }

    public Join(List<String> channels) {
        super(BASE_COMMAND + SPACE + Channels.join(channels));
    }
}
