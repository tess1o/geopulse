package org.github.tess1o.geopulse.notifications.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gps_health_incidents")
@Getter
@Setter
@NoArgsConstructor
public class GpsHealthIncidentEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "last_recovered_at")
    private Instant lastRecoveredAt;
}
