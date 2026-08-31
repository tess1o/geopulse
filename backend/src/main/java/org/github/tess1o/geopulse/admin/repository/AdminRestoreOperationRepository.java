package org.github.tess1o.geopulse.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.model.AdminRestoreOperationEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AdminRestoreOperationRepository implements PanacheRepositoryBase<AdminRestoreOperationEntity, UUID> {

    public Optional<AdminRestoreOperationEntity> findLatest() {
        return findAll(Sort.descending("startedAt")).firstResultOptional();
    }

    public boolean hasActiveRestore() {
        return count("status = ?1 OR status = ?2", "VALIDATING", "RUNNING") > 0;
    }

    public boolean hasBlockingRestore() {
        return findLatestDataMutationRestore()
                .map(operation -> "FAILED".equals(operation.getStatus()) || "RUNNING".equals(operation.getStatus()))
                .orElse(false);
    }

    public boolean hasFailedRestore() {
        return findLatestDataMutationRestore()
                .map(operation -> "FAILED".equals(operation.getStatus()))
                .orElse(false);
    }

    private Optional<AdminRestoreOperationEntity> findLatestDataMutationRestore() {
        return find(
                "status = ?1 OR status = ?2 OR status = ?3",
                Sort.descending("startedAt"),
                "RUNNING",
                "FAILED",
                "COMPLETED"
        ).firstResultOptional();
    }

    public void failActiveRestoresFromPreviousProcess() {
        Instant now = Instant.now();
        List<AdminRestoreOperationEntity> activeOperations = list(
                "status = ?1 OR status = ?2",
                "VALIDATING",
                "RUNNING"
        );
        for (AdminRestoreOperationEntity operation : activeOperations) {
            if ("VALIDATING".equals(operation.getStatus())) {
                operation.setStatus("VALIDATION_FAILED");
                operation.setError(defaultError(operation, "GeoPulse stopped while validating a full restore."));
            } else {
                operation.setStatus("FAILED");
                operation.setError(defaultError(operation, "GeoPulse stopped during a destructive full restore. Retry a full restore before using the app."));
            }
            operation.setCompletedAt(now);
            operation.setUpdatedAt(now);
        }
    }

    private String defaultError(AdminRestoreOperationEntity operation, String fallback) {
        return operation.getError() == null || operation.getError().isBlank()
                ? fallback
                : operation.getError();
    }
}
