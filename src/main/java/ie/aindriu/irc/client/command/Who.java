package ie.aindriu.irc.client.command;

public class Who extends Command {

    private static final String BASE_COMMAND = "WHO";
    
    protected Who(String nick) {
	setCommand(BASE_COMMAND + nick);
    }

    
}
