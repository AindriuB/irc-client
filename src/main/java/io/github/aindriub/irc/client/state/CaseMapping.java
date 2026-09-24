package io.github.aindriub.irc.client.state;

/**
 * How a server folds case when comparing nicks and channel names, as declared by its
 * ISUPPORT {@code CASEMAPPING} token.
 *
 * <p>IRC predates Unicode-aware case folding, and every variant here only touches
 * ASCII letters and, for two of the three, a handful of punctuation characters that
 * sit next to the letters in the old ASCII table. Anything outside that range is
 * left exactly as it was.
 */
public enum CaseMapping {

    /**
     * Folds {@code A-Z} to {@code a-z} and nothing else.
     */
    ASCII {
        @Override
        public String fold(String value) {
            return foldLetters(value);
        }
    },

    /**
     * Folds {@code A-Z} to {@code a-z}, and additionally {@code [ ] \ ~} to
     * {@code { } | ^}. The RFC 1459 default, and still the most common in practice.
     */
    RFC1459 {
        @Override
        public String fold(String value) {
            return foldLetters(foldChar(value, '[', '{', ']', '}', '\\', '|', '~', '^'));
        }
    },

    /**
     * Folds {@code A-Z} to {@code a-z}, and additionally {@code [ ] \} to
     * {@code { } |}, but leaves {@code ~} alone.
     */
    STRICT_RFC1459 {
        @Override
        public String fold(String value) {
            return foldLetters(foldChar(value, '[', '{', ']', '}', '\\', '|'));
        }
    };

    /**
     * @return {@code value} with case folded per this mapping, or null if {@code value}
     *         is null
     */
    public abstract String fold(String value);

    /**
     * Compares two nicks or channel names under this mapping.
     *
     * @return true if both fold to the same value; two nulls are equal, one null is not
     */
    public boolean equals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return fold(a).equals(fold(b));
    }

    /**
     * Maps an ISUPPORT {@code CASEMAPPING} token to its enum constant.
     *
     * @param value the token value, such as {@code "ascii"} or {@code "rfc1459"},
     *              matched case-insensitively
     * @return RFC1459 if value is null (the RFC 2812 default before ISUPPORT arrives),
     *         the matching constant for a recognised value, and ASCII for anything else
     *         (a server declaring a mapping this client does not know, such as
     *         {@code rfc8265}, is safest treated as plain ASCII folding)
     */
    public static CaseMapping forToken(String value) {
        if (value == null) {
            return RFC1459;
        }
        if ("ascii".equalsIgnoreCase(value)) {
            return ASCII;
        }
        if ("rfc1459".equalsIgnoreCase(value)) {
            return RFC1459;
        }
        if ("strict-rfc1459".equalsIgnoreCase(value)) {
            return STRICT_RFC1459;
        }
        return ASCII;
    }

    private static String foldLetters(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder folded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + ('a' - 'A'));
            }
            folded.append(c);
        }
        return folded.toString();
    }

    /**
     * Maps each {@code from} character in {@code pairs} (alternating from, to) to its
     * paired {@code to} character. Letters are handled separately by
     * {@link #foldLetters(String)}.
     */
    private static String foldChar(String value, char... pairs) {
        if (value == null) {
            return null;
        }
        StringBuilder folded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            for (int p = 0; p < pairs.length; p += 2) {
                if (c == pairs[p]) {
                    c = pairs[p + 1];
                    break;
                }
            }
            folded.append(c);
        }
        return folded.toString();
    }
}
