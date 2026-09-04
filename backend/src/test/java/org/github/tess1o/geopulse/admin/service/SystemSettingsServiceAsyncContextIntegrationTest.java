package org.github.tess1o.geopulse.admin.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SystemSettingsServiceAsyncContextIntegrationTest {

    @Inject
    SystemSettingsService settingsService;

    @Test
    void getIntegerCanReadDatabaseBackedSettingsFromAnAsyncWorker() throws Exception {
        CompletableFuture<Integer> setting = CompletableFuture.supplyAsync(
                () -> settingsService.getInteger("geocoding.reconcile.item.max-attempts"));

        assertThat(setting.get(10, TimeUnit.SECONDS)).isPositive();
    }
}
