package io.github.aindriub.irc.client.configuration;

/**
 * Automatic reconnection after an unexpected disconnection, with exponential
 * backoff. A deliberate {@code disconnect()} never triggers it.
 */
public class ReconnectConfiguration {

    private boolean enabled;

    /** Delay before the first attempt. */
    private long initialDelay;

    /** Ceiling for the backoff, so a long outage settles at a steady retry rate. */
    private long maxDelay;

    /** Each attempt waits this many times longer than the last, up to maxDelay. */
    private double multiplier;

    /** Zero means keep trying indefinitely. */
    private int maxAttempts;

    public ReconnectConfiguration() {
        enabled = true;
        initialDelay = 1000;
        maxDelay = 60000;
        multiplier = 2.0;
        maxAttempts = 0;
    }

    /**
     * The delay before the given attempt, counting from 1.
     */
    public long delayFor(int attempt) {
        double delay = initialDelay * Math.pow(multiplier, Math.max(0, attempt - 1));
        return (long) Math.min(delay, maxDelay);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getMaxDelay() {
        return maxDelay;
    }

    public void setMaxDelay(long maxDelay) {
        this.maxDelay = maxDelay;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
