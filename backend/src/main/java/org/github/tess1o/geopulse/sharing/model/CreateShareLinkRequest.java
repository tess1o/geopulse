package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShareLinkRequest {

    @NotBlank(message = "Name cannot be empty")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    @JsonProperty("name")
    @Schema(examples = "Weekend in Lviv")
    private String name;

    @Future(message = "Expiration date must be in the future")
    @JsonProperty("expires_at")
    @Schema(examples = "2025-06-30T23:59:59Z")
    private Instant expiresAt;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @JsonProperty("password")
    @Schema(examples = "share-password")
    private String password;

    @JsonProperty("show_history")
    @Schema(examples = "true")
    private boolean showHistory;

    @JsonProperty("history_hours")
    @Schema(examples = "24")
    private int historyHours;

    @JsonProperty("share_type")
    @Schema(examples = "TIMELINE")
    private String shareType = "LIVE_LOCATION";

    @JsonProperty("start_date")
    @Schema(examples = "2025-06-06T00:00:00Z")
    private Instant startDate;

    @JsonProperty("end_date")
    @Schema(examples = "2025-06-08T23:59:59Z")
    private Instant endDate;

    @JsonProperty("show_current_location")
    @Schema(examples = "false")
    private Boolean showCurrentLocation = true;

    @JsonProperty("show_photos")
    @Schema(examples = "true")
    private Boolean showPhotos = false;

    @Size(max = 255, message = "Immich album id cannot exceed 255 characters")
    @JsonProperty("immich_album_id")
    @Schema(examples = "7b9e8c1a-2f4d-4b6e-9a3c-5d1e0f2a8b7c")
    private String immichAlbumId;

    @JsonProperty("show_notes")
    @Schema(examples = "true")
    private Boolean showNotes = false;

    @Size(max = 1000, message = "Custom map tile URL cannot exceed 1000 characters")
    @JsonProperty("custom_map_tile_url")
    @Schema(examples = "https://tile.openstreetmap.org/{z}/{x}/{y}.png")
    private String customMapTileUrl;

    @Size(max = 1000, message = "Custom map style URL cannot exceed 1000 characters")
    @JsonProperty("custom_map_style_url")
    @Schema(examples = "https://tiles.openfreemap.org/styles/liberty")
    private String customMapStyleUrl;

    @JsonProperty("map_render_mode")
    @Schema(examples = "RASTER")
    private MapRenderMode mapRenderMode = MapRenderMode.RASTER;
}
