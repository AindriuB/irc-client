package io.github.aindriub.irc.client.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.CommandClient;
import io.github.aindriub.irc.client.IRCClientException;
import io.github.aindriub.irc.client.command.Command;
import io.github.aindriub.irc.client.command.Join;
import io.github.aindriub.irc.client.command.Part;
import io.github.aindriub.irc.client.command.Quit;
import io.github.aindriub.irc.client.configuration.ClientConfiguration;

public class BasicIRCClient extends AbstractClient implements CommandClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicIRCClient.class);

    /**
     * Channel to its key, or to NO_KEY when it needs none. Stored per channel rather
     * than as the original JOIN, because parting one channel of a multi-channel join
     * must not leave a command behind that would rejoin the parted one.
     */
    private final Map<String, String> joined = new LinkedHashMap<>();

    private static final String NO_KEY = "";

    public BasicIRCClient(ClientConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void sendCommand(Command command) {
        send(command.render());
        track(command);
    }

    /**
     * Remembers what has been joined, so an unexpected disconnection can be undone
     * fully rather than leaving a registered client sitting in no channels.
     */
    private void track(Command command) {
        synchronized (joined) {
            if (command instanceof Join) {
                Join join = (Join) command;
                if (join.isPartAll()) {
                    joined.clear();
                    return;
                }
                for (String channel : join.getChannels()) {
                    String key = join.getKey(channel);
                    joined.put(channel, key == null ? NO_KEY : key);
                }
            } else if (command instanceof Part) {
                for (String channel : ((Part) command).getChannels()) {
                    joined.remove(channel);
                }
            }
        }
    }

    /**
     * The channels this client believes it is in.
     */
    public List<String> getJoinedChannels() {
        synchronized (joined) {
            return new ArrayList<>(joined.keySet());
        }
    }

    @Override
    protected void onReconnected() {
        super.onReconnected();
        for (Join join : rejoinCommands()) {
            LOGGER.info("Rejoining {}", join.getChannels());
            // send() rather than sendCommand(): these channels are already tracked,
            // and re-tracking while rebuilding from that state would be circular.
            send(join.render());
        }
    }

    /**
     * Rebuilt from the current channel set rather than replayed from the original
     * commands, so a channel that has since been parted is not rejoined. Keyless
     * channels go in one JOIN; each keyed channel needs its own.
     */
    private List<Join> rejoinCommands() {
        List<String> keyless = new ArrayList<>();
        List<Join> commands = new ArrayList<>();
        synchronized (joined) {
            for (Map.Entry<String, String> entry : joined.entrySet()) {
                if (NO_KEY.equals(entry.getValue())) {
                    keyless.add(entry.getKey());
                } else {
                    commands.add(new Join(entry.getKey(), entry.getValue()));
                }
            }
        }
        if (!keyless.isEmpty()) {
            commands.add(0, new Join(keyless));
        }
        return commands;
    }

    @Override
    public void disconnect() {
        // Guarded: send() now throws when there is no connection, and a deliberate
        // disconnect of an already dead client should still shut down cleanly.
        if (isConnected()) {
            try {
                sendCommand(new Quit());
            } catch (IRCClientException e) {
                // The connection may die between the check and the write. We are
                // closing anyway, so a QUIT that never lands is not worth failing on.
                LOGGER.debug("Could not send QUIT before disconnecting", e);
            }
        }
        super.disconnect();
    }
}
