package io.github.aindriub.irc.client.handler;

import io.github.aindriub.irc.client.IRCClientException;

/**
 * Thrown when a message is produced faster than flood protection will release it,
 * and the queue behind the limiter is already full.
 *
 * <p>This is backpressure rather than a fault. A bot that sees it is asking the
 * server to accept more than the server allows, and the choices are to slow down,
 * to drop the message, or to raise {@code maxQueueDepth} knowing what that costs in
 * memory. Silently queueing without limit makes that decision by default, and makes
 * it badly.
 */
public class OutboundQueueFullException extends IRCClientException {

    private static final long serialVersionUID = 1L;

    private final int depth;
    private final int limit;

    public OutboundQueueFullException(int depth, int limit) {
        super("Outbound queue is full: " + depth + " message(s) waiting, limit " + limit
                + ". Messages are being produced faster than flood protection releases them.");
        this.depth = depth;
        this.limit = limit;
    }

    public int getDepth() {
        return depth;
    }

    public int getLimit() {
        return limit;
    }
}
