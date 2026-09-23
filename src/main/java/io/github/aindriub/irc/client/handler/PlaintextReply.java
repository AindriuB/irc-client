package io.github.aindriub.irc.client.handler;

import java.nio.charset.StandardCharsets;

/**
 * Reads the server's own words out of a failed TLS handshake.
 *
 * <p>A server that refuses a connection before TLS begins answers in plain text,
 * and Netty reports that as {@code not an SSL/TLS record:} followed by a hex dump.
 * The bytes are almost always an IRC {@code ERROR} line saying exactly why — a
 * throttle, a ban, a full server — and that sentence is worth more to whoever is
 * reading the log than forty characters of hex they have to decode by hand.
 *
 * <p>The other common cause is a plaintext port dialled with TLS on, where the
 * bytes are the server's ordinary greeting. Both are worth saying out loud.
 */
final class PlaintextReply {

    /** Beyond this the reply is not an IRC line and quoting it stops helping. */
    private static final int MAX_QUOTED = 200;

    private static final String MARKER = "not an SSL/TLS record: ";

    private PlaintextReply() {
    }

    /**
     * The text a server sent instead of starting TLS, or null when the failure was
     * something else or the bytes do not read as text.
     */
    static String from(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null || !message.contains(MARKER)) {
                continue;
            }
            String decoded = decode(message.substring(message.indexOf(MARKER) + MARKER.length()));
            if (decoded != null) {
                return decoded;
            }
        }
        return null;
    }

    private static String decode(String hex) {
        String trimmed = hex.trim();
        if (trimmed.isEmpty() || trimmed.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[trimmed.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(trimmed.charAt(i * 2), 16);
            int low = Character.digit(trimmed.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[i] = (byte) ((high << 4) | low);
        }

        // US-ASCII deliberately: this is a handshake failure, so there is no
        // negotiated charset to trust, and anything outside ASCII here is not a
        // message somebody meant to be read.
        String raw = new String(bytes, StandardCharsets.US_ASCII);

        // Checked before trimming, not after. trim() removes anything below 0x20,
        // so a real TLS record from a mismatched peer would have its leading
        // control bytes stripped and whatever printable byte came next quoted as
        // though the server had said it.
        if (!printable(raw)) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_QUOTED ? text.substring(0, MAX_QUOTED) + "..." : text;
    }

    private static boolean printable(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\r' && c != '\n' && c != '\t' && (c < 0x20 || c > 0x7e)) {
                return false;
            }
        }
        return true;
    }
}
