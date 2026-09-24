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
     * True when text is a CTCP payload: starts with the delimiter, has a
     * non-empty command, and neither the command nor the argument (if any)
     * contains a NUL, CR or LF. Text failing only this last check is treated
     * as plain text rather than CTCP, so callers never see an invariant
     * violation when they round-trip it through {@link #build}.
     */
    static boolean isCtcp(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != DELIMITER) {
            return false;
        }
        String body = bodyOf(text);
        if (body.isEmpty()) {
            return false;
        }
        String command = commandPart(body);
        if (command.isEmpty() || containsControlChar(command)) {
            return false;
        }
        String argument = argumentPart(body);
        return argument == null || !containsControlChar(argument);
    }

    /**
     * The CTCP command, upper-cased, or null when text is not CTCP.
     */
    static String command(String text) {
        if (!isCtcp(text)) {
            return null;
        }
        return commandPart(bodyOf(text)).toUpperCase(Locale.ROOT);
    }

    /**
     * The CTCP argument, or null when there is none, or when text is not CTCP.
     */
    static String argument(String text) {
        if (!isCtcp(text)) {
            return null;
        }
        return argumentPart(bodyOf(text));
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

    /**
     * The command portion of a CTCP body: everything before the first space,
     * or the whole body when there is no space.
     */
    private static String commandPart(String body) {
        int space = body.indexOf(' ');
        return space < 0 ? body : body.substring(0, space);
    }

    /**
     * The argument portion of a CTCP body, or null when there is no space or
     * nothing follows it.
     */
    private static String argumentPart(String body) {
        int space = body.indexOf(' ');
        if (space < 0) {
            return null;
        }
        String argument = body.substring(space + 1);
        return argument.isEmpty() ? null : argument;
    }

    private static boolean containsControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\u0000') {
                return true;
            }
        }
        return false;
    }
}
