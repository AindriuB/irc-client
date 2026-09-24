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
}
