package ie.aindriu.irc.client;

import ie.aindriu.irc.client.command.Command;

public interface Client {

    void connect();
    
    void disconnect();

    void sendCommand(Command command);
    
    void send(byte[] payload);
    
}
