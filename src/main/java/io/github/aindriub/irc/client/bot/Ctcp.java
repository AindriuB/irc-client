package io.github.aindriub.irc.client.bot;

import java.util.Locale;

/**
 * Parses and builds CTCP payloads: {@code \u0001COMMAND[ SP argument]\u0001}.
 * The trailing delimiter is often missing from real clients and is tolerated.
 */
final class Ctcp {

    private static final char DELIMITER = '\u0001';

    private Ctcp() {
    }

    /**
     * True when text is a CTCP payload: starts with the delimiter and has more
     * than just the delimiter(s).
     */
    static boolean isCtcp(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != DELIMITER) {
            return false;
        }
        String body = stripDelimiters(text);
        return !body.isEmpty();
    }

    /**
     * The CTCP command, upper-cased, or null when text is not CTCP.
     */
    static String command(String text) {
        if (!isCtcp(text)) {
            return null;
        }
        String body = stripDelimiters(text);
        int space = body.indexOf(' ');
        String command = space < 0 ? body : body.substring(0, space);
        return command.toUpperCase(Locale.ROOT);
    }

    /**
     * The CTCP argument, or null when there is none, or when text is not CTCP.
     */
    static String argument(String text) {
        if (!isCtcp(text)) {
            return null;
        }
        String body = stripDelimiters(text);
        int space = body.indexOf(' ');
        return space < 0 ? null : body.substring(space + 1);
    }

    /**
     * Builds a CTCP payload from a command and optional argument.
     */
    static String build(String command, String argument) {
        StringBuilder sb = new StringBuilder();
        sb.append(DELIMITER).append(command);
        if (argument != null) {
            sb.append(' ').append(argument);
        }
        sb.append(DELIMITER);
        return sb.toString();
    }

    private static String stripDelimiters(String text) {
        int end = text.length();
        if (end > 1 && text.charAt(end - 1) == DELIMITER) {
            end--;
        }
        return text.substring(1, end);
    }
}
