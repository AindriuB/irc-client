package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import io.github.aindriub.irc.client.IRCClientException;
import io.github.aindriub.irc.client.configuration.RegistrationConfiguration;
import io.netty.channel.embedded.EmbeddedChannel;

public class RegistrationHandlerTest {

    private final CompletableFuture<Void> registered = new CompletableFuture<>();

    @Test
    public void sendsThePasswordBeforeNickAndUser() {
        EmbeddedChannel channel = connect(config("bot", "oauth:token"));

        assertEquals("PASS oauth:token", channel.readOutbound());
        assertEquals("NICK bot", channel.readOutbound());
        assertEquals("USER bot 0 * :bot", channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    @Test
    public void omitsThePasswordWhenThereIsNone() {
        EmbeddedChannel channel = connect(config("bot", null));

        assertEquals("NICK bot", channel.readOutbound());
        assertEquals("USER bot 0 * :bot", channel.readOutbound());
    }

    @Test
    public void usesTheConfiguredUsernameAndRealname() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.setUsername("botuser");
        configuration.setRealname("A Friendly Bot");

        EmbeddedChannel channel = connect(configuration);

        assertEquals("NICK bot", channel.readOutbound());
        assertEquals("USER botuser 0 * :A Friendly Bot", channel.readOutbound());
    }

    @Test
    public void negotiatesOnlyTheCapabilitiesTheServerOffers() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.getCapabilities().add("twitch.tv/tags");
        configuration.getCapabilities().add("twitch.tv/membership");
        EmbeddedChannel channel = connect(configuration);

        assertEquals("CAP LS 302", channel.readOutbound());
        drain(channel);

        // The server offers one of the two, and an unrelated third.
        channel.writeInbound(":tmi.twitch.tv CAP * LS :twitch.tv/tags sasl");

        assertEquals("CAP REQ :twitch.tv/tags", channel.readOutbound());

        channel.writeInbound(":tmi.twitch.tv CAP * ACK :twitch.tv/tags");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void endsNegotiationWhenTheServerOffersNothingWanted() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.getCapabilities().add("twitch.tv/tags");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server CAP * LS :sasl multi-prefix");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void endsNegotiationOnNak() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.getCapabilities().add("twitch.tv/tags");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server CAP * LS :twitch.tv/tags");
        assertEquals("CAP REQ :twitch.tv/tags", channel.readOutbound());

        channel.writeInbound(":server CAP * NAK :twitch.tv/tags");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void matchesACapabilityTheServerAdvertisesWithAValue() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.getCapabilities().add("sasl");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server CAP * LS :sasl=PLAIN,EXTERNAL multi-prefix");

