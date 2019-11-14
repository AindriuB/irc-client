package ie.aindriu.irc.client.impl;

import ie.aindriu.irc.client.CommandClient;
import ie.aindriu.irc.client.command.Command;
import ie.aindriu.irc.client.command.Quit;
import ie.aindriu.irc.client.configuration.ClientConfiguration;

public class BasicIRCClient extends AbstractClient implements CommandClient {

    
    public BasicIRCClient(ClientConfiguration configuration) {
	super(configuration);
    }
    
    
    @Override
    public void sendCommand(Command command) {
	send(command.toString());
    }
    
    
    @Override
    public void disconnect() {
	sendCommand(new Quit());
        super.disconnect();
    }

}
