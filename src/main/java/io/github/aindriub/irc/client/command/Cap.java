package io.github.aindriub.irc.client.command;

import java.util.List;

import io.github.aindriub.irc.client.IRCText;

/**
 * IRCv3 capability negotiation. Required to get message tags and membership events
 * from servers that gate them, Twitch among them.
 */
public class Cap extends Command {

    private static final String BASE_COMMAND = "CAP";

    /** Version 302 asks the server for capability values, not just names. */
    private static final String VERSION = "302";

    private Cap(String rendered) {
        super(rendered);
    }

    /**
     * Asks what the server supports.
     */
    public static Cap ls() {
        return new Cap(BASE_COMMAND + SPACE + "LS" + SPACE + VERSION);
    }

    /**
     * Requests capabilities. The server answers with CAP ACK or CAP NAK.
     */
    public static Cap req(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new IllegalArgumentException("at least one capability is required");
        }
        StringBuilder rendered = new StringBuilder(BASE_COMMAND).append(SPACE).append("REQ")
                .append(SPACE).append(COLON);
        for (int i = 0; i < capabilities.size(); i++) {
            if (i > 0) {
                rendered.append(' ');
            }
            rendered.append(IRCText.requireParam(capabilities.get(i), "capability"));
        }
        return new Cap(rendered.toString());
    }

    /**
     * Ends negotiation. Registration does not complete until this is sent.
     */
    public static Cap end() {
        return new Cap(BASE_COMMAND + SPACE + "END");
    }
}
