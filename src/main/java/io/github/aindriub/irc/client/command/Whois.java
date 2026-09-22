package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Asks about a user who is currently connected.
 */
public class Whois extends Command {

    private static final String BASE_COMMAND = "WHOIS";

    public Whois(String nick) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(nick, "nick"));
    }
}
