package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Removes a user from a channel.
 */
public class Kick extends Command {

    private static final String BASE_COMMAND = "KICK";

    public Kick(String channel, String nick) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(channel, "channel") + SPACE
                + IRCText.requireParam(nick, "nick"));
    }

    /**
     * @param reason shown to the channel, so the kick is not unexplained
     */
    public Kick(String channel, String nick, String reason) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(channel, "channel") + SPACE
                + IRCText.requireParam(nick, "nick") + SPACE + COLON
                + IRCText.requireText(reason, "reason"));
    }
}
