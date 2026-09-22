package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * An IRC protocol message. The rendered form is fixed at construction time; the
 * trailing CRLF is added by the pipeline's line encoder, not here.
 *
 * <p>{@link #render()} is what goes on the wire and {@link #toString()} is what goes
 * in a log. They are the same for every command except {@link Pass}, which must not
 * log its password.
 */
public abstract class Command {

    protected static final String SPACE = " ";
    protected static final String COMMA = ",";
    protected static final String COLON = ":";

    private final String command;

    protected Command(String command) {
        // Last line of defence: a subclass that forgets to validate its arguments
        // still cannot produce a command that spans two lines.
        this.command = IRCText.requireText(command, "command");
    }

    /**
     * The wire form. Always send this, never {@link #toString()}.
     */
    public final String render() {
        return command;
    }

    @Override
    public String toString() {
        return render();
    }
}
