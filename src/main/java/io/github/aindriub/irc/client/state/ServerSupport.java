package io.github.aindriub.irc.client.state;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.aindriub.irc.client.message.IRCMessage;

/**
 * What the server says it supports, from the RPL_ISUPPORT (005) lines it sends
 * after registration.
 *
 * <p>The useful part is CHANMODES and PREFIX. Without them a client has to guess
 * whether a mode letter takes an argument, and guessing wrong misaligns every
 * argument after it. Sensible RFC 2812 defaults apply until a server says otherwise,
 * so this is safe to read before any 005 arrives.
 */
public final class ServerSupport {

    /**
     * RFC 2812 mode letters, used until the server sends its own CHANMODES.
     */
    private static final String DEFAULT_ADDRESS_MODES = "b";
    private static final String DEFAULT_PARAM_MODES = "k";
    private static final String DEFAULT_PARAM_WHEN_SET_MODES = "l";

    private final Map<String, String> tokens = new LinkedHashMap<>();

    /** Modes that always take an argument, both setting and unsetting. */
    private volatile Set<Character> alwaysParameterised = charsOf(
            DEFAULT_ADDRESS_MODES + DEFAULT_PARAM_MODES);

    /** Modes that take an argument only when being set. */
    private volatile Set<Character> parameterisedWhenSet = charsOf(DEFAULT_PARAM_WHEN_SET_MODES);

    /** Status modes, which always take a nick. */
    private volatile Map<Character, UserStatus> statusModes = defaultStatusModes();

    /**
     * Reads one RPL_ISUPPORT line. Everything after the nick and before the trailing
     * human readable part is a {@code KEY} or {@code KEY=value} token.
     */
    void apply(IRCMessage message) {
        int last = message.getParams().size();
        if (message.hasTrailing()) {
            // The trailing part is "are supported by this server", not a token.
            last--;
        }
        for (int i = 1; i < last; i++) {
            String token = message.getParam(i);
            if (token == null || token.isEmpty()) {
                continue;
            }
            int equals = token.indexOf('=');
            String key = (equals < 0 ? token : token.substring(0, equals))
                    .toUpperCase(Locale.ROOT);
            String value = equals < 0 ? "" : token.substring(equals + 1);
            tokens.put(key, value);
            if ("CHANMODES".equals(key)) {
                applyChanmodes(value);
            } else if ("PREFIX".equals(key)) {
                applyPrefix(value);
            }
        }
    }

    /**
     * {@code CHANMODES=A,B,C,D}: A takes an address, B takes a parameter, C takes one
     * only when set, D takes none.
     */
    private void applyChanmodes(String value) {
        String[] groups = value.split(",", -1);
        if (groups.length < 4) {
            return;
        }
        alwaysParameterised = charsOf(groups[0] + groups[1]);
        parameterisedWhenSet = charsOf(groups[2]);
    }

    /**
     * {@code PREFIX=(ohv)@%+}: mode letters in order, then the prefix characters
     * they map to.
     */
    private void applyPrefix(String value) {
        int close = value.indexOf(')');
        if (!value.startsWith("(") || close < 0) {
            return;
        }
        String modes = value.substring(1, close);
        String prefixes = value.substring(close + 1);
        if (modes.length() != prefixes.length()) {
            return;
        }
        Map<Character, UserStatus> parsed = new LinkedHashMap<>();
        for (int i = 0; i < modes.length(); i++) {
            UserStatus status = UserStatus.fromPrefix(prefixes.charAt(i));
            if (status != null) {
                parsed.put(modes.charAt(i), status);
            }
        }
        if (!parsed.isEmpty()) {
            statusModes = Collections.unmodifiableMap(parsed);
        }
    }

    /**
     * @return the status a mode letter grants, or null when it is not a status mode
     */
    public UserStatus statusFor(char mode) {
        return statusModes.get(mode);
    }

    /**
     * Whether a channel mode consumes an argument, which depends on whether it is
     * being set or unset.
     */
    public boolean takesParameter(char mode, boolean adding) {
        if (statusModes.containsKey(mode) || alwaysParameterised.contains(mode)) {
            return true;
        }
        return adding && parameterisedWhenSet.contains(mode);
    }

    /**
     * A raw ISUPPORT token, such as {@code NETWORK} or {@code CHANTYPES}.
     *
     * @return its value, an empty string for a valueless token, or null if the
     *         server never mentioned it
     */
    public String get(String key) {
        return key == null ? null : tokens.get(key.toUpperCase(Locale.ROOT));
    }

    public boolean supports(String key) {
        return get(key) != null;
    }

    /**
     * How this server folds case in nicks and channel names, from its ISUPPORT
     * {@code CASEMAPPING} token. RFC1459 is the RFC 2812 default, used before any
     * ISUPPORT line arrives and when the server never sends the token at all.
     */
    public CaseMapping getCaseMapping() {
        return CaseMapping.forToken(get("CASEMAPPING"));
    }

    public Map<String, String> getTokens() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(tokens));
    }

    private static Map<Character, UserStatus> defaultStatusModes() {
        Map<Character, UserStatus> defaults = new LinkedHashMap<>();
        for (UserStatus status : UserStatus.values()) {
            defaults.put(status.getMode(), status);
        }
        return Collections.unmodifiableMap(defaults);
    }

    private static Set<Character> charsOf(String value) {
        Set<Character> chars = new LinkedHashSet<>();
        for (int i = 0; i < value.length(); i++) {
            chars.add(value.charAt(i));
        }
        return Collections.unmodifiableSet(chars);
    }

    @Override
    public String toString() {
        return "ServerSupport" + tokens.keySet();
    }
}
