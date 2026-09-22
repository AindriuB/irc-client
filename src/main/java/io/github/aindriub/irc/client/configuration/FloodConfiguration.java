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

    public FloodConfiguration() {
        enabled = true;
        burst = 5;
        interval = 2000;
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
