package io.github.aindriub.irc.client.state;

/**
 * Channel status, in descending order of privilege, so that {@code ordinal()}
 * compares them.
 *
 * <p>Owner, admin and half-op are widespread conventions rather than anything in
 * RFC 2812, and not every server implements them.
 */
public enum UserStatus {

    OWNER('~', 'q'),
    ADMIN('&', 'a'),
    OPERATOR('@', 'o'),
    HALF_OPERATOR('%', 'h'),
    VOICE('+', 'v');

    private final char prefix;
    private final char mode;

    UserStatus(char prefix, char mode) {
        this.prefix = prefix;
        this.mode = mode;
    }

    /**
     * The character a NAMES reply puts in front of the nick.
     */
    public char getPrefix() {
        return prefix;
    }

    /**
     * The mode letter that grants it, as in {@code MODE #chan +o nick}.
     */
    public char getMode() {
        return mode;
    }

    /**
     * @return the status for a NAMES prefix character, or null if it is not one
     */
    public static UserStatus fromPrefix(char prefix) {
        for (UserStatus status : values()) {
            if (status.prefix == prefix) {
                return status;
            }
        }
        return null;
    }

    /**
     * @return the status for a mode letter, or null if that mode is not a status
     */
    public static UserStatus fromMode(char mode) {
        for (UserStatus status : values()) {
            if (status.mode == mode) {
                return status;
            }
        }
        return null;
    }
}
