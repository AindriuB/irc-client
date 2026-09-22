package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Reply to a server PING. The token must be echoed back unchanged.
 */
public class Pong extends Command {

    private static final String BASE_COMMAND = "PONG";

    public Pong(String token) {
        super(BASE_COMMAND + SPACE + COLON + IRCText.requireText(token, "token"));
    }
}
