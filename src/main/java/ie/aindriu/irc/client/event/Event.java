package ie.aindriu.irc.client.event;

import java.util.Date;

public class Event<T> {

    private Date timestamp;
    private T payload;

    public Date getTimestamp() {
	return timestamp;
    }

    public void setTimestamp(Date timestamp) {
	this.timestamp = timestamp;
    }

    public T getPayload() {
	return payload;
    }

    public void setPayload(T payload) {
	this.payload = payload;
    }

}
