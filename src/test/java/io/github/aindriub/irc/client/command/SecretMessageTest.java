package io.github.aindriub.irc.client.command;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * A bad password or SASL credential must never reach an exception message: it
 * ends up in logs verbatim otherwise.
 */
public class SecretMessageTest {

    @Test
    public void passRejectsAnInvalidPasswordWithoutLoggingIt() {
        try {
            new Pass("oauth:abc def");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNoSecret(e.getMessage(), "abc", "def", "oauth:");
        }
    }

    @Test
    public void passRejectsALeadingColonWithoutLoggingIt() {
        try {
            new Pass(":secret");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNoSecret(e.getMessage(), "secret");
        }
    }

    @Test
    public void authenticatePlainRejectsAWhitespaceUsernameWithoutLoggingIt() {
        try {
            Authenticate.plain("us er", "pw");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNoSecret(e.getMessage(), "us er");
        }
    }

    @Test
    public void authenticatePlainRejectsAnInjectingPasswordWithoutLoggingIt() {
        try {
            Authenticate.plain("user", "hunter2\nPRIVMSG");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNoSecret(e.getMessage(), "hunter2");
        }
    }

    private static void assertNoSecret(String message, String... secrets) {
        for (String secret : secrets) {
            assertFalse("message should not contain \"" + secret + "\": " + message,
                    message.contains(secret));
        }
    }
}
