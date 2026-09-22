package ie.aindriu.irc.client.configuration;

public class ConnectionConfiguration {

    private String host;

    private int port;

    private boolean secure;

    private boolean keepAlive;

    private int connectTimeout;

    /**
     * Accept any server certificate. Off by default: turning it on leaves the
     * connection encrypted but unauthenticated, and so open to interception. Only
     * useful against a local or self-signed test server.
     */
    private boolean trustAllCertificates;

    public ConnectionConfiguration() {
        secure = true;
        keepAlive = true;
        connectTimeout = 5000;
        trustAllCertificates = false;
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

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }
}
