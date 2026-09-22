package io.github.aindriub.irc.client;

import java.nio.charset.Charset;

/**
 * Validation for text that is about to go on the wire.
 *
 * <p>The pipeline terminates every outbound message with CRLF, so a caller who
 * passes unsanitised input through to a command can otherwise inject a second IRC
 * message: {@code new Join("#chan\r\nQUIT")} would render as two. A bot echoing
 * chat input into a command is exactly the path that makes that reachable.
 */
public final class IRCText {

    /**
     * The whole message, including the trailing CRLF (RFC 2812 section 2.3).
     * A server does not reject a longer line, it truncates it, which is worse:
     * the message arrives looking like something the sender chose to write.
     */
    public static final int MAX_MESSAGE_BYTES = 512;

    /**
     * What a message body may occupy once the command, the target and the CRLF
     * have taken their share. Not a constant, because the share depends on the
     * command, so this is only the ceiling.
     */
    public static final int MAX_PAYLOAD_BYTES = MAX_MESSAGE_BYTES - 2;

    private IRCText() {
    }

    /**
     * How many bytes a string occupies on the wire.
     *
     * <p>Not its length. In UTF-8 an emoji is one character and four bytes, and
     * the limit the server applies is counted in bytes, so a check against
     * {@code String.length()} passes lines the server will cut in half.
     */
    public static int byteLength(String value, Charset charset) {
        return value == null ? 0 : value.getBytes(charset).length;
    }

    /**
     * Checks that a rendered command fits in one IRC message.
     *
     * @throws IllegalArgumentException when it does not, naming both sizes, since
     *                                  "too long" without a number leaves the
     *                                  caller to work out by how much
     */
    public static String requireFits(String line, Charset charset) {
        int bytes = byteLength(line, charset) + 2;
        if (bytes > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("message is " + bytes + " bytes including CRLF, "
                    + "over the " + MAX_MESSAGE_BYTES + " byte limit. The server would truncate "
                    + "it rather than refuse it. Split it, or see PrivMsg.split.");
        }
        return line;
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
