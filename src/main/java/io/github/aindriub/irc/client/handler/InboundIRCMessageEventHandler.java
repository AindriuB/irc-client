package io.github.aindriub.irc.client.handler;

import java.util.List;

import io.github.aindriub.irc.client.event.EventHandler;
import io.github.aindriub.irc.client.message.IRCMessage;

public class InboundIRCMessageEventHandler extends AbstractInboundEventHandler<IRCMessage> {

    public InboundIRCMessageEventHandler(List<EventHandler<IRCMessage>> eventHandlers) {
        super(eventHandlers);
    }
}
