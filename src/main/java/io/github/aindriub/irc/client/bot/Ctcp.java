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
        String body = bodyOf(text);
        return !body.isEmpty();
    }

    /**
     * The CTCP command, upper-cased, or null when text is not CTCP.
     */
    static String command(String text) {
        if (!isCtcp(text)) {
            return null;
        }
        String body = bodyOf(text);
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
        String body = bodyOf(text);
        int space = body.indexOf(' ');
        return space < 0 ? null : body.substring(space + 1);
    }

    /**
     * Builds a CTCP payload from a command and optional argument.
     *
     * @throws IllegalArgumentException when the command is null, empty, contains
     *     a space, or when the command or argument contains a CTCP delimiter,
     *     CR, LF or NUL
     */
    static String build(String command, String argument) {
        requireValidToken("command", command, true);
        if (argument != null) {
            requireValidToken("argument", argument, false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(DELIMITER).append(command);
        if (argument != null) {
            sb.append(' ').append(argument);
        }
        sb.append(DELIMITER);
        return sb.toString();
    }

    private static void requireValidToken(String name, String value, boolean rejectSpace) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
        if (rejectSpace && value.indexOf(' ') >= 0) {
            throw new IllegalArgumentException(name + " must not contain a space");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == DELIMITER || c == '\r' || c == '\n' || c == '\u0000') {
                throw new IllegalArgumentException(name + " must not contain a CTCP delimiter, CR, LF or NUL");
            }
        }
    }

    /**
     * The text between the leading delimiter and the first delimiter that
     * follows it, or the end of the string when there is no such delimiter.
     */
    private static String bodyOf(String text) {
        int end = text.indexOf(DELIMITER, 1);
        return end < 0 ? text.substring(1) : text.substring(1, end);
    }
}
