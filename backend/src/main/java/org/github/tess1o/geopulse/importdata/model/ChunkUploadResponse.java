package org.github.tess1o.geopulse.importdata.model;

public record ChunkUploadResponse(
        int chunkIndex, int receivedChunks, int totalChunks, int progress, boolean complete
) {
}
