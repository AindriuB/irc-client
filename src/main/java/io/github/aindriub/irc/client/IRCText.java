package io.github.aindriub.irc.client;

/**
 * Validation for text that is about to go on the wire.
 *
 * <p>The pipeline terminates every outbound message with CRLF, so a caller who
 * passes unsanitised input through to a command can otherwise inject a second IRC
 * message: {@code new Join("#chan\r\nQUIT")} would render as two. A bot echoing
 * chat input into a command is exactly the path that makes that reachable.
 */
public final class IRCText {

    private IRCText() {
    }

    /**
     * Validates a middle parameter: one space delimited token. Rejects whitespace,
     * since that would silently split one parameter into two, and a leading colon,
     * which would turn it into a trailing parameter that swallows the rest of the
     * message.
     */
    public static String requireParam(String value, String name) {
        requireNoControlCharacters(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        if (value.startsWith(":")) {
            throw new IllegalArgumentException(
                    name + " must not start with ':', which would make it a trailing parameter: "
                            + value);
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                throw new IllegalArgumentException(
                        name + " must not contain whitespace: " + value);
            }
        }
        return value;
    }

    /**
     * Validates a trailing parameter, such as a message body. Spaces are fine here;
     * line breaks are not.
     */
    public static String requireText(String value, String name) {
        requireNoControlCharacters(value, name);
        return value;
    }

    private static void requireNoControlCharacters(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0') {
                throw new IllegalArgumentException(name
                        + " must not contain CR, LF or NUL, which would let it inject a separate "
                        + "IRC message: " + describe(value));
            }
        }
    }

    private static String describe(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\0", "\\0");
    }
}
