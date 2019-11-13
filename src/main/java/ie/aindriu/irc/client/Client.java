package ie.aindriu.irc.client;

import ie.aindriu.irc.client.command.Command;

public interface Client {

    void connect();
    
    void register();
    
    void disconnect();

    void sendCommand(Command command);
    
    void sendString(String message);
    
}
