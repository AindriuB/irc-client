package io.github.aindriub.irc.client.configuration;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

import io.github.aindriub.irc.client.IRCText;
import io.github.aindriub.irc.client.event.ConnectionEvent;
import io.github.aindriub.irc.client.event.EventHandler;
import io.github.aindriub.irc.client.message.IRCMessage;

public class ClientConfigurationBuilder {

    private static final String SASL_CAPABILITY = "sasl";

    private final ClientConfiguration configuration;

    /**
     * Tracks whether any per-field connection setter has been used, so that a later
     * {@link #connection(ConnectionConfiguration)} cannot silently discard it.
     */
    private boolean connectionFieldsSet;

    public ClientConfigurationBuilder() {
        configuration = new ClientConfiguration();
        connectionFieldsSet = false;
    }

    /**
     * Supplies the connection settings wholesale. Mutually exclusive with the
     * individual connection setters below, which would otherwise be silently
     * overwritten depending on call order.
     */
    public ClientConfigurationBuilder connection(ConnectionConfiguration connection) {
        if (connectionFieldsSet) {
            throw new IllegalStateException(
                    "connection(..) replaces all connection settings and cannot be combined with "
                            + "host/port/secure/keepAlive/connectTimeout/trustAllCertificates");
        }
        configuration.setConnection(Objects.requireNonNull(connection, "connection"));
        return this;
    }

    public ClientConfigurationBuilder secure(boolean secure) {
        connectionFieldsSet = true;
        configuration.getConnection().setSecure(secure);
        return this;
    }

    public ClientConfigurationBuilder host(String host) {
        connectionFieldsSet = true;
        configuration.getConnection().setHost(host);
        return this;
    }

    public ClientConfigurationBuilder port(int port) {
        connectionFieldsSet = true;
        configuration.getConnection().setPort(port);
        return this;
    }

    public ClientConfigurationBuilder keepAlive(boolean keepAlive) {
        connectionFieldsSet = true;
        configuration.getConnection().setKeepAlive(keepAlive);
        return this;
    }

    public ClientConfigurationBuilder connectTimeout(int connectTimeoutMillis) {
        connectionFieldsSet = true;
        configuration.getConnection().setConnectTimeout(connectTimeoutMillis);
        return this;
    }

    /**
     * @see ConnectionConfiguration#isTrustAllCertificates()
     */
    public ClientConfigurationBuilder trustAllCertificates(boolean trustAllCertificates) {
        connectionFieldsSet = true;
        configuration.getConnection().setTrustAllCertificates(trustAllCertificates);
        return this;
    }

    /**
     * Sets the nick and so enables the registration handshake on connect. Username
     * and realname default to the nick.
     */
    public ClientConfigurationBuilder nick(String nick) {
        configuration.getRegistration().setNick(nick);
        return this;
    }

    public ClientConfigurationBuilder username(String username) {
        configuration.getRegistration().setUsername(username);
        return this;
    }

    public ClientConfigurationBuilder realname(String realname) {
        configuration.getRegistration().setRealname(realname);
        return this;
    }

    /**
     * Server password, or a Twitch {@code oauth:...} token.
     *
     * <p>{@link RegistrationConfiguration#setPassword(String)} is deliberately
     * permissive; this builder is where the value is checked.
     *
     * @throws IllegalArgumentException when {@code password} is not null and
     *                                  contains whitespace, starts with ':', or
     *                                  contains CR, LF or NUL. The value itself is
     *                                  never included in the message.
     */
    public ClientConfigurationBuilder password(String password) {
        if (password != null) {
            IRCText.requireParam(password, "password");
        }
        configuration.getRegistration().setPassword(password);
        return this;
    }

    /**
     * Requests an IRCv3 capability. Only capabilities the server actually offers are
     * requested, so naming one it does not support is harmless.
     */
    public ClientConfigurationBuilder capability(String capability) {
        configuration.getRegistration().getCapabilities()
                .add(Objects.requireNonNull(capability, "capability"));
        return this;
    }

    /**
     * Authenticates with SASL PLAIN, which most modern networks prefer to a
     * NickServ message. Requires TLS in practice, since PLAIN sends the password
     * base64 encoded rather than hashed. Adds the sasl capability for you.
     *
     * <p>{@link RegistrationConfiguration#setSaslPassword(String)} is deliberately
     * permissive; this builder is where the value is checked.
     *
     * @throws NullPointerException     when {@code password} is null
     * @throws IllegalArgumentException when {@code password} contains CR, LF or
     *                                  NUL, or {@code username} is not null and
     *                                  contains whitespace, starts with ':', or
     *                                  contains CR, LF or NUL. Neither value is
     *                                  ever included in the message.
     */
    public ClientConfigurationBuilder sasl(String username, String password) {
        Objects.requireNonNull(password, "sasl password");
        IRCText.requireText(password, "sasl password");
        if (username != null) {
            IRCText.requireParam(username, "sasl username");
        }
        RegistrationConfiguration registration = configuration.getRegistration();
        registration.setSaslUsername(username);
        registration.setSaslPassword(password);
        if (!registration.getCapabilities().contains(SASL_CAPABILITY)) {
            registration.getCapabilities().add(SASL_CAPABILITY);
        }
        return this;
    }

