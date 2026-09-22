package io.github.aindriub.irc.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Answers server PING with the matching PONG (RFC 1459 section 4.6.2). Without this
 * the server drops the connection on ping timeout, usually within a few minutes.
 * The message is passed on so that subscribers and output streams still see it.
 */
public class PingHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PingHandler.class);

    private static final String PING = "PING";
    private static final String PONG = "PONG";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        if (isPing(msg)) {
            String pong = PONG + msg.substring(PING.length());
            LOGGER.debug("Replying to server ping: {}", pong);
            ctx.writeAndFlush(pong);
        }
        ctx.fireChannelRead(msg);
    }

    private static boolean isPing(String msg) {
        if (!msg.startsWith(PING)) {
            return false;
        }
        return msg.length() == PING.length() || msg.charAt(PING.length()) == ' ';
    }
}
