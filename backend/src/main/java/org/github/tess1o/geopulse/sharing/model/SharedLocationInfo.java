package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedLocationInfo {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    @JsonProperty("has_password")
    private boolean hasPassword;

    @JsonProperty("show_history")
    private boolean showHistory;

    @JsonProperty("history_hours")
    private int historyHours;

    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("view_count")
    private Integer viewCount;

    @JsonProperty("shared_by")
    private String sharedBy;

    @JsonProperty("share_type")
    private String shareType;

    @JsonProperty("start_date")
    private Instant startDate;

    @JsonProperty("end_date")
    private Instant endDate;

    @JsonProperty("show_current_location")
    private Boolean showCurrentLocation;

    @JsonProperty("show_photos")
    private Boolean showPhotos;

    @JsonProperty("timeline_status")
    private String timelineStatus;

    @JsonProperty("custom_map_tile_url")
    private String customMapTileUrl;
}