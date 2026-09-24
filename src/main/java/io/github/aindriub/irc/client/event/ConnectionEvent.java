package io.github.aindriub.irc.client.event;

import java.util.Objects;

/**
 * A change in connection state: the connection was lost, a reconnect attempt is
 * about to be made, a reconnect succeeded, or the client gave up retrying.
 *
 * <p>Published to the connection handlers registered via
 * {@link io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder#connectionListener(EventHandler)}.
 * A deliberate {@code disconnect()}/shutdown publishes no event at all, since the
 * caller already knows it asked for that.
 *
 * <p><b>Delivery thread and ordering.</b> Every event for a given client is
 * delivered on one dedicated thread, never the Netty event loop that happens to
 * be handling the connection's I/O, and handlers are called one at a time, in
 * registration order, for one event before the next event is even decided. Two
 * events for the same client are therefore never delivered concurrently and
 * always arrive in the order they actually happened (for example RECONNECTING 1
 * before RECONNECTING 2, and never RECONNECTED before the RECONNECTING that led
 * to it). A handler that blocks delays not only later events but the reconnect
 * attempt that follows, since scheduling that attempt happens on the same
 * thread; keep handlers fast, and hand off any real work elsewhere.
 */
public final class ConnectionEvent {

    /**
     * The kind of connection-state change being reported.
     */
    public enum Type {
        /** The connection was lost unexpectedly. */
        DISCONNECTED,
        /** A reconnect attempt is about to be made. */
        RECONNECTING,
        /** A reconnect attempt succeeded and registration (if configured) completed. */
        RECONNECTED,
        /** Reconnection was abandoned after the configured number of attempts. */
        GAVE_UP
    }

    private final Type type;
    private final int attempt;
    private final long delayMillis;

    private ConnectionEvent(Type type, int attempt, long delayMillis) {
        this.type = Objects.requireNonNull(type, "type");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative: " + attempt);
        }
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative: " + delayMillis);
        }
        this.attempt = attempt;
        this.delayMillis = delayMillis;
    }

    /**
     * The connection was lost.
     */
    public static ConnectionEvent disconnected() {
        return new ConnectionEvent(Type.DISCONNECTED, 0, 0);
    }

    /**
     * A reconnect attempt is about to be made.
     *
     * @param attempt     the attempt number, counting from 1
     * @param delayMillis the delay before this attempt actually runs
     * @throws IllegalArgumentException if attempt or delayMillis is negative
     */
    public static ConnectionEvent reconnecting(int attempt, long delayMillis) {
        return new ConnectionEvent(Type.RECONNECTING, attempt, delayMillis);
    }

    /**
     * A reconnect attempt succeeded.
     */
    public static ConnectionEvent reconnected() {
        return new ConnectionEvent(Type.RECONNECTED, 0, 0);
    }

    /**
     * Reconnection was abandoned.
     *
     * @param attempts the number of attempts made before giving up
     * @throws IllegalArgumentException if attempts is negative
     */
    public static ConnectionEvent gaveUp(int attempts) {
        return new ConnectionEvent(Type.GAVE_UP, attempts, 0);
    }

    public Type getType() {
        return type;
    }

    /**
     * The attempt number for {@link Type#RECONNECTING}, the number of attempts made
     * for {@link Type#GAVE_UP}, zero otherwise.
     */
    public int getAttempt() {
        return attempt;
    }

    /**
     * The delay before the reconnect attempt actually runs, for
     * {@link Type#RECONNECTING}; zero otherwise.
     */
    public long getDelayMillis() {
        return delayMillis;
    }

    @Override
    public String toString() {
        return "ConnectionEvent{type=" + type + ", attempt=" + attempt + ", delayMillis="
                + delayMillis + '}';
    }
}
