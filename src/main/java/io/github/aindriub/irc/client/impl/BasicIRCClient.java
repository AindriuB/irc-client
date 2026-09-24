package io.github.aindriub.irc.client.impl;

import java.util.ArrayList;
import java.util.Iterator;
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
import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.message.IRCMessageParser;
import io.github.aindriub.irc.client.message.IRCParseException;
import io.github.aindriub.irc.client.message.Numerics;
import io.github.aindriub.irc.client.state.CaseMapping;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class BasicIRCClient extends AbstractClient implements CommandClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicIRCClient.class);

    /**
     * Channel to its key, or to NO_KEY when it needs none. Stored per channel rather
     * than as the original JOIN, because parting one channel of a multi-channel join
     * must not leave a command behind that would rejoin the parted one.
     */
    private final Map<String, String> joined = new LinkedHashMap<>();

    private static final String NO_KEY = "";

    /**
     * This client's own nick, so an inbound KICK can be told apart from one aimed at
     * someone else. Starts as the configured nick and is corrected by RPL_WELCOME
     * (the server may hand back a different one after a collision retry) and by any
     * later NICK naming this client.
     */
    private volatile String selfNick;

    /**
     * The value of the ISUPPORT {@code CASEMAPPING} token, or null before one has
     * arrived. Read through {@link #getCaseMapping()} rather than directly, so the
     * RFC 2812 default and the unrecognised-value fallback live in one place.
     */
    private volatile String caseMappingToken;

    public BasicIRCClient(ClientConfiguration configuration) {
        super(configuration);
        this.selfNick = configuration.getRegistration().getNick();
    }

    /**
     * How this server folds case when comparing nicks and channel names, from its
     * ISUPPORT {@code CASEMAPPING} token.
     *
     * @return RFC1459 before ISUPPORT has arrived (the RFC 2812 default), the
     *         mapping the server advertised once it has, or ASCII if that value is
     *         not one this client recognises
     */
    public CaseMapping getCaseMapping() {
        return CaseMapping.forToken(caseMappingToken);
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
                    addChannel(channel, key == null ? NO_KEY : key);
                }
            } else if (command instanceof Part) {
                for (String channel : ((Part) command).getChannels()) {
                    removeChannel(channel);
                }
            }
        }
    }

    /**
     * Records a channel as joined, replacing any entry that already matches under
     * the current case mapping rather than adding a second one: joining {@code #a}
     * after {@code #A} must not leave both spellings tracked, or a reconnect would
     * send a JOIN for a channel the server considers one and the same.
     */
    private void addChannel(String channel, String key) {
        synchronized (joined) {
            removeChannel(channel);
            joined.put(channel, key);
        }
    }

    /**
     * Drops a channel using the current case mapping rather than a plain string
     * match, so parting or being kicked from {@code #a} also forgets a channel
     * tracked as {@code #A}.
     */
    private void removeChannel(String channel) {
        synchronized (joined) {
            CaseMapping caseMapping = getCaseMapping();
            Iterator<String> tracked = joined.keySet().iterator();
            while (tracked.hasNext()) {
                if (caseMapping.equals(tracked.next(), channel)) {
                    tracked.remove();
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

    /**
     * Adds {@link ChannelKickTrackingHandler} ahead of everything that decodes IRC
     * messages for subscribers, so a KICK of this client is forgotten before any
     * configured listener sees it, and whether or not the caller configured any
     * message handlers at all: {@link AbstractClient#configurePipeline} only wires
     * up its own decoder when {@code configuration.getMessageHandlers()} is
     * non-empty, and this tracking must not depend on that.
     */
    @Override
    protected void configurePipeline(Channel ch) {
        // Reset before the new registration, not after it fails or succeeds: state
        // from the previous connection (a CASEMAPPING that server no longer sends,
        // a nick a collision retry left us on) must not survive into the next one.
        selfNick = configuration.getRegistration().getNick();
        caseMappingToken = null;
        super.configurePipeline(ch);
        ch.pipeline().addAfter("stringDecoder", "channelKickTrackingHandler",
                new ChannelKickTrackingHandler());
    }

    /**
     * Watches every inbound line for what this client needs to keep {@code joined}
     * honest and {@link #getCaseMapping()} current, independent of whatever the
     * caller configured: RPL_WELCOME and NICK for this client's own nick, ISUPPORT
     * for CASEMAPPING, and KICK to drop a channel this client was thrown out of.
     */
    private class ChannelKickTrackingHandler extends SimpleChannelInboundHandler<String> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String line) throws Exception {
            try {
                handle(IRCMessageParser.parse(line));
            } catch (IRCParseException e) {
                // A malformed line is someone else's problem to report; this
                // tracking must not take the connection down over it.
                LOGGER.debug("Skipping unparseable line while tracking channels: {}", line, e);
            }
            ctx.fireChannelRead(line);
        }

        private void handle(IRCMessage message) {
            String command = message.getCommand();
            if (Numerics.RPL_WELCOME.equals(command)) {
                // Param 0, not getNick(): RPL_WELCOME's prefix is the server, and
                // the nick actually in use (which a collision retry may have
                // changed) is the first parameter.
                String nick = message.getParam(0);
                if (nick != null) {
                    selfNick = nick;
                }
            } else if (Numerics.RPL_ISUPPORT.equals(command)) {
                applyCaseMapping(message);
            } else if ("NICK".equals(command)) {
                String oldNick = message.getNick();
                String newNick = message.getParam(0);
                if (oldNick != null && newNick != null && isSelf(oldNick)) {
                    selfNick = newNick;
                }
            } else if ("KICK".equals(command)) {
                String channel = message.getParam(0);
                String kicked = message.getParam(1);
                if (channel != null && kicked != null && isSelf(kicked)) {
                    removeChannel(channel);
                }
            }
        }

        private boolean isSelf(String nick) {
            return getCaseMapping().equals(selfNick, nick);
        }

        /**
         * {@code 005 <nick> TOKEN TOKEN=value ... :are supported by this server}.
         * Only CASEMAPPING is of interest here; the rest is ChannelStateTracker's
         * job (task 01) when channel state tracking is wanted.
         */
        private void applyCaseMapping(IRCMessage message) {
            int last = message.getParams().size();
            if (message.hasTrailing()) {
                // The trailing part is the human readable "are supported by this
                // server", not a token.
                last--;
            }
            for (int i = 1; i < last; i++) {
                String token = message.getParam(i);
                if (token == null || token.isEmpty()) {
                    continue;
                }
                if (token.charAt(0) == '-') {
                    // A negated token, e.g. "-CASEMAPPING": the server is taking back
                    // an earlier advertisement, which for CASEMAPPING means reverting
                    // to the RFC 2812 default rather than keeping the stale value.
                    if ("CASEMAPPING".equalsIgnoreCase(token.substring(1))) {
                        caseMappingToken = null;
                    }
                    continue;
                }
                int equals = token.indexOf('=');
                String key = equals < 0 ? token : token.substring(0, equals);
                if ("CASEMAPPING".equalsIgnoreCase(key)) {
                    caseMappingToken = equals < 0 ? null : token.substring(equals + 1);
                }
            }
        }
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
