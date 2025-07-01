package org.github.tess1o.geopulse.importdata.service;

import org.github.tess1o.geopulse.importdata.model.ImportJob;

import java.io.IOException;
import java.util.List;

/**
 * Strategy interface for different import formats (OwnTracks, GeoPulse, etc.)
 */
public interface ImportStrategy {
    
    /**
     * Get the format this strategy handles
     */
    String getFormat();
    
    /**
     * Validate import data and detect data types
     */
    List<String> validateAndDetectDataTypes(ImportJob job) throws IOException;
    
    /**
     * Process the import data
     */
    void processImportData(ImportJob job) throws IOException;
    
    /**
     * Check if this strategy can handle the given format
     */
    default boolean canHandle(String format) {
        return getFormat().equals(format);
    }
}