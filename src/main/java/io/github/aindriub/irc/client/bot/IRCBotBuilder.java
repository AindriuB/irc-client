package io.github.aindriub.irc.client.bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.aindriub.irc.client.configuration.ClientConfiguration;
import io.github.aindriub.irc.client.configuration.ClientConfigurationBuilder;

/**
 * Builds an {@link IRCBot}. Wraps {@link ClientConfigurationBuilder} rather than
 * replacing it: {@link #client()} reaches the full set of connection settings for
 * anything not surfaced here.
 */
public class IRCBotBuilder {

    private static final String DEFAULT_COMMAND_PREFIX = "!";

    /** The conventional TLS port for IRC. */
    private static final int DEFAULT_PORT = 6697;

    private final ClientConfigurationBuilder client = new ClientConfigurationBuilder();
    private final List<String> channels = new ArrayList<>();
    private final List<BotListener> listeners = new ArrayList<>();
    private final Map<String, CommandHandler> commands = new LinkedHashMap<>();

    private String commandPrefix = DEFAULT_COMMAND_PREFIX;
    private boolean portSet = false;
    private boolean trackChannelState = true;
    private boolean respondToCtcp = false;
    private boolean autoRejoinAfterKick = false;

    public IRCBotBuilder host(String host) {
        client.host(host);
        return this;
    }

    public IRCBotBuilder port(int port) {
        client.port(port);
        portSet = true;
        return this;
    }

    public IRCBotBuilder nick(String nick) {
        client.nick(nick);
        return this;
    }

    /**
     * Server password, or a Twitch {@code oauth:...} token.
     *
     * @throws IllegalArgumentException when {@code password} is not null and
     *                                  contains whitespace, starts with ':', or
     *                                  contains CR, LF or NUL. The value itself is
     *                                  never included in the message.
     */
    public IRCBotBuilder password(String password) {
        client.password(password);
        return this;
    }

    /**
     * Channels to join once registered. They are rejoined automatically after a
     * reconnect.
     */
    public IRCBotBuilder channels(String... channels) {
        this.channels.addAll(Arrays.asList(channels));
        return this;
    }

    public IRCBotBuilder listener(BotListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    /**
     * Registers a chat command. Matching is case insensitive, and the word must
     * include the prefix: {@code command("!hello", ...)}.
     */
    public IRCBotBuilder command(String command, CommandHandler handler) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(handler, "handler");
        if (!command.startsWith(commandPrefix)) {
            throw new IllegalArgumentException(
                    "command must start with the prefix '" + commandPrefix + "': " + command);
        }
        commands.put(command.toLowerCase(Locale.ROOT), handler);
        return this;
    }

    /**
     * Changes the command prefix from the default {@code !}. Set it before
     * registering any command.
     */
    public IRCBotBuilder commandPrefix(String commandPrefix) {
        if (!commands.isEmpty()) {
            throw new IllegalStateException(
                    "set the command prefix before registering commands");
        }
        if (commandPrefix == null || commandPrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("command prefix must not be blank");
        }
        this.commandPrefix = commandPrefix;
        return this;
    }

    /**
     * Keeps track of who is in each channel and what status they hold, readable
     * through {@link IRCBot#getChannelState(String)}. On by default; switch it off
     * for a bot that never asks, since a busy channel makes it work for nothing.
     */
    public IRCBotBuilder trackChannelState(boolean trackChannelState) {
        this.trackChannelState = trackChannelState;
        return this;
    }

    /**
     * Answers CTCP requests received in a PRIVMSG: {@code VERSION}, {@code PING}
     * and {@code TIME}. Off by default, since a bot that never opts in should see
     * no new traffic on the wire. CTCP {@code ACTION} and anything else unknown
     * are never answered, and a CTCP arriving as a NOTICE is never answered
     * either, per RFC 2812 section 3.3.2.
     */
    public IRCBotBuilder respondToCtcp(boolean respondToCtcp) {
        this.respondToCtcp = respondToCtcp;
        return this;
    }

    /**
     * Rejoins a channel the bot was kicked from. Off by default: a bot that keeps
     * getting kicked and keeps rejoining can look like it is fighting whoever kicked
     * it. The rejoin is sent without a key, so a keyed channel will most likely
     * have its JOIN refused by the server; the key the bot originally joined with,
     * if any, is not remembered or resent.
     */
    public IRCBotBuilder autoRejoinAfterKick(boolean autoRejoinAfterKick) {
        this.autoRejoinAfterKick = autoRejoinAfterKick;
        return this;
    }

    /**
     * The underlying client configuration, for TLS, timeouts, flood protection,
     * reconnection and everything else this builder does not surface.
     */
    public ClientConfigurationBuilder client() {
        return client;
    }

    public IRCBot build() {
        if (!portSet) {
            client.port(DEFAULT_PORT);
        }
        ClientConfiguration configuration = client.build();
        if (!configuration.getRegistration().isConfigured()) {
            throw new IllegalStateException("a nick is required");
        }
        return new IRCBot(configuration, channels, listeners, commands, commandPrefix,
                trackChannelState, respondToCtcp, autoRejoinAfterKick);
    }
}
