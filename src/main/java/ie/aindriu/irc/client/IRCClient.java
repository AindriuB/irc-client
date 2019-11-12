package ie.aindriu.irc.client;

import ie.aindriu.irc.client.command.Command;

public interface IRCClient {

    void connect();
    
    void register();
    
    void disconnect();

    void sendCommand(Command command);
    
    void sendString(String message);
    
}
