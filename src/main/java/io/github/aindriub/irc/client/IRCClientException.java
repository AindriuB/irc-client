package io.github.aindriub.irc.client;

/**
 * Thrown when the client cannot complete a connect, send or disconnect. Previously
 * these failures were logged and swallowed, which left callers unable to tell a
 * successful operation from a failed one.
 */
public class IRCClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IRCClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public IRCClientException(String message) {
        super(message);
    }
}
