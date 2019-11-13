package ie.aindriu.irc.client;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import ie.aindriu.irc.client.event.EventHandler;

public class ClientConfiguration {

    private List<EventHandler<String>> eventHandlers;
    private List<OutputStream> outputStreams;
    private Connection connection;
    
    private boolean debug;

    public ClientConfiguration() {
	eventHandlers = new ArrayList<EventHandler<String>>();
	outputStreams = new ArrayList<OutputStream>();
	connection = new Connection();
	debug = false;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setOutputStreams(List<OutputStream> outputStreams) {
        this.outputStreams = outputStreams;
    }

    public List<OutputStream> getOutputStreams() {
	return outputStreams;
    }

    public void setOutputStream(List<OutputStream> outputStreams) {
	this.outputStreams = outputStreams;
    }

    public Connection getConnection() {
	return connection;
    }

    public void setConnection(Connection connection) {
	this.connection = connection;
    }

    public List<EventHandler<String>> getEventHandlers() {
        return eventHandlers;
    }

    public void setEventHandlers(List<EventHandler<String>> eventHandlers) {
        this.eventHandlers = eventHandlers;
    }

}
