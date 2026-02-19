package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.importdata.model.ImportFormat;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
@Startup
@Slf4j
public class DropFolderImportService {

    private static final String SETTING_ENABLED = "import.drop-folder.enabled";
    private static final String SETTING_PATH = "import.drop-folder.path";
    private static final String SETTING_POLL_INTERVAL_SECONDS = "import.drop-folder.poll-interval-seconds";
    private static final String SETTING_STABLE_AGE_SECONDS = "import.drop-folder.stable-age-seconds";
    private static final String SETTING_GEOPULSE_MAX_SIZE_MB = "import.drop-folder.geopulse-max-size-mb";

    private static final String FAILED_FOLDER_NAME = ".failed";
    private static final String ERROR_FILE_SUFFIX = ".error.json";

    @Inject
    SystemSettingsService settingsService;

    @Inject
    ImportJobService importJobService;

    @Inject
    ImportStrategyRegistry strategyRegistry;

    @Inject
    ImportTempFileService tempFileService;

    @Inject
    UserRepository userRepository;

    @Inject
    ObjectMapper objectMapper;

    private Path dropRoot;
    private boolean enabled;
    private int pollIntervalSeconds;
    private int stableAgeSeconds;
    private int geopulseMaxSizeMb;
    private Instant lastScan = Instant.EPOCH;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private final Map<UUID, TrackedDropImport> trackedJobs = new ConcurrentHashMap<>();
    private final Set<Path> claimedFiles = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void init() {
        refreshSettings(true);
    }

    private void refreshSettings(boolean logOnChange) {
        boolean nextEnabled = settingsService.getBoolean(SETTING_ENABLED);
        int nextPollIntervalSeconds = Math.max(1, settingsService.getInteger(SETTING_POLL_INTERVAL_SECONDS));
        int nextStableAgeSeconds = Math.max(1, settingsService.getInteger(SETTING_STABLE_AGE_SECONDS));
        int nextGeopulseMaxSizeMb = Math.max(1, settingsService.getInteger(SETTING_GEOPULSE_MAX_SIZE_MB));

        String path = settingsService.getString(SETTING_PATH);
        Path nextDropRoot = null;
        if (path != null && !path.isBlank()) {
            nextDropRoot = Paths.get(path);
        }

        boolean changed = nextEnabled != enabled
                || nextPollIntervalSeconds != pollIntervalSeconds
                || nextStableAgeSeconds != stableAgeSeconds
                || nextGeopulseMaxSizeMb != geopulseMaxSizeMb
                || !Objects.equals(nextDropRoot, dropRoot);

        enabled = nextEnabled;
        pollIntervalSeconds = nextPollIntervalSeconds;
        stableAgeSeconds = nextStableAgeSeconds;
        geopulseMaxSizeMb = nextGeopulseMaxSizeMb;
        dropRoot = nextDropRoot;

        if (!enabled) {
            if (logOnChange && changed) {
                log.info("Drop folder import is disabled");
            }
            return;
        }

        if (dropRoot == null) {
            if (logOnChange && changed) {
                log.warn("Drop folder path is not configured. Disabling drop folder import.");
            }
            enabled = false;
            return;
        }

        try {
            Files.createDirectories(dropRoot);
            if (!Files.isWritable(dropRoot)) {
                throw new IllegalStateException("Drop folder is not writable: " + dropRoot);
            }
            if (logOnChange && changed) {
                log.info("Drop folder import enabled: path={}, pollInterval={}s, stableAge={}s",
                        dropRoot, pollIntervalSeconds, stableAgeSeconds);
            }
        } catch (Exception e) {
            if (logOnChange && changed) {
                log.error("Failed to initialize drop folder {}: {}", dropRoot, e.getMessage(), e);
            }
            enabled = false;
        }
    }

    @Scheduled(every = "5s")
    @RunOnVirtualThread
    void scanDropFolder() {
        refreshSettings(false);
        handleCompletedJobs();
        if (!enabled || dropRoot == null) {
            return;
        }

        if (!processing.compareAndSet(false, true)) {
            return;
        }

        try {
            if (Duration.between(lastScan, Instant.now()).getSeconds() < pollIntervalSeconds) {
                return;
            }
            lastScan = Instant.now();

            scanUserDirectories();
        } finally {
            processing.set(false);
        }
    }

