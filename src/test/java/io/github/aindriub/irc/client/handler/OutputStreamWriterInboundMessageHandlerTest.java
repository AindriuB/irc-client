package io.github.aindriub.irc.client.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.junit.Test;

import io.netty.channel.embedded.EmbeddedChannel;

public class OutputStreamWriterInboundMessageHandlerTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String NL = System.lineSeparator();

    @Test
    public void writesEachMessageOnItsOwnLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EmbeddedChannel channel = new EmbeddedChannel(
                new OutputStreamWriterInboundMessageHandler(out, UTF_8));

        channel.writeInbound("one");
        channel.writeInbound("two");

        assertEquals("one" + NL + "two" + NL, new String(out.toByteArray(), UTF_8));
    }

    @Test
    public void honoursTheConfiguredCharset() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Charset utf16 = Charset.forName("UTF-16BE");
        EmbeddedChannel channel = new EmbeddedChannel(
                new OutputStreamWriterInboundMessageHandler(out, utf16));

        channel.writeInbound("héllo");

        assertEquals("héllo" + NL, new String(out.toByteArray(), utf16));
    }

    @Test
    public void passesTheMessageOnToTheRestOfThePipeline() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new OutputStreamWriterInboundMessageHandler(new ByteArrayOutputStream(), UTF_8));

        channel.writeInbound("one");

        assertEquals("one", channel.readInbound());
    }

    @Test
    public void keepsGoingWhenTheStreamFails() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new OutputStreamWriterInboundMessageHandler(failingStream(), UTF_8));

        channel.writeInbound("one");

        // A broken output stream is the caller's problem, not a reason to drop the
        // IRC connection.
        assertTrue(channel.isOpen());
        assertEquals("one", channel.readInbound());
    }

    @Test
    public void flushesWhenTheConnectionGoesAway() throws Exception {
        final boolean[] flushed = { false };
        OutputStream counting = new ByteArrayOutputStream() {
            @Override
            public void flush() {
                flushed[0] = true;
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(
                new OutputStreamWriterInboundMessageHandler(counting, UTF_8));

        channel.close().sync();

        assertTrue("buffered output would otherwise be lost", flushed[0]);
    }

    @Test
    public void closesTheChannelOnAPipelineException() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new OutputStreamWriterInboundMessageHandler(new ByteArrayOutputStream(), UTF_8));

        channel.pipeline().fireExceptionCaught(new IllegalStateException("broke"));

        assertFalse(channel.isOpen());
    }

    @Test
    public void survivesAStreamThatAlsoFailsToFlushOnShutdown() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(
                new OutputStreamWriterInboundMessageHandler(failingStream(), UTF_8));

        channel.close().sync();

        assertFalse(channel.isOpen());
    }

    @Test(expected = NullPointerException.class)
    public void rejectsANullStream() {
        new OutputStreamWriterInboundMessageHandler(null, UTF_8);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsANullCharset() {
        new OutputStreamWriterInboundMessageHandler(new ByteArrayOutputStream(), null);
    }

    private static OutputStream failingStream() {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("stream is broken");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("stream is broken");
            }

            @Override
            public void flush() throws IOException {
                throw new IOException("stream is broken");
            }
        };
    }
}
