package io.github.aindriub.irc.client.configuration;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import io.github.aindriub.irc.client.event.ConnectionEvent;
import io.github.aindriub.irc.client.event.EventHandler;
import io.github.aindriub.irc.client.message.IRCMessage;

public class ClientConfiguration {

    private Charset charSet;
    private List<EventHandler<String>> eventHandlers;
    private List<EventHandler<IRCMessage>> messageHandlers;
    private List<EventHandler<ConnectionEvent>> connectionHandlers;
    private List<OutputStream> outputStreams;
    private ConnectionConfiguration connection;
    private RegistrationConfiguration registration;
    private ReconnectConfiguration reconnect;
    private FloodConfiguration flood;

    private boolean debug;

    public ClientConfiguration() {
        eventHandlers = new ArrayList<>();
        messageHandlers = new ArrayList<>();
        connectionHandlers = new ArrayList<>();
        outputStreams = new ArrayList<>();
        connection = new ConnectionConfiguration();
        registration = new RegistrationConfiguration();
        reconnect = new ReconnectConfiguration();
        flood = new FloodConfiguration();
        debug = false;
        charSet = Charset.defaultCharset();
    }

    public Charset getCharSet() {
        return charSet;
    }

    public void setCharSet(Charset charSet) {
        this.charSet = charSet;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public List<OutputStream> getOutputStreams() {
        return outputStreams;
    }

    public void setOutputStreams(List<OutputStream> outputStreams) {
        this.outputStreams = outputStreams;
    }

    public ConnectionConfiguration getConnection() {
        return connection;
    }

    public void setConnection(ConnectionConfiguration connection) {
        this.connection = connection;
    }

    /**
     * Subscribers that receive parsed messages. Raw line subscribers live in
     * {@link #getEventHandlers()}; both can be used at once.
     */
    public List<EventHandler<IRCMessage>> getMessageHandlers() {
        return messageHandlers;
    }

    public void setMessageHandlers(List<EventHandler<IRCMessage>> messageHandlers) {
        this.messageHandlers = messageHandlers;
    }

    public RegistrationConfiguration getRegistration() {
        return registration;
    }

    public void setRegistration(RegistrationConfiguration registration) {
        this.registration = registration;
    }

    public ReconnectConfiguration getReconnect() {
        return reconnect;
    }

    public void setReconnect(ReconnectConfiguration reconnect) {
        this.reconnect = reconnect;
    }

    public FloodConfiguration getFlood() {
        return flood;
    }

    public void setFlood(FloodConfiguration flood) {
        this.flood = flood;
    }

    public List<EventHandler<String>> getEventHandlers() {
        return eventHandlers;
    }

    public void setEventHandlers(List<EventHandler<String>> eventHandlers) {
        this.eventHandlers = eventHandlers;
    }

    /**
     * Subscribers notified of connection-state changes: a lost connection, a
     * reconnect attempt, a successful reconnect, or giving up. A deliberate
     * {@code disconnect()}/shutdown publishes nothing.
     */
    public List<EventHandler<ConnectionEvent>> getConnectionHandlers() {
        return connectionHandlers;
    }

    public void setConnectionHandlers(List<EventHandler<ConnectionEvent>> connectionHandlers) {
        this.connectionHandlers = connectionHandlers;
    }
}
