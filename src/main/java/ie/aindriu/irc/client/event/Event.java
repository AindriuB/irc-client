package ie.aindriu.irc.client.event;

import java.util.Date;
import java.util.Objects;

/**
 * An immutable inbound event. Handlers used to be able to mutate an event after it
 * had been published to earlier subscribers.
 */
public class Event<T> {

    private final Date timestamp;
    private final T payload;

    public Event(T payload) {
        this(payload, new Date());
    }

    public Event(T payload, Date timestamp) {
        this.payload = Objects.requireNonNull(payload, "payload");
        this.timestamp = new Date(Objects.requireNonNull(timestamp, "timestamp").getTime());
    }

    public Date getTimestamp() {
        return new Date(timestamp.getTime());
    }

    public T getPayload() {
        return payload;
    }
}
