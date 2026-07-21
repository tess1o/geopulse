package org.github.tess1o.geopulse.importdata.service;

/**
 * Signals that an import batch failed while writing to the database.
 * Parsing code must not treat this as a bad input record that can be skipped.
 */
public class ImportBatchPersistenceException extends RuntimeException {

    public ImportBatchPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
