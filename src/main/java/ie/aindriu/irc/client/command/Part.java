package ie.aindriu.irc.client.command;

import java.util.List;

public class Part extends Command {

    private static final String BASE_COMMAND = "JOIN";
    
    protected Part(String channel) {
	setCommand(BASE_COMMAND + channel);
    }

    public Part(List<String> channels) {
	StringBuilder channelListBuilder = new StringBuilder();
	channels.stream().forEach(c -> channelListBuilder.append(c + COMMA));
	String channelList = channelListBuilder.substring(0, channelListBuilder.length() -1 );
	setCommand(BASE_COMMAND + SPACE + channelList);
    }
}
