package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

public class Who extends Command {

    private static final String BASE_COMMAND = "WHO";

    /**
     * @param mask a nick, channel or wildcard mask
     */
    public Who(String mask) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(mask, "mask"));
    }

    /**
     * @param operatorsOnly restrict the reply to operators ({@code WHO <mask> o})
     */
    public Who(String mask, boolean operatorsOnly) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(mask, "mask")
                + (operatorsOnly ? SPACE + "o" : ""));
    }
}
