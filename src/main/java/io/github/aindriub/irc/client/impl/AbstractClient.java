package io.github.aindriub.irc.client.impl;

import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.Client;
import io.github.aindriub.irc.client.IRCClientException;
import io.github.aindriub.irc.client.IRCText;
import io.github.aindriub.irc.client.configuration.ClientConfiguration;
import io.github.aindriub.irc.client.configuration.ConnectionConfiguration;
import io.github.aindriub.irc.client.configuration.ReconnectConfiguration;
import io.github.aindriub.irc.client.handler.ConnectionLostHandler;
import io.github.aindriub.irc.client.handler.IdleConnectionHandler;
import io.github.aindriub.irc.client.handler.OutboundRateLimiter;
import io.github.aindriub.irc.client.handler.InboundIRCMessageEventHandler;
import io.github.aindriub.irc.client.handler.IRCMessageDecoder;
import io.github.aindriub.irc.client.handler.InboundMessageEventHandler;
import io.github.aindriub.irc.client.handler.OutputStreamWriterInboundMessageHandler;
import io.github.aindriub.irc.client.handler.PingHandler;
import io.github.aindriub.irc.client.handler.RegistrationHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.LineSeparator;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
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
    protected volatile ChannelFuture channelFuture;
    protected EventLoopGroup workerGroup;
    protected ClientConfiguration configuration;

    /**
     * Completed when the server accepts registration. Replaced on each connect, so
     * a reconnect waits on its own handshake rather than the previous one.
     */
    private volatile CompletableFuture<Void> registered;

    /**
     * Set by disconnect(). Suppresses reconnection, so a deliberate shutdown is not
     * immediately undone, and rejects later use of a closed client.
     */
    private volatile boolean shutdown;

    /** Guards connect and disconnect against each other. */
    private final Object lifecycleLock = new Object();

    /**
     * Consecutive failed reconnects. Client scoped rather than passed down the
     * attempt chain: closing a channel fires channelInactive, so a per-call counter
     * was restarted at 1 by the very close that followed a failed handshake, and
     * maxAttempts was never reached.
     */
    private final AtomicInteger reconnectAttempts = new AtomicInteger();

    public AbstractClient(final ClientConfiguration configuration) {
        this.configuration = configuration;
        this.shutdown = false;
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
        long readTimeout = configuration.getConnection().getReadTimeout();
        if (readTimeout > 0) {
            pipeline.addLast("idleStateHandler",
                    new IdleStateHandler(readTimeout, 0, 0, TimeUnit.MILLISECONDS));
            pipeline.addLast("idleConnectionHandler", new IdleConnectionHandler());
        }
        pipeline.addLast("pingHandler", new PingHandler());
        if (configuration.getRegistration().isConfigured()) {
            registered = new CompletableFuture<>();
            pipeline.addLast("registrationHandler",
                    new RegistrationHandler(configuration.getRegistration(), registered));
        }

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

        if (configuration.getFlood().isEnabled()) {
            // Below the ping, idle and registration handlers, so their writes start
            // closer to the socket and are never throttled: delaying a PONG or the
            // handshake would cause the very timeouts they exist to prevent.
            pipeline.addLast("outboundRateLimiter",
                    new OutboundRateLimiter(configuration.getFlood()));
        }

        if (!configuration.getMessageHandlers().isEmpty()) {
            // Last, so that raw subscribers and output streams see the unparsed line
            // and nothing pays for parsing unless a typed subscriber asked for it.
            pipeline.addLast("ircMessageDecoder", new IRCMessageDecoder());
            pipeline.addLast("inboundIRCMessageEventHandler",
                    new InboundIRCMessageEventHandler(configuration.getMessageHandlers()));
        }

        pipeline.addLast("connectionLostHandler",
                new ConnectionLostHandler(new ConnectionLostHandler.Listener() {
                    @Override
                    public void connectionLost() {
                        onConnectionLost();
                    }
                }));
    }

    @Override
    public void connect() {
        synchronized (lifecycleLock) {
            requireUsable();
            reconnectAttempts.set(0);
            String host = configuration.getConnection().getHost();
            int port = configuration.getConnection().getPort();
            LOGGER.info("Connecting to {}:{}", host, port);
            try {
                channelFuture = bootstrap.connect(host, port).sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IRCClientException(
                        "Interrupted while connecting to " + host + ":" + port, e);
            }
            if (configuration.getRegistration().isConfigured()) {
                // connect() returning should mean "ready to use", not just "TCP is open".
                awaitRegistration();
            }
        }
    }

    /**
     * Blocks until the server accepts registration, so that a caller cannot send a
     * command into a half-open handshake.
     */
    private void awaitRegistration() {
        CompletableFuture<Void> handshake = registered;
        int timeout = configuration.getRegistration().getRegistrationTimeout();
        try {
            handshake.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IRCClientException("Interrupted while registering", e);
        } catch (TimeoutException e) {
            throw new IRCClientException(
                    "Server did not complete registration within " + timeout + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IRCClientException) {
                throw (IRCClientException) cause;
            }
            throw new IRCClientException("Registration failed", cause);
        }
    }

    /**
     * True once the server has accepted registration on the current connection.
     */
    public boolean isRegistered() {
        CompletableFuture<Void> handshake = registered;
        return handshake != null && handshake.isDone() && !handshake.isCompletedExceptionally();
    }

    @Override
    public void send(String payload) {
        // The raw send path has to validate too, or it is a way around the checks
        // every Command performs.
        IRCText.requireText(payload, "payload");
        ChannelFuture current = channelFuture;
        if (current == null || !current.channel().isActive()) {
            // Previously this quietly reconnected, which since registration exists
            // would leave an unregistered connection that rejects every command.
            // Reconnection is now the reconnect logic's job, and it re-registers.
            throw new IRCClientException("Not connected; call connect() first");
        }
        ChannelFuture write = current.channel().writeAndFlush(payload);
        if (current.channel().eventLoop().inEventLoop()) {
            // A listener replying from a callback runs on the event loop, and
            // waiting there would deadlock the thread that has to do the writing.
            return;
        }
        try {
            // await() rather than sync(): sync() rethrows Netty's own failure cause
            // unwrapped, so a channel that died between the check above and the write
            // surfaced as a raw ClosedChannelException rather than ours.
            write.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IRCClientException("Interrupted while sending: " + payload, e);
        }
        if (!write.isSuccess()) {
            throw new IRCClientException("Failed to send: " + payload, write.cause());
        }
    }

    public boolean isConnected() {
        return channelFuture != null && channelFuture.channel().isActive();
    }

    @Override
    public void disconnect() {
        synchronized (lifecycleLock) {
            LOGGER.info("Disconnecting");
            // Set first: closing the channel fires channelInactive, and this is what
            // tells the reconnect logic the disconnection was deliberate.
            shutdown = true;
            ChannelFuture current = channelFuture;
            try {
                if (current != null) {
                    // close(), not closeFuture(): the latter only waits for a close
                    // that something else initiates, so it hung until the peer did.
                    current.channel().close().sync();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IRCClientException("Interrupted while closing the connection", e);
            } finally {
                workerGroup.shutdownGracefully();
            }
        }
    }

    /**
     * Called when a connection goes away for any reason. Reconnects with backoff
     * unless the disconnection was deliberate or reconnection is switched off.
     */
    private void onConnectionLost() {
        if (shutdown) {
            return;
        }
        if (!configuration.getReconnect().isEnabled()) {
            LOGGER.info("Connection lost; reconnection is disabled");
            return;
        }
        LOGGER.warn("Connection lost; reconnecting");
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        ReconnectConfiguration reconnect = configuration.getReconnect();
        final int attempt = reconnectAttempts.incrementAndGet();
        if (reconnect.getMaxAttempts() > 0 && attempt > reconnect.getMaxAttempts()) {
            LOGGER.error("Giving up after {} reconnect attempts", reconnect.getMaxAttempts());
            return;
        }
        long delay = reconnect.delayFor(attempt);
        LOGGER.info("Reconnect attempt {} in {}ms", attempt, delay);
        try {
            workerGroup.schedule(new Runnable() {
                @Override
                public void run() {
                    tryReconnect(attempt);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The group is shutting down, which means we are on our way out anyway.
            LOGGER.debug("Not reconnecting, the event loop is shutting down");
        }
    }

    /**
     * Connects without blocking: this runs on an event loop thread, so waiting for
     * the handshake here would stop the handshake from ever completing.
     */
    private void tryReconnect(final int attempt) {
        if (shutdown) {
            return;
        }
        String host = configuration.getConnection().getHost();
        int port = configuration.getConnection().getPort();
        bootstrap.connect(host, port).addListener(new GenericFutureListener<ChannelFuture>() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!future.isSuccess()) {
                    LOGGER.warn("Reconnect attempt {} failed: {}", attempt,
                            String.valueOf(future.cause()));
                    // The channel never went active, so nothing else will drive the
                    // next attempt.
                    scheduleReconnect();
                    return;
                }
                channelFuture = future;
                awaitRegistrationThenResume(future, attempt);
            }
        });
    }

    private void awaitRegistrationThenResume(final ChannelFuture future, final int attempt) {
        CompletableFuture<Void> handshake = registered;
        if (!configuration.getRegistration().isConfigured() || handshake == null) {
            reconnectSucceeded();
            return;
        }
        handshake.whenComplete(new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(Void ignored, Throwable error) {
                if (error == null) {
                    reconnectSucceeded();
                    return;
                }
                LOGGER.warn("Reconnected but registration failed on attempt {}: {}", attempt,
                        String.valueOf(error));
                // Closing fires channelInactive, which schedules the next attempt.
                // Scheduling one here as well would run two chains at once.
                future.channel().close();
            }
        });
    }

    private void reconnectSucceeded() {
        reconnectAttempts.set(0);
        onReconnected();
    }

    /**
     * Hook for subclasses to restore per-connection state, such as rejoining
     * channels. Runs after registration completes on a reconnected channel.
     */
    protected void onReconnected() {
        LOGGER.info("Reconnected");
    }

    private void requireUsable() {
        if (shutdown) {
            throw new IRCClientException(
                    "This client has been disconnected and cannot be reused");
        }
    }

}
