package ie.aindriu.irc.client.impl;

import org.junit.Before;
import org.junit.Test;

import ie.aindriu.irc.client.configuration.ClientConfigurationBuilder;
import ie.aindriu.irc.client.event.LoggingEventHandler;

public class BasicClientTest {

    
    private BasicIRCClient client;
    
    @Before
    public void setUp() {
	ClientConfigurationBuilder cb = new ClientConfigurationBuilder();
	cb.host("irc.chat.twitch.tv")
	.port(6697)
	.secure(true)
	.keepAlive(true);
	
	cb.eventListener(new LoggingEventHandler());
	cb.outputStream(System.out);
	client = new BasicIRCClient(cb.build());
    }
    
    @Test
    public void testConnect() throws InterruptedException {
	client.connect();
	client.send("oauth:test");
	client.disconnect();
    }

}
