package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Client initiated ping, used to find out whether a quiet connection is still alive.
 */
public class Ping extends Command {

    private static final String BASE_COMMAND = "PING";

    public Ping(String token) {
        super(BASE_COMMAND + SPACE + COLON + IRCText.requireText(token, "token"));
    }
}
