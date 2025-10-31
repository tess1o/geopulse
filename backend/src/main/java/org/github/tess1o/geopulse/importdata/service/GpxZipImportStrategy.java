package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import strategy for ZIP files containing multiple GPX files.
 *
 * This strategy:
 * - Streams through ZIP file without loading everything into memory
 * - Processes each GPX file one at a time
 * - Accumulates all GPS points from all files
 * - Triggers timeline regeneration only once after all files are imported
 * - Provides progress updates per-file
 */
@ApplicationScoped
@Slf4j
public class GpxZipImportStrategy extends BaseGpsImportStrategy {

    @Inject
    GpxParserService gpxParserService;

    @Override
    public String getFormat() {
        return "gpx-zip";
    }

    @Override
    protected FormatValidationResult validateFormatSpecificData(ImportJob job) throws IOException {
        log.info("Validating GPX ZIP file for user {}", job.getUserId());

        int totalGpxFiles = 0;
        int totalValidPoints = 0;
        Instant firstTimestamp = null;
        Instant lastTimestamp = null;

        // Stream through ZIP file to count GPX files and validate
        try (InputStream inputStream = job.getDataStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Skip directories and non-GPX files
                if (entry.isDirectory() || !isGpxFile(entry.getName())) {
                    log.debug("Skipping non-GPX entry: {}", entry.getName());
                    zipInputStream.closeEntry();
                    continue;
                }

                log.debug("Validating GPX file: {}", entry.getName());
                totalGpxFiles++;

                // Read GPX content from zip entry (without decompressing entire ZIP)
                String gpxContent = readZipEntryAsString(zipInputStream);

                // Validate this GPX file
                try {
                    GpxParserService.ValidationResult validationResult = gpxParserService.validateGpx(gpxContent);
                    totalValidPoints += validationResult.getValidRecordCount();

                    // Track timestamp range across all files
                    if (validationResult.getFirstTimestamp() != null) {
                        if (firstTimestamp == null || validationResult.getFirstTimestamp().isBefore(firstTimestamp)) {
                            firstTimestamp = validationResult.getFirstTimestamp();
                        }
                    }
                    if (validationResult.getLastTimestamp() != null) {
                        if (lastTimestamp == null || validationResult.getLastTimestamp().isAfter(lastTimestamp)) {
                            lastTimestamp = validationResult.getLastTimestamp();
                        }
                    }

                    log.debug("Validated GPX file {}: {} valid points", entry.getName(),
                            validationResult.getValidRecordCount());

                } catch (Exception e) {
                    log.warn("Failed to validate GPX file {}: {}", entry.getName(), e.getMessage());
                    // Continue with other files - don't fail entire import for one bad file
                }

                zipInputStream.closeEntry();
            }
        }

        if (totalGpxFiles == 0) {
            throw new IllegalArgumentException("ZIP file contains no valid GPX files");
        }

        if (totalValidPoints == 0) {
            throw new IllegalArgumentException("ZIP file contains no valid GPS data");
        }

        log.info("GPX ZIP validation successful: {} GPX files, {} total valid GPS points",
                totalGpxFiles, totalValidPoints);

        return new FormatValidationResult(totalValidPoints, totalValidPoints, firstTimestamp, lastTimestamp);
    }

    @Override
    protected List<GpsPointEntity> parseAndConvertToGpsEntities(ImportJob job, UserEntity user) throws IOException {
        log.info("Processing GPX ZIP file for user {}", job.getUserId());

        List<GpsPointEntity> allGpsPoints = new ArrayList<>();
        int processedFiles = 0;
        int totalFiles = 0;

        // First pass: count total GPX files for progress tracking
        try (InputStream inputStream = job.getDataStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && isGpxFile(entry.getName())) {
                    totalFiles++;
                }
                zipInputStream.closeEntry();
            }
        }

        log.info("Found {} GPX files in ZIP", totalFiles);
        job.updateProgress(25, String.format("Processing %d GPX files...", totalFiles));

        // Second pass: process each GPX file
        try (InputStream inputStream = job.getDataStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Skip directories and non-GPX files
                if (entry.isDirectory() || !isGpxFile(entry.getName())) {
                    zipInputStream.closeEntry();
                    continue;
                }

                processedFiles++;
                String fileName = entry.getName();
                log.debug("Processing GPX file {}/{}: {}", processedFiles, totalFiles, fileName);

                job.updateProgress(
                        25 + (int) ((double) processedFiles / totalFiles * 35), // Progress from 25% to 60%
                        String.format("Processing file %d of %d: %s", processedFiles, totalFiles, getShortFileName(fileName))
                );

                // Read and parse GPX file
                try {
                    String gpxContent = readZipEntryAsString(zipInputStream);
                    List<GpsPointEntity> gpsPoints = gpxParserService.parseGpxXmlToGpsPoints(gpxContent, user, job);
                    allGpsPoints.addAll(gpsPoints);

                    log.debug("Extracted {} GPS points from {}", gpsPoints.size(), fileName);

                } catch (Exception e) {
                    log.warn("Failed to process GPX file {}: {}", fileName, e.getMessage(), e);
                    // Continue with other files - don't fail entire import for one bad file
                }

                zipInputStream.closeEntry();
            }
        }

        log.info("GPX ZIP import completed: processed {} files, extracted {} GPS points",
                processedFiles, allGpsPoints.size());

        return allGpsPoints;
    }

    /**
     * Check if a ZIP entry is a GPX file based on extension
     */
    private boolean isGpxFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".gpx");
    }

    /**
     * Read a ZIP entry as a string (for GPX XML content)
     */
    private String readZipEntryAsString(ZipInputStream zipInputStream) throws IOException {
        byte[] buffer = new byte[8192];
        StringBuilder content = new StringBuilder();
        int bytesRead;

        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
            content.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }

        return content.toString();
    }

    /**
     * Get short file name for display (last component of path)
     */
    private String getShortFileName(String fullPath) {
        if (fullPath == null) {
            return "unknown";
        }
        int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }
}
