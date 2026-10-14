package org.github.tess1o.geopulse.gpssource.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceIngestStatusEntity;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Writes run in their own transaction so a failed status update can never roll back stored GPS points.
 */
@ApplicationScoped
public class GpsSourceIngestStatusRepository implements PanacheRepositoryBase<GpsSourceIngestStatusEntity, UUID> {

    public List<GpsSourceIngestStatusEntity> findBySourceConfigIds(Collection<UUID> sourceConfigIds) {
        if (sourceConfigIds == null || sourceConfigIds.isEmpty()) {
            return List.of();
        }
        return list("sourceConfigId in ?1", sourceConfigIds);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void upsertIngest(UUID sourceConfigId, Instant receivedAt, Instant savedAt, Instant savedPointTimestamp,
                             Instant rejectedAt, String rejectionCode, String rejectionParams) {
        getEntityManager().createNativeQuery("""
                        INSERT INTO gps_source_ingest_status AS s
                            (source_config_id, last_received_at, last_saved_at, last_saved_point_timestamp,
                             last_rejected_at, last_rejection_code, last_rejection_params)
                        VALUES (:id, :receivedAt, :savedAt, :savedPointTimestamp,
                                :rejectedAt, :rejectionCode, :rejectionParams)
                        ON CONFLICT (source_config_id) DO UPDATE
                        SET last_received_at = GREATEST(s.last_received_at, EXCLUDED.last_received_at),
                            last_saved_at = COALESCE(EXCLUDED.last_saved_at, s.last_saved_at),
                            last_saved_point_timestamp = GREATEST(s.last_saved_point_timestamp,
                                                                  EXCLUDED.last_saved_point_timestamp),
                            last_rejected_at = COALESCE(EXCLUDED.last_rejected_at, s.last_rejected_at),
                            last_rejection_code = CASE WHEN EXCLUDED.last_rejected_at IS NULL
                                THEN s.last_rejection_code ELSE EXCLUDED.last_rejection_code END,
                            last_rejection_params = CASE WHEN EXCLUDED.last_rejected_at IS NULL
                                THEN s.last_rejection_params ELSE EXCLUDED.last_rejection_params END
                        """)
                .unwrap(NativeQuery.class)
                .setParameter("id", sourceConfigId)
                .setParameter("receivedAt", receivedAt, StandardBasicTypes.INSTANT)
                .setParameter("savedAt", savedAt, StandardBasicTypes.INSTANT)
                .setParameter("savedPointTimestamp", savedPointTimestamp, StandardBasicTypes.INSTANT)
                .setParameter("rejectedAt", rejectedAt, StandardBasicTypes.INSTANT)
                .setParameter("rejectionCode", rejectionCode, StandardBasicTypes.STRING)
                .setParameter("rejectionParams", rejectionParams, StandardBasicTypes.STRING)
                .executeUpdate();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void upsertAuthFailure(UUID sourceConfigId, Instant failedAt, String failureCode) {
        getEntityManager().createNativeQuery("""
                        INSERT INTO gps_source_ingest_status AS s
                            (source_config_id, last_auth_failure_at, last_auth_failure_code)
                        VALUES (:id, :failedAt, :failureCode)
                        ON CONFLICT (source_config_id) DO UPDATE
                        SET last_auth_failure_at = EXCLUDED.last_auth_failure_at,
                            last_auth_failure_code = EXCLUDED.last_auth_failure_code
                        """)
                .unwrap(NativeQuery.class)
                .setParameter("id", sourceConfigId)
                .setParameter("failedAt", failedAt, StandardBasicTypes.INSTANT)
                .setParameter("failureCode", failureCode, StandardBasicTypes.STRING)
                .executeUpdate();
    }
}
