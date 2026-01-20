package org.github.tess1o.geopulse.importdata.model;

/**
 * Status of a chunked upload session
 */
public enum UploadStatus {
    UPLOADING,   // Chunks are being received
    ASSEMBLING,  // All chunks received, file being assembled
    COMPLETED,   // File successfully assembled
    FAILED,      // Upload or assembly failed
    EXPIRED      // Session timed out
}
