package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.event.Event;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.github.tess1o.geopulse.admin.event.LoggingSettingsChangedEvent;
import org.github.tess1o.geopulse.admin.model.SystemSettingsEntity;
import org.github.tess1o.geopulse.admin.model.ValueType;
import org.github.tess1o.geopulse.admin.repository.SystemSettingsRepository;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.mapmatching.event.MapMatchingSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class SystemSettingsServiceCacheTest {

    private static final String KEY = "ai.tool-result.max-length";
    private static final int DEFAULT = 12000;

    private SystemSettingsRepository repository;
    private AIEncryptionService encryptionService;
    private TransactionSynchronizationRegistry transactions;
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);

    @BeforeEach
    void setUp() {
        repository = mock(SystemSettingsRepository.class);
        encryptionService = mock(AIEncryptionService.class);
        transactions = mock(TransactionSynchronizationRegistry.class);
        when(transactions.getTransactionStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);
        when(repository.findByKey(KEY)).thenReturn(Optional.empty());
    }

    @Test
    void repeatedReadsQueryTheDatabaseOnce() {
        stored("5000");
        SystemSettingsService service = service(true, Duration.ofHours(1));

        assertThat(service.getInteger(KEY)).isEqualTo(5000);
        assertThat(service.getInteger(KEY)).isEqualTo(5000);
        assertThat(service.isDefault(KEY)).isFalse();

        verify(repository, times(1)).findByKey(KEY);
    }

    @Test
    void settingAValueEvictsTheCachedOne() {
        SystemSettingsEntity entity = stored("5000");
        SystemSettingsService service = service(true, Duration.ofHours(1));
        assertThat(service.getInteger(KEY)).isEqualTo(5000);

        service.setValue(KEY, "7000", null); // mutates the entity the repository returns

        assertThat(entity.getValue()).isEqualTo("7000");
        assertThat(service.getInteger(KEY)).isEqualTo(7000);
    }

    @Test
    void resettingToDefaultEvictsTheCachedOne() {
        stored("5000");
        doAnswer(invocation -> {
            when(repository.findByKey(KEY)).thenReturn(Optional.empty());
            return null;
        }).when(repository).deleteByKey(KEY);
        SystemSettingsService service = service(true, Duration.ofHours(1));
        assertThat(service.getInteger(KEY)).isEqualTo(5000);

        service.resetToDefault(KEY);

        assertThat(service.getInteger(KEY)).isEqualTo(DEFAULT);
        assertThat(service.isDefault(KEY)).isTrue();
    }

    @Test
    void entriesExpireAfterTheTtl() {
        SystemSettingsEntity entity = stored("5000");
        SystemSettingsService service = service(true, Duration.ofHours(1));
        assertThat(service.getInteger(KEY)).isEqualTo(5000);

        entity.setValue("6000"); // changed directly in the database
        clock.addAndGet(Duration.ofMinutes(59).toNanos());
        assertThat(service.getInteger(KEY)).isEqualTo(5000);

        clock.addAndGet(Duration.ofMinutes(2).toNanos());
        assertThat(service.getInteger(KEY)).isEqualTo(6000);
        verify(repository, times(2)).findByKey(KEY);
    }

    @Test
    void invalidateCacheReloadsEverything() {
        SystemSettingsEntity entity = stored("5000");
        SystemSettingsService service = service(true, Duration.ofHours(1));
        assertThat(service.getInteger(KEY)).isEqualTo(5000);

        entity.setValue("6000");
        service.invalidateCache();

        assertThat(service.getInteger(KEY)).isEqualTo(6000);
    }

    @Test
    void disabledCacheReadsTheDatabaseEveryTime() {
        stored("5000");
        SystemSettingsService disabled = service(false, Duration.ofHours(1));
        SystemSettingsService zeroTtl = service(true, Duration.ZERO);

        disabled.getInteger(KEY);
        disabled.getInteger(KEY);
        zeroTtl.getInteger(KEY);
        zeroTtl.isDefault(KEY);

        verify(repository, times(4)).findByKey(KEY);
    }

    @Test
    void writesInATransactionBypassTheCacheUntilTheTransactionCompletes() {
        SystemSettingsEntity entity = stored("5000");
        when(transactions.getTransactionStatus()).thenReturn(Status.STATUS_ACTIVE);
        SystemSettingsService service = service(true, Duration.ofHours(1));
        assertThat(service.getInteger(KEY)).isEqualTo(5000);

        service.setValue(KEY, "7000", null);
        ArgumentCaptor<Synchronization> synchronization = ArgumentCaptor.forClass(Synchronization.class);
        verify(transactions).registerInterposedSynchronization(synchronization.capture());

        // Pending: every read goes to the database, and nothing it returns is kept.
        assertThat(service.getInteger(KEY)).isEqualTo(7000);
        entity.setValue("5000"); // the transaction rolls back
        assertThat(service.getInteger(KEY)).isEqualTo(5000);
        verify(repository, times(1 + 1 + 2)).findByKey(KEY); // first read, setValue, two pending reads

        synchronization.getValue().afterCompletion(Status.STATUS_ROLLEDBACK);

        assertThat(service.getInteger(KEY)).isEqualTo(5000);
        assertThat(service.getInteger(KEY)).isEqualTo(5000);
        verify(repository, times(1 + 1 + 2 + 1)).findByKey(KEY);
    }

    @Test
    void encryptedValuesStayEncryptedInTheCache() {
        SystemSettingsEntity entity = SystemSettingsEntity.builder()
                .key("geocoding.mapbox.access-token")
                .value("cipher")
                .valueType(ValueType.ENCRYPTED)
                .encryptionKeyId("k1")
                .build();
        when(repository.findByKey("geocoding.mapbox.access-token")).thenReturn(Optional.of(entity));
        when(encryptionService.decrypt("cipher", "k1")).thenReturn("secret");
        SystemSettingsService service = service(true, Duration.ofHours(1));

        assertThat(service.getString("geocoding.mapbox.access-token")).isEqualTo("secret");
        assertThat(service.getString("geocoding.mapbox.access-token")).isEqualTo("secret");

        verify(repository, times(1)).findByKey("geocoding.mapbox.access-token");
        verify(encryptionService, times(2)).decrypt("cipher", "k1");
    }

    private SystemSettingsEntity stored(String value) {
        SystemSettingsEntity entity = SystemSettingsEntity.builder()
                .key(KEY)
                .value(value)
                .valueType(ValueType.INTEGER)
                .build();
        when(repository.findByKey(KEY)).thenReturn(Optional.of(entity));
        return entity;
    }

    @SuppressWarnings("unchecked")
    private SystemSettingsService service(boolean cacheEnabled, Duration ttl) {
        SystemSettingsService service = new SystemSettingsService(
                repository,
                encryptionService,
                (Event<WeatherSettingsChangedEvent>) mock(Event.class),
                (Event<MapMatchingSettingsChangedEvent>) mock(Event.class),
                (Event<LoggingSettingsChangedEvent>) mock(Event.class),
                transactions,
                cacheEnabled,
                ttl);
        service.nanoTime = clock::get;
        return service;
    }
}
