package org.github.tess1o.geopulse.trips.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(
        name = "trip_collaborators",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_trip_collaborators_trip_user", columnNames = {"trip_id", "collaborator_user_id"})
        }
)
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TripCollaboratorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @ToString.Exclude
    private TripEntity trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collaborator_user_id", nullable = false)
    @ToString.Exclude
    private UserEntity collaborator;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_role", nullable = false, length = 16)
    private TripCollaboratorAccessRole accessRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.accessRole == null) {
            this.accessRole = TripCollaboratorAccessRole.VIEW;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
