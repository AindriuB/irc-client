package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class IdleConnectionHandlerTest {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new IdleConnectionHandler());
    }

    @Test
    public void pingsOnTheFirstQuietPeriod() {
        fireReaderIdle();

        String sent = channel.readOutbound();
        assertTrue(sent, sent.startsWith("PING :irc-client-"));
        assertTrue("one quiet period is not proof of a dead connection", channel.isOpen());
    }

    @Test
    public void closesTheConnectionIfTheSecondQuietPeriodPassesWithNoAnswer() {
        fireReaderIdle();
        channel.readOutbound();

        fireReaderIdle();

        assertFalse("a server that answers nothing is gone", channel.isOpen());
    }

    @Test
    public void anyInboundTrafficProvesTheConnectionIsAlive() {
        fireReaderIdle();
        channel.readOutbound();

        // Not our pong, but it still proves the server is talking to us.
        channel.writeInbound(":server PRIVMSG #chan :hello");
        fireReaderIdle();

        assertTrue("traffic arrived, so this is a fresh quiet period", channel.isOpen());
        assertTrue(((String) channel.readOutbound()).startsWith("PING :"));
    }

    @Test
    public void passesInboundTrafficOn() {
        channel.writeInbound(":server PRIVMSG #chan :hello");

        assertEquals(":server PRIVMSG #chan :hello", channel.readInbound());
    }

    @Test
    public void ignoresWriterAndAllIdleEvents() {
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);

        assertNull("only a silent reader means a possibly dead connection",
                channel.readOutbound());
        assertTrue(channel.isOpen());
    }

    @Test
    public void passesUnrelatedUserEventsOn() {
        final Object[] seen = new Object[1];
        EmbeddedChannel pipeline = new EmbeddedChannel(new IdleConnectionHandler(),
                new io.netty.channel.ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(io.netty.channel.ChannelHandlerContext ctx,
                            Object event) {
                        seen[0] = event;
                    }
                });

        Object event = new Object();
        pipeline.pipeline().fireUserEventTriggered(event);

        assertEquals(event, seen[0]);
    }

    private void fireReaderIdle() {
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
    }
}
