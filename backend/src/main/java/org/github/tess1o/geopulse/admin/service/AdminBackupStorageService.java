package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.backup.RestoreJournal;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupFileDto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class AdminBackupStorageService {
    private static final Pattern BACKUP_NAME = Pattern.compile(
            "geopulse-full-backup-[0-9]{13}-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.gpb");

    @Inject SystemSettingsService settingsService;

    public Path directory() {
        return Path.of(settingsService.getString("backup.local.path")).toAbsolutePath().normalize();
    }

    public Path newTemporaryFile(String name) throws IOException {
        Path directory = directory();
        Files.createDirectories(directory);
        return directory.resolve(name + ".tmp");
    }

    public Path publish(Path temporary, String name) throws IOException {
        Path destination = directory().resolve(name);
        Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        RestoreJournal.forceDirectory(directory());
        return destination;
    }

    public List<AdminBackupFileDto> list() throws IOException {
        Path directory = directory();
        if (!Files.exists(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && isBackupName(path.getFileName().toString()))
                    .map(path -> {
                        try {
                            return AdminBackupFileDto.builder().fileName(path.getFileName().toString())
                                    .sizeBytes(Files.size(path)).lastModifiedAt(Files.getLastModifiedTime(path).toInstant()).build();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .sorted(Comparator.comparing(AdminBackupFileDto::getLastModifiedAt).reversed())
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public Path resolve(String name) {
        if (!isBackupName(name) || Path.of(name).getNameCount() != 1) {
            throw new IllegalArgumentException("Invalid backup file name");
        }
        Path directory = directory();
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Backup file not found");
        }
        return file;
    }

    public void delete(String name) throws IOException {
        Files.delete(resolve(name));
    }

    public void prune(int retentionCount, String operationId) {
        try {
            List<AdminBackupFileDto> backups = list();
            for (int i = retentionCount; i < backups.size(); i++) {
                Path expired = resolve(backups.get(i).getFileName());
                try {
                    Files.deleteIfExists(expired);
                    log.info("Backup operation {} deleted expired full backup {}", operationId, expired.getFileName());
                } catch (IOException e) {
                    log.warn("Backup operation {} could not delete expired full backup {}; retention will retry later",
                            operationId, expired.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.warn("Backup operation {} could not enumerate full backups for retention; publication remains successful",
                    operationId, e);
        }
    }

    public boolean isBackupName(String name) {
        return name != null && BACKUP_NAME.matcher(name).matches();
    }
}
