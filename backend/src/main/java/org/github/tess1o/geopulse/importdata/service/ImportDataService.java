package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.importdata.model.ImportJob;

import java.io.IOException;
import java.util.List;

/**
 * Simplified ImportDataService that delegates to strategy pattern implementations.
 * This replaces the 700+ line monolithic ImportDataService.
 */
@ApplicationScoped
@Slf4j
public class ImportDataService {
    
    @Inject
    ImportStrategyRegistry strategyRegistry;
    
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
        
        ImportStrategy strategy = strategyRegistry.getStrategy(job.getOptions().getImportFormat());
        strategy.processImportData(job);
        
        log.info("Import processing completed for user: {}", job.getUserId());
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