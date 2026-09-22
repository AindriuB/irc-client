package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * The other half of registration: {@code USER <username> <mode> * :<realname>}.
 */
public class User extends Command {

    private static final String BASE_COMMAND = "USER";

    private static final String NO_MODE = "0";
    private static final String UNUSED = "*";

    public User(String username, String realname) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(username, "username") + SPACE + NO_MODE
                + SPACE + UNUSED + SPACE + COLON + IRCText.requireText(realname, "realname"));
    }
}
