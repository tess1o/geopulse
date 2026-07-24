package org.github.tess1o.geopulse.geocoding.service;

import org.github.tess1o.geopulse.geocoding.model.GeonamesCountryRecord;
import org.github.tess1o.geopulse.geocoding.repository.GeonamesCountryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GeonamesCountryImportServiceTest {

    @Mock
    GeonamesCountryRepository geonamesCountryRepository;

    private GeonamesCountryImportService service;

    @BeforeEach
    void setUp() {
        service = new GeonamesCountryImportService(
                geonamesCountryRepository,
                new GeonamesCountryLineParser(),
                null
        );
        service.batchSize = 50;
        service.minimumRowThreshold = 1;
    }

    @Test
    void importFromStream_ShouldOverrideUnitedStatesCountryName() throws IOException {
        List<GeonamesCountryRecord> imported = importRows(
                countryInfoRow("US", "USA", "840", "US", "United States")
        );

        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().isoAlpha2()).isEqualTo("US");
        assertThat(imported.getFirst().countryName()).isEqualTo("United States of America");
    }

    @Test
    void importFromStream_ShouldKeepNonOverriddenCountryNames() throws IOException {
        List<GeonamesCountryRecord> imported = importRows(
                countryInfoRow("BE", "BEL", "056", "BE", "Belgium")
        );

        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().isoAlpha2()).isEqualTo("BE");
        assertThat(imported.getFirst().countryName()).isEqualTo("Belgium");
    }

    @Test
    void importFromStream_ShouldApplyOverridesUsingUppercaseIsoAlpha2() throws IOException {
        List<GeonamesCountryRecord> imported = importRows(
                countryInfoRow("us", "usa", "840", "US", "United States")
        );

        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().isoAlpha2()).isEqualTo("us");
        assertThat(imported.getFirst().countryName()).isEqualTo("United States of America");
    }

    private List<GeonamesCountryRecord> importRows(String... rows) throws IOException {
        List<GeonamesCountryRecord> stagedRecords = new ArrayList<>();

        when(geonamesCountryRepository.upsertBatchToStaging(anyList()))
                .thenAnswer(invocation -> {
                    List<GeonamesCountryRecord> batch = invocation.getArgument(0);
                    stagedRecords.addAll(batch);
                    return batch.size();
                });
        when(geonamesCountryRepository.countStagingCountries()).thenReturn((long) rows.length);
        when(geonamesCountryRepository.countCountries()).thenReturn((long) rows.length);

        String content = String.join("\n", rows);
        service.importFromStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        return stagedRecords;
    }

    private String countryInfoRow(String isoAlpha2, String isoAlpha3, String isoNumeric, String fipsCode, String countryName) {
        return String.join("\t",
                isoAlpha2,
                isoAlpha3,
                isoNumeric,
                fipsCode,
                countryName,
                "Capital",
                "1000",
                "1000000",
                "NA",
                ".example",
                "USD",
                "Dollar",
                "1",
                "#####",
                "^\\d{5}$",
                "en",
                "1",
                "",
                ""
        );
    }
}
