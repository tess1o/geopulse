package org.github.tess1o.geopulse.importdata.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ChunkedUploadStatusResponse(
        UUID uploadId,
        String fileName,
        long fileSize,
        int totalChunks,
        int receivedChunks,
        int progress,
        UploadStatus status,
        boolean complete,
        boolean expired,
        Instant expiresAt,
        Set<Integer> receivedChunkIndices
) {
}