        assertEquals("CAP REQ :sasl", channel.readOutbound());
    }

    @Test
    public void completesOnWelcomeAndThenRemovesItself() throws Exception {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);
        assertFalse(registered.isDone());

        channel.writeInbound(":server 001 bot :Welcome to the network");

        assertTrue(registered.isDone());
        registered.get();
        assertNull("the handler should remove itself once registered",
                channel.pipeline().get(RegistrationHandler.class));
    }

    @Test
    public void retriesWithAModifiedNickWhenTheNickIsTaken() {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        channel.writeInbound(":server 433 * bot :Nickname is already in use");
        assertEquals("NICK bot_", channel.readOutbound());

        channel.writeInbound(":server 433 * bot_ :Nickname is already in use");
        assertEquals("NICK bot__", channel.readOutbound());

        channel.writeInbound(":server 001 bot__ :Welcome");
        assertTrue(registered.isDone());
    }

    @Test
    public void givesUpAfterTheConfiguredNumberOfNickAttempts() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.setMaxNickAttempts(1);
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server 433 * bot :Nickname is already in use");
        assertEquals("NICK bot_", channel.readOutbound());

        channel.writeInbound(":server 433 * bot_ :Nickname is already in use");

        assertFailedWith("unavailable");
    }

    @Test
    public void failsOnABadPassword() {
        EmbeddedChannel channel = connect(config("bot", "oauth:wrong"));
        drain(channel);

        channel.writeInbound(":server 464 * :Password incorrect");

        assertFailedWith("rejected by the server");
        assertFalse("the channel should be closed", channel.isOpen());
    }

    @Test
    public void failsWhenTheServerHangsUpMidHandshake() {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        channel.close();

        assertFailedWith("closed before registration completed");
    }

    @Test
    public void passesEveryMessageOnToTheRestOfThePipeline() {
        List<String> seen = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(
                new RegistrationHandler(config("bot", null), registered),
                new io.netty.channel.SimpleChannelInboundHandler<String>() {
                    @Override
                    protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx,
                            String msg) {
                        seen.add(msg);
                    }
                });
        drain(channel);

        channel.writeInbound(":server 001 bot :Welcome");
        channel.writeInbound(":n!u@h PRIVMSG #chan :hello");

        assertEquals(2, seen.size());
        assertEquals(":server 001 bot :Welcome", seen.get(0));
    }

    @Test
    public void toleratesAnUnparseableLine() {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        channel.writeInbound(":");

        assertFalse(registered.isDone());
        assertTrue(channel.isOpen());
    }

    @Test
    public void retriesOnANickCollisionToo() {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        channel.writeInbound(":server 436 * bot :Nickname collision KILL");

        assertEquals("NICK bot_", channel.readOutbound());
    }

    @Test
    public void failsOnAnIllegalNick() {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        channel.writeInbound(":server 432 * bot :Erroneous nickname");

        assertFailedWith("rejected by the server");
    }

    @Test
    public void ignoresACapMessageWithNoSubcommand() {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        channel.writeInbound(":server CAP");

        assertNull("nothing to negotiate, so nothing to send", channel.readOutbound());
        assertFalse(registered.isDone());
    }

    @Test
    public void endsNegotiationWhenTheServerListsNoCapabilitiesAtAll() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.getCapabilities().add("sasl");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        // CAP LS with no trailing parameter: the server offers nothing.
        channel.writeInbound(":server CAP * LS");

        assertEquals("CAP END", channel.readOutbound());
    }

    @Test
    public void matchesACapabilityAdvertisedWithABareEquals() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.getCapabilities().add("sasl");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server CAP * LS :sasl= multi-prefix");

        assertEquals("CAP REQ :sasl", channel.readOutbound());
    }

    @Test
    public void sendsCapEndOnlyOnce() {
        RegistrationConfiguration configuration = config("bot", null);
        configuration.getCapabilities().add("sasl");
        EmbeddedChannel channel = connect(configuration);
        drain(channel);

        channel.writeInbound(":server CAP * LS :sasl");
        assertEquals("CAP REQ :sasl", channel.readOutbound());
        channel.writeInbound(":server CAP * ACK :sasl");
        assertEquals("CAP END", channel.readOutbound());

        // A duplicate ACK, which a server is free to send, must not re-END.
        channel.writeInbound(":server CAP * ACK :sasl");

        assertNull(channel.readOutbound());
    }

    @Test
    public void reportsTheNickItSettledOn() {
        RegistrationHandler handler = new RegistrationHandler(config("bot", null), registered);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        drain(channel);
        assertEquals("bot", handler.getCurrentNick());

        channel.writeInbound(":server 433 * bot :Nickname is already in use");

        assertEquals("bot_", handler.getCurrentNick());
    }

    private RegistrationConfiguration config(String nick, String password) {
        RegistrationConfiguration configuration = new RegistrationConfiguration();
        configuration.setNick(nick);
        configuration.setPassword(password);
        return configuration;
    }

    @Test
    public void anErrorDuringRegistrationIsARefusalWithTheServersWords() throws Exception {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        // What EFnet sends on a plaintext port when it has decided against you.
        channel.writeInbound("ERROR :Closing Link: throttled\r\n");

        assertFailedWith("throttled");

        ServerRefusedException refusal = refusal();
        assertEquals("Closing Link: throttled", refusal.getReply());
        assertFalse("the server hung up, so the channel should be closed",
                channel.isOpen());
    }

    @Test
    public void aRefusalIsDistinguishableFromAnOrdinaryFailure() throws Exception {
        EmbeddedChannel channel = connect(config("bot", null));
        drain(channel);

        // A nick collision is a failure, but not the server refusing to have us:
        // retrying shortly is reasonable, so it must not read as a refusal.
        channel.writeInbound(":server 464 bot :Password incorrect\r\n");

        assertTrue(registered.isCompletedExceptionally());
        try {
            registered.get();
            fail("expected an exception");
        } catch (ExecutionException e) {
            assertFalse("a bad password is not a refusal to accept connections",
                    e.getCause() instanceof ServerRefusedException);
        }
    }

    private ServerRefusedException refusal() throws Exception {
        try {
            registered.get();
            fail("expected an exception");
            return null;
        } catch (ExecutionException e) {
            assertTrue("expected a refusal, got " + e.getCause(),
                    e.getCause() instanceof ServerRefusedException);
            return (ServerRefusedException) e.getCause();
        }
    }

    private EmbeddedChannel connect(RegistrationConfiguration configuration) {
        // Constructing with the handler fires channelActive, as a real connect does.
        return new EmbeddedChannel(new RegistrationHandler(configuration, registered));
    }

    private static void drain(EmbeddedChannel channel) {
        while (channel.readOutbound() != null) {
            // discard the handshake so each test asserts only what follows
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
