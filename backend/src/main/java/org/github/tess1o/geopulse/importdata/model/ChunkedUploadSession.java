package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an active chunked file upload session.
 * Used for handling large file uploads (>80MB) that are split into smaller chunks
 * to bypass Cloudflare's 100MB upload limit.
 */
@Data
public class ChunkedUploadSession {
    private UUID uploadId;
    private UUID userId;
    private String fileName;
    private long fileSize;
    private int totalChunks;
    private String importFormat;
    private String options;  // JSON string of import options
    private Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();
    private Instant createdAt;
    private Instant expiresAt;
    private String tempDirectoryPath;  // /tmp/geopulse/chunks/{uploadId}/
    private UploadStatus status;

    public ChunkedUploadSession() {
        this.uploadId = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.receivedChunks = ConcurrentHashMap.newKeySet();
        this.status = UploadStatus.UPLOADING;
    }

    public ChunkedUploadSession(UUID userId, String fileName, long fileSize, int totalChunks,
                                 String importFormat, String options, long sessionTimeoutHours) {
        this();
        this.userId = userId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.totalChunks = totalChunks;
        this.importFormat = importFormat;
        this.options = options;
        this.expiresAt = Instant.now().plusSeconds(sessionTimeoutHours * 3600);
    }

    /**
     * Check if all chunks have been received
     */
    public boolean isComplete() {
        return receivedChunks.size() == totalChunks;
    }

    /**
     * Check if a specific chunk has been received
     */
    public boolean hasChunk(int chunkIndex) {
        return receivedChunks.contains(chunkIndex);
    }

    /**
     * Mark a chunk as received
     */
    public void markChunkReceived(int chunkIndex) {
        receivedChunks.add(chunkIndex);
    }

    /**
     * Check if this session has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Get the number of received chunks
     */
    public int getReceivedChunkCount() {
        return receivedChunks.size();
    }

    /**
     * Calculate upload progress percentage
     */
    public int getProgressPercentage() {
        if (totalChunks == 0) return 0;
        return (int) ((receivedChunks.size() * 100L) / totalChunks);
    }
}
