package io.github.aindriub.irc.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.command.Pong;
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

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        if (isPing(msg)) {
            Pong pong = new Pong(token(msg));
            LOGGER.debug("Replying to server ping: {}", pong);
            ctx.writeAndFlush(pong.render());
        }
        ctx.fireChannelRead(msg);
    }

    private static boolean isPing(String msg) {
        if (!msg.startsWith(PING)) {
            return false;
        }
        return msg.length() == PING.length() || msg.charAt(PING.length()) == ' ';
    }

    /**
     * The token must come back unchanged, so strip only the framing: the separating
     * space and the trailing parameter's colon.
     */
    private static String token(String msg) {
        String token = msg.substring(PING.length()).trim();
        if (token.startsWith(":")) {
            token = token.substring(1);
        }
        return token;
    }
}
