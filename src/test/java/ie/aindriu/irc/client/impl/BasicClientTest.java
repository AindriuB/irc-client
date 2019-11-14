package ie.aindriu.irc.client.impl;

import org.junit.Before;
import org.junit.Test;

import ie.aindriu.irc.client.ClientConfigurationBuilder;
import ie.aindriu.irc.client.event.LoggingEventHandler;

public class BasicClientTest {

    
    private BasicClient client;
    
    @Before
    public void setUp() {
	ClientConfigurationBuilder cb = new ClientConfigurationBuilder();
	cb.host("irc.chat.twitch.tv")
	.port(6697)
	.secure(true)
	.keepAlive(true);
	
	cb.eventListener(new LoggingEventHandler());
	cb.outputStream(System.out);
	client = new BasicClient(cb.build());
    }
    
    @Test
    public void testConnect() throws InterruptedException {
	client.connect();
	client.send("oauth:test".getBytes());
	client.disconnect();
    }

}
