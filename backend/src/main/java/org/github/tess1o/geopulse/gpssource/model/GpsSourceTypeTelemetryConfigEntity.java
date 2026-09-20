package org.github.tess1o.geopulse.gpssource.model;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "gps_source_type_telemetry_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_gps_source_type_telemetry_config_user_source",
                columnNames = {"user_id", "source_type"}
        )
)
public class GpsSourceTypeTelemetryConfigEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private GpsSourceType sourceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping", columnDefinition = "jsonb", nullable = false)
    private List<GpsTelemetryMappingEntry> mapping;
}
