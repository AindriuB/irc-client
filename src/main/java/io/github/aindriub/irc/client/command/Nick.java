package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

public class Nick extends Command {

    private static final String BASE_COMMAND = "NICK";

    public Nick(String nick) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(nick, "nick"));
    }
}
