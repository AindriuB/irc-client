package io.github.aindriub.irc.client.bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.IRCText;
import io.github.aindriub.irc.client.command.Away;
import io.github.aindriub.irc.client.command.Invite;
import io.github.aindriub.irc.client.command.Join;
import io.github.aindriub.irc.client.command.Kick;
import io.github.aindriub.irc.client.command.Messages;
import io.github.aindriub.irc.client.command.Mode;
import io.github.aindriub.irc.client.command.Notice;
import io.github.aindriub.irc.client.command.Part;
import io.github.aindriub.irc.client.command.PrivMsg;
import io.github.aindriub.irc.client.command.Topic;
import io.github.aindriub.irc.client.configuration.ClientConfiguration;
import io.github.aindriub.irc.client.event.MessageListener;
import io.github.aindriub.irc.client.impl.BasicIRCClient;
import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.message.Numerics;
import io.github.aindriub.irc.client.state.ChannelState;
import io.github.aindriub.irc.client.state.ChannelStateTracker;

/**
 * A chat bot on top of {@link BasicIRCClient}: connects, registers, joins its
 * channels and routes what arrives to listeners and command handlers.
 *
 * <pre>
 * IRCBot bot = IRCBot.builder()
 *         .host("irc.example.org")
 *         .nick("mybot")
 *         .channels("#chat")
 *         .command("!hello", (context, args) -&gt; context.reply("hello " + context.getSender()))
 *         .build();
 * bot.start();
 * </pre>
 */
