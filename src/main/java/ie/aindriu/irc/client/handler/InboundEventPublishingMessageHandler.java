package ie.aindriu.irc.client.handler;

import java.util.List;

import ie.aindriu.irc.client.event.EventHandler;

public class InboundEventPublishingMessageHandler extends EventPublishingMessageHandler<String> {
    
    
    public InboundEventPublishingMessageHandler(List<EventHandler<String>> eventHandlers) {
	super(eventHandlers);
    }
    

}
