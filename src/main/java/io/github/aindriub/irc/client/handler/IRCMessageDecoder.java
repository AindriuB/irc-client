package io.github.aindriub.irc.client.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.aindriub.irc.client.message.IRCMessageParser;
import io.github.aindriub.irc.client.message.IRCParseException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

/**
 * Turns a raw line into a parsed message for the typed subscribers downstream.
 *
 * <p>Sits after the String consumers, so raw subscribers and output streams still
 * see the unparsed line. A line that will not parse is logged and dropped rather
 * than propagated: one malformed line from a server must not take the connection
 * down.
 */
public class IRCMessageDecoder extends MessageToMessageDecoder<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IRCMessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, String line, List<Object> out) {
        try {
            out.add(IRCMessageParser.parse(line));
        } catch (IRCParseException e) {
            LOGGER.warn("Dropping unparseable line: {}", line, e);
        }
    }
}
