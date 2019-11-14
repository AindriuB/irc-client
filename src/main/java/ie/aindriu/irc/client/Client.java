package ie.aindriu.irc.client;

public interface Client {

    void connect();
    
    void disconnect();
    
    void send(String payload);
    
}
