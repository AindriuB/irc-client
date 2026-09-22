package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.github.aindriub.irc.client.configuration.FloodConfiguration;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;

public class OutboundRateLimiterTest {

    private static final long INTERVAL = 120;

    @Test
    public void letsTheBurstThroughImmediately() {
        EmbeddedChannel channel = channel(3, INTERVAL);

        channel.writeAndFlush("one");
        channel.writeAndFlush("two");
        channel.writeAndFlush("three");

        assertEquals("one", channel.readOutbound());
        assertEquals("two", channel.readOutbound());
        assertEquals("three", channel.readOutbound());
    }

    @Test
    public void holdsBackAnythingBeyondTheBurst() {
        OutboundRateLimiter limiter = new OutboundRateLimiter(config(2, INTERVAL));
        EmbeddedChannel channel = new EmbeddedChannel(limiter);

        channel.writeAndFlush("one");
        channel.writeAndFlush("two");
        channel.writeAndFlush("three");

        assertEquals("one", channel.readOutbound());
        assertEquals("two", channel.readOutbound());
        assertNull("the third must wait for a token", channel.readOutbound());
        assertEquals(1, limiter.queueDepth());
    }

    @Test
    public void releasesQueuedMessagesInOrderAsTokensArrive() throws Exception {
        EmbeddedChannel channel = channel(1, INTERVAL);

        channel.writeAndFlush("one");
        channel.writeAndFlush("two");
        channel.writeAndFlush("three");
        assertEquals("one", channel.readOutbound());

        List<String> released = drainOverTime(channel, 2);

        assertEquals("order must be preserved", java.util.Arrays.asList("two", "three"),
                released);
    }

    @Test
    public void completesAWritePromiseOnlyWhenTheMessageActuallyGoesOut() throws Exception {
        EmbeddedChannel channel = channel(1, INTERVAL);

        channel.writeAndFlush("one");
        ChannelFuture queued = channel.writeAndFlush("two");
        assertEquals("one", channel.readOutbound());

        assertFalse("still waiting for a token", queued.isDone());

        drainOverTime(channel, 1);

        assertTrue(queued.isDone());
        assertTrue(queued.isSuccess());
    }

    @Test
    public void failsQueuedWritesWhenTheConnectionGoesAway() throws Exception {
        EmbeddedChannel channel = channel(1, INTERVAL);

        channel.writeAndFlush("one");
        ChannelFuture queued = channel.writeAndFlush("two");
        channel.close().sync();

        assertTrue("a caller must not wait forever on a message that will never go",
                queued.isDone());
        assertFalse(queued.isSuccess());
        assertTrue(String.valueOf(queued.cause()),
                queued.cause() instanceof ClosedChannelException);
    }

    @Test
    public void refillsGraduallyRatherThanAllAtOnce() throws Exception {
        EmbeddedChannel channel = channel(1, INTERVAL);
        channel.writeAndFlush("one");
        channel.readOutbound();

        // Long enough for several intervals, so the bucket is full again.
        Thread.sleep(INTERVAL * 3);
        channel.runPendingTasks();

        channel.writeAndFlush("two");
        assertEquals("a saved up token releases immediately", "two", channel.readOutbound());
    }

    @Test
    public void neverSavesUpMoreThanTheBurst() throws Exception {
        EmbeddedChannel channel = channel(2, INTERVAL);

        // Idle far longer than it takes to refill, then send a long run at once.
        Thread.sleep(INTERVAL * 5);
        channel.runPendingTasks();
        for (int i = 0; i < 5; i++) {
            channel.writeAndFlush("m" + i);
        }

        assertEquals("m0", channel.readOutbound());
        assertEquals("m1", channel.readOutbound());
        assertNull("the bucket is capped at the burst size", channel.readOutbound());
    }

    @Test
    public void flushWithNothingQueuedStillReachesTheChannel() {
        EmbeddedChannel channel = channel(5, INTERVAL);

        channel.write("one");
        channel.flush();

        assertEquals("one", channel.readOutbound());
    }

    private static List<String> drainOverTime(EmbeddedChannel channel, int expected)
            throws InterruptedException {
        List<String> released = new ArrayList<>();
        long deadline = System.currentTimeMillis() + INTERVAL * expected * 10 + 1000;
        while (released.size() < expected && System.currentTimeMillis() < deadline) {
            channel.runPendingTasks();
            String next = channel.readOutbound();
            if (next != null) {
                released.add(next);
            } else {
                Thread.sleep(10);
            }
        }
        return released;
    }

    private static EmbeddedChannel channel(int burst, long interval) {
        return new EmbeddedChannel(new OutboundRateLimiter(config(burst, interval)));
    }

    private static FloodConfiguration config(int burst, long interval) {
        FloodConfiguration flood = new FloodConfiguration();
        flood.setBurst(burst);
        flood.setInterval(interval);
        return flood;
    }
}
