package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Asks about a nick that is no longer connected.
 */
public class Whowas extends Command {

    private static final String BASE_COMMAND = "WHOWAS";

    public Whowas(String nick) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(nick, "nick"));
    }

    /**
     * @param count how many past entries to return
     */
    public Whowas(String nick, int count) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(nick, "nick") + SPACE
                + requirePositive(count));
    }

    private static String requirePositive(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
        return Integer.toString(count);
    }
}
