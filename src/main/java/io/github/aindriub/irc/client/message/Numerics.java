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

    /** One line of the list of nicks in a channel. */
    public static final String RPL_NAMREPLY = "353";

    /** The nick list for a channel is complete. */
    public static final String RPL_ENDOFNAMES = "366";

    /** The channel has no topic set. */
    public static final String RPL_NOTOPIC = "331";

    /** The channel's topic. */
    public static final String RPL_TOPIC = "332";

    /** One line of a WHO reply. */
    public static final String RPL_WHOREPLY = "352";

    /** The WHO reply is complete. */
    public static final String RPL_ENDOFWHO = "315";

    /** The modes currently set on a channel. */
    public static final String RPL_CHANNELMODEIS = "324";
}
