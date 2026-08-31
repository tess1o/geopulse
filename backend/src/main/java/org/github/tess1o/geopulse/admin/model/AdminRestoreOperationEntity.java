package org.github.tess1o.geopulse.admin.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_restore_operations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRestoreOperationEntity extends PanacheEntityBase {
    @Id
    private UUID id;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "phase", length = 100)
    private String phase;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "progress_percent")
    private Integer progressPercent;

    @Column(name = "processed_users")
    private Integer processedUsers;

    @Column(name = "total_users")
    private Integer totalUsers;

    @Column(name = "current_user_id")
    private UUID currentUserId;

    @Column(name = "current_user_email")
    private String currentUserEmail;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;
}
