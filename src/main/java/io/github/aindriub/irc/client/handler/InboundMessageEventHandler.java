package io.github.aindriub.irc.client.handler;

import java.util.List;

import io.github.aindriub.irc.client.event.EventHandler;

public class InboundMessageEventHandler extends AbstractInboundEventHandler<String> {

    public InboundMessageEventHandler(List<EventHandler<String>> eventHandlers) {
        super(eventHandlers);
    }
}
