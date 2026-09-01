package org.github.tess1o.geopulse.admin.backup;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;

/**
 * Stored outside PostgreSQL so a database replacement cannot erase its recovery record.
 */
public final class RestoreJournal {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path root;

    public RestoreJournal(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public synchronized RestoreState read() throws IOException {
        Path file = root.resolve("restore-state.json");
        if (!Files.exists(file)) return null;
        if (Files.isSymbolicLink(file) || Files.size(file) > 65536) throw new IOException("Invalid restore journal");
        var tree = JSON.readTree(Files.readAllBytes(file));
        if (!tree.hasNonNull("state") || !tree.hasNonNull("operationId"))
            throw new IOException("Incomplete restore journal");
        RestoreState state = JSON.treeToValue(tree, RestoreState.class);
        try {
            state.validateDurableShape();
        } catch (RuntimeException e) {
            throw new IOException("Invalid restore journal", e);
        }
        return state;
    }

    public synchronized void write(RestoreState state) throws IOException {
        secureDirectory(root);
        state.updatedAt = Instant.now().toString();
        try {
            state.validateDurableShape();
        } catch (RuntimeException e) {
            throw new IOException("Refusing to persist invalid restore journal", e);
        }
        Path temp = Files.createTempFile(root, "journal-", ".tmp");
        secureFile(temp);
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(JSON.writeValueAsBytes(state));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temp, root.resolve("restore-state.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(root);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public synchronized void archive(RestoreState state) throws IOException {
        if (state == null) return;
        try {
            state.validateDurableShape();
        } catch (RuntimeException e) {
            throw new IOException("Refusing to archive invalid restore journal", e);
        }
        Path history = root.resolve("history");
        secureDirectory(history);
        Path target = history.resolve(state.operationId + ".json");
        if (Files.exists(target)) return;
        Path temp = Files.createTempFile(history, "journal-", ".tmp");
        secureFile(temp);
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(JSON.writeValueAsBytes(state));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            forceDirectory(history);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void secureDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        if (Files.isSymbolicLink(path)) throw new IOException("Backup working directory cannot be a symlink");
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
    }

    public static Path createPrivateFile(Path path) throws IOException {
        if (Files.getFileStore(path.toAbsolutePath().getParent()).supportsFileAttributeView("posix"))
            return Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        return Files.createFile(path);
    }

    public static void secureFile(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    public static void forceDirectory(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            try (FileChannel dir = FileChannel.open(path, StandardOpenOption.READ)) {
                dir.force(true);
            }
        }
    }

    public static void removeTree(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (var files = Files.walk(path)) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
