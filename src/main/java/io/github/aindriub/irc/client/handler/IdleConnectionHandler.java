package io.github.aindriub.irc.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.command.Ping;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Notices a connection that has gone quiet.
 *
 * <p>PingHandler answers the server's pings, but nothing detected a server that had
 * stopped sending altogether: a dropped connection that never produced a TCP reset
 * would leave the client sitting on a socket that was never going to deliver
 * anything again. On the first idle period this sends a PING; if a second passes
 * with still nothing read, the connection is closed, which is what lets the
 * reconnect logic take over.
 */
public class IdleConnectionHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdleConnectionHandler.class);

    private static final String TOKEN_PREFIX = "irc-client-";

    private boolean awaitingResponse;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Any traffic at all proves the connection is alive; it need not be our pong.
        awaitingResponse = false;
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (!(event instanceof IdleStateEvent)
                || ((IdleStateEvent) event).state() != IdleState.READER_IDLE) {
            ctx.fireUserEventTriggered(event);
            return;
        }
        if (awaitingResponse) {
            LOGGER.warn("No response to our ping; closing the connection as dead");
            ctx.close();
            return;
        }
        awaitingResponse = true;
        Ping ping = new Ping(TOKEN_PREFIX + System.currentTimeMillis());
        LOGGER.debug("Connection is quiet, checking it is alive: {}", ping);
        ctx.writeAndFlush(ping.render());
    }
}
