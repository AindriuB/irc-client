package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.event.EventHandler;
import io.netty.channel.embedded.EmbeddedChannel;

public class AbstractInboundEventHandlerTest {

    private final List<String> first = new ArrayList<>();
    private final List<String> second = new ArrayList<>();

    @Test
    public void publishesToEverySubscriber() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new InboundMessageEventHandler(Arrays.asList(collect(first), collect(second))));

        channel.writeInbound("hello");

        assertEquals(Arrays.asList("hello"), first);
        assertEquals(Arrays.asList("hello"), second);
    }

    @Test
    public void toleratesANullHandlerList() {
        EmbeddedChannel channel = new EmbeddedChannel(new InboundMessageEventHandler(null));

        channel.writeInbound("hello");

        assertTrue("no subscribers means nothing happens, not a crash", channel.isOpen());
    }

    @Test
    public void oneFailingSubscriberDoesNotStopTheOthers() {
        EventHandler<String> exploding = new EventHandler<String>() {
            @Override
            public void publishEvent(Event<String> event) {
                throw new IllegalStateException("subscriber is broken");
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(
                new InboundMessageEventHandler(Arrays.asList(exploding, collect(second))));

        channel.writeInbound("hello");

        assertEquals("the later subscriber still ran", Arrays.asList("hello"), second);
        assertTrue("a broken subscriber must not close the channel", channel.isOpen());
    }

    @Test
    public void isNotAffectedByLaterChangesToTheHandlerList() {
        List<EventHandler<String>> handlers = new ArrayList<>();
        handlers.add(collect(first));
        EmbeddedChannel channel = new EmbeddedChannel(new InboundMessageEventHandler(handlers));

        handlers.add(collect(second));
        channel.writeInbound("hello");

        assertEquals(Arrays.asList("hello"), first);
        assertTrue("the list is copied at construction", second.isEmpty());
    }

    @Test
    public void closesTheChannelOnAPipelineException() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new InboundMessageEventHandler(Arrays.asList(collect(first))));

        channel.pipeline().fireExceptionCaught(new IllegalStateException("pipeline broke"));

        assertFalse(channel.isOpen());
    }

    private static EventHandler<String> collect(final List<String> into) {
        return new EventHandler<String>() {
            @Override
            public void publishEvent(Event<String> event) {
                into.add(event.getPayload());
            }
        };
    }
}
