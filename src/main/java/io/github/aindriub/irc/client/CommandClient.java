package io.github.aindriub.irc.client;

import io.github.aindriub.irc.client.command.Command;

public interface CommandClient {
    
    void sendCommand(Command command);
   
    
}
