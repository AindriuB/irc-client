package io.github.aindriub.irc.client.impl;

import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import io.github.aindriub.irc.client.event.ConnectionEvent;
import io.github.aindriub.irc.client.event.Event;
import io.github.aindriub.irc.client.event.EventHandler;
import io.github.aindriub.irc.client.handler.ConnectionLostHandler;
import io.github.aindriub.irc.client.handler.IdleConnectionHandler;
import io.github.aindriub.irc.client.handler.OutboundRateLimiter;
import io.github.aindriub.irc.client.handler.ServerRefusedException;
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
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
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

    /** How long disconnect() waits for in-flight connection-state work to drain. */
    private static final long CONNECTION_EVENT_LOOP_SHUTDOWN_TIMEOUT_MILLIS = 5000;

    /** Minimum gap between two "refused write" WARN log lines. */
    private static final long REFUSED_WRITE_LOG_INTERVAL_MILLIS = 10_000;

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

    /**
     * Set when the last attempt ended in the server saying no rather than the
     * connection simply failing. Read once by the reconnect that follows: a
     * refusal is a decision, and knocking again a second later tends to confirm it
     * rather than get past it. EFnet restarts its throttle window on every
     * attempt inside it, so the short end of the backoff cannot ever escape.
     */
    private final AtomicBoolean serverRefused = new AtomicBoolean();

    /**
     * Runs every connection-state decision (a loss, scheduling the next attempt,
     * an attempt's result) and every call to {@link #publishConnectionEvent}. A
     * single thread, dedicated to this and never shared with a channel's own event
     * loop, so {@link io.github.aindriub.irc.client.event.ConnectionEvent}s reach
     * connection handlers strictly in the order they happened and one at a time,
     * never two at once and never out of order, regardless of which Netty event
     * loop happened to run the underlying I/O. A handler that blocks here delays
     * not just later events but the reconnect attempts themselves, since scheduling
     * the next attempt happens on this same thread.
     */
    private final EventExecutor connectionEventLoop = new DefaultEventExecutor();

    /** Refused writes from the event loop since the last WARN about them. */
    private final AtomicInteger refusedWritesSinceLastLog = new AtomicInteger();

    /** When the last "refused write" WARN was logged, 0 before the first one. */
    private final AtomicLong lastRefusedWriteLogAt = new AtomicLong();

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
        // A server truncates an over-long line rather than refusing it, so the part
        // that does not fit is lost while the part that does looks deliberate.
        // Failing here makes that visible where it can still be fixed.
        IRCText.requireFits(payload, configuration.getCharSet());
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
            // The promise is not awaited here, so a refusal (for example
            // OutboundRateLimiter's queue being full) would otherwise be silently
            // dropped; log it instead of throwing, since there is nobody left to
            // catch a throw from a fire-and-forget call.
            write.addListener(new GenericFutureListener<ChannelFuture>() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (!future.isSuccess()) {
                        logRefusedWrite(future.cause());
                    }
                }
            });
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

    /**
     * Logs a write refused by the pipeline (for example
     * {@code OutboundRateLimiter} rejecting a full queue) at WARN. A remote peer
     * can drive this by flooding, so it is throttled to the first refusal and then
     * at most one WARN per {@link #REFUSED_WRITE_LOG_INTERVAL_MILLIS}, reporting
     * how many were refused since the last report. Never logs the payload.
     */
    private void logRefusedWrite(Throwable cause) {
        refusedWritesSinceLastLog.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastRefusedWriteLogAt.get();
        if (last != 0 && now - last < REFUSED_WRITE_LOG_INTERVAL_MILLIS) {
            return;
        }
        if (!lastRefusedWriteLogAt.compareAndSet(last, now)) {
            // Another thread just logged; let it own this report.
            return;
        }
        int refused = refusedWritesSinceLastLog.getAndSet(0);
        LOGGER.warn("{} send(s) from the event loop were refused since the last report "
                + "(most recent cause: {})", refused, String.valueOf(cause));
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
                awaitConnectionEventLoopShutdown();
            }
        }
    }

    /**
     * Waits for the connection-event loop to drain and stop, so that once
     * disconnect() returns, no further {@link io.github.aindriub.irc.client.event.ConnectionEvent}
     * can arrive for this client. Skipped when called from that very loop (for
     * example a connection handler that calls {@code disconnect()} on itself),
     * since waiting there would deadlock forever.
     */
    private void awaitConnectionEventLoopShutdown() {
        Future<?> shutdownFuture = connectionEventLoop.shutdownGracefully(
                0, CONNECTION_EVENT_LOOP_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (connectionEventLoop.inEventLoop()) {
            return;
        }
        try {
            shutdownFuture.await(CONNECTION_EVENT_LOOP_SHUTDOWN_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Called when a connection goes away for any reason, on whichever Netty event
     * loop the dead channel happened to be using. Everything this triggers -
     * deciding whether the loss is fresh, scheduling the next attempt, handling
     * that attempt's result, and publishing every
     * {@link io.github.aindriub.irc.client.event.ConnectionEvent} - is handed off
     * to {@link #connectionEventLoop} so that it all happens on one thread, in the
     * order it actually occurred.
     */
    private void onConnectionLost() {
        connectionEventLoop.execute(new Runnable() {
            @Override
            public void run() {
                handleConnectionLost();
            }
        });
    }

    /**
     * Runs on {@link #connectionEventLoop}. Reconnects with backoff unless the
     * disconnection was deliberate or reconnection is switched off.
     */
    private void handleConnectionLost() {
        if (shutdown) {
            return;
        }
        // A fresh loss is one that did not happen in the middle of an ongoing
        // reconnect cycle: reconnectAttempts is zero only right after connect() or
        // a successful reconnect. A registration failure during a reconnect attempt
        // also closes the channel and re-enters here, but that must not be reported
        // as a second DISCONNECTED for the same outage.
        boolean freshLoss = reconnectAttempts.get() == 0;
        if (freshLoss) {
            publishConnectionEvent(ConnectionEvent.disconnected());
        }
        if (!configuration.getReconnect().isEnabled()) {
            LOGGER.info("Connection lost; reconnection is disabled");
            return;
        }
        if (freshLoss) {
            LOGGER.warn("Connection lost; reconnecting");
        }
        scheduleReconnect();
    }

    /**
     * Runs on {@link #connectionEventLoop}. Schedules the next attempt on
     * {@link #workerGroup} and, once scheduling has actually succeeded, publishes
     * RECONNECTING on this same thread before returning - so that by the time a
     * caller's {@code scheduleReconnect()} call for the following attempt could
     * possibly run (which can only happen after this method returns, since both
     * run on the same single thread), this attempt's event has already reached
     * every handler.
     */
    private void scheduleReconnect() {
        ReconnectConfiguration reconnect = configuration.getReconnect();
        final int attempt = reconnectAttempts.incrementAndGet();
        if (reconnect.getMaxAttempts() > 0 && attempt > reconnect.getMaxAttempts()) {
            LOGGER.error("Giving up after {} reconnect attempts", reconnect.getMaxAttempts());
            publishConnectionEvent(ConnectionEvent.gaveUp(attempt - 1));
            return;
        }

        // Read and cleared together, so it governs exactly the one attempt that
        // follows the refusal rather than every attempt after it.
        boolean refused = serverRefused.getAndSet(false);
        long delay = refused ? reconnect.getMaxDelay() : reconnect.delayFor(attempt);
        if (refused) {
            LOGGER.info("The server refused the last attempt, so waiting {}ms rather "
                    + "than {}ms", delay, reconnect.delayFor(attempt));
        }
        LOGGER.info("Reconnect attempt {} in {}ms", attempt, delay);
        try {
            workerGroup.schedule(new Runnable() {
                @Override
                public void run() {
                    // Back onto connectionEventLoop: this runs on a workerGroup
                    // event loop thread, and tryReconnect's own callbacks must join
                    // the same serial ordering as everything else.
                    connectionEventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            tryReconnect(attempt);
                        }
                    });
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The group is shutting down, which means we are on our way out anyway.
            LOGGER.debug("Not reconnecting, the event loop is shutting down");
            return;
        }
        if (shutdown) {
            // A disconnect() landed in the small window between the check above and
            // here; the task just scheduled will find shutdown set and no-op.
            return;
        }
        publishConnectionEvent(ConnectionEvent.reconnecting(attempt, delay));
    }

    /**
     * Runs on {@link #connectionEventLoop}. Starts the actual connect attempt;
     * its result is marshalled back onto this same loop rather than handled on
     * whichever Netty event loop the new channel ends up on.
     */
    private void tryReconnect(final int attempt) {
        if (shutdown) {
            return;
        }
        String host = configuration.getConnection().getHost();
        int port = configuration.getConnection().getPort();
        bootstrap.connect(host, port).addListener(new GenericFutureListener<ChannelFuture>() {
            @Override
            public void operationComplete(final ChannelFuture future) {
                connectionEventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        handleReconnectResult(future, attempt);
                    }
                });
            }
        });
    }

    /** Runs on {@link #connectionEventLoop}. */
    private void handleReconnectResult(ChannelFuture future, int attempt) {
        if (!future.isSuccess()) {
            LOGGER.warn("Reconnect attempt {} failed: {}", attempt,
                    String.valueOf(future.cause()));
            // The channel never went active, so nothing else will drive the next
            // attempt.
            scheduleReconnect();
            return;
        }
        channelFuture = future;
        awaitRegistrationThenResume(future, attempt);
    }

    /** Runs on {@link #connectionEventLoop}. */
    private void awaitRegistrationThenResume(final ChannelFuture future, final int attempt) {
        CompletableFuture<Void> handshake = registered;
        if (!configuration.getRegistration().isConfigured() || handshake == null) {
            reconnectSucceeded();
            return;
        }
        handshake.whenComplete(new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(final Void ignored, final Throwable error) {
                // Registration completes on whatever thread drove the handshake;
                // rejoin the serial loop before touching any reconnect state.
                connectionEventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        handleRegistrationResult(error, future, attempt);
                    }
                });
            }
        });
    }

    /** Runs on {@link #connectionEventLoop}. */
    private void handleRegistrationResult(Throwable error, ChannelFuture future, int attempt) {
        if (error == null) {
            reconnectSucceeded();
            return;
        }
        LOGGER.warn("Reconnected but registration failed on attempt {}: {}", attempt,
                String.valueOf(error));
        noteRefusal(error);
        // Closing fires channelInactive, which schedules the next attempt.
        // Scheduling one here as well would run two chains at once.
        future.channel().close();
    }

    /** Runs on {@link #connectionEventLoop}. */
    private void reconnectSucceeded() {
        reconnectAttempts.set(0);
        serverRefused.set(false);
        publishConnectionEvent(ConnectionEvent.reconnected());
        onReconnected();
    }

    /**
     * Publishes a connection-state change to every registered connection handler,
     * in registration order, one handler at a time. Always runs on
     * {@link #connectionEventLoop}, so handlers across different events also never
     * overlap and always see events in the order they happened. One misbehaving
     * handler must not stop the others, nor the reconnect logic that called this;
     * a slow one delays both the remaining handlers and the reconnect attempt that
     * follows, since scheduling the next attempt happens on this same thread.
     */
    private void publishConnectionEvent(ConnectionEvent connectionEvent) {
        Event<ConnectionEvent> event = new Event<>(connectionEvent);
        for (EventHandler<ConnectionEvent> handler : configuration.getConnectionHandlers()) {
            try {
                handler.publishEvent(event);
            } catch (RuntimeException e) {
                LOGGER.warn("Connection handler {} failed", handler.getClass().getName(), e);
            }
        }
    }

    /**
     * Records whether a failure was the server turning us away, so the next
     * reconnect waits properly instead of knocking again immediately.
     */
    private void noteRefusal(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ServerRefusedException) {
                serverRefused.set(true);
                return;
            }
        }
    }

    /**
     * Hook for subclasses to restore per-connection state, such as rejoining
     * channels. Runs after registration completes on a reconnected channel, on
     * {@link #connectionEventLoop} rather than the channel's own Netty event loop;
     * blocking here delays later connection events and the reconnect attempts
     * that follow them.
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
