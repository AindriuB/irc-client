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
}
