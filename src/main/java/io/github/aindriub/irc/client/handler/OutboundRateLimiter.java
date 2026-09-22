package io.github.aindriub.irc.client.handler;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.configuration.FloodConfiguration;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Releases outbound messages no faster than the configured rate, so the client is
 * not disconnected for flooding.
 *
 * <p>A token bucket holding up to {@code burst} tokens and gaining one every
 * {@code interval}. Messages beyond that are queued in order and released as tokens
 * arrive; nothing is dropped, and a caller's write promise completes when the
 * message actually goes out.
 *
 * <p>Sits below PingHandler and RegistrationHandler in the pipeline, so their writes
 * start closer to the socket and bypass it. Delaying a PONG would risk the very ping
 * timeout the handler exists to prevent, and delaying the handshake would risk the
 * registration timeout.
 */
public class OutboundRateLimiter extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboundRateLimiter.class);

    private final int burst;
    private final long intervalNanos;

    private final Queue<Pending> queued = new ArrayDeque<>();

    private double tokens;
    private long lastRefill;
    private boolean releaseScheduled;
    private boolean flushPending;

    public OutboundRateLimiter(FloodConfiguration configuration) {
        this.burst = configuration.getBurst();
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(configuration.getInterval());
        this.tokens = configuration.getBurst();
        this.lastRefill = System.nanoTime();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        queued.add(new Pending(msg, promise));
        release(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (queued.isEmpty()) {
            ctx.flush();
        } else {
            // Flushing now would push nothing; the release loop flushes instead.
            flushPending = true;
        }
    }

    private void release(ChannelHandlerContext ctx) {
        refill();
        boolean wrote = false;
        while (tokens >= 1.0 && !queued.isEmpty()) {
            tokens -= 1.0;
            Pending pending = queued.poll();
            ctx.write(pending.message, pending.promise);
            wrote = true;
        }
        if (wrote) {
            ctx.flush();
            flushPending = false;
        }
        if (!queued.isEmpty()) {
            scheduleRelease(ctx);
        } else if (flushPending) {
            ctx.flush();
            flushPending = false;
        }
    }

    private void scheduleRelease(final ChannelHandlerContext ctx) {
        if (releaseScheduled) {
            return;
        }
        releaseScheduled = true;
        long waitNanos = (long) Math.ceil((1.0 - tokens) * intervalNanos);
        LOGGER.debug("Rate limited: {} message(s) queued, next in {}ms", queued.size(),
                TimeUnit.NANOSECONDS.toMillis(waitNanos));
        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                releaseScheduled = false;
                if (ctx.channel().isActive()) {
                    release(ctx);
                } else {
                    failQueued(new java.nio.channels.ClosedChannelException());
                }
            }
        }, Math.max(waitNanos, 0), TimeUnit.NANOSECONDS);
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefill;
        if (elapsed <= 0) {
            return;
        }
        lastRefill = now;
        tokens = Math.min(burst, tokens + (double) elapsed / intervalNanos);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Anything still waiting will never be sent; tell the callers rather than
        // leaving their promises unresolved forever.
        failQueued(new java.nio.channels.ClosedChannelException());
        ctx.fireChannelInactive();
    }

    private void failQueued(Throwable cause) {
        Pending pending;
        while ((pending = queued.poll()) != null) {
            pending.promise.tryFailure(cause);
        }
    }

    /**
     * How many messages are waiting for a token.
     */
    public int queueDepth() {
        return queued.size();
    }

    private static final class Pending {
        private final Object message;
        private final ChannelPromise promise;

        private Pending(Object message, ChannelPromise promise) {
            this.message = message;
            this.promise = promise;
        }
    }
}
