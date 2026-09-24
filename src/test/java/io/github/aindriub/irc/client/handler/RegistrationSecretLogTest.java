package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;

import io.github.aindriub.irc.client.configuration.RegistrationConfiguration;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * A registration-time failure must never write the secret it failed on to any
 * log, at any level, whether the secret appears in a formatted message or
 * anywhere in a throwable's cause chain.
 */
public class RegistrationSecretLogTest {

    private static final String PASSWORD_SECRET = "tok123";
    private static final String SASL_SECRET = "saslSecretXYZ";

    private final List<ILoggingEvent> logged = new CopyOnWriteArrayList<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
        @Override
        protected void append(ILoggingEvent event) {
            logged.add(event);
        }
    };
    private ch.qos.logback.classic.Logger rootLogger;
    private Level originalLevel;

    @Before
    public void setUp() {
        rootLogger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        originalLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.TRACE);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @After
    public void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
        rootLogger.setLevel(originalLevel);
    }

    @Test
    public void aRejectedPasswordNeverReachesAnyLog() throws Exception {
        RegistrationConfiguration configuration = new RegistrationConfiguration();
        configuration.setNick("bot");
        // Bypasses the builder's validation entirely: the POJO setter is permissive.
        configuration.setPassword("PASS oauth:" + PASSWORD_SECRET);

        CompletableFuture<Void> registered = new CompletableFuture<>();
        // Constructing fires channelActive, which is where PASS is built and fails.
        new EmbeddedChannel(new RegistrationHandler(configuration, registered));

        assertTrue("expected the handshake to fail", registered.isCompletedExceptionally());
        assertNoSecretAnywhere(registered, PASSWORD_SECRET);
    }

    @Test
    public void aRejectedSaslPasswordNeverReachesAnyLog() throws Exception {
        RegistrationConfiguration configuration = new RegistrationConfiguration();
        configuration.setNick("bot");
        configuration.setSaslUsername("bot");
        // Bypasses the builder's validation entirely: the POJO setter is permissive.
        configuration.setSaslPassword(SASL_SECRET + "\nQUIT");
        configuration.getCapabilities().add("sasl");

        CompletableFuture<Void> registered = new CompletableFuture<>();
        EmbeddedChannel channel = new EmbeddedChannel(
                new RegistrationHandler(configuration, registered));
        drain(channel);

        channel.writeInbound(":irc.example CAP * LS :sasl");
        drain(channel);
        channel.writeInbound(":irc.example CAP * ACK :sasl");
        drain(channel);
        try {
            // RegistrationHandler.exceptionCaught completes the future first, then
            // forwards the raw cause down the pipeline; EmbeddedChannel rethrows it
            // here from its default tail once nothing else has handled it.
            channel.writeInbound("AUTHENTICATE +");
        } catch (IllegalArgumentException expected) {
            // Handled above; what matters is what the future and the log captured.
        }

        assertTrue("expected the SASL exchange to fail", registered.isCompletedExceptionally());
        assertNoSecretAnywhere(registered, SASL_SECRET);
    }

    private static void drain(EmbeddedChannel channel) {
        while (channel.readOutbound() != null) {
            // discard the handshake so far
        }
    }

    private void assertNoSecretAnywhere(CompletableFuture<Void> registered, String secret) {
        try {
            registered.get();
            fail("expected the future to complete exceptionally");
        } catch (ExecutionException e) {
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                String message = cause.getMessage();
                assertFalse("exception message leaked the secret: " + cause,
                        message != null && message.contains(secret));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the future");
        }

        for (ILoggingEvent event : logged) {
            assertFalse("a logged message leaked the secret: " + event.getFormattedMessage(),
                    event.getFormattedMessage() != null
                            && event.getFormattedMessage().contains(secret));
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            while (throwableProxy != null) {
                assertFalse("a logged throwable leaked the secret: " + throwableProxy.getMessage(),
                        throwableProxy.getMessage() != null
                                && throwableProxy.getMessage().contains(secret));
                throwableProxy = throwableProxy.getCause();
            }
        }
    }
}
