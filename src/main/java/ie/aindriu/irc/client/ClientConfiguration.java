package ie.aindriu.irc.client;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import ie.aindriu.irc.client.event.EventHandler;

public class ClientConfiguration {

    private Charset charSet;
    private List<EventHandler<String>> eventHandlers;
    private List<OutputStream> outputStreams;
    private Connection connection;
    
    private boolean debug;

    public ClientConfiguration() {
	eventHandlers = new ArrayList<EventHandler<String>>();
	outputStreams = new ArrayList<OutputStream>();
	connection = new Connection();
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
