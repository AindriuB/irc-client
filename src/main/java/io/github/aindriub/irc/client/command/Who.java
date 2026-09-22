package io.github.aindriub.irc.client.command;

public class Who extends Command {

    private static final String BASE_COMMAND = "WHO";

    public Who(String nick) {
        super(BASE_COMMAND + SPACE + nick);
    }
}
