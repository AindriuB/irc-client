package io.github.aindriub.irc.client.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.IRCClientException;
import io.github.aindriub.irc.client.command.Authenticate;
import io.github.aindriub.irc.client.command.Cap;
import io.github.aindriub.irc.client.command.Command;
import io.github.aindriub.irc.client.command.Nick;
import io.github.aindriub.irc.client.command.Pass;
import io.github.aindriub.irc.client.command.User;
import io.github.aindriub.irc.client.configuration.RegistrationConfiguration;
import io.github.aindriub.irc.client.message.IRCMessage;
import io.github.aindriub.irc.client.message.IRCMessageParser;
import io.github.aindriub.irc.client.message.IRCParseException;
import io.github.aindriub.irc.client.message.Numerics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Drives the registration handshake and then removes itself from the pipeline.
 *
 * <p>On connect it sends CAP LS (when capabilities are wanted), PASS, NICK and USER,
 * then watches for the server's replies: capability negotiation is completed with
 * CAP REQ and CAP END, a taken nick is retried with a modified one, and RPL_WELCOME
 * completes the handshake. Messages are always passed on, so subscribers see the
 * whole conversation.
 */
public class RegistrationHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationHandler.class);

    private static final String CAP = "CAP";
    private static final String AUTHENTICATE = "AUTHENTICATE";
    private static final String SASL = "sasl";
    private static final String PLAIN = "PLAIN";

    /** The server's cue that it is ready for our credentials. */
    private static final String CONTINUE = "+";

    private final RegistrationConfiguration configuration;
    private final CompletableFuture<Void> registered;

    private String currentNick;
    private int nickAttempts;
    private boolean capabilitiesNegotiated;
    private boolean saslInProgress;

    public RegistrationHandler(RegistrationConfiguration configuration,
            CompletableFuture<Void> registered) {
        this.configuration = configuration;
        this.registered = registered;
        this.currentNick = configuration.getNick();
        this.nickAttempts = 0;
        this.capabilitiesNegotiated = false;
        this.saslInProgress = false;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // CAP LS goes first: the server holds registration open until CAP END, which
        // gives the negotiation somewhere to happen.
        if (!configuration.getCapabilities().isEmpty()) {
            write(ctx, Cap.ls());
        }
        if (configuration.getPassword() != null) {
            // Before NICK and USER, per RFC 2812 section 3.1.1.
            write(ctx, new Pass(configuration.getPassword()));
        }
        write(ctx, new Nick(currentNick));
        write(ctx, new User(configuration.getUsername(), configuration.getRealname()));
        ctx.flush();
        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String line) throws Exception {
        try {
            handle(ctx, IRCMessageParser.parse(line));
        } catch (IRCParseException e) {
            // A malformed line must not take the connection down.
            LOGGER.warn("Skipping unparseable line during registration: {}", line, e);
        }
        ctx.fireChannelRead(line);
    }

    private void handle(ChannelHandlerContext ctx, IRCMessage message) {
        String command = message.getCommand();
        if (CAP.equals(command)) {
            handleCapability(ctx, message);
            return;
        }
        if (AUTHENTICATE.equals(command)) {
            handleAuthenticate(ctx, message);
            return;
        }
        if (Numerics.RPL_SASLSUCCESS.equals(command) || Numerics.ERR_SASLALREADY.equals(command)) {
            LOGGER.info("Authenticated with SASL as {}", configuration.getSaslUsername());
            saslInProgress = false;
            endCapabilityNegotiation(ctx);
            return;
        }
        if (Numerics.ERR_SASLFAIL.equals(command) || Numerics.ERR_SASLTOOLONG.equals(command)
                || Numerics.ERR_SASLABORTED.equals(command)
                || Numerics.ERR_NICKLOCKED.equals(command)) {
            // Configured credentials that do not work is a failure, not something to
            // carry on unauthenticated from.
            saslInProgress = false;
            fail(ctx, "SASL authentication failed: " + message);
            return;
        }
        if (Numerics.RPL_WELCOME.equals(command)) {
            complete(ctx);
            return;
        }
        if (Numerics.ERR_NICKNAMEINUSE.equals(command)
                || Numerics.ERR_NICKCOLLISION.equals(command)) {
            retryWithAnotherNick(ctx, message);
            return;
        }
        if (Numerics.ERR_PASSWDMISMATCH.equals(command)
                || Numerics.ERR_ERRONEUSNICKNAME.equals(command)) {
            fail(ctx, "Registration rejected by the server: " + message);
        }
    }

    /**
     * {@code CAP * LS :cap1 cap2} then {@code CAP * ACK :cap1}. Only the ones the
     * server actually offers are requested, so a NAK for an unknown capability
     * cannot stall the handshake.
     */
    private void handleCapability(ChannelHandlerContext ctx, IRCMessage message) {
        String subcommand = message.getParam(1);
        if (subcommand == null) {
            return;
        }
        subcommand = subcommand.toUpperCase(Locale.ROOT);
        if ("LS".equals(subcommand)) {
            // getParam(2), not getTrailing(): for a bare "CAP * LS" with no list at
            // all, getTrailing() would return the subcommand itself.
            List<String> wanted = offeredAndWanted(message.getParam(2));
            if (wanted.isEmpty()) {
                LOGGER.debug("Server offers none of the requested capabilities");
                endCapabilityNegotiation(ctx);
            } else {
                write(ctx, Cap.req(wanted));
                ctx.flush();
            }
        } else if ("ACK".equals(subcommand)) {
            if (configuration.isSaslConfigured() && acknowledges(message, SASL)) {
                beginSasl(ctx);
                // CAP END deliberately not sent: the server keeps registration open
                // until it is, which is the only window SASL has to run in.
                return;
            }
            endCapabilityNegotiation(ctx);
        } else if ("NAK".equals(subcommand)) {
            LOGGER.warn("Server refused capabilities: {}", message.getParam(2));
            if (configuration.isSaslConfigured()) {
                // Carrying on would leave us connected but unauthenticated, which is
                // not what asking for SASL meant.
                fail(ctx, "SASL was configured but the server refused the sasl capability");
                return;
            }
            endCapabilityNegotiation(ctx);
        }
    }

    private List<String> offeredAndWanted(String offered) {
        Set<String> available = new LinkedHashSet<>(
                offered == null ? java.util.Collections.<String>emptyList()
                        : Arrays.asList(offered.trim().split("\\s+")));
        List<String> wanted = new ArrayList<>();
        for (String capability : configuration.getCapabilities()) {
            // A server advertising "cap=value" still answers to the bare name.
            if (available.contains(capability) || available.contains(capability + "=")
                    || startsWithName(available, capability)) {
                wanted.add(capability);
            }
        }
        return wanted;
    }

    private static boolean startsWithName(Set<String> available, String capability) {
        String prefix = capability + "=";
        for (String candidate : available) {
            if (candidate.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean acknowledges(IRCMessage message, String capability) {
        String acknowledged = message.getParam(2);
        if (acknowledged == null) {
            return false;
        }
        for (String name : acknowledged.trim().split("\\s+")) {
            // A server may acknowledge with a modifier prefix such as '-' or '='.
            if (capability.equalsIgnoreCase(name) || name.endsWith(capability)) {
                return true;
            }
        }
        return false;
    }

    private void beginSasl(ChannelHandlerContext ctx) {
        saslInProgress = true;
        LOGGER.debug("Starting SASL {} as {}", PLAIN, configuration.getSaslUsername());
        write(ctx, Authenticate.mechanism(PLAIN));
        ctx.flush();
    }

    /**
     * The server answers our mechanism with a bare "+", meaning send the credentials.
     */
    private void handleAuthenticate(ChannelHandlerContext ctx, IRCMessage message) {
        if (!saslInProgress) {
            return;
        }
        if (!CONTINUE.equals(message.getParam(0))) {
            LOGGER.debug("Ignoring unexpected AUTHENTICATE: {}", message);
            return;
        }
        for (Authenticate part : Authenticate.plain(configuration.getSaslUsername(),
                configuration.getSaslPassword())) {
            write(ctx, part);
        }
        ctx.flush();
    }

    private void endCapabilityNegotiation(ChannelHandlerContext ctx) {
        if (capabilitiesNegotiated) {
            return;
        }
        capabilitiesNegotiated = true;
        write(ctx, Cap.end());
        ctx.flush();
    }

    private void retryWithAnotherNick(ChannelHandlerContext ctx, IRCMessage message) {
        if (nickAttempts >= configuration.getMaxNickAttempts()) {
            fail(ctx, "Nick " + currentNick + " is unavailable and "
                    + configuration.getMaxNickAttempts() + " alternatives were rejected");
            return;
        }
        nickAttempts++;
        currentNick = currentNick + "_";
        LOGGER.info("Nick was taken, retrying as {}", currentNick);
        write(ctx, new Nick(currentNick));
        ctx.flush();
    }

    private void complete(ChannelHandlerContext ctx) {
        LOGGER.info("Registered as {}", currentNick);
        registered.complete(null);
        // Nothing left to do, so stop parsing every inbound line.
        ctx.pipeline().remove(this);
    }

    private void fail(ChannelHandlerContext ctx, String reason) {
        LOGGER.error(reason);
        registered.completeExceptionally(new IRCClientException(reason));
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Unblock connect() if the server hangs up mid-handshake.
        registered.completeExceptionally(
                new IRCClientException("Connection closed before registration completed"));
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        registered.completeExceptionally(
                new IRCClientException("Registration failed", cause));
        ctx.fireExceptionCaught(cause);
    }

    /**
     * The command's log form is used here; render() is what reaches the encoder, so
     * a password is never logged.
     */
    private void write(ChannelHandlerContext ctx, Command command) {
        LOGGER.debug("Registration > {}", command);
        ctx.write(command.render());
    }

    /**
     * The nick registration settled on, which may differ from the configured one.
     */
    public String getCurrentNick() {
        return currentNick;
    }
}
