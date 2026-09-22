package io.github.aindriub.irc.client;

public interface Client {

    void connect();
    
    void disconnect();
    
    void send(String payload);
    
}
