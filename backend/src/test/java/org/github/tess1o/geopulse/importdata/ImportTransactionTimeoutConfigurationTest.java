package org.github.tess1o.geopulse.importdata;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ImportTransactionTimeoutConfigurationTest {

    @Test
    void applicationPropertiesAndServiceFallbackUseTwentyFourHourImportTimeout() throws Exception {
        Properties properties = new Properties();
        try (var stream = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(stream);
        }

        assertThat(properties.getProperty("geopulse.import.transaction-timeout-minutes"))
                .isEqualTo("${GEOPULSE_IMPORT_TRANSACTION_TIMEOUT_MINUTES:1440}");

        Field timeoutField = ImportDataService.class.getDeclaredField("importTransactionTimeoutMinutes");
        ConfigProperty configProperty = timeoutField.getAnnotation(ConfigProperty.class);

        assertThat(configProperty).isNotNull();
        assertThat(configProperty.name()).isEqualTo("geopulse.import.transaction-timeout-minutes");
        assertThat(configProperty.defaultValue()).isEqualTo("1440");
    }
}
