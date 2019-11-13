package ie.aindriu.irc.client.handler;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ie.aindriu.irc.client.event.Event;
import ie.aindriu.irc.client.event.EventHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public abstract class EventPublishingMessageHandler<T> extends SimpleChannelInboundHandler<T> {

    private static Logger LOGGER = LoggerFactory.getLogger(EventPublishingMessageHandler.class);

    private List<EventHandler<T>> eventHandlers;
    
    public EventPublishingMessageHandler() {
	eventHandlers = null;
    }
    
    public EventPublishingMessageHandler(List<EventHandler<T>> eventHandlers) {
	this.eventHandlers = eventHandlers;
    }
    

    @Override
    public void channelRead0(ChannelHandlerContext ctx, T msg) throws Exception {
	if (eventHandlers != null) {
	    final Event<T> event = new Event<T>();
	    event.setPayload(msg);
	    event.setTimestamp(new Date());
	    eventHandlers.stream().filter(eh-> eh.getClass().getGenericSuperclass().equals(msg.getClass())).forEach(eh -> eh.publishEvent(event));
	}
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
	LOGGER.error("Error handling message", cause);
	ctx.fireExceptionCaught(cause);
	ctx.close();
    }

}
