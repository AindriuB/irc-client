package io.github.aindriub.irc.client.configuration;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

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
     */
    public ClientConfigurationBuilder password(String password) {
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
     */
    public ClientConfigurationBuilder sasl(String username, String password) {
        RegistrationConfiguration registration = configuration.getRegistration();
        registration.setSaslUsername(username);
        registration.setSaslPassword(Objects.requireNonNull(password, "sasl password"));
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
     * How long the connection may go silent before the client checks it is alive and
     * then closes it. Zero switches the check off.
     */
    public ClientConfigurationBuilder readTimeout(long readTimeoutMillis) {
        connectionFieldsSet = true;
        configuration.getConnection().setReadTimeout(readTimeoutMillis);
        return this;
    }

    /**
     * Logs the raw wire traffic. Note that this includes the server password and any
     * SASL credentials, which the commands themselves take care to redact.
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
