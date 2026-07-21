package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.importdata.model.ImportJob;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Simplified ImportDataService that delegates to strategy pattern implementations.
 * This replaces the 700+ line monolithic ImportDataService.
 */
@ApplicationScoped
@Slf4j
public class ImportDataService {
    
    @Inject
    ImportStrategyRegistry strategyRegistry;

    @Inject
    TimelineImportHelper timelineImportHelper;

    @Inject
    TransactionManager transactionManager;

    @ConfigProperty(name = "geopulse.import.transaction-timeout-minutes", defaultValue = "1440")
    long importTransactionTimeoutMinutes;
    
    /**
     * Validate import data and detect data types using the appropriate strategy
     */
    public List<String> validateAndDetectDataTypes(ImportJob job) throws IOException {
        log.info("Validating import data for format: {} and user: {}", 
                job.getOptions().getImportFormat(), job.getUserId());
        
        ImportStrategy strategy = strategyRegistry.getStrategy(job.getOptions().getImportFormat());
        return strategy.validateAndDetectDataTypes(job);
    }
    
    /**
     * Process import data using the appropriate strategy
     */
    public void processImportData(ImportJob job) throws IOException {
        log.info("Processing import data for format: {} and user: {}", 
                job.getOptions().getImportFormat(), job.getUserId());

        assertNoActiveCallerTransaction();
        
        ImportStrategy strategy = strategyRegistry.getStrategy(job.getOptions().getImportFormat());

        int timeoutSeconds = resolveImportTransactionTimeoutSeconds();
        UUID timelineJobId;
        try {
            QuarkusTransaction.requiringNew()
                    .timeout(timeoutSeconds)
                    .call(() -> {
                        strategy.processImportData(job);
                        return null;
                    });
            timelineJobId = job.getTimelineJobId();
        } catch (QuarkusTransactionException e) {
            failTimelineJobIfImportTransactionFailed(job, e);
            throw ImportTransactionExceptions.unwrapIOExceptionOrThrowRuntime(e);
        } catch (RuntimeException e) {
            failTimelineJobIfImportTransactionFailed(job, e);
            throw e;
        }

        if (timelineJobId != null) {
            timelineImportHelper.finishTimelineJob(timelineJobId, job.getUserId());
        }
        
        log.info("Import processing completed for user: {}", job.getUserId());
    }

    private int resolveImportTransactionTimeoutSeconds() {
        if (importTransactionTimeoutMinutes < 1) {
            throw new IllegalArgumentException(
                    "geopulse.import.transaction-timeout-minutes must be at least 1 minute");
        }

        long timeoutSeconds;
        try {
            timeoutSeconds = Math.multiplyExact(importTransactionTimeoutMinutes, 60L);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "geopulse.import.transaction-timeout-minutes is too large; max supported value is "
                            + (Integer.MAX_VALUE / 60) + " minutes", e);
        }
        if (timeoutSeconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "geopulse.import.transaction-timeout-minutes is too large; max supported value is "
                            + (Integer.MAX_VALUE / 60) + " minutes");
        }
        return (int) timeoutSeconds;
    }

    private void assertNoActiveCallerTransaction() {
        try {
            if (transactionManager.getTransaction() != null) {
                throw new IllegalStateException(
                        "ImportDataService.processImportData must be called outside an active transaction. "
                                + "The import service starts its own transaction using "
                                + "geopulse.import.transaction-timeout-minutes.");
            }
        } catch (SystemException e) {
            throw new IllegalStateException("Unable to inspect current transaction before import", e);
        }
    }

    private void failTimelineJobIfImportTransactionFailed(ImportJob job, Exception e) {
        if (job.getTimelineJobId() == null) {
            return;
        }
        timelineImportHelper.failTimelineJob(job.getTimelineJobId(), "Import failed: " + e.getMessage());
    }
    
    /**
     * Get list of supported import formats
     */
    public List<String> getSupportedFormats() {
        return strategyRegistry.getSupportedFormats();
    }
    
    /**
     * Check if an import format is supported
     */
    public boolean isFormatSupported(String format) {
        return strategyRegistry.isFormatSupported(format);
    }
}
