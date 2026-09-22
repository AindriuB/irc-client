package io.github.aindriub.irc.client.command;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import io.github.aindriub.irc.client.IRCText;

/**
 * Splits a message body so that each piece fits in one IRC message.
 *
 * <p>A server truncates an over-long line rather than refusing it, so the half that
 * does not fit is simply lost and the half that does looks like what the sender
 * meant to say. That is worse than an error, and worst in exactly the case the
 * README's own example demonstrates: echoing chat input back into a reply.
 */
public final class Messages {

    private Messages() {
    }

    /**
     * Splits {@code text} into pieces that each fit alongside {@code overhead}.
     *
     * <p>Splits on whitespace where it can, because a sentence cut mid-word reads
     * as a bug, and mid-character not at all. Falls back to the byte budget for a
     * run with no spaces in it, such as a URL.
     *
     * @param text     the body to split
     * @param overhead bytes the rest of the command occupies, including the CRLF
     * @param charset  the connection's charset, because the limit is counted in
     *                 bytes and an emoji is one character and four of them
     */
    public static List<String> split(String text, int overhead, Charset charset) {
        int budget = IRCText.MAX_MESSAGE_BYTES - overhead;
        if (budget < 1) {
            throw new IllegalArgumentException(
                    "no room left for a message body: overhead is " + overhead + " bytes");
        }
        List<String> pieces = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            pieces.add("");
            return pieces;
        }
        if (IRCText.byteLength(text, charset) <= budget) {
            pieces.add(text);
            return pieces;
        }

        String remaining = text;
        while (!remaining.isEmpty()) {
            int cut = cutPoint(remaining, budget, charset);
            // trim() rather than strip*(): this library targets Java 8, where the
            // strip family does not exist. For the spaces being removed here the
            // difference does not arise.
            pieces.add(trimEnd(remaining.substring(0, cut)));
            remaining = trimStart(remaining.substring(cut));
        }
        return pieces;
    }

    private static String trimEnd(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String trimStart(String value) {
        int start = 0;
        while (start < value.length() && value.charAt(start) == ' ') {
            start++;
        }
        return value.substring(start);
    }

    /**
     * The largest number of characters whose encoded form fits the budget, rounded
     * back to a space when one is near enough to be worth using.
     */
    private static int cutPoint(String text, int budget, Charset charset) {
        int fits = charactersThatFit(text, budget, charset);
        if (fits >= text.length()) {
            return text.length();
        }
        // Only look back a little way: on a long run with no spaces, walking back
        // to the last one could discard most of a piece and turn a split into many.
        int lookBack = Math.max(1, fits - Math.min(fits / 4, 40));
        int space = text.lastIndexOf(' ', fits);
        return space >= lookBack ? space : fits;
    }

    /**
     * Counts by code point rather than by char, so a split never lands between the
     * halves of a surrogate pair and produces a broken character.
     */
    private static int charactersThatFit(String text, int budget, Charset charset) {
        int used = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int width = Character.charCount(codePoint);
            int bytes = IRCText.byteLength(text.substring(index, index + width), charset);
            if (used + bytes > budget) {
                break;
            }
            used += bytes;
            index += width;
        }
        // A budget too small for even one character would loop forever otherwise.
        return index == 0 ? Math.min(text.length(),
                Character.charCount(text.codePointAt(0))) : index;
    }
}
