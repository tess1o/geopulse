package org.github.tess1o.geopulse.gpssource.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Latest ingest activity of one GPS source. Read through JPA; written only by the native upserts in
 * {@link org.github.tess1o.geopulse.gpssource.repository.GpsSourceIngestStatusRepository}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "gps_source_ingest_status")
public class GpsSourceIngestStatusEntity {

    @Id
    @Column(name = "source_config_id")
    private UUID sourceConfigId;

    @Column(name = "last_received_at")
    private Instant lastReceivedAt;

    @Column(name = "last_saved_at")
    private Instant lastSavedAt;

    @Column(name = "last_saved_point_timestamp")
    private Instant lastSavedPointTimestamp;

    @Column(name = "last_rejected_at")
    private Instant lastRejectedAt;

    @Column(name = "last_rejection_code", length = 40)
    private String lastRejectionCode;

    @Column(name = "last_rejection_params", columnDefinition = "TEXT")
    private String lastRejectionParams;

    @Column(name = "last_auth_failure_at")
    private Instant lastAuthFailureAt;

    @Column(name = "last_auth_failure_code", length = 40)
    private String lastAuthFailureCode;
}
