package ie.aindriu.irc.client.handler;

import java.util.List;

import ie.aindriu.irc.client.event.EventHandler;

public class InboundMessageEventHandler extends AbstractInboundEventHandler<String> {
    
    
    public InboundMessageEventHandler(List<EventHandler<String>> eventHandlers) {
	super(eventHandlers);
    }
    

}
