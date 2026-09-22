package ie.aindriu.irc.client.command;

import java.util.Objects;

/**
 * An IRC protocol message. The rendered form is fixed at construction time; the
 * trailing CRLF is added by the pipeline's line encoder, not here.
 */
public abstract class Command {

    protected static final String SPACE = " ";
    protected static final String COMMA = ",";

    private final String command;

    protected Command(String command) {
        this.command = Objects.requireNonNull(command, "command");
    }

    @Override
    public String toString() {
        return command;
    }
}
