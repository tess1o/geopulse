package org.github.tess1o.geopulse.gpssource.model;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gps_source_config")
public class GpsSourceConfigEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private GpsSourceType sourceType; // OWNTRACKS, OVERLAND

    private String username;      // For OwnTracks

    @Column(name = "password_hash")
    private String passwordHash;  // For OwnTracks
    private String token;         // For Overland
    private boolean active;
}