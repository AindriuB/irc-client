package io.github.aindriub.irc.client.handler;

import io.github.aindriub.irc.client.IRCClientException;

/**
 * The server turned the connection away and said why.
 *
 * <p>Distinct from a connection that simply failed, and the difference matters to
 * anything deciding when to try again: a refusal is a decision the server has
 * made, so knocking a second later tends to confirm it rather than get past it.
 * EFnet's "Reconnecting too fast, throttled" is the usual example — each attempt
 * inside the window restarts the window.
 *
 * <p>{@link #getReply()} is the server's own sentence, which is generally the only
 * thing that explains what to do about it.
 */
public class ServerRefusedException extends IRCClientException {

    private static final long serialVersionUID = 1L;

    private final String reply;

    public ServerRefusedException(String message, String reply, Throwable cause) {
        super(message, cause);
        this.reply = reply;
    }

    public ServerRefusedException(String message, String reply) {
        this(message, reply, null);
    }

    /** What the server said, verbatim, or null if it did not say anything. */
    public String getReply() {
        return reply;
    }
}
