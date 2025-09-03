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
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Import strategy for OwnTracks JSON format
 */
@ApplicationScoped
@Slf4j
public class OwnTracksImportStrategy extends BaseGpsImportStrategy {
    
    @Inject
    GpsPointMapper gpsPointMapper;
    
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public String getFormat() {
        return "owntracks";
    }
    
    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
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
        
        return new FormatValidationResult(messages.length, validMessages);
    }
    
    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        String jsonContent = new String(job.getZipData());
        OwnTracksLocationMessage[] messages = objectMapper.readValue(jsonContent, OwnTracksLocationMessage[].class);
        
        return convertMessagesToGpsPoints(messages, user, job);
    }
    
    private List<GpsPointEntity> convertMessagesToGpsPoints(OwnTracksLocationMessage[] messages, UserEntity user, ImportJob job) {
        List<GpsPointEntity> gpsPoints = new ArrayList<>();
        int processedMessages = 0;
        
        for (OwnTracksLocationMessage message : messages) {
            // Skip invalid messages
            if (!isValidGpsMessage(message)) {
                continue;
            }
            
            // Apply date range filter using base class method
            Instant messageTime = Instant.ofEpochSecond(message.getTst());
            if (shouldSkipDueDateFilter(messageTime, job)) {
                continue;
            }
            
            try {
                String deviceId = message.getTid() != null ? message.getTid() : "owntracks-import";
                GpsPointEntity gpsPoint = gpsPointMapper.toEntity(message, user, deviceId, GpsSourceType.OWNTRACKS);
                gpsPoints.add(gpsPoint);
            } catch (Exception e) {
                log.warn("Failed to create GPS point from message: {}", e.getMessage());
            }
            
            // Update progress using base class method
            processedMessages++;
            updateProgress(processedMessages, messages.length, job, 10, 80);
        }
        
        return gpsPoints;
    }
    
    private boolean isValidGpsMessage(OwnTracksLocationMessage message) {
        return message.getLat() != null && 
               message.getLon() != null && 
               message.getTst() != null;
    }
}