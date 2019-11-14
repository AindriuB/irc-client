package ie.aindriu.irc.client;

import java.io.OutputStream;
import java.nio.charset.Charset;

import ie.aindriu.irc.client.event.EventHandler;

public class ClientConfigurationBuilder {

    
    private ClientConfiguration configuration;
    
    public ClientConfigurationBuilder() {
	configuration = new ClientConfiguration();
    }
    
    public ClientConfigurationBuilder connection(Connection connection) {
	configuration.setConnection(connection);
	return this;
    }

    
    public ClientConfigurationBuilder secure(boolean secure) {
	configuration.getConnection().setSecure(secure);
	return this;
    }
    public ClientConfigurationBuilder host(String host) {
	configuration.getConnection().setHost(host);
	return this;
    }

    public ClientConfigurationBuilder port(int port) {
	configuration.getConnection().setPort(port);
	return this;
    }
    
    public ClientConfigurationBuilder keepAlive(boolean keepAlive) {
	configuration.getConnection().setKeepAlive(keepAlive);
	return this;
    }
    
    public ClientConfigurationBuilder eventListener(EventHandler<String> eventHandler) {
	configuration.getEventHandlers().add(eventHandler);
	return this;
    }
    
    public ClientConfigurationBuilder outputStream(OutputStream outputStream) {
	configuration.getOutputStreams().add(outputStream);
	return this;
    }
    
    public ClientConfigurationBuilder charset(Charset charset) {
	configuration.setCharSet(charset);
	return this;
    }
    
    public ClientConfiguration build() {
	return configuration;
    }
}
