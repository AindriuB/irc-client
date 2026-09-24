package io.github.aindriub.irc.client.bot;

import io.github.aindriub.irc.client.message.IRCFormatting;
import io.github.aindriub.irc.client.message.IRCMessage;

/**
 * One received message, and the means to answer it.
 */
public class MessageContext {

    private final IRCBot bot;
    private final IRCMessage raw;
    private final String target;
    private final String sender;
    private final String text;

    MessageContext(IRCBot bot, IRCMessage raw, String target, String sender, String text) {
        this.bot = bot;
        this.raw = raw;
        this.target = target;
        this.sender = sender;
        this.text = text;
    }

    /**
     * The bot that received this, for anything the context does not cover.
     */
    public IRCBot getBot() {
        return bot;
    }

    /**
     * The full parsed message, including IRCv3 tags.
     */
    public IRCMessage getRaw() {
        return raw;
    }

    /**
     * Where the message was sent: a channel, or the bot's own nick for a direct
     * message.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Who sent it, or null when it came from the server rather than a user.
     */
    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    /**
     * The text with mIRC formatting codes (bold, colour and the like) stripped,
     * so command matching and other text processing can ignore how it would have
     * rendered.
     */
    public String getPlainText() {
        return IRCFormatting.strip(text);
    }

    /**
     * True when the text is a CTCP payload, such as {@code \u0001ACTION waves\u0001}.
     */
    public boolean isCtcp() {
        return Ctcp.isCtcp(text);
    }

    /**
     * The CTCP command, upper-cased, or null when the text is not CTCP.
     */
    public String getCtcpCommand() {
        return Ctcp.command(text);
    }

    /**
     * The CTCP argument, or null when there is none, or when the text is not
     * CTCP.
     */
    public String getCtcpArgument() {
        return Ctcp.argument(text);
    }

    /**
     * True when this was sent straight to the bot rather than to a channel.
     */
    public boolean isPrivate() {
        return !IRCMessage.isChannel(target);
    }

    /**
     * Where a reply belongs: the channel for a channel message, the sender for a
     * direct one. Answering a direct message to its "target" would send it to the
     * bot's own nick.
     */
    public String getReplyTarget() {
        return isPrivate() ? sender : target;
    }

    /**
     * Replies where the message came from.
     */
    public void reply(String text) {
        bot.say(getReplyTarget(), text);
    }

    /**
     * Replies to the sender directly, even when the message came from a channel.
     */
    public void replyDirectly(String text) {
        bot.say(sender, text);
    }

    /**
     * Replies with a NOTICE. Other clients will not auto-respond to it, which is
     * what you want for anything noisy or automated.
     */
    public void replyNotice(String text) {
        bot.notice(getReplyTarget(), text);
    }

    @Override
    public String toString() {
        return "<" + sender + " -> " + target + "> " + text;
    }
}
