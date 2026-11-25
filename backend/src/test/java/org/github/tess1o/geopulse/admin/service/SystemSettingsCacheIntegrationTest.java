package org.github.tess1o.geopulse.admin.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System settings are cached in memory. A change made through {@link SystemSettingsService} must be visible to
 * every thread as soon as it commits, and a change that rolls back must not leave its value behind.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SystemSettingsCacheIntegrationTest {

    private static final String KEY = "ai.tool-result.max-length";
    private static final int DEFAULT = 12000;
    private static final String OBSERVED_KEY = "weather.coordinate-precision";

    @Inject
    SystemSettingsService settingsService;

    @Inject
    EntityManager entityManager;

    @Inject
    ObservedSettingRecorder recorder;

    @BeforeEach
    void setUp() {
        settingsService.resetToDefault(KEY);
        settingsService.resetToDefault(OBSERVED_KEY);
        recorder.clear();
    }

    @AfterEach
    void tearDown() {
        settingsService.resetToDefault(KEY);
        settingsService.resetToDefault(OBSERVED_KEY);
    }

    @Test
    void readsAreServedFromTheCacheUntilInvalidated() {
        settingsService.setValue(KEY, "5000", null);
        assertThat(settingsService.getInteger(KEY)).isEqualTo(5000);

        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("UPDATE system_settings SET value = '6000' WHERE key = ?1")
                .setParameter(1, KEY)
                .executeUpdate());

        assertThat(settingsService.getInteger(KEY)).as("a direct database edit waits for the TTL").isEqualTo(5000);
        settingsService.invalidateCache();
        assertThat(settingsService.getInteger(KEY)).isEqualTo(6000);
    }

    @Test
    void aCommittedChangeIsVisibleToOtherThreadsImmediately() throws Exception {
        assertThat(onAnotherThread(() -> settingsService.getInteger(KEY))).isEqualTo(DEFAULT);

        settingsService.setValue(KEY, "7000", null);

        assertThat(onAnotherThread(() -> settingsService.getInteger(KEY))).isEqualTo(7000);
        assertThat(onAnotherThread(() -> settingsService.isDefault(KEY))).isFalse();
    }

    @Test
    void aResetIsVisibleImmediately() throws Exception {
        settingsService.setValue(KEY, "5000", null);
        assertThat(onAnotherThread(() -> settingsService.getInteger(KEY))).isEqualTo(5000);

        settingsService.resetToDefault(KEY);

        assertThat(onAnotherThread(() -> settingsService.getInteger(KEY))).isEqualTo(DEFAULT);
        assertThat(onAnotherThread(() -> settingsService.isDefault(KEY))).isTrue();
    }

    @Test
    void aRolledBackChangeLeavesTheCommittedValue() throws Exception {
        settingsService.setValue(KEY, "5000", null);
        assertThat(settingsService.getInteger(KEY)).isEqualTo(5000);

        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> {
            settingsService.setValue(KEY, "9000", null);
            assertThat(settingsService.getInteger(KEY)).as("the writing transaction sees its own change").isEqualTo(9000);
            throw new IllegalStateException("roll back");
        })).isInstanceOf(IllegalStateException.class).hasMessage("roll back");

        assertThat(settingsService.getInteger(KEY)).isEqualTo(5000);
        assertThat(onAnotherThread(() -> settingsService.getInteger(KEY))).isEqualTo(5000);
    }

    @Test
    void changeObserversReadTheNewValue() {
        assertThat(settingsService.getInteger(OBSERVED_KEY)).isEqualTo(2); // cached default

        settingsService.setValue(OBSERVED_KEY, "3", null);

        assertThat(recorder.values()).containsExactly("3");
    }

    private static <T> T onAnotherThread(Supplier<T> read) throws Exception {
        return CompletableFuture.supplyAsync(read).get(10, TimeUnit.SECONDS);
    }

    /**
     * Reads the setting the way GeoPulse's own observers do: after the change commits, possibly before the cache
     * hears that the transaction ended.
     */
    @ApplicationScoped
    static class ObservedSettingRecorder {

        private final List<String> values = new CopyOnWriteArrayList<>();

        @Inject
        SystemSettingsService settingsService;

        void onWeatherSettingChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) WeatherSettingsChangedEvent event) {
            if (OBSERVED_KEY.equals(event.key())) {
                values.add(settingsService.getString(OBSERVED_KEY));
            }
        }

        List<String> values() {
            return List.copyOf(values);
        }

        void clear() {
            values.clear();
        }
    }
}
