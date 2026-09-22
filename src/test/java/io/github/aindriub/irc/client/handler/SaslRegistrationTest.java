package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import io.github.aindriub.irc.client.IRCClientException;
import io.github.aindriub.irc.client.command.Authenticate;
import io.github.aindriub.irc.client.configuration.RegistrationConfiguration;
import io.netty.channel.embedded.EmbeddedChannel;

public class SaslRegistrationTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final CompletableFuture<Void> registered = new CompletableFuture<>();

    @Test
    public void runsTheWholePlainExchange() throws Exception {
        EmbeddedChannel channel = connect(sasl("account", "secret"));

        assertEquals("CAP LS 302", channel.readOutbound());
        drain(channel);

        channel.writeInbound(":server CAP * LS :sasl multi-prefix");
        assertEquals("CAP REQ :sasl", channel.readOutbound());

        channel.writeInbound(":server CAP * ACK :sasl");
        assertEquals("AUTHENTICATE PLAIN", channel.readOutbound());
        assertNull("CAP END must wait for SASL, or the window closes",
                channel.readOutbound());

        channel.writeInbound("AUTHENTICATE +");
        assertEquals(encoded("account", "secret"), channel.readOutbound());
        assertNull(channel.readOutbound());

        channel.writeInbound(":server 903 bot :SASL authentication successful");
        assertEquals("CAP END", channel.readOutbound());

        channel.writeInbound(":server 001 bot :Welcome");
        assertTrue(registered.isDone());
        registered.get();
    }

    @Test
    public void defaultsTheAccountNameToTheNick() {
        RegistrationConfiguration configuration = new RegistrationConfiguration();
        configuration.setNick("bot");
        configuration.setSaslPassword("secret");
        configuration.getCapabilities().add("sasl");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server CAP * LS :sasl");
        drain(channel);
        channel.writeInbound(":server CAP * ACK :sasl");
        drain(channel);
        channel.writeInbound("AUTHENTICATE +");

        assertEquals(encoded("bot", "secret"), channel.readOutbound());
    }

    @Test
    public void treatsAlreadyAuthenticatedAsSuccess() {
        EmbeddedChannel channel = advanceToAuthenticated(sasl("account", "secret"));

        channel.writeInbound(":server 907 bot :You have already authenticated");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void failsOnBadCredentials() {
        EmbeddedChannel channel = advanceToAuthenticated(sasl("account", "wrong"));

        channel.writeInbound(":server 904 bot :SASL authentication failed");

        assertFailedWith("SASL authentication failed");
    }

    @Test
    public void failsWhenTheNickIsLockedToAnAccount() {
        EmbeddedChannel channel = advanceToAuthenticated(sasl("account", "secret"));

        channel.writeInbound(":server 902 bot :You must use a nick assigned to you");

        assertFailedWith("SASL authentication failed");
    }

    @Test
    public void failsRatherThanConnectingUnauthenticatedWhenTheServerRefusesSasl() {
        EmbeddedChannel channel = connect(sasl("account", "secret"));
        drain(channel);

        channel.writeInbound(":server CAP * NAK :sasl");

        assertFailedWith("refused the sasl capability");
        assertNull("no point ending negotiation on a doomed connection",
                channel.readOutbound());
    }

    @Test
    public void ignoresAnAuthenticateThatWasNotAskedFor() {
        RegistrationConfiguration configuration = new RegistrationConfiguration();
        configuration.setNick("bot");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound("AUTHENTICATE +");

        assertNull("we never started SASL, so there is nothing to send",
                channel.readOutbound());
        assertFalse(registered.isDone());
    }

    @Test
    public void ignoresAnUnexpectedAuthenticatePayload() {
        EmbeddedChannel channel = advanceToAuthenticated(sasl("account", "secret"));

        // Anything other than the bare "+" is not a cue for credentials.
        channel.writeInbound("AUTHENTICATE something-else");

        assertNull(channel.readOutbound());
    }

    @Test
    public void endsNegotiationNormallyWhenSaslIsNotConfigured() {
        RegistrationConfiguration configuration = new RegistrationConfiguration();
        configuration.setNick("bot");
        configuration.getCapabilities().add("multi-prefix");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server CAP * LS :multi-prefix");
        drain(channel);
        channel.writeInbound(":server CAP * ACK :multi-prefix");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void failsOnAPayloadTheServerCallsTooLong() {
        EmbeddedChannel channel = advanceToAuthenticated(sasl("account", "secret"));

        channel.writeInbound(":server 905 bot :SASL message too long");

        assertFailedWith("SASL authentication failed");
    }

    @Test
    public void failsOnAnAbortedExchange() {
        EmbeddedChannel channel = advanceToAuthenticated(sasl("account", "secret"));

        channel.writeInbound(":server 906 bot :SASL aborted");

        assertFailedWith("SASL authentication failed");
    }

    @Test
    public void doesNotStartSaslWhenTheAckNamesSomethingElse() {
        EmbeddedChannel channel = connect(sasl("account", "secret"));
        drain(channel);
        channel.writeInbound(":server CAP * LS :sasl multi-prefix");
        drain(channel);

        // Acknowledged, but not the capability SASL needs.
        channel.writeInbound(":server CAP * ACK :multi-prefix");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void doesNotStartSaslOnAnAckWithNoCapabilityList() {
        EmbeddedChannel channel = connect(sasl("account", "secret"));
        drain(channel);
        channel.writeInbound(":server CAP * LS :sasl");
        drain(channel);

        channel.writeInbound(":server CAP * ACK");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void acceptsAnAckThatPrefixesTheCapabilityName() {
        EmbeddedChannel channel = connect(sasl("account", "secret"));
        drain(channel);
        channel.writeInbound(":server CAP * LS :sasl");
        drain(channel);

        // Some servers acknowledge with a modifier prefix.
        channel.writeInbound(":server CAP * ACK :=sasl");

        assertEquals("AUTHENTICATE PLAIN", channel.readOutbound());
    }

    @Test
    public void neverLogsTheCredentials() {
        List<Authenticate> parts = Authenticate.plain("account", "secret");

        for (Authenticate part : parts) {
            assertFalse("the password must not reach a log line: " + part,
                    part.toString().contains("secret"));
        }
        assertTrue(parts.get(0).render().length() > 0);
        assertEquals("AUTHENTICATE <redacted>", parts.get(0).toString());
        assertEquals("a mechanism name is not a secret", "AUTHENTICATE PLAIN",
                Authenticate.mechanism("PLAIN").toString());
        assertEquals("AUTHENTICATE *", Authenticate.abort().toString());
    }

    @Test
    public void splitsALongPayloadAcrossMessages() {
        StringBuilder longPassword = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longPassword.append('x');
        }

        List<Authenticate> parts = Authenticate.plain("account", longPassword.toString());

        assertTrue("a payload over 400 bytes needs splitting", parts.size() > 1);
        for (Authenticate part : parts) {
            String payload = part.render().substring("AUTHENTICATE ".length());
            assertTrue(payload.length() + " is over the limit", payload.length() <= 400);
        }
    }

    @Test
    public void terminatesAPayloadThatLandsExactlyOnTheBoundary() {
        // 300 credential bytes encode to exactly 400 base64 characters, so without a
        // trailing "+" the server would wait for a continuation that never arrives.
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 300 - "\0account\0".length(); i++) {
            password.append('x');
        }

        List<Authenticate> parts = Authenticate.plain("account", password.toString());

        assertEquals(2, parts.size());
        assertEquals("AUTHENTICATE +", parts.get(1).render());
    }

    private static String encoded(String username, String password) {
        return "AUTHENTICATE " + Base64.getEncoder()
                .encodeToString(("\0" + username + "\0" + password).getBytes(UTF_8));
    }

    private RegistrationConfiguration sasl(String username, String password) {
        RegistrationConfiguration configuration = new RegistrationConfiguration();
        configuration.setNick("bot");
        configuration.setSaslUsername(username);
        configuration.setSaslPassword(password);
        configuration.getCapabilities().add("sasl");
        return configuration;
    }

    private EmbeddedChannel advanceToAuthenticated(RegistrationConfiguration configuration) {
        EmbeddedChannel channel = connect(configuration);
        drain(channel);
        channel.writeInbound(":server CAP * LS :sasl");
        drain(channel);
        channel.writeInbound(":server CAP * ACK :sasl");
        drain(channel);
        return channel;
    }

    private EmbeddedChannel connect(RegistrationConfiguration configuration) {
        return new EmbeddedChannel(new RegistrationHandler(configuration, registered));
    }

    private static void drain(EmbeddedChannel channel) {
        while (channel.readOutbound() != null) {
            // discard whatever the handshake has already produced
        }
    }

    private void assertFailedWith(String expectedFragment) {
        assertTrue("expected the handshake to fail", registered.isCompletedExceptionally());
        try {
            registered.get();
            fail("expected an exception");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IRCClientException);
            assertTrue(e.getCause().getMessage(),
                    e.getCause().getMessage().contains(expectedFragment));
        }
    }
}
