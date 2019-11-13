package ie.aindriu.irc.client.impl;

import java.util.List;

import ie.aindriu.irc.client.Connection;
import ie.aindriu.irc.client.command.Command;
import ie.aindriu.irc.client.event.EventHandler;

public class BasicIRCClient extends AbstractIRCClient {

    
    public BasicIRCClient(Connection connection) {
	super(connection);
    }
    
    public BasicIRCClient(Connection connection,List<EventHandler<String>> eventHandlers) {
	super(connection, eventHandlers);
    }

    @Override
    public void register() {
	// TODO Auto-generated method stub
	
    }

    

}
