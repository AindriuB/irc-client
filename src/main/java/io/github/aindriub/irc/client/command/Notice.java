package io.github.aindriub.irc.client.command;

import io.github.aindriub.irc.client.IRCText;

/**
 * Like PRIVMSG, but clients must never send an automatic reply to one
 * (RFC 2812 section 3.3.2). Automated responses belong here rather than in
 * PRIVMSG, so that two bots cannot talk each other into a loop.
 */
public class Notice extends Command {

    private static final String BASE_COMMAND = "NOTICE";

    public Notice(String target, String message) {
        super(BASE_COMMAND + SPACE + IRCText.requireParam(target, "target") + SPACE + COLON
                + IRCText.requireText(message, "message"));
    }
}
