package io.github.aindriub.irc.client.event;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class LoggingEventHandlerTest {

    private final List<ILoggingEvent> logged = new ArrayList<>();

    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
        @Override
        protected void append(ILoggingEvent event) {
            logged.add(event);
        }
    };

    private Logger logger;

    @Before
    public void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LoggingEventHandler.class);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @After
    public void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    public void logsThePayloadOfEveryEvent() {
        new LoggingEventHandler().publishEvent(new Event<>("PING :tmi.twitch.tv"));

        assertEquals(1, logged.size());
        assertEquals(Level.INFO, logged.get(0).getLevel());
        assertEquals("PING :tmi.twitch.tv", logged.get(0).getArgumentArray()[0]);
    }
}
