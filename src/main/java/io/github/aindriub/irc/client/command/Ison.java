package io.github.aindriub.irc.client.command;

import java.util.Arrays;
import java.util.List;

import io.github.aindriub.irc.client.IRCText;

/**
 * Asks which of the given nicks are online. Cheaper than a WHOIS per nick.
 */
public class Ison extends Command {

    private static final String BASE_COMMAND = "ISON";

    public Ison(String... nicks) {
        this(Arrays.asList(nicks));
    }

    public Ison(List<String> nicks) {
        super(BASE_COMMAND + SPACE + spaceSeparated(nicks));
    }

    static String spaceSeparated(List<String> nicks) {
        if (nicks == null || nicks.isEmpty()) {
            throw new IllegalArgumentException("at least one nick is required");
        }
        StringBuilder rendered = new StringBuilder();
        for (String nick : nicks) {
            if (rendered.length() > 0) {
                rendered.append(' ');
            }
            rendered.append(IRCText.requireParam(nick, "nick"));
        }
        return rendered.toString();
    }
}
