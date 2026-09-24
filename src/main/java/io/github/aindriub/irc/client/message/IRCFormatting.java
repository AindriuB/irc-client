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
     * <p>The colour codes ({@code \u0003} and {@code \u0004}) carry digits that
     * select a foreground and, optionally, a background colour; those digits are
     * removed along with the control code itself, but only when they are part of
     * a well-formed colour spec, so stray digits or a bare comma that follows
     * are left untouched.
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
                    i = skipColourCode(text, i + 1, 2, IRCFormatting::isDecimalDigit);
                    break;
                case HEX_COLOUR:
                    i = skipColourCode(text, i + 1, 6, IRCFormatting::isHexDigit);
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
     * Consumes the digits following a colour control code: up to
     * {@code maxDigits} foreground digits, then, only if a comma is
     * immediately followed by a digit, the comma and up to {@code maxDigits}
     * background digits. Returns the index just past what was consumed.
     */
    private static int skipColourCode(String text, int start, int maxDigits, DigitPredicate isDigit) {
        int i = start;
        int length = text.length();
        int fgEnd = Math.min(i + maxDigits, length);
        while (i < fgEnd && isDigit.test(text.charAt(i))) {
            i++;
        }
        if (i < length && text.charAt(i) == ',' && i + 1 < length && isDigit.test(text.charAt(i + 1))) {
            i++;
            int bgEnd = Math.min(i + maxDigits, length);
            while (i < bgEnd && isDigit.test(text.charAt(i))) {
                i++;
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

    @FunctionalInterface
    private interface DigitPredicate {
        boolean test(char c);
    }
}
