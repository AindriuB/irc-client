package ie.aindriu.irc.client;

import ie.aindriu.irc.client.command.Command;

public interface CommandClient {
    
    void sendCommand(Command command);
   
    
}
