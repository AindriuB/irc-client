package ie.aindriu.irc.client.command;

public class Nick extends Command {

    private static final String BASE_COMMAND = "NICK";
    
    protected Nick(String nick) {
	setCommand(BASE_COMMAND + SPACE +  nick);
    }

    
}
