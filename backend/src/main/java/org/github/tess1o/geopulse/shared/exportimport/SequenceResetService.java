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
        "timeline_data_gaps", "timeline_data_gaps_id_seq",
        "gps_points", "gps_points_id_seq"
        // Note: gps_source_config uses UUID, not a sequence
    );
    
    public void resetSequenceAfterImport(String tableName, String sequenceName) {
        try {
            // First, check if the sequence exists
            @SuppressWarnings("unchecked")
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
            
            // Only ever move the sequence forward, past rows imported with their original ids. Hibernate hands
            // out ids in blocks taken from the sequence, and rows from a block may be saved but not committed yet
            // (MAX(id) cannot see them): moving the sequence back below its last value would hand that block out
            // again, and the inserts that get its ids would fail with duplicate keys. One statement, so the
            // sequence cannot move between reading it and setting it.
            Object[] result = (Object[]) entityManager
                .createNativeQuery("SELECT m.max_id, s.last_value, "
                    + "CASE WHEN m.max_id + 1 > s.last_value THEN setval('" + sequenceName + "', m.max_id + 1) "
                    + "ELSE s.last_value END "
                    + "FROM (SELECT COALESCE(MAX(id), 0) AS max_id FROM " + tableName + ") m, " + sequenceName + " s")
                .getSingleResult();
            long maxId = ((Number) result[0]).longValue();
            long lastValue = ((Number) result[1]).longValue();
            long newSequenceValue = ((Number) result[2]).longValue();

            if (newSequenceValue == lastValue) {
                log.info("Table {} has max ID: {}, sequence {} is already at {}, leaving it unchanged",
                    tableName, maxId, sequenceName, lastValue);
            } else {
                log.info("Table {} has max ID: {}, advanced sequence {} from {} to {}",
                    tableName, maxId, sequenceName, lastValue, newSequenceValue);
            }
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
