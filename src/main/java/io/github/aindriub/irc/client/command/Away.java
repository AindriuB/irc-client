package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Marks the client away, so that anyone messaging it gets an automatic reply.
 */
public class Away extends Command {

    private static final String BASE_COMMAND = "AWAY";

    public Away(String message) {
        super(BASE_COMMAND + SPACE + COLON + IRCText.requireText(message, "message"));
    }

    private Away() {
        super(BASE_COMMAND);
    }

    /**
     * Clears the away status: AWAY with no message means back.
     */
    public static Away back() {
        return new Away();
    }
}
