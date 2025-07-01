package org.github.tess1o.geopulse.shared.exportimport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@ApplicationScoped
@Slf4j
public class SequenceResetService {
    
    @Inject
    EntityManager entityManager;
    
    private static final Map<String, String> TABLE_SEQUENCE_MAP = Map.of(
        "reverse_geocoding_location", "reverse_geocoding_location_seq",
        "favorite_locations", "favorite_locations_id_seq",
        "timeline_stays", "timeline_stays_id_seq", 
        "timeline_trips", "timeline_trips_id_seq",
        "gps_points", "gps_points_id_seq"
        // Note: gps_source_config uses UUID, not a sequence
    );
    
    public void resetSequenceAfterImport(String tableName, String sequenceName) {
        try {
            // First, check if the sequence exists
            Object sequenceExists = entityManager
                .createNativeQuery("SELECT 1 FROM pg_sequences WHERE sequencename = ?")
                .setParameter(1, sequenceName)
                .getResultStream()
                .findFirst()
                .orElse(null);
            
            if (sequenceExists == null) {
                log.warn("Sequence {} does not exist, skipping reset", sequenceName);
                return;
            }
            
            // Get the maximum ID from the table
            Long maxId = (Long) entityManager
                .createNativeQuery("SELECT COALESCE(MAX(id), 0) FROM " + tableName)
                .getSingleResult();
            
            log.info("Table {} has max ID: {}, resetting sequence {} to {}", tableName, maxId, sequenceName, maxId + 1);
            
            // Use getSingleResult() instead of executeUpdate() since setval() returns a value
            Long newSequenceValue = (Long) entityManager
                .createNativeQuery("SELECT setval('" + sequenceName + "', ?)")
                .setParameter(1, maxId + 1)
                .getSingleResult();
            
            log.info("Successfully reset sequence {} to {} for table {}", sequenceName, newSequenceValue, tableName);
        } catch (Exception e) {
            log.error("Failed to reset sequence {} for table {}: {}", sequenceName, tableName, e.getMessage(), e);
        }
    }
    
    public void resetAllSequences() {
        for (Map.Entry<String, String> entry : TABLE_SEQUENCE_MAP.entrySet()) {
            resetSequenceAfterImport(entry.getKey(), entry.getValue());
        }
    }
    
    public void resetSequencesForTables(String... tableNames) {
        for (String tableName : tableNames) {
            String sequenceName = TABLE_SEQUENCE_MAP.get(tableName);
            if (sequenceName != null) {
                resetSequenceAfterImport(tableName, sequenceName);
            } else {
                log.warn("No sequence mapping found for table: {}", tableName);
            }
        }
    }
}