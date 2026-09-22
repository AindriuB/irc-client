package io.github.aindriub.irc.client.impl;

import java.io.OutputStream;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.Client;
import io.github.aindriub.irc.client.IRCClientException;
import io.github.aindriub.irc.client.configuration.ClientConfiguration;
import io.github.aindriub.irc.client.configuration.ConnectionConfiguration;
import io.github.aindriub.irc.client.handler.InboundMessageEventHandler;
import io.github.aindriub.irc.client.handler.OutputStreamWriterInboundMessageHandler;
import io.github.aindriub.irc.client.handler.PingHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.LineSeparator;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public abstract class AbstractClient implements Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractClient.class);

    /**
     * RFC 1459 caps a message at 512 bytes, but IRCv3 message tags push real traffic
     * well past that, so allow some headroom.
     */
    private static final int MAX_FRAME_SIZE = 2048;

    private final SslContext sslContext;
    protected Bootstrap bootstrap;
    protected ChannelFuture channelFuture;
    protected EventLoopGroup workerGroup;
    protected ClientConfiguration configuration;

    public AbstractClient(final ClientConfiguration configuration) {
        this.configuration = configuration;
        this.sslContext = buildSslContext(configuration.getConnection());
        workerGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, configuration.getConnection().isKeepAlive());
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                configuration.getConnection().getConnectTimeout());
        bootstrap.handler(channelInitializer());
    }

    private static SslContext buildSslContext(ConnectionConfiguration connection) {
        if (!connection.isSecure()) {
            return null;
        }
        try {
            SslContextBuilder builder = SslContextBuilder.forClient();
            if (connection.isTrustAllCertificates()) {
                LOGGER.warn("TLS certificate validation is disabled; the connection to {} is "
                        + "encrypted but not authenticated", connection.getHost());
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            }
            return builder.build();
        } catch (SSLException e) {
            // Failing loudly: the old code logged this and carried on in plaintext.
            throw new IRCClientException("Couldn't build SSL context", e);
        }
    }

    protected ChannelInitializer<SocketChannel> channelInitializer() {
        return new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                configurePipeline(ch);
            }
        };
    }

    /**
     * Builds the pipeline for a channel. Order matters: inbound bytes must be framed
     * into lines before they are decoded to strings, or the decoder hands on whatever
     * arbitrary TCP chunk happened to arrive.
     */
    protected void configurePipeline(Channel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslContext != null) {
            pipeline.addLast("sslHandler", sslContext.newHandler(ch.alloc(),
                    configuration.getConnection().getHost(), configuration.getConnection().getPort()));
        }
        if (configuration.isDebug()) {
            pipeline.addLast("loggingHandler", new LoggingHandler());
        }
        pipeline.addLast("lineBasedFrameDecoder", new LineBasedFrameDecoder(MAX_FRAME_SIZE));
        pipeline.addLast("stringDecoder", new StringDecoder(configuration.getCharSet()));
        // Every IRC message is CRLF terminated (RFC 1459 section 2.3); the encoder adds
        // it so that callers never have to.
        pipeline.addLast("lineEncoder",
                new LineEncoder(LineSeparator.WINDOWS, configuration.getCharSet()));
        pipeline.addLast("pingHandler", new PingHandler());

        int index = 0;
        for (OutputStream outputStream : configuration.getOutputStreams()) {
            // Indexed rather than named after the stream class: two streams of the same
            // class collided, and an anonymous subclass has no canonical name at all.
            pipeline.addLast("outputStreamWriterInboundMessageHandler#" + index++,
                    new OutputStreamWriterInboundMessageHandler(outputStream,
                            configuration.getCharSet()));
        }
        pipeline.addLast("inboundMessageEventHandler",
                new InboundMessageEventHandler(configuration.getEventHandlers()));
    }

    @Override
    public void connect() {
        String host = configuration.getConnection().getHost();
        int port = configuration.getConnection().getPort();
        LOGGER.info("Connecting to {}:{}", host, port);
        try {
            channelFuture = bootstrap.connect(host, port).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IRCClientException("Interrupted while connecting to " + host + ":" + port, e);
        }
    }

    @Override
    public void send(String payload) {
        if (!isConnected()) {
            connect();
        }
        try {
            channelFuture.channel().writeAndFlush(payload).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IRCClientException("Interrupted while sending: " + payload, e);
        }
    }

    public boolean isConnected() {
        return channelFuture != null && channelFuture.channel().isActive();
    }

    @Override
    public void disconnect() {
        LOGGER.info("Disconnecting");
        try {
            if (channelFuture != null) {
                // close(), not closeFuture(): the latter only waits for a close that
                // something else initiates, so it hung until the peer hung up.
                channelFuture.channel().close().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IRCClientException("Interrupted while closing the connection", e);
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}
