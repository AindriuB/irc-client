package ie.aindriu.irc.client;

public interface IRCClient {

    void connect();
    
    void register();
    
    void disconnect();
    
    void sendString(String message);
    
}
