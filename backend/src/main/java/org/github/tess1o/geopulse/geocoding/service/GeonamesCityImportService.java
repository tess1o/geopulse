package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.github.tess1o.geopulse.geocoding.model.GeonamesCityRecord;
import org.github.tess1o.geopulse.geocoding.repository.GeonamesCityRepository;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApplicationScoped
@Slf4j
public class GeonamesCityImportService {

    private static final int INPUT_BUFFER_SIZE = 64 * 1024;

    @ConfigProperty(name = "geopulse.geonames.import.enabled", defaultValue = "true")
    boolean importEnabled;

    @ConfigProperty(name = "geopulse.geonames.import.url", defaultValue = "https://download.geonames.org/export/dump/cities500.zip")
    String importUrl;

    @ConfigProperty(name = "geopulse.geonames.import.batch-size", defaultValue = "1000")
    int batchSize;

    @ConfigProperty(name = "geopulse.geonames.import.min-row-threshold", defaultValue = "100000")
    long minimumRowThreshold;

    @ConfigProperty(name = "geopulse.geonames.import.force-refresh", defaultValue = "false")
    boolean forceRefresh;

    @ConfigProperty(name = "geopulse.geonames.import.connect-timeout-seconds", defaultValue = "20")
    int connectTimeoutSeconds;

    @ConfigProperty(name = "geopulse.geonames.import.read-timeout-seconds", defaultValue = "300")
    int readTimeoutSeconds;

    private final GeonamesCityRepository geonamesCityRepository;
    private final GeonamesCityLineParser lineParser;
    private final ManagedExecutor managedExecutor;
    private final AtomicBoolean importInProgress = new AtomicBoolean(false);

    @Inject
    public GeonamesCityImportService(
            GeonamesCityRepository geonamesCityRepository,
            GeonamesCityLineParser lineParser,
            ManagedExecutor managedExecutor
    ) {
        this.geonamesCityRepository = geonamesCityRepository;
        this.lineParser = lineParser;
        this.managedExecutor = managedExecutor;
    }

    void onStart(@Observes StartupEvent ignored) {
        if (!importEnabled) {
            log.info("GeoNames city import is disabled");
            return;
        }

        managedExecutor.runAsync(this::importIfNeededOnStartup)
                .exceptionally(throwable -> {
                    log.error("GeoNames startup import failed: {}", throwable.getMessage(), throwable);
                    return null;
                });
    }

    void importIfNeededOnStartup() {
        if (!importInProgress.compareAndSet(false, true)) {
            log.info("GeoNames import is already running, skipping duplicate startup trigger");
            return;
        }

        try {
            long existingRows = geonamesCityRepository.countCities();
            if (!forceRefresh && existingRows >= minimumRowThreshold) {
                log.info("GeoNames city table already populated ({} rows), skipping startup import", existingRows);
                return;
            }

            if (forceRefresh && existingRows > 0) {
                log.info("Force refresh is enabled, reimporting GeoNames city table ({} rows)", existingRows);
            } else if (existingRows > 0) {
                log.warn("GeoNames city table has only {} rows (< threshold {}), reimporting with staging swap",
                        existingRows, minimumRowThreshold);
            }

            importFromRemoteArchive();
        } catch (Exception e) {
            log.error("Failed to import GeoNames cities on startup: {}", e.getMessage(), e);
        } finally {
            importInProgress.set(false);
        }
    }

    private void importFromRemoteArchive() throws IOException {
        URLConnection connection = URI.create(importUrl).toURL().openConnection();
        connection.setConnectTimeout(Math.max(1, connectTimeoutSeconds) * 1000);
        connection.setReadTimeout(Math.max(1, readTimeoutSeconds) * 1000);
        connection.setRequestProperty("User-Agent", "GeoPulse/GeoNames-Importer");

        if (connection instanceof HttpURLConnection httpConnection) {
            int statusCode = httpConnection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("GeoNames download failed with status " + statusCode + " from " + importUrl);
            }
        }

        log.info("Downloading GeoNames cities archive from {}", importUrl);
        try (InputStream responseStream = connection.getInputStream()) {
            importFromZipStream(responseStream);
        }
    }

    void importFromZipStream(InputStream zipStream) throws IOException {
        int safeBatchSize = Math.max(100, batchSize);
        long processedLines = 0;
        long skippedLines = 0;
        long stagedRows = 0;
        List<GeonamesCityRecord> batch = new ArrayList<>(safeBatchSize);

        geonamesCityRepository.prepareStagingTable();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(zipStream, INPUT_BUFFER_SIZE))) {
            ZipEntry targetEntry = findFirstTxtEntry(zis);
            if (targetEntry == null) {
                throw new IOException("No .txt file found inside GeoNames archive");
            }

            log.info("Importing GeoNames cities from zip entry '{}'", targetEntry.getName());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8), INPUT_BUFFER_SIZE)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processedLines++;
                    var parsed = lineParser.parseLine(line);
                    if (parsed.isEmpty()) {
                        skippedLines++;
                        continue;
                    }

                    batch.add(parsed.get());
                    if (batch.size() >= safeBatchSize) {
                        stagedRows += geonamesCityRepository.upsertBatchToStaging(batch);
                        batch.clear();
                    }

                    if (processedLines % 50000 == 0) {
                        log.info("GeoNames staging progress: processed={} staged={} skipped={}",
                                processedLines, stagedRows, skippedLines);
                    }
                }
            }
        }

        if (!batch.isEmpty()) {
            stagedRows += geonamesCityRepository.upsertBatchToStaging(batch);
            batch.clear();
        }

        long totalStagedRows = geonamesCityRepository.countStagingCities();
        if (totalStagedRows < minimumRowThreshold) {
            throw new IOException("GeoNames staged row count is below threshold: staged="
                    + totalStagedRows + ", threshold=" + minimumRowThreshold);
        }

        geonamesCityRepository.replaceMainFromStagingAtomic();

        long totalRows = geonamesCityRepository.countCities();
        log.info("GeoNames import finished: processed={} staged={} skipped={} stagedRows={} totalTableRows={}",
                processedLines, stagedRows, skippedLines, totalStagedRows, totalRows);
    }

    private ZipEntry findFirstTxtEntry(ZipInputStream zis) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".txt")) {
                return entry;
            }
            zis.closeEntry();
        }
        return null;
    }
}