    private void scanUserDirectories() {
        if (!Files.exists(dropRoot)) {
            log.warn("Drop folder path does not exist: {}", dropRoot);
            return;
        }

        try (Stream<Path> dirs = Files.list(dropRoot)) {
            dirs.filter(Files::isDirectory)
                .filter(dir -> !dir.getFileName().toString().startsWith("."))
                .forEach(this::processUserDirectory);
        } catch (IOException e) {
            log.error("Failed to list drop folder {}: {}", dropRoot, e.getMessage(), e);
        }
    }

    private void processUserDirectory(Path userDir) {
        String folderName = userDir.getFileName().toString();
        Optional<UserEntity> userOpt = userRepository.findByEmailIgnoreCase(folderName);
        if (userOpt.isEmpty()) {
            log.warn("Drop folder user not found for directory: {}", userDir);
            return;
        }

        UUID userId = userOpt.get().getId();
        if (importJobService.hasActiveImportJob(userId)) {
            return;
        }

        List<Path> candidates = listCandidateFiles(userDir);
        if (candidates.isEmpty()) {
            return;
        }

        for (Path file : candidates) {
            if (processFile(userId, userDir, file)) {
                break; // Only one file per user per scan
            }
        }
    }

    private List<Path> listCandidateFiles(Path userDir) {
        try (Stream<Path> files = Files.list(userDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isCandidateFile)
                    .sorted(Comparator.comparing(this::getLastModifiedSafe))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list files in {}: {}", userDir, e.getMessage(), e);
            return List.of();
        }
    }

