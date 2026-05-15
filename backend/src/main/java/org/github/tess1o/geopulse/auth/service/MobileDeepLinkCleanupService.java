package org.github.tess1o.geopulse.auth.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.repository.MobileAuthCodeRepository;

import java.time.Instant;

@ApplicationScoped
@Slf4j
public class MobileDeepLinkCleanupService {
    @Inject
    MobileAuthCodeRepository mobileAuthCodeRepository;

    @ConfigProperty(name = "geopulse.auth.mobile.deeplink.cleanup.enabled", defaultValue = "true")
    @StaticInitSafe
    boolean isEnabled;

    @Blocking
    @Scheduled(cron = "${geopulse.auth.mobile.deeplink.cleanup.cron}", identity = "mobile-deeplink-cleanup")
    public void cleanupDeeplinkCodes() {
        if  (!isEnabled) {
            log.debug("Mobile deeplink cleanup is disabled");
            return;
        }
        Instant now = Instant.now();

        try {
            long deleted = mobileAuthCodeRepository.deleteExpiredBefore(now, now);
            log.info("Mobile deeplink cleanup removed {} expired code(s)", deleted);

        } catch (Exception e) {
            log.error("Failed to cleanup mobile deeplink codes", e);
        }
    }

}
