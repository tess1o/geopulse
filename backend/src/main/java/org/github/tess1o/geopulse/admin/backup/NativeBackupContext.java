package org.github.tess1o.geopulse.admin.backup;

import java.nio.file.Path;

/** Filesystem/tool settings for a native operation, separate from PostgreSQL credentials. */
public record NativeBackupContext(PostgresTarget postgres, Path workPath, String binaryDirectory,
                                  String keyLocation, String applicationVersion) {
    public RestoreJournal journal() { return new RestoreJournal(workPath); }
}
