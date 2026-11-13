package org.github.tess1o.geopulse.admin.backup;

@FunctionalInterface
public interface BackupPhaseListener {
    void onPhase(BackupPhase phase) throws Exception;
}
