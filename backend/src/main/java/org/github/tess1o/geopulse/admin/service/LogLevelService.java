package org.github.tess1o.geopulse.admin.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.event.LoggingSettingsChangedEvent;
import org.jboss.logmanager.LogContext;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Slf4j
public class LogLevelService {

    private static final String CATEGORY = "org.github.tess1o.geopulse";
    private static final Set<String> LEVELS = Set.of("ERROR", "WARN", "INFO", "DEBUG");

    @Inject
    SystemSettingsService settingsService;

    private volatile String appliedConfiguration;
    private volatile Level baseline;
    private volatile LoggingStatus status = new LoggingStatus("INHERIT", effectiveLevel(), Source.DEFAULT);
    private volatile boolean warnedInvalid;
    private volatile boolean warnedDatabaseFailure;

    @Transactional
    void onStart(@Observes StartupEvent ignored) {
        // Capture the level applied from configuration (quarkus.log.category."org.github.tess1o.geopulse".level)
        // before touching the logger, so DEFAULT (no Admin override, no env var) can restore it instead of
        // clearing it - otherwise a configured DEBUG level (dev profile) would be silently dropped.
        baseline = LogContext.getLogContext().getLogger(CATEGORY).getLevel();
        reconcile();
    }

    @Transactional
    void onSettingsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) LoggingSettingsChangedEvent ignored) {
        reconcile();
    }

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void scheduledReconcile() {
        reconcile();
    }

    public LoggingStatus getStatus() {
        return status;
    }

    void reconcile() {
        try {
            boolean adminOverride = !settingsService.isDefault(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY);
            String configured = settingsService.getString(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY);
            Source source = adminOverride ? Source.ADMIN
                    : configured == null || configured.isBlank() || "inherit".equalsIgnoreCase(configured)
                    ? Source.DEFAULT : Source.ENVIRONMENT;
            String normalized = source == Source.DEFAULT ? "INHERIT" : configured.toUpperCase(Locale.ROOT);

            if (source != Source.DEFAULT && !LEVELS.contains(normalized)) {
                if (!warnedInvalid) {
                    log.warn("Ignoring invalid application log level '{}'; expected ERROR, WARN, INFO, or DEBUG", configured);
                    warnedInvalid = true;
                }
                return;
            }

            warnedInvalid = false;
            warnedDatabaseFailure = false;
            if (!normalized.equals(appliedConfiguration)) {
                LogContext.getLogContext().getLogger(CATEGORY)
                        .setLevel(source == Source.DEFAULT ? baseline : toJulLevel(normalized));
                appliedConfiguration = normalized;
            }
            status = new LoggingStatus(normalized, effectiveLevel(), source);
        } catch (RuntimeException exception) {
            if (!warnedDatabaseFailure) {
                log.warn("Unable to reconcile application log level; retaining the last valid level", exception);
                warnedDatabaseFailure = true;
            }
        }
    }

    private static Level toJulLevel(String level) {
        return switch (level) {
            case "ERROR" -> Level.SEVERE;
            case "WARN" -> Level.WARNING;
            case "DEBUG" -> Level.FINE;
            default -> Level.INFO;
        };
    }

    private static String effectiveLevel() {
        Logger logger = LogContext.getLogContext().getLogger(CATEGORY);
        while (logger != null && logger.getLevel() == null) {
            logger = logger.getParent();
        }
        if (logger == null || logger.getLevel() == null) {
            return "INFO";
        }
        int value = logger.getLevel().intValue();
        if (value >= Level.SEVERE.intValue()) return "ERROR";
        if (value >= Level.WARNING.intValue()) return "WARN";
        if (value >= Level.INFO.intValue()) return "INFO";
        return "DEBUG";
    }

    public enum Source { ADMIN, ENVIRONMENT, DEFAULT }

    public record LoggingStatus(String configuredLevel, String effectiveLevel, Source source) {
    }
}
