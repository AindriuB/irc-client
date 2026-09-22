package io.github.aindriub.irc.client.configuration;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import io.github.aindriub.irc.client.event.EventHandler;

public class ClientConfiguration {

    private Charset charSet;
    private List<EventHandler<String>> eventHandlers;
    private List<OutputStream> outputStreams;
    private ConnectionConfiguration connection;
    private RegistrationConfiguration registration;

    private boolean debug;

    public ClientConfiguration() {
        eventHandlers = new ArrayList<>();
        outputStreams = new ArrayList<>();
        connection = new ConnectionConfiguration();
        registration = new RegistrationConfiguration();
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

    public RegistrationConfiguration getRegistration() {
        return registration;
    }

    public void setRegistration(RegistrationConfiguration registration) {
        this.registration = registration;
    }

    public List<EventHandler<String>> getEventHandlers() {
        return eventHandlers;
    }

    public void setEventHandlers(List<EventHandler<String>> eventHandlers) {
        this.eventHandlers = eventHandlers;
    }
}
