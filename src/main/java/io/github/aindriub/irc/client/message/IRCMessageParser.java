package io.github.aindriub.irc.client.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses one line of the IRC wire protocol (RFC 2812 section 2.3.1, plus the IRCv3
 * message-tags extension).
 *
 * <p>Deliberately lenient: a malformed line from a server should not take the
 * connection down, so anything unparseable raises {@link IRCParseException} for the
 * caller to log and skip.
 */
public final class IRCMessageParser {

    private IRCMessageParser() {
    }

    public static IRCMessage parse(String line) {
        if (line == null) {
            throw new IRCParseException("line must not be null");
        }
        // The frame decoder strips CRLF, but be forgiving if a caller parses a raw line.
        String remainder = stripTrailingLineBreak(line);

        Map<String, String> tags = new LinkedHashMap<>();
        if (remainder.startsWith("@")) {
            int end = remainder.indexOf(' ');
            if (end < 0) {
                throw new IRCParseException("message has tags but no command: " + line);
            }
            parseTags(remainder.substring(1, end), tags);
            remainder = skipSpaces(remainder, end);
        }

        String prefix = null;
        if (remainder.startsWith(":")) {
            int end = remainder.indexOf(' ');
            if (end < 0) {
                throw new IRCParseException("message has a prefix but no command: " + line);
            }
            prefix = remainder.substring(1, end);
            remainder = skipSpaces(remainder, end);
        }

        int commandEnd = remainder.indexOf(' ');
        String command = commandEnd < 0 ? remainder : remainder.substring(0, commandEnd);
        if (command.isEmpty()) {
            throw new IRCParseException("message has no command: " + line);
        }
        remainder = commandEnd < 0 ? "" : skipSpaces(remainder, commandEnd);

        List<String> params = new ArrayList<>();
        boolean trailing = false;
        while (!remainder.isEmpty()) {
            if (remainder.startsWith(":")) {
                // Everything after the colon is one trailing parameter, spaces included.
                params.add(remainder.substring(1));
                trailing = true;
                break;
            }
            int end = remainder.indexOf(' ');
            if (end < 0) {
                params.add(remainder);
                break;
            }
            params.add(remainder.substring(0, end));
            remainder = skipSpaces(remainder, end);
        }

        // Commands are case insensitive on the wire; normalising here means callers
        // can compare against a literal without thinking about it.
        return new IRCMessage(tags, prefix, command.toUpperCase(Locale.ROOT), params, trailing);
    }

    private static void parseTags(String raw, Map<String, String> tags) {
        for (String tag : raw.split(";", -1)) {
            if (tag.isEmpty()) {
                continue;
            }
            int equals = tag.indexOf('=');
            if (equals < 0) {
                // A tag with no '=' is present with an empty value.
                tags.put(tag, "");
            } else {
                tags.put(tag.substring(0, equals), unescapeTagValue(tag.substring(equals + 1)));
            }
        }
    }

    /**
     * IRCv3 escapes the characters that would otherwise break tag framing.
     */
    private static String unescapeTagValue(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder unescaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') {
                unescaped.append(c);
                continue;
            }
            if (i + 1 >= value.length()) {
                // A trailing lone backslash is dropped, per the spec.
                break;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
            case ':':
                unescaped.append(';');
                break;
            case 's':
                unescaped.append(' ');
                break;
            case 'r':
                unescaped.append('\r');
                break;
            case 'n':
                unescaped.append('\n');
                break;
            case '\\':
                unescaped.append('\\');
                break;
            default:
                // Unrecognised escapes drop the backslash and keep the character.
                unescaped.append(escaped);
                break;
            }
        }
        return unescaped.toString();
    }

    private static String skipSpaces(String value, int from) {
        int i = from;
        while (i < value.length() && value.charAt(i) == ' ') {
            i++;
        }
        return value.substring(i);
    }

    private static String stripTrailingLineBreak(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\r' || line.charAt(end - 1) == '\n')) {
            end--;
        }
        return line.substring(0, end);
    }
}
