package org.github.tess1o.geopulse.timeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing a timeline regeneration task in the priority queue.
 */
@Entity
@Table(name = "timeline_regeneration_queue")
@Getter
@Setter
public class TimelineRegenerationTask {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    public enum TaskStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public enum Priority {
        HIGH(1),    // Favorite changes - processed immediately
        LOW(2);     // Bulk imports - processed in background

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Priority fromValue(int value) {
            for (Priority priority : values()) {
                if (priority.value == value) {
                    return priority;
                }
            }
            throw new IllegalArgumentException("Unknown priority value: " + value);
        }
    }
}