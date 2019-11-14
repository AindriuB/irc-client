package ie.aindriu.irc.client.handler;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class OutputStreamWriterInboundMessageHandler extends SimpleChannelInboundHandler<String> {

    private static Logger LOGGER = LoggerFactory.getLogger(OutputStreamWriterInboundMessageHandler.class);

    private BufferedOutputStream outputStream;

    public OutputStreamWriterInboundMessageHandler() {
	outputStream = null;
    }

    public OutputStreamWriterInboundMessageHandler(OutputStream outputStream) {
	this.outputStream = new BufferedOutputStream(outputStream);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
	LOGGER.error("Error handling message", cause);
	if (outputStream != null) {
	    try {
		outputStream.close();
	    } catch (IOException e) {
		LOGGER.error("Cant close output stream", e);
	    }
	}
	ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

	if (outputStream != null) {
	    LOGGER.debug("Writing message to stream {}", outputStream.getClass());
	    try {
		byte[] bytes = msg.getBytes();
		outputStream.write(bytes, 0, bytes.length);
		outputStream.flush();
	    } catch (IOException e) {
		LOGGER.error("Error writing to stream", e);
	    }
	}
	ctx.fireChannelRead(msg);
    }

}
