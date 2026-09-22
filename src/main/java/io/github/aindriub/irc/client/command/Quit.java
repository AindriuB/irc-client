package io.github.aindriub.irc.client.command;

public class Quit extends Command {

    private static final String BASE_COMMAND = "QUIT";

    public Quit() {
        super(BASE_COMMAND);
    }
}
