package org.github.tess1o.geopulse.gpssource.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceTypeTelemetryConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsTelemetryMappingEntry;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceTypeTelemetryConfigRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class GpsSourceServiceTelemetryTest {

    @Inject
    GpsSourceTypeTelemetryConfigService telemetryConfigService;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    GpsSourceTypeTelemetryConfigRepository telemetryConfigRepository;

    @Inject
    UserRepository userRepository;

    private UserEntity user;

    @BeforeEach
    @Transactional
    void setup() {
        gpsSourceRepository.deleteAll();
        telemetryConfigRepository.deleteAll();
        userRepository.deleteAll();

        user = UserEntity.builder()
                .email("gps-source-telemetry-" + System.nanoTime() + "@test.local")
                .role(Role.USER)
                .passwordHash("pass")
                .build();
        userRepository.persist(user);
    }

    @Test
    @Transactional
    void ownTracksSourceUsesDefaultTelemetryMappingWhenNoUserConfig() {
        GpsSourceTypeTelemetryConfigDTO resolved = telemetryConfigService.getResolvedConfig(user.getId(), GpsSourceType.OWNTRACKS);

        assertFalse(resolved.isCustomized());
        assertNotNull(resolved.getMapping());
        assertFalse(resolved.getMapping().isEmpty());
        assertTrue(resolved.getMapping().stream().anyMatch(entry -> "ignition".equals(entry.getKey())));
        assertTrue(resolved.getMapping().stream()
                .filter(entry -> entry.getKey().startsWith("geofence_"))
                .allMatch(entry -> !entry.isEnabled()));
    }

    @Test
    @Transactional
    void canUpsertAndResetGlobalTelemetryMappingForSourceType() {
        List<GpsTelemetryMappingEntry> customMapping = List.of(
                GpsTelemetryMappingEntry.builder()
                        .key("ignition")
                        .label("Engine")
                        .type("boolean")
                        .enabled(true)
                        .showInGpsData(false)
                        .showInCurrentPopup(true)
                        .order(10)
                        .trueValues(List.of("1"))
                        .falseValues(List.of("0"))
                        .build()
        );

        GpsSourceTypeTelemetryConfigDTO saved = telemetryConfigService.upsertConfig(
                user.getId(),
                GpsSourceType.OWNTRACKS,
                customMapping
        );
        assertTrue(saved.isCustomized());
        assertEquals(1, saved.getMapping().size());
        assertEquals("Engine", saved.getMapping().get(0).getLabel());
        assertFalse(saved.getMapping().get(0).isShowInGpsData());

        telemetryConfigService.resetConfig(user.getId(), GpsSourceType.OWNTRACKS);

        GpsSourceTypeTelemetryConfigDTO afterReset = telemetryConfigService.getResolvedConfig(user.getId(), GpsSourceType.OWNTRACKS);
        assertFalse(afterReset.isCustomized());
        assertTrue(afterReset.getMapping().stream().anyMatch(entry -> "ignition".equals(entry.getKey())));
        assertTrue(afterReset.getMapping().stream().anyMatch(entry -> "lte_pct".equals(entry.getKey())));
    }
}
