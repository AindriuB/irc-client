package io.github.aindriub.irc.client.event;

import io.github.aindriub.irc.client.message.IRCMessage;

/**
 * Convenience base class: override only the events you care about.
 *
 * <pre>
 * client.configuration().messageListener(new MessageListener() {
 *     protected void onMessage(String target, String sender, String text, IRCMessage raw) {
 *         if (text.startsWith("!hello")) {
 *             client.sendCommand(new Notice(target, "hello " + sender));
 *         }
 *     }
 * });
 * </pre>
 *
 * <p>Every callback also receives the raw {@link IRCMessage}, so nothing is hidden:
 * tags, the full prefix and the remaining parameters are all still reachable.
 */
public abstract class MessageListener implements EventHandler<IRCMessage> {

    @Override
    public final void publishEvent(Event<IRCMessage> event) {
        IRCMessage message = event.getPayload();
        switch (message.getCommand()) {
        case "PRIVMSG":
            // getParam(1), not getTrailing(): a single word body is a legal middle
            // parameter, and dropping it would lose the message.
            onMessage(message.getParam(0), message.getNick(), message.getParam(1), message);
            break;
        case "NOTICE":
            onNotice(message.getParam(0), message.getNick(), message.getParam(1), message);
            break;
        case "JOIN":
            onJoin(message.getParam(0), message.getNick(), message);
            break;
        case "PART":
            onPart(message.getParam(0), message.getNick(), message);
            break;
        case "QUIT":
            onQuit(message.getNick(), message.getParam(0), message);
            break;
        case "KICK":
            onKick(message.getParam(0), message.getParam(1), message.getNick(), message);
            break;
        case "NICK":
            // The new nick is the only parameter, in either the middle or trailing form.
            onNickChange(message.getNick(), message.getParam(0), message);
            break;
        default:
            if (message.isNumeric()) {
                onNumeric(message.getCommand(), message);
            } else {
                onOther(message);
            }
            break;
        }
    }

    /**
     * @param target  the channel, or your own nick when the message was sent directly
     * @param sender  the nick that sent it, or null when a server sent it
     * @param text    the message body
     */
    protected void onMessage(String target, String sender, String text, IRCMessage raw) {
    }

    /**
     * Never reply automatically to a NOTICE (RFC 2812 section 3.3.2).
     */
    protected void onNotice(String target, String sender, String text, IRCMessage raw) {
    }

    protected void onJoin(String channel, String nick, IRCMessage raw) {
    }

    protected void onPart(String channel, String nick, IRCMessage raw) {
    }

    protected void onQuit(String nick, String reason, IRCMessage raw) {
    }

    protected void onKick(String channel, String kicked, String by, IRCMessage raw) {
    }

    protected void onNickChange(String oldNick, String newNick, IRCMessage raw) {
    }

    /**
     * @param numeric the three digit reply code, for example {@code 001}
     */
    protected void onNumeric(String numeric, IRCMessage raw) {
    }

    /**
     * Anything with no dedicated callback, CAP and PING among them.
     */
    protected void onOther(IRCMessage raw) {
    }
}
