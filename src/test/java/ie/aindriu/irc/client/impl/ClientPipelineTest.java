package ie.aindriu.irc.client.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ie.aindriu.irc.client.command.Join;
import ie.aindriu.irc.client.configuration.ClientConfiguration;
import ie.aindriu.irc.client.configuration.ClientConfigurationBuilder;
import ie.aindriu.irc.client.event.Event;
import ie.aindriu.irc.client.event.EventHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * Exercises the pipeline without a network: everything here used to pass silently
 * while the client could not actually decode or send a single IRC message.
 */
public class ClientPipelineTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final List<String> received = new ArrayList<>();
    private ByteArrayOutputStream mirror;
    private BasicIRCClient client;
    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        mirror = new ByteArrayOutputStream();
        ClientConfiguration configuration = new ClientConfigurationBuilder()
                .host("irc.example.org")
                .port(6667)
                .secure(false)
                .charset(UTF_8)
                .outputStream(mirror)
                .eventListener(new EventHandler<String>() {
                    @Override
                    public void publishEvent(Event<String> event) {
                        received.add(event.getPayload());
                    }
                })
                .build();
        client = new BasicIRCClient(configuration);
        channel = new EmbeddedChannel();
        client.configurePipeline(channel);
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();
        client.disconnect();
    }

    @Test
    public void framesInboundBytesIntoWholeLinesBeforeDecoding() {
        // Arrives split across two reads, as a real socket would deliver it.
        writeInbound("PRIVMSG #chan :hel");
        assertTrue("a partial line must not be published", received.isEmpty());

        writeInbound("lo there\r\nPRIVMSG #chan :second\r\n");

        assertEquals(2, received.size());
        assertEquals("PRIVMSG #chan :hello there", received.get(0));
        assertEquals("PRIVMSG #chan :second", received.get(1));
    }

    @Test
    public void terminatesOutboundMessagesWithCrLf() {
        channel.writeOutbound(new Join("#chan").toString());

        assertEquals("JOIN #chan\r\n", readOutbound());
    }

    @Test
    public void answersServerPingWithAMatchingPong() {
        writeInbound("PING :tmi.twitch.tv\r\n");

        assertEquals("PONG :tmi.twitch.tv\r\n", readOutbound());
        assertEquals("the ping is still published to subscribers", 1, received.size());
    }

    @Test
    public void doesNotPongOnAMessageThatMerelyStartsWithPing() {
        writeInbound("PINGU :not a ping\r\n");

        assertNull(channel.readOutbound());
    }

    @Test
    public void mirrorsInboundMessagesToConfiguredOutputStreams() {
        writeInbound("PRIVMSG #chan :one\r\nPRIVMSG #chan :two\r\n");

        String expected = "PRIVMSG #chan :one" + System.lineSeparator()
                + "PRIVMSG #chan :two" + System.lineSeparator();
        assertEquals(expected, new String(mirror.toByteArray(), UTF_8));
    }

    private void writeInbound(String raw) {
        channel.writeInbound(Unpooled.copiedBuffer(raw, UTF_8));
    }

    private String readOutbound() {
        ByteBuf out = channel.readOutbound();
        try {
            return out.toString(UTF_8);
        } finally {
            out.release();
        }
    }
}
