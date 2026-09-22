package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Sends a message to a channel or a user.
 */
public class PrivMsg extends Command {

    private static final String BASE_COMMAND = "PRIVMSG";

    /**
     * @param target a channel such as {@code #chat}, or a nick
     */
    public PrivMsg(String target, String message) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(target, "target") + SPACE + COLON
                + IRCText.requireText(message, "message"));
    }
}
