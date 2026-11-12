package com.ivomens.mcheli.content.report;

import com.ivomens.mcheli.content.report.Diagnostic.Severity;
import com.ivomens.mcheli.util.ModLogger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects the {@link ContentReport} of one content load while it runs. Every problem the content pipeline finds
 * is logged where it is found (the readers, the binders of the features, the HUD compiler); while a collector is
 * open it also receives each warning and error the mod logs on the loading thread, and files them under the
 * content file being read at the time ({@link #enterFile}).
 * <p>
 * Only one collector is open at a time; the loader opens it in a try-with-resources block.
 */
public final class ReportCollector implements AutoCloseable {

    private static final AtomicReference<ReportCollector> OPEN = new AtomicReference<>();
    /** A scope that records nothing, for files read while no collector is open (an in-game reload). */
    private static final FileScope NO_SCOPE = new FileScope(null, false);

    private final String title;
    private final Thread thread = Thread.currentThread();
    private final long threadId = this.thread.threadId();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final List<FileOverride> overrides = new ArrayList<>();
    private final List<ContentReport.Pack> packs = new ArrayList<>();
    private final Capture capture = new Capture();
    /** Where the mod's log events go, or null when the log is not Log4j's (the report then has no messages). */
    @Nullable
    private final LoggerConfig listened;
    private final String loggerName;
    private String pack;
    private String file;
    private int filesRead;
    private int filesLoaded;

    private ReportCollector(String title) {
        this.title = title;
        this.loggerName = ModLogger.get().getName();
        this.listened = ModLogger.get() instanceof Logger core ? core.get() : null;
    }

    /**
     * Opens the collector of a load and starts listening to the log.
     *
     * @param title what runs, such as {@code Content load}
     * @throws IllegalStateException when another collector is open
     */
    public static ReportCollector open(String title) {
        ReportCollector collector = new ReportCollector(title);
        if (!OPEN.compareAndSet(null, collector)) {
            throw new IllegalStateException("a content report is already being collected");
        }
        if (collector.listened != null) {
            // listens where the events already go, so the log's own routing is left as it is
            collector.capture.start();
            collector.listened.addAppender(collector.capture, Level.WARN, null);
        }
        return collector;
    }

    /**
     * Marks the start of one content file; close the scope when it is read. Problems logged in between are filed
     * under it. Without an open collector, or on another thread, the scope does nothing.
     */
    public static FileScope enterFile(String pack, String file) {
        ReportCollector collector = OPEN.get();
        if (collector == null || collector.thread != Thread.currentThread()) {
            return NO_SCOPE;
        }
        collector.pack = pack;
        collector.file = file;
        return new FileScope(collector, true);
    }

    /**
     * Files the problems logged until the scope closes under a file without counting it as read, such as the
     * message that it replaced another file.
     */
    public static FileScope about(String pack, String file) {
        ReportCollector collector = OPEN.get();
        if (collector == null || collector.thread != Thread.currentThread()) {
            return NO_SCOPE;
        }
        collector.pack = pack;
        collector.file = file;
        return new FileScope(collector, false);
    }

    /** Lists a pack, in load order. */
    public void pack(String domain, String source) {
        this.packs.add(new ContentReport.Pack(domain, source));
    }

    /** Records a file that replaced another; the caller logs it. */
    public void override(FileOverride override) {
        this.overrides.add(override);
    }

    /** Adds a problem that is not logged, or logged at a level the collector does not listen to. */
    public void add(Diagnostic diagnostic) {
        this.diagnostics.add(diagnostic);
    }

    /** Stops listening and returns what was collected. */
    public ContentReport finish() {
        close();
        return new ContentReport(this.title, this.packs, this.filesRead, this.filesLoaded, this.overrides,
                this.diagnostics);
    }

    @Override
    public void close() {
        if (OPEN.compareAndSet(this, null) && this.listened != null) {
            this.listened.removeAppender(this.capture.getName());
            this.capture.stop();
        }
    }

    private void received(Severity severity, String message) {
        this.diagnostics.add(new Diagnostic(severity, this.pack, this.file, message));
    }

    /** One content file being read. */
    public static final class FileScope implements AutoCloseable {

        @Nullable
        private final ReportCollector collector;
        private final boolean counts;
        private boolean loaded;

        private FileScope(@Nullable ReportCollector collector, boolean counts) {
            this.collector = collector;
            this.counts = counts;
        }

        /** The file loaded; a file whose scope closes without this was skipped. */
        public void loaded() {
            this.loaded = true;
        }

        @Override
        public void close() {
            if (this.collector != null) {
                if (this.counts) {
                    this.collector.filesRead++;
                    this.collector.filesLoaded += this.loaded ? 1 : 0;
                }
                this.collector.pack = null;
                this.collector.file = null;
            }
        }
    }

    /** Receives the mod's log events while the collector is open. */
    private final class Capture extends AbstractAppender {

        Capture() {
            super("mcheli-content-report", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getThreadId() != ReportCollector.this.threadId
                    || !ReportCollector.this.loggerName.equals(event.getLoggerName())
                    || !event.getLevel().isMoreSpecificThan(Level.WARN)) {
                return;
            }
            StringBuilder message = new StringBuilder(event.getMessage().getFormattedMessage());
            for (Throwable cause = event.getThrown(); cause != null; cause = cause.getCause()) {
                message.append("\n  caused by ").append(cause);
            }
            received(event.getLevel().isMoreSpecificThan(Level.ERROR) ? Severity.ERROR : Severity.WARNING,
                    message.toString());
        }
    }
}
