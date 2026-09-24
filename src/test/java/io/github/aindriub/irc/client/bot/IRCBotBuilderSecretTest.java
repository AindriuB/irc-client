package io.github.aindriub.irc.client.bot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * A bad server password must fail at the setter, before build(), and never echo the
 * secret in the exception message.
 */
public class IRCBotBuilderSecretTest {

    @Test
    public void passwordFailsAtTheSetterAndDoesNotEchoTheSecret() {
        try {
            IRCBot.builder()
                    .host("irc.example.org")
                    .nick("bot")
                    .password("PASS oauth:tok123");
            fail("expected an IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage(), e.getMessage().contains("tok123"));
        }
    }
}
