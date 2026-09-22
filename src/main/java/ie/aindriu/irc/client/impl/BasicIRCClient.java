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
        // Guarded: send() reconnects when the channel is down, so an unguarded QUIT
        // would dial the server just to say goodbye.
        if (isConnected()) {
            sendCommand(new Quit());
        }
        super.disconnect();
    }
}
