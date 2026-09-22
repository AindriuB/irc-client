package io.github.aindriub.irc.client.handler;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.event.EventHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public abstract class AbstractInboundEventHandler<T> extends SimpleChannelInboundHandler<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractInboundEventHandler.class);

    private final List<EventHandler<T>> eventHandlers;

    protected AbstractInboundEventHandler(List<EventHandler<T>> eventHandlers) {
        this.eventHandlers = eventHandlers == null ? new ArrayList<>() : new ArrayList<>(eventHandlers);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, T msg) throws Exception {
        // The handler list is already typed to T, so every subscriber accepts this
        // payload. The previous runtime type check compared the handler's generic
        // superclass to the message class and therefore never matched anything.
        final Event<T> event = new Event<>(msg);
        for (EventHandler<T> eventHandler : eventHandlers) {
            try {
                eventHandler.publishEvent(event);
            } catch (RuntimeException e) {
                // One misbehaving subscriber must not stop the others or kill the channel.
                LOGGER.error("Event handler {} failed", eventHandler.getClass().getName(), e);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Error handling message", cause);
        ctx.close();
    }
}
