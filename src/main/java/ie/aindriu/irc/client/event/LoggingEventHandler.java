package ie.aindriu.irc.client.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEventHandler implements EventHandler<String> {

    private static Logger LOGGER = LoggerFactory.getLogger(LoggingEventHandler.class);
    @Override
    public void publishEvent(Event<String> event) {
	LOGGER.info("Message recieved: > {}", event.getPayload());
    }

}
