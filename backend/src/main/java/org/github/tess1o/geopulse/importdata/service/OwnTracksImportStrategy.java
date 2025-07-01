package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Import strategy for OwnTracks JSON format
 */
@ApplicationScoped
@Slf4j
public class OwnTracksImportStrategy implements ImportStrategy {
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    GpsPointMapper gpsPointMapper;
    
    @Inject
    BatchProcessor batchProcessor;
    
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
    
    @Override
    public String getFormat() {
        return "owntracks";
    }
    
    @Override
    public List<String> validateAndDetectDataTypes(ImportJob job) throws IOException {
        log.info("Validating OwnTracks JSON data for user {}", job.getUserId());
        
        try {
            String jsonContent = new String(job.getZipData()); // zipData contains JSON for OwnTracks
            
            // Parse as JSON array of OwnTracks messages
            OwnTracksLocationMessage[] messages = objectMapper.readValue(jsonContent, OwnTracksLocationMessage[].class);
            
            if (messages.length == 0) {
                throw new IllegalArgumentException("OwnTracks file contains no location data");
            }
            
            // Validate GPS data quality
            int validMessages = 0;
            for (OwnTracksLocationMessage message : messages) {
                if (isValidGpsMessage(message)) {
                    validMessages++;
                }
            }
            
            if (validMessages == 0) {
                throw new IllegalArgumentException("OwnTracks file contains no valid GPS coordinates");
            }
            
            log.info("OwnTracks validation successful: {} total messages, {} valid GPS points", 
                    messages.length, validMessages);
            
            return List.of(ExportImportConstants.DataTypes.RAW_GPS);
            
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("Invalid OwnTracks JSON format: " + e.getMessage());
        }
    }
    
    @Override
    public void processImportData(ImportJob job) throws IOException {
        log.info("Processing OwnTracks import data for user {}", job.getUserId());
        
        try {
            String jsonContent = new String(job.getZipData());
            OwnTracksLocationMessage[] messages = objectMapper.readValue(jsonContent, OwnTracksLocationMessage[].class);
            
            UserEntity user = userRepository.findById(job.getUserId());
            if (user == null) {
                throw new IllegalStateException("User not found: " + job.getUserId());
            }
            
            // Convert messages to GPS entities
            List<GpsPointEntity> gpsPoints = convertMessagesToGpsPoints(messages, user, job);
            
            // Process in batches to avoid memory issues and timeouts
            int batchSize = 500; // Optimized batch size for large datasets
            BatchProcessor.BatchResult result = batchProcessor.processInBatches(gpsPoints, batchSize);
            
            job.setProgress(100);
            
            log.info("OwnTracks import completed for user {}: {} imported, {} skipped from {} total messages", 
                    job.getUserId(), result.imported, result.skipped, messages.length);

        } catch (Exception e) {
            log.error("Failed to process OwnTracks import for user {}: {}", job.getUserId(), e.getMessage(), e);
            throw new IOException("Failed to process OwnTracks import: " + e.getMessage(), e);
        }
    }
    
    private List<GpsPointEntity> convertMessagesToGpsPoints(OwnTracksLocationMessage[] messages, UserEntity user, ImportJob job) {
        List<GpsPointEntity> gpsPoints = new ArrayList<>();
        int processedMessages = 0;
        
        for (OwnTracksLocationMessage message : messages) {
            // Skip invalid messages
            if (!isValidGpsMessage(message)) {
                continue;
            }
            
            // Apply date range filter if specified
            if (job.getOptions().getDateRangeFilter() != null) {
                Instant messageTime = Instant.ofEpochSecond(message.getTst());
                if (messageTime.isBefore(job.getOptions().getDateRangeFilter().getStartDate()) ||
                    messageTime.isAfter(job.getOptions().getDateRangeFilter().getEndDate())) {
                    continue;
                }
            }
            
            try {
                String deviceId = message.getTid() != null ? message.getTid() : "owntracks-import";
                GpsPointEntity gpsPoint = gpsPointMapper.toEntity(message, user, deviceId, GpsSourceType.OWNTRACKS);
                gpsPoints.add(gpsPoint);
            } catch (Exception e) {
                log.warn("Failed to create GPS point from message: {}", e.getMessage());
            }
            
            // Update progress periodically
            processedMessages++;
            if (processedMessages % 1000 == 0) {
                int progress = 10 + (int) ((double) processedMessages / messages.length * 80);
                job.setProgress(Math.min(progress, 90));
            }
        }
        
        return gpsPoints;
    }
    
    private boolean isValidGpsMessage(OwnTracksLocationMessage message) {
        return message.getLat() != null && 
               message.getLon() != null && 
               message.getTst() != null;
    }
}