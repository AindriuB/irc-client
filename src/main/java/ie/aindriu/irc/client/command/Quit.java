package ie.aindriu.irc.client.command;

public class Quit extends Command {

    private static final String BASE_COMMAND = "QUIT";
    
    public Quit() {
	setCommand(BASE_COMMAND);
    }
}
