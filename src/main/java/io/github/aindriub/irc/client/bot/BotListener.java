package io.github.aindriub.irc.client.bot;

import io.github.aindriub.irc.client.message.IRCMessage;

/**
 * Bot events. Override only what you need; every method does nothing by default.
 */
public abstract class BotListener {

    /**
     * A message in a channel or sent directly to the bot. Messages the bot itself
     * sent are never delivered here, so a bot cannot answer itself into a loop.
     */
    public void onMessage(MessageContext context) {
    }

    /**
     * A NOTICE. Never reply automatically to one (RFC 2812 section 3.3.2).
     */
    public void onNotice(MessageContext context) {
    }

    public void onJoin(IRCBot bot, String channel, String nick) {
    }

    public void onPart(IRCBot bot, String channel, String nick) {
    }

    public void onQuit(IRCBot bot, String nick, String reason) {
    }

    /**
     * Someone was removed from a channel, possibly the bot itself.
     *
     * @param bot     the bot
     * @param channel the channel the kick happened in
     * @param kicked  the nick that was kicked
     * @param by      the nick that did the kicking, or null when a server did it
     * @param reason  the kick reason, or null when none was given
     */
    public void onKick(IRCBot bot, String channel, String kicked, String by, String reason) {
    }

    /**
     * The bot has registered and joined its channels, on first connect and again
     * after every reconnect.
     */
    public void onReady(IRCBot bot) {
    }

    /**
     * Anything without a dedicated callback.
     */
    public void onOther(IRCBot bot, IRCMessage message) {
    }

    /**
     * The connection was lost unexpectedly. Runs on the event loop and must not
     * block. Fires only when the connection is actually lost, never for a
     * deliberate {@code disconnect()}/shutdown, since the application already
     * knows it asked for that. If reconnection is enabled, a successful
     * reconnect is still reported through {@link #onReady}, not here.
     */
    public void onDisconnected(IRCBot bot) {
    }

    /**
     * A reconnect attempt is about to be made. Runs on the event loop and must
     * not block. Reconnection success is reported through {@link #onReady},
     * not here.
     *
     * @param bot         the bot
     * @param attempt     the attempt number, counting from 1
     * @param delayMillis the delay before this attempt actually runs
     */
    public void onReconnecting(IRCBot bot, int attempt, long delayMillis) {
    }

    /**
     * Reconnection was abandoned after the configured number of attempts. Runs
     * on the event loop and must not block.
     *
     * @param bot      the bot
     * @param attempts the number of attempts made before giving up
     */
    public void onGaveUp(IRCBot bot, int attempts) {
    }
}
