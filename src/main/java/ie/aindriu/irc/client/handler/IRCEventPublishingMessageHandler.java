package ie.aindriu.irc.client.handler;

import java.util.List;

import ie.aindriu.irc.client.event.EventHandler;

public class IRCEventPublishingMessageHandler extends EventPublishingMessageHandler<String> {
    
    
    public IRCEventPublishingMessageHandler(List<EventHandler<String>> eventHandlers) {
	super(eventHandlers);
    }
    

}
