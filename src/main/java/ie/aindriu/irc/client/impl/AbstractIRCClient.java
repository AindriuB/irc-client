package ie.aindriu.irc.client.impl;

import java.util.List;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ie.aindriu.irc.client.Connection;
import ie.aindriu.irc.client.IRCClient;
import ie.aindriu.irc.client.command.Command;
import ie.aindriu.irc.client.command.Quit;
import ie.aindriu.irc.client.event.EventHandler;
import ie.aindriu.irc.client.handler.IRCEventPublishingMessageHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public abstract class AbstractIRCClient implements IRCClient {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractIRCClient.class);
    
    private static final int MAX_FRAME_SIZE = 2048;
    
    private SslContext sslContext;
    protected Bootstrap b;
    protected ChannelFuture f;
    protected EventLoopGroup workerGroup;
    final private Connection connection;
    
    final private List<EventHandler<String>> eventHandlers;
    
    public AbstractIRCClient(final Connection connection) {
	this.eventHandlers = null;
	this.connection = connection;
	try {
	    sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
	} catch (SSLException e) {
	    LOGGER.error("Couldn't get SSL context", e);
	}
	workerGroup = new NioEventLoopGroup();
	b = new Bootstrap();
	b.group(workerGroup);
	b.channel(NioSocketChannel.class);
	b.option(ChannelOption.SO_KEEPALIVE, connection.isKeepAlive());
	b.option(ChannelOption.AUTO_CLOSE, false);
    }
    
    public AbstractIRCClient(final Connection connection, List<EventHandler<String>> eventHandlers) {
	this.eventHandlers = eventHandlers;
	this.connection = connection;
	try {
	    sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
	} catch (SSLException e) {
	    LOGGER.error("Couldn't get SSL context", e);
	}
	workerGroup = new NioEventLoopGroup();
	b = new Bootstrap();
	b.group(workerGroup);
	b.channel(NioSocketChannel.class);
	b.option(ChannelOption.SO_KEEPALIVE, connection.isKeepAlive());
	b.option(ChannelOption.AUTO_CLOSE, false);
    }
    

    
    protected ChannelInitializer<SocketChannel> channelInitializer() {
	return new ChannelInitializer<SocketChannel>() {
	    @Override
	    public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		if (sslContext != null) {
		    pipeline.addLast(sslContext.newHandler(ch.alloc(), connection.getHost(), connection.getPort()));
		}
		pipeline.addLast("frameDecoder", new DelimiterBasedFrameDecoder(MAX_FRAME_SIZE, true, Delimiters.lineDelimiter()));
		pipeline.addLast("Logging Handler", new LoggingHandler());
		pipeline.addLast("decoder", new StringDecoder());
		pipeline.addLast("encoder", new StringEncoder());
		pipeline.addLast("messageHandler", new IRCEventPublishingMessageHandler(eventHandlers));
	    }
	};
    }

    public void connect() {
	LOGGER.info("Connecting");
	try {
	    b.handler(channelInitializer());
	    f = b.connect(connection.getHost(), connection.getPort());
	    f.sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Connection attempt failed", e);
	}

    }
    
    @Override
    public void sendCommand(Command command) {
	sendString(command.toString());
    }

    public void sendString(String message) {
	
	if(!f.channel().isOpen()) {
	    connect();
	}
	
	try {
	    f.channel().writeAndFlush(message).sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Error sending message: {} ", message, e);
	}
    }

    public void disconnect() {
	LOGGER.info("Disconnecting");
	try {
	    sendCommand(new Quit());
	    f.channel().closeFuture().sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Failed to close connection", e);
	} finally {
	    workerGroup.shutdownGracefully();
	}

    }

}
