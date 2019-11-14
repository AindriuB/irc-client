package ie.aindriu.irc.client.impl;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ie.aindriu.irc.client.Client;
import ie.aindriu.irc.client.configuration.ClientConfiguration;
import ie.aindriu.irc.client.handler.InboundMessageEventHandler;
import ie.aindriu.irc.client.handler.OutputStreamWriterInboundMessageHandler;
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
    protected ClientConfiguration configuration;

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
	bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, configuration.getConnection().getConnectionTimeout());
	bootstrap.option(ChannelOption.AUTO_CLOSE, false);
	bootstrap.handler(channelInitializer());
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
		pipeline.addLast("lineBasedFrameDecoder", new LineBasedFrameDecoder(MAX_FRAME_SIZE));
		configuration.getOutputStreams().stream()
			.forEach(os -> pipeline.addLast("outputStreamWriterInboundMessageHandler#" + os.getClass().getCanonicalName().trim(),
				new OutputStreamWriterInboundMessageHandler(os)));
		pipeline.addLast("inboundMessageEventHandler",
			new InboundMessageEventHandler(configuration.getEventHandlers()));


	    }
	};
    }

    public void connect() {
	LOGGER.info("Connecting");
	try {
	    channelFuture = bootstrap.connect(configuration.getConnection().getHost(), configuration.getConnection().getPort());
	    channelFuture.sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Connection attempt failed", e);
	}

    }


    public void send(String payload) {

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
	    channelFuture.channel().closeFuture().sync();
	} catch (InterruptedException e) {
	    LOGGER.error("Failed to close connection", e);
	} finally {
	    workerGroup.shutdownGracefully();
	}

    }

}
