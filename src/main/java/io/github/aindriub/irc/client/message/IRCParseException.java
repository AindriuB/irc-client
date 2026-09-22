package io.github.aindriub.irc.client.message;

/**
 * A line that could not be parsed. Callers log and skip: one malformed line from a
 * server must not take the connection down.
 */
public class IRCParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IRCParseException(String message) {
        super(message);
    }
}
