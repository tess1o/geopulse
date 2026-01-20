package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.importdata.model.ChunkedUploadSession;
import org.github.tess1o.geopulse.importdata.model.UploadStatus;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Service for managing chunked file uploads.
 * Handles large file uploads that are split into smaller chunks on the frontend
 * to bypass Cloudflare's 100MB upload limit.
 */
@ApplicationScoped
@Slf4j
@Startup
public class ChunkedUploadService {

    @Inject
    SystemSettingsService settingsService;

    // Startup-only settings (directories and scheduler intervals require restart)
    @ConfigProperty(name = "geopulse.import.upload-cleanup-minutes", defaultValue = "15")
    int uploadCleanupMinutes;

    @ConfigProperty(name = "geopulse.import.chunks-directory", defaultValue = "/tmp/geopulse/chunks")
    String chunksDirectory;

    private final ConcurrentHashMap<UUID, ChunkedUploadSession> activeSessions = new ConcurrentHashMap<>();

    @PostConstruct
    void initTempDirectory() {
        try {
            Path tempDir = Paths.get(chunksDirectory);

            // Create directory if it doesn't exist
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                log.info("Created chunked upload temp directory: {}", tempDir);
            }

            // Verify it's writable
            if (!Files.isWritable(tempDir)) {
                throw new IllegalStateException("Chunked upload temp directory is not writable: " + tempDir);
            }

            log.info("Chunked upload service initialized");
            log.info("  Temp directory: {}", tempDir);
            log.info("  Chunk size: {} MB", getChunkSizeBytes());
            log.info("  Max file size: {} GB", getMaxFileSizeBytes());
            log.info("  Session timeout: {} hours", getSessionTimeoutHours());
            log.info("  Cleanup interval: {} minutes", uploadCleanupMinutes);

            // Clean up any orphaned directories from previous runs
            cleanupOrphanedDirectories();

        } catch (IOException e) {
            log.error("Failed to initialize chunked upload temp directory: {}", chunksDirectory, e);
            throw new IllegalStateException("Cannot initialize chunked upload temp directory", e);
        }
    }

    /**
     * Initialize a new chunked upload session.
     * Total chunks is calculated by the backend based on configured chunk size
     * to ensure consistency between frontend and backend.
     */
    public ChunkedUploadSession initializeUpload(UUID userId, String fileName, long fileSize,
                                                   String importFormat, String options) {
        // Calculate totalChunks based on configured chunk size
        long chunkSizeBytes = getChunkSizeBytes();
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSizeBytes);

        // Create session
        int timeoutHours = getSessionTimeoutHours();
        ChunkedUploadSession session = new ChunkedUploadSession(
                userId, fileName, fileSize, totalChunks, importFormat, options, timeoutHours
        );

        // Create temp directory for this upload
        Path sessionDir = Paths.get(chunksDirectory, session.getUploadId().toString());
        try {
            Files.createDirectories(sessionDir);
            session.setTempDirectoryPath(sessionDir.toString());
        } catch (IOException e) {
            log.error("Failed to create temp directory for upload session: {}", sessionDir, e);
            throw new IllegalStateException("Failed to create temp directory for upload", e);
        }

        // Store session
        activeSessions.put(session.getUploadId(), session);

        log.info("Initialized chunked upload session: uploadId={}, userId={}, fileName={}, " +
                        "fileSize={} MB, totalChunks={} (chunkSize={} MB), format={}",
                session.getUploadId(), userId, fileName,
                fileSize / (1024 * 1024), totalChunks, chunkSizeBytes / (1024 * 1024), importFormat);

        return session;
    }

    /**
     * Save a chunk to disk
     */
    public void saveChunk(UUID uploadId, int chunkIndex, InputStream chunkData) throws IOException {
        ChunkedUploadSession session = activeSessions.get(uploadId);
        if (session == null) {
            throw new IllegalStateException("Upload session not found: " + uploadId);
        }

        if (session.isExpired()) {
            session.setStatus(UploadStatus.EXPIRED);
            throw new IllegalStateException("Upload session has expired: " + uploadId);
        }

        if (session.getStatus() != UploadStatus.UPLOADING) {
            throw new IllegalStateException("Upload session is not in uploading state: " + session.getStatus());
        }

        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new IllegalArgumentException("Invalid chunk index: " + chunkIndex +
                    " (expected 0-" + (session.getTotalChunks() - 1) + ")");
        }

        // Write chunk to disk
        Path chunkPath = Paths.get(session.getTempDirectoryPath(), "chunk_" + chunkIndex);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(chunkPath))) {
            chunkData.transferTo(out);
        }

        // Mark chunk as received
        session.markChunkReceived(chunkIndex);

        log.debug("Saved chunk {} for upload {}, progress: {}/{}",
                chunkIndex, uploadId, session.getReceivedChunkCount(), session.getTotalChunks());
    }

    /**
     * Assemble all chunks into a final file
     * Returns the path to the assembled file
     * Chunks are only deleted after successful assembly to allow recovery on failure
     */
    public Path assembleFile(UUID uploadId) throws IOException {
        ChunkedUploadSession session = activeSessions.get(uploadId);
        if (session == null) {
            throw new IllegalStateException("Upload session not found: " + uploadId);
        }

        if (!session.isComplete()) {
            throw new IllegalStateException("Upload is not complete. Received " +
                    session.getReceivedChunkCount() + "/" + session.getTotalChunks() + " chunks");
        }

        session.setStatus(UploadStatus.ASSEMBLING);
        log.info("Assembling {} chunks for upload {}", session.getTotalChunks(), uploadId);

        // Create the final file
        String extension = getFileExtension(session.getFileName());
        Path assembledFile = Paths.get(session.getTempDirectoryPath(), "assembled" + extension);

        // First pass: verify all chunks exist before starting assembly
        List<Path> chunkPaths = new ArrayList<>();
        for (int i = 0; i < session.getTotalChunks(); i++) {
            Path chunkPath = Paths.get(session.getTempDirectoryPath(), "chunk_" + i);
            if (!Files.exists(chunkPath)) {
                session.setStatus(UploadStatus.FAILED);
                throw new IOException("Chunk file missing: " + chunkPath);
            }
            chunkPaths.add(chunkPath);
        }

        // Second pass: assemble all chunks (without deleting)
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(assembledFile))) {
            for (int i = 0; i < chunkPaths.size(); i++) {
                Path chunkPath = chunkPaths.get(i);
                try (InputStream in = new BufferedInputStream(Files.newInputStream(chunkPath))) {
                    in.transferTo(out);
                }
                log.debug("Concatenated chunk {} for upload {}", i, uploadId);
            }
        }

        // Third pass: delete chunks only after successful assembly
        for (int i = 0; i < chunkPaths.size(); i++) {
            try {
                Files.delete(chunkPaths.get(i));
                log.debug("Deleted chunk {} for upload {}", i, uploadId);
            } catch (IOException e) {
                // Log but don't fail - assembly was successful, cleanup is best-effort
                log.warn("Failed to delete chunk {} for upload {}: {}", i, uploadId, e.getMessage());
            }
        }

        session.setStatus(UploadStatus.COMPLETED);
        log.info("Successfully assembled file for upload {}: {} ({} bytes)",
                uploadId, assembledFile, Files.size(assembledFile));

        return assembledFile;
    }

    /**
     * Get the current status of an upload session
     */
    public Optional<ChunkedUploadSession> getUploadStatus(UUID uploadId) {
        return Optional.ofNullable(activeSessions.get(uploadId));
    }

    /**
     * Get the current status of an upload session for a specific user
     */
    public Optional<ChunkedUploadSession> getUploadStatus(UUID uploadId, UUID userId) {
        ChunkedUploadSession session = activeSessions.get(uploadId);
        if (session == null || !session.getUserId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Abort an upload and cleanup temp files
     */
    public boolean abortUpload(UUID uploadId, UUID userId) {
        ChunkedUploadSession session = activeSessions.get(uploadId);
        if (session == null || !session.getUserId().equals(userId)) {
            return false;
        }

        return cleanupSession(session);
    }

    /**
     * Check if user has an active chunked upload session
     */
    public boolean hasActiveUpload(UUID userId) {
        return activeSessions.values().stream()
                .anyMatch(session -> session.getUserId().equals(userId) &&
                        session.getStatus() == UploadStatus.UPLOADING);
    }

    /**
     * Cleanup a session and its temp directory
     */
    private boolean cleanupSession(ChunkedUploadSession session) {
        try {
            // Remove from active sessions
            activeSessions.remove(session.getUploadId());

            // Delete temp directory
            Path sessionDir = Paths.get(session.getTempDirectoryPath());
            if (Files.exists(sessionDir)) {
                try (Stream<Path> files = Files.list(sessionDir)) {
                    files.forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            log.warn("Failed to delete chunk file: {}", file, e);
                        }
                    });
                }
                Files.delete(sessionDir);
            }

            log.info("Cleaned up upload session: {}", session.getUploadId());
            return true;

        } catch (IOException e) {
            log.error("Failed to cleanup upload session: {}", session.getUploadId(), e);
            return false;
        }
    }

    /**
     * Cleanup orphaned directories from previous runs
     */
    private void cleanupOrphanedDirectories() {
        try {
            Path tempDir = Paths.get(chunksDirectory);
            if (!Files.exists(tempDir)) {
                return;
            }

            try (Stream<Path> directories = Files.list(tempDir)) {
                long cleanedCount = directories
                        .filter(Files::isDirectory)
                        .peek(dir -> {
                            try {
                                // Delete contents first
                                try (Stream<Path> files = Files.list(dir)) {
                                    files.forEach(file -> {
                                        try {
                                            Files.delete(file);
                                        } catch (IOException e) {
                                            log.warn("Failed to delete orphaned file: {}", file, e);
                                        }
                                    });
                                }
                                // Delete directory
                                Files.delete(dir);
                                log.info("Cleaned up orphaned upload directory: {}", dir.getFileName());
                            } catch (IOException e) {
                                log.warn("Failed to cleanup orphaned directory: {}", dir, e);
                            }
                        })
                        .count();

                if (cleanedCount > 0) {
                    log.info("Cleaned up {} orphaned upload directories from previous run", cleanedCount);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup orphaned upload directories", e);
        }
    }

    /**
     * Scheduled cleanup of expired upload sessions
     */
    @Scheduled(every = "${geopulse.import.upload-cleanup-minutes}m")
    void cleanupExpiredSessions() {
        List<ChunkedUploadSession> expiredSessions = activeSessions.values().stream()
                .filter(ChunkedUploadSession::isExpired)
                .toList();

        for (ChunkedUploadSession session : expiredSessions) {
            log.info("Cleaning up expired upload session: uploadId={}, userId={}, fileName={}",
                    session.getUploadId(), session.getUserId(), session.getFileName());
            session.setStatus(UploadStatus.EXPIRED);
            cleanupSession(session);
        }

        if (!expiredSessions.isEmpty()) {
            log.info("Cleaned up {} expired upload sessions", expiredSessions.size());
        }
    }

    /**
     * Get chunk size in bytes
     */
    public long getChunkSizeBytes() {
        return settingsService.getInteger("import.chunk-size-mb") * 1024L * 1024L;
    }

    /**
     * Get maximum file size in bytes
     */
    public long getMaxFileSizeBytes() {
        return settingsService.getInteger("import.max-file-size-gb") * 1024L * 1024L * 1024L;
    }

    /**
     * Get session timeout in hours
     */
    public int getSessionTimeoutHours() {
        return settingsService.getInteger("import.upload-timeout-hours");
    }


    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return ".dat";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return ".dat";
    }
}
