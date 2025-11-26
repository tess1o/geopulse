package org.github.tess1o.geopulse.sharing.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shared_link")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class SharedLinkEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    private String name;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "password")
    private String password;

    @Column(name = "show_history")
    private boolean showHistory;

    @Column(name = "history_hours")
    private int historyHours;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserEntity user;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "share_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ShareType shareType = ShareType.LIVE_LOCATION;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "show_current_location")
    @Builder.Default
    private Boolean showCurrentLocation = true;

    @Column(name = "show_photos")
    @Builder.Default
    private Boolean showPhotos = false;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (viewCount == null) {
            viewCount = 0;
        }
        if (shareType == null) {
            shareType = ShareType.LIVE_LOCATION;
        }
        if (showCurrentLocation == null) {
            showCurrentLocation = true;
        }
        if (showPhotos == null) {
            showPhotos = false;
        }
    }

    /**
     * Get the status of a timeline share based on current time
     * @return "upcoming", "active", "completed", or null for non-timeline shares
     */
    public String getTimelineStatus() {
        if (shareType != ShareType.TIMELINE || startDate == null || endDate == null) {
            return null;
        }
        Instant now = Instant.now();
        if (now.isBefore(startDate)) {
            return "upcoming";
        } else if (now.isAfter(endDate)) {
            return "completed";
        } else {
            return "active";
        }
    }

    /**
     * Check if the share is active (not expired)
     * @return true if the share is active
     */
    public boolean isActive() {
        if (expiresAt == null) {
            return true;
        }
        return Instant.now().isBefore(expiresAt);
    }
}
