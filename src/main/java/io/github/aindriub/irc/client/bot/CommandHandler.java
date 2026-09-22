package io.github.aindriub.irc.client.bot;

import java.util.List;

/**
 * Handles one chat command, such as {@code !hello}.
 */
public interface CommandHandler {

    /**
     * @param context the message the command arrived in
     * @param args    whitespace separated arguments after the command word, empty
     *                when there were none
     */
    void handle(MessageContext context, List<String> args);
}