    /**
     * How long connect() waits for the server to accept registration.
     */
    public ClientConfigurationBuilder registrationTimeout(int registrationTimeoutMillis) {
        configuration.getRegistration().setRegistrationTimeout(registrationTimeoutMillis);
        return this;
    }

    /**
     * Reconnect automatically after an unexpected disconnection. On by default; a
     * deliberate disconnect() never reconnects either way.
     */
    public ClientConfigurationBuilder reconnect(boolean enabled) {
        configuration.getReconnect().setEnabled(enabled);
        return this;
    }

    /**
     * Backoff between reconnect attempts, and how many to make. Zero attempts means
     * keep trying indefinitely.
     */
    public ClientConfigurationBuilder reconnectBackoff(long initialDelayMillis,
            long maxDelayMillis, int maxAttempts) {
        ReconnectConfiguration reconnect = configuration.getReconnect();
        reconnect.setInitialDelay(initialDelayMillis);
        reconnect.setMaxDelay(maxDelayMillis);
        reconnect.setMaxAttempts(maxAttempts);
        return this;
    }

    /**
     * Rate limit outbound messages so the server does not disconnect the client for
     * flooding. On by default.
     */
    public ClientConfigurationBuilder floodProtection(boolean enabled) {
        configuration.getFlood().setEnabled(enabled);
        return this;
    }

    /**
     * @param burst           messages allowed back to back
     * @param intervalMillis  gap between releases once the burst is spent
     */
    public ClientConfigurationBuilder floodProtection(int burst, long intervalMillis) {
        FloodConfiguration flood = configuration.getFlood();
        flood.setEnabled(true);
        flood.setBurst(burst);
        flood.setInterval(intervalMillis);
        return this;
    }

    /**
     * How many messages may wait behind flood protection before a send is refused
     * with an OutboundQueueFullException. Zero removes the limit, which trades an
     * error a caller can handle for memory growth it cannot see.
     */
    public ClientConfigurationBuilder outboundQueueDepth(int maxQueueDepth) {
        configuration.getFlood().setMaxQueueDepth(maxQueueDepth);
        return this;
    }

    /**
     * How long the connection may go silent before the client checks it is alive and
     * then closes it. Zero switches the check off.
     */
    public ClientConfigurationBuilder readTimeout(long readTimeoutMillis) {
        connectionFieldsSet = true;
        configuration.getConnection().setReadTimeout(readTimeoutMillis);
        return this;
    }

    /**
     * Logs the raw wire traffic via Netty's {@code LoggingHandler}. Note that this
     * includes the server password and any SASL credentials: the redaction that
     * {@link io.github.aindriub.irc.client.command.Pass#toString()} and
     * {@link io.github.aindriub.irc.client.command.Authenticate#toString()} apply is
     * only for the command's own log form, and this logs the actual bytes sent on the
     * wire instead. Do not enable this where logs are kept, such as production.
     */
    public ClientConfigurationBuilder debug(boolean debug) {
        configuration.setDebug(debug);
        return this;
    }

    public ClientConfigurationBuilder eventListener(EventHandler<String> eventHandler) {
        configuration.getEventHandlers().add(Objects.requireNonNull(eventHandler, "eventHandler"));
        return this;
    }

    /**
     * Subscribes to parsed messages. Usually a
     * {@link io.github.aindriub.irc.client.event.MessageListener}, which dispatches
     * to a callback per message type.
     */
    public ClientConfigurationBuilder messageListener(EventHandler<IRCMessage> messageHandler) {
        configuration.getMessageHandlers()
                .add(Objects.requireNonNull(messageHandler, "messageHandler"));
        return this;
    }

    /**
     * Subscribes to connection-state changes: a lost connection, a reconnect
     * attempt, a successful reconnect, or giving up. See {@link ConnectionEvent}
     * for the delivery thread and ordering guarantee, and note in particular that
     * a blocking handler delays the reconnect attempts themselves.
     */
    public ClientConfigurationBuilder connectionListener(EventHandler<ConnectionEvent> connectionHandler) {
        configuration.getConnectionHandlers()
                .add(Objects.requireNonNull(connectionHandler, "connectionHandler"));
        return this;
    }

    public ClientConfigurationBuilder outputStream(OutputStream outputStream) {
        configuration.getOutputStreams().add(Objects.requireNonNull(outputStream, "outputStream"));
        return this;
    }

    public ClientConfigurationBuilder charset(Charset charset) {
        configuration.setCharSet(Objects.requireNonNull(charset, "charset"));
        return this;
    }

    public ClientConfiguration build() {
        ConnectionConfiguration connection = configuration.getConnection();
        if (connection.getHost() == null || connection.getHost().trim().isEmpty()) {
            throw new IllegalStateException("host is required");
        }
        if (connection.getPort() <= 0 || connection.getPort() > 65535) {
            throw new IllegalStateException("port must be between 1 and 65535");
        }
        return configuration;
    }
}
