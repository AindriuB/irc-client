package ie.aindriu.irc.client.configuration;

public class ConnectionConfiguration {
    
    private String host;
    
    private int port;
    
    private boolean secure;
    
    private boolean keepAlive;
    
    private int connectionTimeout;
    
    private int connectTimeout;
    
    public ConnectionConfiguration() {
	secure = true;
	keepAlive = true;
	connectionTimeout  = 5000;	
	connectTimeout = 5000;
    }
    
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    


    
    
}
