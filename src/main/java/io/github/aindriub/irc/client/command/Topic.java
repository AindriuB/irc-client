package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Queries or sets a channel topic. Setting it to the empty string clears it.
 */
public class Topic extends Command {

    private static final String BASE_COMMAND = "TOPIC";

    /**
     * Asks for the current topic.
     */
    public Topic(String channel) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(channel, "channel"));
    }

    public Topic(String channel, String topic) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(channel, "channel") + SPACE + COLON
                + IRCText.requireText(topic, "topic"));
    }
}
