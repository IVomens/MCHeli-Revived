package com.ivomens.mcheli.wingman.control;

import com.ivomens.mcheli.core.wingman.brain.BrainLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The wingman brain's messages, written to the {@code McHeliWingman} log as they always were. */
final class WingmanBrainLog implements BrainLog {

    static final BrainLog INSTANCE = new WingmanBrainLog();

    private static final Logger LOG = LogManager.getLogger("McHeliWingman");

    private WingmanBrainLog() {
    }

    @Override
    public void debug(String format, Object... args) {
        LOG.debug(format, args);
    }

    @Override
    public void info(String format, Object... args) {
        LOG.info(format, args);
    }

    @Override
    public void warn(String format, Object... args) {
        LOG.warn(format, args);
    }

    @Override
    public void error(String format, Object... args) {
        LOG.error(format, args);
    }
}
