package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

public class Quit extends Command {

    private static final String BASE_COMMAND = "QUIT";

    public Quit() {
        super(BASE_COMMAND);
    }

    /**
     * Quits with a reason, which is what other users see in the channels you were in.
     */
    public Quit(String reason) {
        super(BASE_COMMAND + SPACE + COLON + IRCText.requireText(reason, "reason"));
    }
}