    private boolean isCandidateFile(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith(".")) {
            return false;
        }
        if (name.endsWith(ERROR_FILE_SUFFIX)) {
            return false;
        }
        return true;
    }

    private Instant getLastModifiedSafe(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private boolean processFile(UUID userId, Path userDir, Path file) {
        if (!isFileStable(file)) {
            return false;
        }

        if (!claimedFiles.add(file)) {
            return false;
        }

        boolean jobCreated = false;
        try {
            long fileSize = Files.size(file);
            if (fileSize == 0) {
                moveToFailed(userDir, file, "File is empty", null);
                return true;
            }

            if (importJobService.hasActiveImportJob(userId)) {
                return true;
            }

            DetectionResult detection = detectFormat(file, fileSize);
            if (detection == null || detection.format == null) {
                String errorMessage = detection != null ? detection.errorMessage : null;
                moveToFailed(userDir, file, errorMessage != null ? errorMessage : "Unsupported or invalid file format", null);
                return true;
            }

            ImportJob job = createImportJob(userId, file, fileSize, detection);
            trackedJobs.put(job.getJobId(), new TrackedDropImport(job, userDir, file));
            jobCreated = true;

            log.info("Created drop-folder import job {} for user {} (format={}, file={})",
                    job.getJobId(), userId, detection.format.getValue(), file.getFileName());

            return true;
        } catch (Exception e) {
            log.error("Failed to process drop file {}: {}", file, e.getMessage(), e);
            moveToFailed(userDir, file, e.getMessage(), e);
            return true;
        } finally {
            if (!jobCreated) {
                claimedFiles.remove(file);
            }
        }
    }

    private boolean isFileStable(Path file) {
        try {
            Instant lastModified = Files.getLastModifiedTime(file).toInstant();
            return lastModified.isBefore(Instant.now().minusSeconds(stableAgeSeconds));
        } catch (IOException e) {
            return false;
        }
    }

    private DetectionResult detectFormat(Path file, long fileSize) {
        String fileName = file.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ENGLISH);

        List<ImportFormat> candidates = candidatesForFileName(lowerName);
        if (candidates.isEmpty()) {
            return null;
        }

        ImportFormat hinted = hintFromName(lowerName, candidates);
        if (fileSize > (long) geopulseMaxSizeMb * 1024L * 1024L
                && candidates.contains(ImportFormat.GEOPULSE)
                && (hinted == ImportFormat.GEOPULSE || isGeopulseZip(file))) {
            return DetectionResult.error("GeoPulse ZIP exceeds max size of " + geopulseMaxSizeMb + " MB");
        }

        if (hinted != null) {
            DetectionResult hintedResult = tryDetectWithFormat(file, fileName, fileSize, hinted);
            if (hintedResult != null) {
                return hintedResult;
            }
            candidates.remove(hinted);
        }

        for (ImportFormat candidate : candidates) {
            DetectionResult result = tryDetectWithFormat(file, fileName, fileSize, candidate);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private DetectionResult tryDetectWithFormat(Path file, String fileName, long fileSize, ImportFormat format) {
        if (!format.isValidExtension(fileName)) {
            return null;
        }

        if (format == ImportFormat.GEOPULSE && fileSize > (long) geopulseMaxSizeMb * 1024L * 1024L) {
            log.warn("GeoPulse drop import file exceeds size limit ({} MB): {}", geopulseMaxSizeMb, fileName);
            return null;
        }

        try {
            ImportStrategy strategy = strategyRegistry.getStrategy(format.getValue());
            ImportJob probeJob = buildProbeJob(format, file, fileName, fileSize);
            strategy.validateAndDetectDataTypes(probeJob);
            return new DetectionResult(format, probeJob.getFileData());
        } catch (Exception e) {
            log.debug("Format detection failed for {} with {}: {}", fileName, format.getValue(), e.getMessage());
            return null;
        }
    }

    private ImportJob buildProbeJob(ImportFormat format, Path file, String fileName, long fileSize) throws IOException {
        ImportOptions options = new ImportOptions();
        options.setImportFormat(format.getValue());

        ImportJob job;
        if (format == ImportFormat.GEOPULSE) {
            byte[] data = Files.readAllBytes(file);
            job = new ImportJob(UUID.randomUUID(), options, fileName, data);
        } else {
            job = new ImportJob(UUID.randomUUID(), options, fileName, new byte[0]);
            job.setTempFilePath(file.toString());
        }

        job.setFileSizeBytes(fileSize);
        return job;
    }

    private ImportJob createImportJob(UUID userId, Path file, long fileSize, DetectionResult detection) throws IOException {
        ImportOptions options = new ImportOptions();
        options.setImportFormat(detection.format.getValue());
        options.setClearDataBeforeImport(false);

        if (detection.format == ImportFormat.GEOPULSE) {
            options.setDataTypes(List.of(
                    ExportImportConstants.DataTypes.RAW_GPS,
                    ExportImportConstants.DataTypes.FAVORITES,
                    ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                    ExportImportConstants.DataTypes.LOCATION_SOURCES,
                    ExportImportConstants.DataTypes.USER_INFO
            ));
        }

        ImportJob job;
        if (detection.format == ImportFormat.GEOPULSE) {
            byte[] data = detection.fileData != null ? detection.fileData : Files.readAllBytes(file);
            job = new ImportJob(userId, options, file.getFileName().toString(), data);
            job.setFileSizeBytes(fileSize);
        } else if (tempFileService.shouldUseTempFile(fileSize)) {
            job = new ImportJob(userId, options, file.getFileName().toString(), new byte[0]);
            job.setFileSizeBytes(fileSize);

            String tempFilePath = tempFileService.copyFileToTemp(file, job.getJobId(), file.getFileName().toString());
            job.setTempFilePath(tempFilePath);
        } else {
            byte[] data = Files.readAllBytes(file);
            job = new ImportJob(userId, options, file.getFileName().toString(), data);
            job.setFileSizeBytes(fileSize);
        }

        importJobService.registerJob(job);
        return job;
    }

    private void handleCompletedJobs() {
        Iterator<Map.Entry<UUID, TrackedDropImport>> iterator = trackedJobs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedDropImport> entry = iterator.next();
            TrackedDropImport tracked = entry.getValue();
            ImportJob job = tracked.job;

            if (job == null) {
                iterator.remove();
                claimedFiles.remove(tracked.filePath);
                continue;
            }

            if (job.getStatus() == ImportStatus.COMPLETED) {
                cleanupTempFile(job);
                deleteOriginalFile(tracked.filePath);
                iterator.remove();
                claimedFiles.remove(tracked.filePath);
            } else if (job.getStatus() == ImportStatus.FAILED) {
                cleanupTempFile(job);
                moveToFailed(tracked.userDir, tracked.filePath, job.getError(), null);
                iterator.remove();
                claimedFiles.remove(tracked.filePath);
            }
        }
    }

    private void cleanupTempFile(ImportJob job) {
        if (job != null && job.hasTempFile()) {
            tempFileService.cleanupTempFile(job);
        }
    }

    private void deleteOriginalFile(Path file) {
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                log.info("Deleted drop import file {}", file);
            }
        } catch (IOException e) {
            log.warn("Failed to delete drop import file {}: {}", file, e.getMessage());
        }
    }

    private void moveToFailed(Path userDir, Path file, String message, Exception exception) {
        try {
            Path failedDir = userDir.resolve(FAILED_FOLDER_NAME);
            Files.createDirectories(failedDir);

            Path target = resolveUniqueTarget(failedDir, file.getFileName().toString());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);

            writeErrorFile(failedDir, target.getFileName().toString(), message, exception);
            log.warn("Moved drop import file to failed: {} -> {}", file, target);
        } catch (IOException e) {
            log.error("Failed to move file {} to failed folder: {}", file, e.getMessage(), e);
        }
    }

    private Path resolveUniqueTarget(Path failedDir, String fileName) {
        Path target = failedDir.resolve(fileName);
        if (!Files.exists(target)) {
            return target;
        }
        String baseName = fileName;
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            baseName = fileName.substring(0, lastDot);
            extension = fileName.substring(lastDot);
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        return failedDir.resolve(baseName + "-" + timestamp + extension);
    }

    private void writeErrorFile(Path failedDir, String fileName, String message, Exception exception) {
        Map<String, Object> errorInfo = new LinkedHashMap<>();
        errorInfo.put("fileName", fileName);
        errorInfo.put("timestamp", Instant.now().toString());
        errorInfo.put("error", message != null ? message : "Unknown error");

        if (exception != null) {
            errorInfo.put("exception", exception.getClass().getName());
        }

        Path errorFile = failedDir.resolve(fileName + ERROR_FILE_SUFFIX);
        try {
            objectMapper.writeValue(errorFile.toFile(), errorInfo);
        } catch (IOException e) {
            log.warn("Failed to write error file {}: {}", errorFile, e.getMessage());
        }
    }

    private List<ImportFormat> candidatesForFileName(String lowerName) {
        if (lowerName.endsWith(".gpx")) {
            return new ArrayList<>(List.of(ImportFormat.GPX));
        }
        if (lowerName.endsWith(".csv")) {
            return new ArrayList<>(List.of(ImportFormat.CSV));
        }
        if (lowerName.endsWith(".geojson")) {
            return new ArrayList<>(List.of(ImportFormat.GEOJSON));
        }
        if (lowerName.endsWith(".json")) {
            return new ArrayList<>(List.of(
                    ImportFormat.OWNTRACKS,
                    ImportFormat.GOOGLE_TIMELINE,
                    ImportFormat.GEOJSON
            ));
        }
        if (lowerName.endsWith(".zip")) {
            return new ArrayList<>(List.of(
                    ImportFormat.GEOPULSE,
                    ImportFormat.GPX_ZIP
            ));
        }
        return new ArrayList<>();
    }

    private ImportFormat hintFromName(String lowerName, List<ImportFormat> candidates) {
        if (lowerName.contains("owntracks") && candidates.contains(ImportFormat.OWNTRACKS)) {
            return ImportFormat.OWNTRACKS;
        }
        if ((lowerName.contains("timeline") || lowerName.contains("google")) &&
                candidates.contains(ImportFormat.GOOGLE_TIMELINE)) {
            return ImportFormat.GOOGLE_TIMELINE;
        }
        if (lowerName.contains("geojson") && candidates.contains(ImportFormat.GEOJSON)) {
            return ImportFormat.GEOJSON;
        }
        if (lowerName.contains("geopulse") && candidates.contains(ImportFormat.GEOPULSE)) {
            return ImportFormat.GEOPULSE;
        }
        if (lowerName.contains("gpx") && candidates.contains(ImportFormat.GPX_ZIP)) {
            return ImportFormat.GPX_ZIP;
        }
        return null;
    }

    private boolean isGeopulseZip(Path file) {
        try (java.io.InputStream inputStream = Files.newInputStream(file);
             java.util.zip.ZipInputStream zipInputStream = new java.util.zip.ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (ExportImportConstants.FileNames.METADATA.equals(name)) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.debug("Failed to inspect ZIP for GeoPulse metadata {}: {}", file, e.getMessage());
        }
        return false;
    }

    private static final class DetectionResult {
        private final ImportFormat format;
        private final byte[] fileData;
        private final String errorMessage;

        private DetectionResult(ImportFormat format, byte[] fileData) {
            this.format = format;
            this.fileData = fileData;
            this.errorMessage = null;
        }

        private DetectionResult(String errorMessage) {
            this.format = null;
            this.fileData = null;
            this.errorMessage = errorMessage;
        }

        private static DetectionResult error(String message) {
            return new DetectionResult(message);
        }
    }

    private static final class TrackedDropImport {
        private final ImportJob job;
        private final Path userDir;
        private final Path filePath;

        private TrackedDropImport(ImportJob job, Path userDir, Path filePath) {
            this.job = job;
            this.userDir = userDir;
            this.filePath = filePath;
        }
    }
}