public class IRCBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(IRCBot.class);

    private final BasicIRCClient client;
    private final ClientConfiguration configuration;
    private final List<String> channels;
    private final List<BotListener> listeners;
    private final Map<String, CommandHandler> commands;
    private final String commandPrefix;
    private final boolean respondToCtcp;

    /**
     * The nick actually in use, which is not always the configured one: the server
     * may have sent us away with a modified nick after a collision.
     */
    private volatile String nick;

    /**
     * Null when channel tracking was switched off, since keeping a member list for
     * a busy channel is work a bot that never reads it should not pay for.
     */
    private final ChannelStateTracker channelState;

    IRCBot(ClientConfiguration configuration, List<String> channels,
            List<BotListener> listeners, Map<String, CommandHandler> commands,
            String commandPrefix, boolean trackChannelState, boolean respondToCtcp) {
        this.configuration = configuration;
        this.channels = new ArrayList<>(channels);
        this.listeners = new CopyOnWriteArrayList<>(listeners);
        this.commands = new LinkedHashMap<>(commands);
        this.commandPrefix = commandPrefix;
        this.respondToCtcp = respondToCtcp;
        this.nick = configuration.getRegistration().getNick();
        this.channelState = trackChannelState
                ? new ChannelStateTracker(configuration.getRegistration().getNick()) : null;
        if (channelState != null) {
            // Before the router, so a listener asking who is in a channel during
            // onJoin sees the join that triggered it.
            configuration.getMessageHandlers().add(channelState);
        }
        configuration.getMessageHandlers().add(new Router());
        this.client = new BasicIRCClient(configuration) {
            @Override
            protected void onReconnected() {
                super.onReconnected();
                // The client rejoins tracked channels itself; this is only to tell
                // listeners the bot is usable again.
                announceReady();
            }
        };
    }

    public static IRCBotBuilder builder() {
        return new IRCBotBuilder();
    }

    /**
     * Connects, registers and joins the configured channels. Returns once the bot is
     * in its channels.
     */
    public void start() {
        client.connect();
        joinConfiguredChannels();
        announceReady();
    }

    /**
     * Parts cleanly and shuts down. The bot cannot be started again afterwards.
     */
    public void stop() {
        client.disconnect();
    }

    public boolean isRunning() {
        return client.isConnected() && client.isRegistered();
    }

    /**
     * The nick the bot is using, which may differ from the configured one if it was
     * taken.
     */
    public String getNick() {
        return nick;
    }

    public List<String> getChannels() {
        return client.getJoinedChannels();
    }

    /**
     * Who is in a channel and what status they hold, or null when the channel is not
     * joined or tracking is switched off.
     */
    public ChannelState getChannelState(String channel) {
        return channelState == null ? null : channelState.getChannel(channel);
    }

    /**
     * The tracker itself, or null when tracking is switched off.
     */
    public ChannelStateTracker getChannelStateTracker() {
        return channelState;
    }

    /**
     * Sends a message to a channel or a user, splitting it if it is too long for
     * one IRC message.
     *
     * <p>Splitting rather than failing, because a bot's text is usually assembled
     * from something it did not choose - a quote, a search result, a line of chat -
     * and the alternative is for the server to truncate it silently. The lower
     * level {@link io.github.aindriub.irc.client.impl.BasicIRCClient#sendCommand}
     * still refuses an over-long line, so nothing is split behind the back of a
     * caller who built the command themselves.
     */
    public void say(String target, String text) {
        for (PrivMsg message : PrivMsg.split(target, text, charset())) {
            client.sendCommand(message);
        }
    }

    /**
     * Sends a NOTICE, which other clients will not auto-respond to. Split on the
     * same terms as {@link #say}.
     */
    public void notice(String target, String text) {
        for (Notice message : Notice.split(target, text, charset())) {
            client.sendCommand(message);
        }
    }

    /**
     * Sends a CTCP ACTION, the {@code /me} of most clients: {@code * nick text}.
     * Split on the same terms as {@link #say}, with each line wrapped in its own
     * complete {@code \u0001ACTION ...\u0001} rather than an action's text being
     * cut across several lines.
     *
     * @throws IllegalArgumentException when text contains a CTCP delimiter
     *     ({@code \u0001}), CR, LF or NUL, since those cannot appear inside a
     *     CTCP argument
     */
    public void action(String target, String text) {
        String prefix = "PRIVMSG " + target + " :\u0001ACTION ";
        String suffix = "\u0001";
        int overhead = IRCText.byteLength(prefix, charset()) + IRCText.byteLength(suffix, charset())
                + RELAY_PREFIX_ALLOWANCE + 2;
        for (String piece : Messages.split(text, overhead, charset())) {
            client.sendCommand(
                    new PrivMsg(target, Ctcp.build("ACTION", piece.isEmpty() ? null : piece)));
        }
    }

    /**
     * Room left for the {@code :nick!user@host } the server prepends when it
     * relays a message, mirroring {@code PrivMsg}'s own allowance.
     */
    private static final int RELAY_PREFIX_ALLOWANCE = 100;

    private java.nio.charset.Charset charset() {
        return configuration.getCharSet();
    }

    public void join(String channel) {
        client.sendCommand(new Join(channel));
    }

    public void part(String channel) {
        client.sendCommand(new Part(channel));
    }

    /**
     * Removes someone from a channel. The bot needs operator status.
     */
    public void kick(String channel, String nick, String reason) {
        client.sendCommand(new Kick(channel, nick, reason));
    }

    /**
     * Sets a channel topic.
     */
    public void topic(String channel, String topic) {
        client.sendCommand(new Topic(channel, topic));
    }

    /**
     * Sets modes, for example {@code mode("#chan", "+o", "someone")}.
     */
    public void mode(String target, String modes, String... args) {
        client.sendCommand(new Mode(target, modes, args));
    }

    public void invite(String nick, String channel) {
        client.sendCommand(new Invite(nick, channel));
    }

    /**
     * Marks the bot away, so anyone messaging it gets an automatic reply.
     */
    public void away(String message) {
        client.sendCommand(new Away(message));
    }

    /**
     * Clears the away status.
     */
    public void back() {
        client.sendCommand(Away.back());
    }

    public void addListener(BotListener listener) {
        listeners.add(listener);
    }

    /**
     * The underlying client, for anything the bot API does not cover.
     */
    public BasicIRCClient getClient() {
        return client;
    }

    private void joinConfiguredChannels() {
        if (!channels.isEmpty()) {
            client.sendCommand(new Join(channels));
        }
    }

    private void announceReady() {
        for (BotListener listener : listeners) {
            safely(listener, "onReady", new Runnable() {
                @Override
                public void run() {
                    listener.onReady(IRCBot.this);
                }
            });
        }
    }

    /**
     * One misbehaving listener should not stop the others, nor take down the
     * connection it is running on.
     */
    private void safely(BotListener listener, String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.error("Listener {} failed in {}", listener.getClass().getName(), what, e);
        }
    }

    /**
     * Turns parsed messages into bot events.
     */
    private final class Router extends MessageListener {

        @Override
        protected void onMessage(String target, String sender, String text, IRCMessage raw) {
            if (isFromSelf(sender)) {
                // Some servers echo our own messages back. Dispatching them would let
                // a bot answer itself forever.
                return;
            }
            final MessageContext context = new MessageContext(IRCBot.this, raw, target, sender,
                    text);
            respondToCtcpIfEnabled(context);
            dispatchCommand(context);
            for (final BotListener listener : listeners) {
                safely(listener, "onMessage", new Runnable() {
                    @Override
                    public void run() {
                        listener.onMessage(context);
                    }
                });
            }
        }

        @Override
        protected void onNotice(String target, String sender, String text, IRCMessage raw) {
            if (isFromSelf(sender)) {
                return;
            }
            final MessageContext context = new MessageContext(IRCBot.this, raw, target, sender,
                    text);
            for (final BotListener listener : listeners) {
                safely(listener, "onNotice", new Runnable() {
                    @Override
                    public void run() {
                        listener.onNotice(context);
                    }
                });
            }
        }

        @Override
        protected void onJoin(final String channel, final String joiner, IRCMessage raw) {
            for (final BotListener listener : listeners) {
                safely(listener, "onJoin", new Runnable() {
                    @Override
                    public void run() {
                        listener.onJoin(IRCBot.this, channel, joiner);
                    }
                });
            }
        }

        @Override
        protected void onPart(final String channel, final String parter, IRCMessage raw) {
            for (final BotListener listener : listeners) {
                safely(listener, "onPart", new Runnable() {
                    @Override
                    public void run() {
                        listener.onPart(IRCBot.this, channel, parter);
                    }
                });
            }
        }

        @Override
        protected void onQuit(final String quitter, final String reason, IRCMessage raw) {
            for (final BotListener listener : listeners) {
                safely(listener, "onQuit", new Runnable() {
                    @Override
                    public void run() {
                        listener.onQuit(IRCBot.this, quitter, reason);
                    }
                });
            }
        }

        @Override
        protected void onNickChange(String oldNick, String newNick, IRCMessage raw) {
            if (isFromSelf(oldNick)) {
                nick = newNick;
            }
        }

        private void rememberNick(String actualNick) {
            nick = actualNick;
            if (channelState != null) {
                channelState.setSelfNick(actualNick);
            }
        }

        @Override
        protected void onNumeric(String numeric, IRCMessage raw) {
            if (Numerics.RPL_WELCOME.equals(numeric) && raw.getParam(0) != null) {
                // Authoritative: this is the nick the server actually gave us, which
                // differs from the configured one after a collision.
                rememberNick(raw.getParam(0));
            }
            forwardAsOther(raw);
        }

        @Override
        protected void onOther(IRCMessage raw) {
            forwardAsOther(raw);
        }

        private void forwardAsOther(final IRCMessage raw) {
            for (final BotListener listener : listeners) {
                safely(listener, "onOther", new Runnable() {
                    @Override
                    public void run() {
                        listener.onOther(IRCBot.this, raw);
                    }
                });
            }
        }
    }

    private boolean isFromSelf(String sender) {
        return sender != null && sender.equalsIgnoreCase(nick);
    }

    /**
     * The reply text for a CTCP VERSION request. Not configurable: bots wanting a
     * different string can answer VERSION themselves via a {@link BotListener}.
     */
    private static final String CTCP_VERSION_REPLY = "irc-client (https://github.com/aindriub/irc-client)";

    private void respondToCtcpIfEnabled(MessageContext context) {
        if (!respondToCtcp || !context.isCtcp()) {
            return;
        }
        String sender = context.getSender();
        if (sender == null) {
            // Servers probe a freshly connected client with a CTCP VERSION that
            // arrives with a server prefix rather than a user's. There is nobody
            // to NOTICE back.
            return;
        }
        String command = context.getCtcpCommand();
        if (!"VERSION".equals(command) && !"PING".equals(command) && !"TIME".equals(command)) {
            // ACTION and anything unknown: no reply.
            return;
        }
        try {
            String reply;
            if ("VERSION".equals(command)) {
                reply = Ctcp.build("VERSION", CTCP_VERSION_REPLY);
            } else if ("PING".equals(command)) {
                reply = Ctcp.build("PING", context.getCtcpArgument());
            } else {
                reply = Ctcp.build("TIME", java.time.ZonedDateTime.now().toString());
            }
            Notice noticeCommand = new Notice(sender, reply);
            // Reserve the same room PrivMsg/action() leave for the
            // :nick!user@host the server prepends when it relays the message, so
            // a reply that fits unrelayed cannot be truncated past its closing
            // \u0001 once the server has prefixed it.
            if (IRCText.byteLength(noticeCommand.render(), charset()) + RELAY_PREFIX_ALLOWANCE + 2
                    > IRCText.MAX_MESSAGE_BYTES) {
                // Splitting would break the CTCP framing: half a
                // \u0001COMMAND ...\u0001 in one line and plain text in the next.
                // Sending nothing is safer than sending garbage.
                LOGGER.debug("CTCP {} reply to {} does not fit in one line, dropping", command,
                        sender);
                return;
            }
            client.sendCommand(noticeCommand);
        } catch (RuntimeException e) {
            // A responder failure - building the reply or sending it - must
            // never stop dispatchCommand or the onMessage listeners that follow.
            LOGGER.warn("CTCP {} responder failed for {}", command, sender, e);
        }
    }

    private void dispatchCommand(MessageContext context) {
        if (commands.isEmpty()) {
            return;
        }
        String text = context.getPlainText().trim();
        if (!text.startsWith(commandPrefix)) {
            return;
        }
        String[] parts = text.split("\\s+");
        final CommandHandler handler = commands.get(parts[0].toLowerCase(Locale.ROOT));
        if (handler == null) {
            return;
        }
        final List<String> args = Arrays.asList(parts).subList(1, parts.length);
        try {
            handler.handle(context, new ArrayList<>(args));
        } catch (RuntimeException e) {
            LOGGER.error("Command {} failed", parts[0], e);
        }
    }
}
