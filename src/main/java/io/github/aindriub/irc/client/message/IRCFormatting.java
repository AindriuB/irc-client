package io.github.aindriub.irc.client.message;

/**
 * Removes mIRC formatting control codes from inbound text.
 *
 * <p>Bots receive message bodies decorated with bold, colour and similar codes
 * that a human client renders but that make command matching on the raw text
 * unreliable. {@link #strip(String)} yields the plain text underneath.
 */
public final class IRCFormatting {

    private static final char BOLD = '\u0002';
    private static final char COLOUR = '\u0003';
    private static final char HEX_COLOUR = '\u0004';
    private static final char ITALIC = '\u001D';
    private static final char UNDERLINE = '\u001F';
    private static final char STRIKETHROUGH = '\u001E';
    private static final char MONOSPACE = '\u0011';
    private static final char REVERSE = '\u0016';
    private static final char RESET = '\u000F';

    private IRCFormatting() {
    }

    /**
     * Strips mIRC formatting control codes, leaving the plain text a human
     * would read. Returns {@code null} when given {@code null}, so callers can
     * pass through an absent body without a null check of their own.
     *
     * <p>The colour code {@code \u0003} carries up to two decimal foreground
     * digits and, only once at least one foreground digit is present, a comma
     * followed by up to two decimal background digits. The hex colour code
     * {@code \u0004} instead requires exactly six hex digits for the
     * foreground and, if present, a comma followed by exactly six more hex
     * digits for the background; anything short of that exact shape is not a
     * colour spec, so only the control code itself is removed and the digits
     * that follow are left untouched. In both cases a comma that is not part
     * of a well-formed spec is left in place.
     */
    public static String strip(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            switch (c) {
                case BOLD:
                case ITALIC:
                case UNDERLINE:
                case STRIKETHROUGH:
                case MONOSPACE:
                case REVERSE:
                case RESET:
                    i++;
                    break;
                case COLOUR:
                    i = skipDecimalColourCode(text, i + 1);
                    break;
                case HEX_COLOUR:
                    i = skipHexColourCode(text, i + 1);
                    break;
                default:
                    out.append(c);
                    i++;
                    break;
            }
        }
        return out.toString();
    }

    /**
     * Consumes the digits following {@code \u0003}: up to two decimal
     * foreground digits, then, only if at least one foreground digit was
     * found and the following comma is itself followed by a digit, the comma
     * and up to two decimal background digits. Returns the index just past
     * what was consumed.
     */
    private static int skipDecimalColourCode(String text, int start) {
        int length = text.length();
        int i = start;
        int fgEnd = Math.min(i + 2, length);
        while (i < fgEnd && isDecimalDigit(text.charAt(i))) {
            i++;
        }
        boolean hasForeground = i > start;
        if (hasForeground && i < length && text.charAt(i) == ',' && i + 1 < length && isDecimalDigit(text.charAt(i + 1))) {
            i++;
            int bgEnd = Math.min(i + 2, length);
            while (i < bgEnd && isDecimalDigit(text.charAt(i))) {
                i++;
            }
        }
        return i;
    }

    /**
     * Consumes the digits following {@code \u0004}: exactly six hex digits
     * for the foreground and, only when that foreground is present in full
     * and is followed by a comma and exactly six more hex digits, that comma
     * and its background digits. When fewer than six foreground digits are
     * available, nothing is consumed, since the digits do not form a colour
     * spec and are left as ordinary text. Returns the index just past what
     * was consumed.
     */
    private static int skipHexColourCode(String text, int start) {
        int length = text.length();
        int i = start;
        int fgEnd = Math.min(i + 6, length);
        while (i < fgEnd && isHexDigit(text.charAt(i))) {
            i++;
        }
        if (i - start != 6) {
            return start;
        }
        if (i < length && text.charAt(i) == ',') {
            int bgStart = i + 1;
            int bgEnd = Math.min(bgStart + 6, length);
            int j = bgStart;
            while (j < bgEnd && isHexDigit(text.charAt(j))) {
                j++;
            }
            if (j - bgStart == 6) {
                i = j;
            }
        }
        return i;
    }

    private static boolean isDecimalDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
