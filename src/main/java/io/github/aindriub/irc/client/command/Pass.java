package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Server password. Must be sent before NICK and USER (RFC 2812 section 3.1.1).
 * Twitch carries its {@code oauth:...} token here.
 */
public class Pass extends Command {

    private static final String BASE_COMMAND = "PASS";

    public Pass(String password) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(password, "password"));
    }

    @Override
    public String toString() {
        // Overridden because this is the form that reaches logs and debug output.
        // render() still returns the real password for the encoder.
        return BASE_COMMAND + SPACE + "<redacted>";
    }
}
