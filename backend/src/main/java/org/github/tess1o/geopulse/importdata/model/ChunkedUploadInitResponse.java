package org.github.tess1o.geopulse.importdata.model;

import java.time.Instant;
import java.util.UUID;

public record ChunkedUploadInitResponse(UUID uploadId, int totalChunks, long chunkSizeBytes, Instant expiresAt) {
}
