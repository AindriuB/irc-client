package io.github.aindriub.irc.client.configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * The registration handshake: PASS, NICK and USER, plus any IRCv3 capabilities to
 * negotiate first. Registration runs automatically on connect when a nick is set.
 */
public class RegistrationConfiguration {

    private String nick;

    /**
     * Defaults to the nick when left unset.
     */
    private String username;

    /**
     * Defaults to the nick when left unset.
     */
    private String realname;

    /**
     * Server password, or a Twitch {@code oauth:...} token. Null when the server
     * needs none.
     */
    private String password;

    private final List<String> capabilities;

    /**
     * SASL account name. Defaults to the nick when a SASL password is set without
     * one.
     */
    private String saslUsername;

    /**
     * SASL account password. Setting it turns SASL on, which also requires the sasl
     * capability from the server.
     */
    private String saslPassword;

    /**
     * How many times to retry with a modified nick after ERR_NICKNAMEINUSE before
     * giving up.
     */
    private int maxNickAttempts;

    /**
     * How long connect() waits for RPL_WELCOME before failing.
     */
    private int registrationTimeout;

    public RegistrationConfiguration() {
        capabilities = new ArrayList<>();
        maxNickAttempts = 3;
        registrationTimeout = 15000;
    }

    /**
     * True when SASL credentials have been supplied.
     */
    public boolean isSaslConfigured() {
        return saslPassword != null;
    }

    public String getSaslUsername() {
        return saslUsername == null ? nick : saslUsername;
    }

    public void setSaslUsername(String saslUsername) {
        this.saslUsername = saslUsername;
    }

    public String getSaslPassword() {
        return saslPassword;
    }

    public void setSaslPassword(String saslPassword) {
        this.saslPassword = saslPassword;
    }

    public boolean isConfigured() {
        return nick != null && !nick.trim().isEmpty();
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getUsername() {
        return username == null ? nick : username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealname() {
        return realname == null ? nick : realname;
    }

    public void setRealname(String realname) {
        this.realname = realname;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public int getMaxNickAttempts() {
        return maxNickAttempts;
    }

    public void setMaxNickAttempts(int maxNickAttempts) {
        this.maxNickAttempts = maxNickAttempts;
    }

    public int getRegistrationTimeout() {
        return registrationTimeout;
    }

    public void setRegistrationTimeout(int registrationTimeout) {
        this.registrationTimeout = registrationTimeout;
    }
}
