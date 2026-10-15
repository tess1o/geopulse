package org.github.tess1o.geopulse.gpssource.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/** The GPS source connected to the calling API token, without any credentials. */
public record ConnectedGpsSourceDTO(
        UUID id,
        @Schema(examples = "HOME_ASSISTANT") String type,
        @Schema(description = "False when the user disabled the source; locations sent with the token are then "
                + "rejected with `GPS_SOURCE_DISABLED`.") boolean active,
        @Schema(description = "Latest ingest activity; null until the first location arrives.")
        GpsSourceIngestStatusDTO ingestStatus
) {
}
