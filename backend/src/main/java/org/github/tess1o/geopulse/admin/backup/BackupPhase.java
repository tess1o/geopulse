package org.github.tess1o.geopulse.admin.backup;

public enum BackupPhase {
    SNAPSHOT("snapshot", "Creating a consistent PostgreSQL snapshot"),
    DUMPING("dumping", "Exporting database data"),
    FINALIZING("finalizing", "Encrypting and finalizing the backup archive");

    private final String wireName;
    private final String fallbackMessage;

    BackupPhase(String wireName, String fallbackMessage) {
        this.wireName = wireName;
        this.fallbackMessage = fallbackMessage;
    }

    public String wireName() {
        return wireName;
    }

    public String fallbackMessage() {
        return fallbackMessage;
    }
}
