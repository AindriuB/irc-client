package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Invites a user to a channel. Required for a channel that is invite only.
 */
public class Invite extends Command {

    private static final String BASE_COMMAND = "INVITE";

    public Invite(String nick, String channel) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(nick, "nick") + SPACE
                + IRCText.requireParam(channel, "channel"));
    }
}
