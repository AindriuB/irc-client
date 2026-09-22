package io.github.aindriub.irc.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Notifies the client that a connection has gone away, so it can decide whether to
 * reconnect. Sits at the tail of the pipeline and does nothing else.
 */
public class ConnectionLostHandler extends ChannelInboundHandlerAdapter {

    /**
     * Run when the channel goes inactive, for whatever reason.
     */
    public interface Listener {
        void connectionLost();
    }

    private final Listener listener;

    public ConnectionLostHandler(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            ctx.fireChannelInactive();
        } finally {
            listener.connectionLost();
        }
    }
}
