package io.github.aindriub.irc.client.message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed inbound message.
 *
 * <pre>
 * [@tags] [:prefix] COMMAND [params...] [:trailing]
 * </pre>
 *
 * <p>Tags are the IRCv3 extension (message-tags); Twitch relies on them heavily.
 * The trailing parameter, when there is one, is both the last entry of
 * {@link #getParams()} and available from {@link #getTrailing()}.
 */
public final class IRCMessage {

    private final Map<String, String> tags;
    private final String prefix;
    private final String command;
    private final List<String> params;
    private final boolean trailing;

    IRCMessage(Map<String, String> tags, String prefix, String command, List<String> params,
            boolean trailing) {
        this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        this.prefix = prefix;
        this.command = command;
        this.params = Collections.unmodifiableList(params);
        this.trailing = trailing;
    }

    /**
     * IRCv3 message tags, empty when the message carried none.
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * The sender, without its leading colon, or null when the message had no prefix.
     * Typically {@code nick!user@host} for a user and a server name otherwise.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * The nick portion of the prefix, or null when there is no prefix or it names a
     * server rather than a user.
     */
    public String getNick() {
        if (prefix == null) {
            return null;
        }
        int bang = prefix.indexOf('!');
        return bang < 0 ? null : prefix.substring(0, bang);
    }

    /**
     * The command, uppercased, or a three digit numeric reply such as {@code 001}.
     */
    public String getCommand() {
        return command;
    }

    /**
     * True when the command is a numeric reply rather than a named command.
     */
    public boolean isNumeric() {
        if (command.length() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (!Character.isDigit(command.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public List<String> getParams() {
        return params;
    }

    /**
     * @return the parameter at {@code index}, or null when there are fewer than that
     */
    public String getParam(int index) {
        return index >= 0 && index < params.size() ? params.get(index) : null;
    }

    /**
     * True when the message ended in a {@code :}-prefixed parameter.
     */
    public boolean hasTrailing() {
        return trailing;
    }

    /**
     * The trailing parameter: the {@code :}-prefixed one that runs to the end of the
     * line, which for most messages is the human readable part.
     *
     * <p>Null when the message had none. It deliberately does not fall back to the
     * last parameter: doing that meant a truncated message handed back whatever
     * happened to be last, and a caller reading it as the text got a channel name or
     * a subcommand instead. Use {@link #getParam(int)} when you want a parameter by
     * position.
     */
    public String getTrailing() {
        return trailing && !params.isEmpty() ? params.get(params.size() - 1) : null;
    }

    /**
     * True when {@code target} names a channel rather than a user. Channel prefixes
     * are {@code #}, {@code &amp;}, {@code !} and {@code +} (RFC 2811 section 2.1).
     */
    public static boolean isChannel(String target) {
        if (target == null || target.isEmpty()) {
            return false;
        }
        char first = target.charAt(0);
        return first == '#' || first == '&' || first == '!' || first == '+';
    }

    @Override
    public String toString() {
        StringBuilder rendered = new StringBuilder();
        if (!tags.isEmpty()) {
            rendered.append('@').append(tags).append(' ');
        }
        if (prefix != null) {
            rendered.append(':').append(prefix).append(' ');
        }
        rendered.append(command);
        for (String param : params) {
            rendered.append(' ').append(param);
        }
        return rendered.toString();
    }
}
