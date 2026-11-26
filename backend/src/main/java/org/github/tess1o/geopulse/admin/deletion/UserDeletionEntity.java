package org.github.tess1o.geopulse.admin.deletion;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A requested user deletion. It has no relation to the user, so it outlives the user and reports the outcome.
 */
@Entity
@Table(name = "user_deletions")
@Getter
@Setter
@NoArgsConstructor
public class UserDeletionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserDeletionStatus status;

    /** NULL until the worker has locked the user out of timeline processing. */
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 40)
    private UserDeletionPhase phase;

    @Column(name = "deleted_gps_points", nullable = false)
    private long deletedGpsPoints;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /**
     * Compared with the database clock when a worker claims the deletion, so it is only ever set by the database
     * (column default, or {@link UserDeletionSteps} and {@link UserDeletionService} with {@code now()}).
     */
    @Column(name = "next_attempt_at", insertable = false, updatable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Bumped by every change. A worker's failure report names the revision its step saw, and is dropped if another
     * worker has changed the deletion since.
     */
    @Column(name = "revision", nullable = false)
    private long revision;
}
