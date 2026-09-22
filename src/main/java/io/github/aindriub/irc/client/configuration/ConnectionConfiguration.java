package io.github.aindriub.irc.client.configuration;

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

    /**
     * How long the connection may go without a single inbound byte before the client
     * pings to check it is alive, and then closes it. Zero switches the check off.
     */
    private long readTimeout;

    public ConnectionConfiguration() {
        secure = true;
        keepAlive = true;
        connectTimeout = 5000;
        trustAllCertificates = false;
        readTimeout = 180000;
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

    public long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }
}
