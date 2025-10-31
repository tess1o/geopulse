package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.util.List;

/**
 * Import strategy for GPX (GPS Exchange Format) files
 */
@ApplicationScoped
@Slf4j
public class GpxImportStrategy extends BaseGpsImportStrategy {

    @Inject
    GpxParserService gpxParserService;

    @Override
    public String getFormat() {
        return "gpx";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        String xmlContent = new String(job.getZipData()); // zipData contains GPX XML for GPX format

        // Validate GPX using parser service
        GpxParserService.ValidationResult validationResult = gpxParserService.validateGpx(xmlContent);

        log.info("GPX validation successful: {} total valid GPS points",
                validationResult.getValidRecordCount());

        return new FormatValidationResult(
                validationResult.getTotalRecordCount(),
                validationResult.getValidRecordCount(),
                validationResult.getFirstTimestamp(),
                validationResult.getLastTimestamp()
        );
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        String xmlContent = new String(job.getZipData());

        // Use parser service to convert GPX to GPS entities
        return gpxParserService.parseGpxXmlToGpsPoints(xmlContent, user, job);
    }
}