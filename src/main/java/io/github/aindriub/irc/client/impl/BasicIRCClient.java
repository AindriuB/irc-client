package io.github.aindriub.irc.client.impl;

import io.github.aindriub.irc.client.CommandClient;
import io.github.aindriub.irc.client.command.Command;
import io.github.aindriub.irc.client.command.Quit;
import io.github.aindriub.irc.client.configuration.ClientConfiguration;

public class BasicIRCClient extends AbstractClient implements CommandClient {

    public BasicIRCClient(ClientConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void sendCommand(Command command) {
        send(command.render());
    }

    @Override
    public void disconnect() {
        // Guarded: send() reconnects when the channel is down, so an unguarded QUIT
        // would dial the server just to say goodbye.
        if (isConnected()) {
            sendCommand(new Quit());
        }
        super.disconnect();
    }
}
