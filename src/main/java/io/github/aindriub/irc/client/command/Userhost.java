package io.github.aindriub.irc.client.command;

import java.util.Arrays;
import java.util.List;

/**
 * Asks for the user@host of up to five nicks.
 */
public class Userhost extends Command {

    private static final String BASE_COMMAND = "USERHOST";

    /** RFC 2812 section 3.6.4 caps this at five. */
    private static final int MAX_NICKS = 5;

    public Userhost(String... nicks) {
        this(Arrays.asList(nicks));
    }

    public Userhost(List<String> nicks) {
        super(BASE_COMMAND + SPACE + Ison.spaceSeparated(requireAtMostFive(nicks)));
    }

    private static List<String> requireAtMostFive(List<String> nicks) {
        if (nicks != null && nicks.size() > MAX_NICKS) {
            throw new IllegalArgumentException(
                    "USERHOST takes at most " + MAX_NICKS + " nicks, got " + nicks.size());
        }
        return nicks;
    }
}
