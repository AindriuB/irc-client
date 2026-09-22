package io.github.aindriub.irc.client.command;

import java.nio.charset.Charset;
import java.util.List;

import io.github.aindriub.irc.client.IRCText;

/**
 * Sends a message to a channel or a user.
 */
public class PrivMsg extends Command {

    private static final String BASE_COMMAND = "PRIVMSG";

    /**
     * @param target a channel such as {@code #chat}, or a nick
     */
    public PrivMsg(String target, String message) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(target, "target") + SPACE + COLON
                + IRCText.requireText(message, "message"));
    }

    /**
     * Splits a body too long for one message into as many as it needs.
     *
     * <p>The overhead is measured from a rendered command rather than guessed:
     * the prefix the server prepends is not counted here, so this is the client
     * side of the budget. Servers vary in how much they add, which is why the
     * pieces aim a little under the limit rather than exactly at it.
     */
    public static List<PrivMsg> split(String target, String message, Charset charset) {
        IRCText.requireParam(target, "target");
        int overhead = IRCText.byteLength(
                "PRIVMSG " + target + " :", charset) + PREFIX_ALLOWANCE + 2;
        List<PrivMsg> messages = new java.util.ArrayList<>();
        for (String piece : Messages.split(message, overhead, charset)) {
            messages.add(new PrivMsg(target, piece));
        }
        return messages;
    }

    /**
     * Room left for the {@code :nick!user@host } the server prepends when it
     * relays the message. A generous fixed allowance rather than a calculation,
     * because the client does not reliably know its own visible host.
     */
    private static final int PREFIX_ALLOWANCE = 100;
}
