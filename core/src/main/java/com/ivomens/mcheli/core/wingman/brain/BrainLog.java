package com.ivomens.mcheli.core.wingman.brain;

/**
 * Where a wingman brain reports its decisions and the setup problems it meets (a missing runway end, an
 * unknown taxi route). Messages use {@code {}} placeholders, filled from the arguments in order. The game
 * writes them to the wingman log; tests usually drop them.
 */
public interface BrainLog {

    /** Drops every message. */
    BrainLog NONE = new BrainLog() {
        @Override
        public void debug(String format, Object... args) {
        }

        @Override
        public void info(String format, Object... args) {
        }

        @Override
        public void warn(String format, Object... args) {
        }

        @Override
        public void error(String format, Object... args) {
        }
    };

    /** A phase change or another step worth tracing. */
    void debug(String format, Object... args);

    /** A mission or order starting, ending or changing course. */
    void info(String format, Object... args);

    /** A setup problem the brain works around. */
    void warn(String format, Object... args);

    /** A setup problem the brain cannot fly with. */
    void error(String format, Object... args);
}
