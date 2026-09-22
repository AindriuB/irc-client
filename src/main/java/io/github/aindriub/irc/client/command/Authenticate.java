package io.github.aindriub.irc.client.command;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.github.aindriub.irc.client.IRCText;

/**
 * One step of a SASL exchange (IRCv3).
 */
public class Authenticate extends Command {

    private static final String BASE_COMMAND = "AUTHENTICATE";

    /**
     * IRCv3 caps an AUTHENTICATE payload at 400 bytes, so a longer one is split.
     */
    private static final int MAX_PAYLOAD = 400;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * True when the payload carries credentials rather than a mechanism name or a
     * control token, and so must not be logged.
     */
    private final boolean sensitive;

    public Authenticate(String payload) {
        this(payload, true);
    }

    private Authenticate(String payload, boolean sensitive) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(payload, "payload"));
        this.sensitive = sensitive;
    }

    @Override
    public String toString() {
        // The payload is the password, base64 encoded rather than hashed. render()
        // still returns it for the encoder.
        return sensitive ? BASE_COMMAND + SPACE + "<redacted>" : super.toString();
    }

    /**
     * Names the mechanism to begin: {@code AUTHENTICATE PLAIN}.
     */
    public static Authenticate mechanism(String mechanism) {
        return new Authenticate(mechanism, false);
    }

    /**
     * Aborts the exchange.
     */
    public static Authenticate abort() {
        return new Authenticate("*", false);
    }

    /**
     * Encodes SASL PLAIN credentials, split into as many messages as the payload
     * needs.
     *
     * <p>PLAIN is {@code authzid NUL authcid NUL password} (RFC 4616), with an empty
     * authzid meaning "the account I am naming". Base64 of that can exceed one IRC
     * message, so it is chunked; a payload that lands exactly on the boundary is
     * followed by a lone {@code +}, or the server would wait for a continuation that
     * never came.
     */
    public static List<Authenticate> plain(String username, String password) {
        IRCText.requireParam(username, "sasl username");
        IRCText.requireText(password, "sasl password");

        byte[] credentials = ("\0" + username + "\0" + password).getBytes(UTF_8);
        String encoded = Base64.getEncoder().encodeToString(credentials);

        List<Authenticate> messages = new ArrayList<>();
        for (int start = 0; start < encoded.length(); start += MAX_PAYLOAD) {
            messages.add(new Authenticate(
                    encoded.substring(start, Math.min(start + MAX_PAYLOAD, encoded.length()))));
        }
        if (encoded.length() % MAX_PAYLOAD == 0) {
            // Either empty, or an exact multiple: both need the explicit terminator.
            messages.add(new Authenticate("+", false));
        }
        return messages;
    }
}
