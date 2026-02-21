package org.github.tess1o.geopulse.coverage.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;

@ApplicationScoped
@Slf4j
public class CoverageStatusRecoveryService {

    private final CoverageRepository coverageRepository;

    @Inject
    public CoverageStatusRecoveryService(CoverageRepository coverageRepository) {
        this.coverageRepository = coverageRepository;
    }

    @Transactional
    public void onStartup(@Observes StartupEvent startupEvent) {
        try {
            int recovered = coverageRepository.resetStuckProcessingStates();
            if (recovered > 0) {
                log.warn("Recovered {} users from stuck coverage processing state", recovered);
            } else {
                log.info("No users found in stuck coverage processing state");
            }
        } catch (Exception e) {
            log.error("Failed to recover coverage processing status on startup: {}", e.getMessage(), e);
        }
    }
}
