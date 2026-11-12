package com.ivomens.mcheli.golden;

import com.ivomens.mcheli.util.ModLogger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Collects what the mod logs while a piece of code runs, as stable text: level, message, and the
 * exception chain without stack traces (line numbers would make every refactor look like a change).
 * Clock times in messages are replaced by {@code [time]}.
 */
public final class LogCapture implements AutoCloseable {

    /** The clock time ModLogger.log puts in some messages. */
    private static final Pattern TIME_OF_DAY = Pattern.compile("\\[\\d\\d:\\d\\d:\\d\\d:\\d{3}]");

    private final Logger logger;
    private final Level previousLevel;
    private final Appender appender = new Appender();

    private LogCapture() {
        this.logger = (Logger) ModLogger.get();
        this.previousLevel = this.logger.getLevel();
        this.appender.start();
        this.logger.addAppender(this.appender);
        this.logger.setLevel(Level.DEBUG);
    }

    /** Starts capturing; close the capture to stop. */
    public static LogCapture start() {
        return new LogCapture();
    }

    public List<String> lines() {
        return List.copyOf(this.appender.lines);
    }

    @Override
    public void close() {
        this.logger.removeAppender(this.appender);
        this.logger.setLevel(this.previousLevel);
        this.appender.stop();
    }

    private static final class Appender extends AbstractAppender {

        private final List<String> lines = new ArrayList<>();

        Appender() {
            super("mcheli-golden-capture", null, null, true, new Property[0]);
        }

        @Override
        public synchronized void append(LogEvent event) {
            String message = TIME_OF_DAY.matcher(event.getMessage().getFormattedMessage()).replaceAll("[time]");
            this.lines.add(event.getLevel() + " " + message);
            for (Throwable cause = event.getThrown(); cause != null; cause = cause.getCause()) {
                this.lines.add("    caused by " + cause.getClass().getName() + ": " + cause.getMessage());
            }
        }
    }
}
