package ie.aindriu.irc.client.command;

public abstract class Command {

    protected static final String SPACE = " ";
    protected static final String COMMA = ",";
    protected String command;
    
    
    protected void setCommand(String command) {
        this.command = command;
    }


    @Override
    public String toString() {
	return command;
    }
    
    
    
}
