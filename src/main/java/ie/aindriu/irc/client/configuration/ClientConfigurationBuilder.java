package ie.aindriu.irc.client.configuration;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

import ie.aindriu.irc.client.event.EventHandler;

public class ClientConfigurationBuilder {

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

    public ClientConfigurationBuilder debug(boolean debug) {
        configuration.setDebug(debug);
        return this;
    }

    public ClientConfigurationBuilder eventListener(EventHandler<String> eventHandler) {
        configuration.getEventHandlers().add(Objects.requireNonNull(eventHandler, "eventHandler"));
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
