package org.github.tess1o.geopulse.admin.backup;

public enum RestorePhase {
    UPLOAD("upload", 5),
    PREFLIGHT("preflight", 20),
    RESTORING("restoring", 35),
    SECRETS("secrets", 75),
    VALIDATING("validating", 90),
    CUTOVER("cutover", 95),
    RESTARTING("restarting", 100),
    COMPLETED("completed", 100),
    ACTIVATION_ROLLED_BACK("activation-rolled-back", 95),
    IDENTITY_MISMATCH("identity-mismatch", 95),
    INTERRUPTED("interrupted", 0),
    FAILED("failed", 0),
    ACTIVATION_FAILED("activation-failed", 95),
    DISCARDED("discarded", 0);

    private final String wireName;
    private final int progress;

    RestorePhase(String wireName, int progress) {
        this.wireName = wireName;
        this.progress = progress;
    }

    public String wireName() {
        return wireName;
    }

    public int progress() {
        return progress;
    }
}
