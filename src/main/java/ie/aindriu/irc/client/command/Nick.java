package ie.aindriu.irc.client.command;

public class Nick extends Command {

    private static final String BASE_COMMAND = "PART";
    
    public Nick(String channel) {
	setCommand(BASE_COMMAND + SPACE +  channel);
    }

    
}
