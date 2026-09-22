package io.github.aindriub.irc.client.message;

/**
 * The numeric replies this library reacts to. There are hundreds; these are the ones
 * that drive registration.
 */
public final class Numerics {

    private Numerics() {
    }

    /** Registration succeeded. */
    public static final String RPL_WELCOME = "001";

    /** The chosen nick is taken. */
    public static final String ERR_NICKNAMEINUSE = "433";

    /** The chosen nick is not a legal nick on this server. */
    public static final String ERR_ERRONEUSNICKNAME = "432";

    /** The nick collided with another connection. */
    public static final String ERR_NICKCOLLISION = "436";

    /** The supplied password was wrong. */
    public static final String ERR_PASSWDMISMATCH = "464";
}
