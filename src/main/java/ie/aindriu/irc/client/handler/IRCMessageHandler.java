package ie.aindriu.irc.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class IRCMessageHandler extends SimpleChannelInboundHandler<String> {
    
    private static Logger LOGGER = LoggerFactory.getLogger(IRCMessageHandler.class);

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
	LOGGER.info(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
	LOGGER.error("Error handling message", cause);
	ctx.close();
    }

}
