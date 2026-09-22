package io.github.aindriub.irc.client.configuration;

/**
 * Outbound rate limiting. IRC servers disconnect clients that send too fast, and a
 * bot reacting to a busy channel will reach that limit without meaning to.
 *
 * <p>A token bucket: {@code burst} messages may go out back to back, after which one
 * more is released every {@code interval}. Protocol traffic the server is waiting on
 * (PONG and the registration handshake) is not throttled.
 */
public class FloodConfiguration {

    private boolean enabled;

    /** Messages allowed back to back before throttling begins. */
    private int burst;

    /** Milliseconds between releases once the burst is spent. */
    private long interval;

    /**
     * How many messages may wait for a token before a send is refused.
     *
     * <p>A queue with no limit turns producing faster than the rate into memory
     * growth instead of an error, and Netty's write watermarks cannot help: the
     * limiter accepts every write immediately, so as far as the channel is
     * concerned nothing is outstanding. Refusing past a depth gives a caller
     * backpressure it can see and act on.
     *
     * <p>The default allows roughly a minute of backlog at the default rate,
     * which absorbs a burst of replies without letting a runaway loop grow
     * without bound.
     */
    private int maxQueueDepth;

    public FloodConfiguration() {
        enabled = true;
        burst = 5;
        interval = 2000;
        maxQueueDepth = 30;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBurst() {
        return burst;
    }

    public void setBurst(int burst) {
        if (burst < 1) {
            throw new IllegalArgumentException("burst must be at least 1");
        }
        this.burst = burst;
    }

    public int getMaxQueueDepth() {
        return maxQueueDepth;
    }

    /**
     * @param maxQueueDepth messages allowed to wait; zero or less removes the
     *                      limit, which is the old behaviour and is only sensible
     *                      when the send rate is known to be bounded
     */
    public void setMaxQueueDepth(int maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        if (interval < 1) {
            throw new IllegalArgumentException("interval must be at least 1ms");
        }
        this.interval = interval;
    }
}
