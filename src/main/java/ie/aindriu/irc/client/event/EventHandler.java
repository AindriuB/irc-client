package ie.aindriu.irc.client.event;

public interface EventHandler<T> {

    void publishEvent(Event<T> event);
    
}
