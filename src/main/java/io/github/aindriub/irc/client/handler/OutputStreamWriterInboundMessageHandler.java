package io.github.aindriub.irc.client.handler;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Mirrors every inbound message to an {@link OutputStream}.
 */
public class OutputStreamWriterInboundMessageHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(OutputStreamWriterInboundMessageHandler.class);

    private final BufferedOutputStream outputStream;
    private final Charset charset;
    private final byte[] lineSeparator;

    public OutputStreamWriterInboundMessageHandler(OutputStream outputStream, Charset charset) {
        this.outputStream = new BufferedOutputStream(
                Objects.requireNonNull(outputStream, "outputStream"));
        this.charset = Objects.requireNonNull(charset, "charset");
        this.lineSeparator = System.lineSeparator().getBytes(this.charset);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        try {
            // The frame decoder strips the delimiter, so re-add one or every message
            // is concatenated into a single unreadable line.
            byte[] bytes = msg.getBytes(charset);
            outputStream.write(bytes, 0, bytes.length);
            outputStream.write(lineSeparator, 0, lineSeparator.length);
            outputStream.flush();
        } catch (IOException e) {
            LOGGER.error("Error writing to stream", e);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        flushQuietly();
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Error handling message", cause);
        flushQuietly();
        ctx.close();
    }

    private void flushQuietly() {
        try {
            // Flush rather than close: the stream is owned by the caller, who may
            // well have handed us System.out.
            outputStream.flush();
        } catch (IOException e) {
            LOGGER.error("Can't flush output stream", e);
        }
    }
}
