package ie.aindriu.irc.client.command;

import java.util.List;

public class Part extends Command {

    private static final String BASE_COMMAND = "PART";

    public Part(String channel) {
        super(BASE_COMMAND + SPACE + channel);
    }

    public Part(List<String> channels) {
        super(BASE_COMMAND + SPACE + Channels.join(channels));
    }
}
