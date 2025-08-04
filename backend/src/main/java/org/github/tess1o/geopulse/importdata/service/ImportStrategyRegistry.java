package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Registry for managing different import strategies
 */
@ApplicationScoped
@Slf4j
public class ImportStrategyRegistry {
    
    @Inject
    Instance<ImportStrategy> strategies;
    
    /**
     * Find the appropriate import strategy for a given format name
     */
    public ImportStrategy getStrategy(String formatName) {
        return StreamSupport.stream(strategies.spliterator(), false)
                .filter(strategy -> strategy.getFormat().equals(formatName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No import strategy found for format: " + formatName));
    }
    
    /**
     * Get all available import formats
     */
    public List<String> getSupportedFormats() {
        return StreamSupport.stream(strategies.spliterator(), false)
                .map(ImportStrategy::getFormat)
                .toList();
    }
    
    /**
     * Check if a format is supported
     */
    public boolean isFormatSupported(String formatName) {
        return StreamSupport.stream(strategies.spliterator(), false)
                .anyMatch(strategy -> strategy.getFormat().equals(formatName));
    }
}