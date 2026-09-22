package io.github.aindriub.irc.client.command;

import java.util.Arrays;
import java.util.List;

import io.github.aindriub.irc.client.IRCText;

/**
 * Sets or queries modes on a channel or a user.
 *
 * <pre>
 * MODE #chan            query the channel's modes
 * MODE #chan +o nick    give a user operator status
 * MODE mynick +i        set your own user mode
 * </pre>
 */
public class Mode extends Command {

    private static final String BASE_COMMAND = "MODE";

    /**
     * Queries the modes currently set on a channel or user.
     */
    public Mode(String target) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(target, "target"));
    }

    /**
     * @param modes a mode string such as {@code +o} or {@code -mi}
     * @param args  parameters the modes take, such as the nick for {@code +o}
     */
    public Mode(String target, String modes, String... args) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(target, "target") + SPACE
                + IRCText.requireParam(modes, "modes") + renderArgs(Arrays.asList(args)));
    }

    public Mode(String target, String modes, List<String> args) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(target, "target") + SPACE
                + IRCText.requireParam(modes, "modes") + renderArgs(args));
    }

    private static String renderArgs(List<String> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        StringBuilder rendered = new StringBuilder();
        for (String arg : args) {
            rendered.append(SPACE).append(IRCText.requireParam(arg, "mode argument"));
        }
        return rendered.toString();
    }
}
