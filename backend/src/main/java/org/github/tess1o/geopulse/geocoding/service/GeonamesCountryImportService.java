package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.github.tess1o.geopulse.geocoding.model.GeonamesCountryRecord;
import org.github.tess1o.geopulse.geocoding.repository.GeonamesCountryRepository;

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

@ApplicationScoped
@Slf4j
public class GeonamesCountryImportService {

    private static final int INPUT_BUFFER_SIZE = 16 * 1024;

    @ConfigProperty(name = "geopulse.geonames.country-import.enabled", defaultValue = "true")
    boolean importEnabled;

    @ConfigProperty(name = "geopulse.geonames.country-import.url", defaultValue = "https://download.geonames.org/export/dump/countryInfo.txt")
    String importUrl;

    @ConfigProperty(name = "geopulse.geonames.country-import.batch-size", defaultValue = "200")
    int batchSize;

    @ConfigProperty(name = "geopulse.geonames.country-import.min-row-threshold", defaultValue = "200")
    long minimumRowThreshold;

    @ConfigProperty(name = "geopulse.geonames.country-import.force-refresh", defaultValue = "false")
    boolean forceRefresh;

    @ConfigProperty(name = "geopulse.geonames.country-import.connect-timeout-seconds", defaultValue = "20")
    int connectTimeoutSeconds;

    @ConfigProperty(name = "geopulse.geonames.country-import.read-timeout-seconds", defaultValue = "120")
    int readTimeoutSeconds;

    private final GeonamesCountryRepository geonamesCountryRepository;
    private final GeonamesCountryLineParser lineParser;
    private final ManagedExecutor managedExecutor;
    private final AtomicBoolean importInProgress = new AtomicBoolean(false);

    @Inject
    public GeonamesCountryImportService(
            GeonamesCountryRepository geonamesCountryRepository,
            GeonamesCountryLineParser lineParser,
            ManagedExecutor managedExecutor
    ) {
        this.geonamesCountryRepository = geonamesCountryRepository;
        this.lineParser = lineParser;
        this.managedExecutor = managedExecutor;
    }

    void onStart(@Observes StartupEvent ignored) {
        if (!importEnabled) {
            log.info("GeoNames country import is disabled");
            return;
        }

        managedExecutor.runAsync(this::importIfNeededOnStartup)
                .exceptionally(throwable -> {
                    log.error("GeoNames country startup import failed: {}", throwable.getMessage(), throwable);
                    return null;
                });
    }

    void importIfNeededOnStartup() {
        if (!importInProgress.compareAndSet(false, true)) {
            log.info("GeoNames country import is already running, skipping duplicate startup trigger");
            return;
        }

        try {
            long existingRows = geonamesCountryRepository.countCountries();
            if (!forceRefresh && existingRows >= minimumRowThreshold) {
                log.info("GeoNames country table already populated ({} rows), skipping startup import", existingRows);
                return;
            }

            if (forceRefresh && existingRows > 0) {
                log.info("Country force refresh is enabled, reimporting geonames_country ({} rows)", existingRows);
            } else if (existingRows > 0) {
                log.warn("GeoNames country table has only {} rows (< threshold {}), reimporting with staging swap",
                        existingRows, minimumRowThreshold);
            }

            importFromRemoteFile();
        } catch (Exception e) {
            log.error("Failed to import GeoNames countries on startup: {}", e.getMessage(), e);
        } finally {
            importInProgress.set(false);
        }
    }

    private void importFromRemoteFile() throws IOException {
        URLConnection connection = URI.create(importUrl).toURL().openConnection();
        connection.setConnectTimeout(Math.max(1, connectTimeoutSeconds) * 1000);
        connection.setReadTimeout(Math.max(1, readTimeoutSeconds) * 1000);
        connection.setRequestProperty("User-Agent", "GeoPulse/GeoNames-Country-Importer");

        if (connection instanceof HttpURLConnection httpConnection) {
            int statusCode = httpConnection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("GeoNames country download failed with status " + statusCode + " from " + importUrl);
            }
        }

        log.info("Downloading GeoNames country file from {}", importUrl);
        try (InputStream responseStream = connection.getInputStream()) {
            importFromStream(responseStream);
        }
    }

    void importFromStream(InputStream stream) throws IOException {
        int safeBatchSize = Math.max(50, batchSize);
        long processedLines = 0;
        long skippedLines = 0;
        long stagedRows = 0;
        List<GeonamesCountryRecord> batch = new ArrayList<>(safeBatchSize);

        geonamesCountryRepository.prepareStagingTable();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8), INPUT_BUFFER_SIZE)) {
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
                    stagedRows += geonamesCountryRepository.upsertBatchToStaging(batch);
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            stagedRows += geonamesCountryRepository.upsertBatchToStaging(batch);
            batch.clear();
        }

        long totalStagedRows = geonamesCountryRepository.countStagingCountries();
        if (totalStagedRows < minimumRowThreshold) {
            throw new IOException("GeoNames country staged row count is below threshold: staged="
                    + totalStagedRows + ", threshold=" + minimumRowThreshold);
        }

        geonamesCountryRepository.replaceMainFromStagingAtomic();

        long totalRows = geonamesCountryRepository.countCountries();
        log.info("GeoNames country import finished: processed={} staged={} skipped={} stagedRows={} totalTableRows={}",
                processedLines, stagedRows, skippedLines, totalStagedRows, totalRows);
    }
}
