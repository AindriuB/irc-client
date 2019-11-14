package ie.aindriu.irc.client.impl;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ie.aindriu.irc.client.Client;
import ie.aindriu.irc.client.ClientConfiguration;
import ie.aindriu.irc.client.command.Command;
import ie.aindriu.irc.client.command.Quit;
import ie.aindriu.irc.client.handler.InboundEventPublishingMessageHandler;
import ie.aindriu.irc.client.handler.OutputStreamWriterMessageHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public abstract class AbstractClient implements Client {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractClient.class);

    private static final int MAX_FRAME_SIZE = 2048;

    private SslContext sslContext;
    protected Bootstrap bootstrap;
    protected ChannelFuture channelFuture;
    protected EventLoopGroup workerGroup;

    private ClientConfiguration configuration;

    public AbstractClient(final ClientConfiguration configuration) {
	this.configuration = configuration;
	try {
	    sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
	} catch (SSLException e) {
	    LOGGER.error("Couldn't get SSL context", e);
	}
	workerGroup = new NioEventLoopGroup();
	bootstrap = new Bootstrap();
	bootstrap.group(workerGroup);
	bootstrap.channel(NioSocketChannel.class);
	bootstrap.option(ChannelOption.SO_KEEPALIVE, configuration.getConnection().isKeepAlive());
	bootstrap.option(ChannelOption.AUTO_CLOSE, false);
    }

    protected ChannelInitializer<SocketChannel> channelInitializer() {
	return new ChannelInitializer<SocketChannel>() {
	    @Override
	    public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		if (sslContext != null) {
		    pipeline.addFirst("sslHandler", sslContext.newHandler(ch.alloc(),
			    configuration.getConnection().getHost(), configuration.getConnection().getPort()));
		}
		if (configuration.isDebug()) {
		    pipeline.addLast("loggingHandler", new LoggingHandler());
		}
		pipeline.addLast("stringDecoder", new StringDecoder(configuration.getCharSet()));
		pipeline.addLast("stringEncoder", new StringEncoder(configuration.getCharSet()));
		pipeline.addLast("frameDecoder", new LineBasedFrameDecoder(MAX_FRAME_SIZE));
		configuration.getOutputStreams().stream()
			.forEach(os -> pipeline.addLast("outputStreamWriterMessageHandler#" + os.getClass().getCanonicalName().trim(),
				new OutputStreamWriterMessageHandler(os)));
		pipeline.addLast("eventPlublishingHandler",
			new InboundEventPublishingMessageHandler(configuration.getEventHandlers()));


	    }
	};
    }

    public void connect() {
	LOGGER.info("Connecting");
	try {
	    bootstrap.handler(channelInitializer());
	    channelFuture = bootstrap.connect(configuration.getConnection().getHost(), configuration.getConnection().getPort());
	    channelFuture.sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Connection attempt failed", e);
	}

    }

    @Override
    public void sendCommand(Command command) {
	send(command.toString().getBytes(configuration.getCharSet()));
    }

    public void send(byte[] payload) {

	if (!channelFuture.channel().isOpen()) {
	    connect();
	}

	try {
	    channelFuture.channel().writeAndFlush(payload).sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Error sending message: {} ", payload, e);
	}
    }

    public void disconnect() {
	LOGGER.info("Disconnecting");
	try {
	    sendCommand(new Quit());
	    channelFuture.channel().closeFuture().sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Failed to close connection", e);
	} finally {
	    workerGroup.shutdownGracefully();
	}

    }

}
