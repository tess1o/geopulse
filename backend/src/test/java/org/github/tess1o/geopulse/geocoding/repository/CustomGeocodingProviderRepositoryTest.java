package org.github.tess1o.geopulse.geocoding.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.CustomGeocodingProviderEntity;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class CustomGeocodingProviderRepositoryTest {

    @Inject
    CustomGeocodingProviderRepository repository;

    @Test
    @Transactional
    void findByName_shouldMatchCaseInsensitivelyAndTrimInput() {
        CustomGeocodingProviderEntity provider = provider(uniqueName("local-photon"), "Local Photon", true);
        repository.persist(provider);

        assertThat(repository.findByName("  " + provider.getName().toUpperCase() + "  "))
                .contains(provider);
    }

    @Test
    @Transactional
    void listEnabled_shouldReturnOnlyEnabledProvidersSortedByDisplayName() {
        CustomGeocodingProviderEntity beta = provider(uniqueName("beta-photon"), "Beta Photon", true);
        CustomGeocodingProviderEntity alpha = provider(uniqueName("alpha-photon"), "Alpha Photon", true);
        CustomGeocodingProviderEntity disabled = provider(uniqueName("disabled-photon"), "Disabled Photon", false);
        repository.persist(beta);
        repository.persist(alpha);
        repository.persist(disabled);

        List<String> names = repository.listEnabled().stream()
                .filter(provider -> List.of(beta.getName(), alpha.getName(), disabled.getName()).contains(provider.getName()))
                .map(CustomGeocodingProviderEntity::getName)
                .toList();

        assertThat(names).containsExactly(alpha.getName(), beta.getName());
    }

    private CustomGeocodingProviderEntity provider(String name, String displayName, boolean enabled) {
        CustomGeocodingProviderEntity provider = new CustomGeocodingProviderEntity();
        provider.setName(name);
        provider.setDisplayName(displayName);
        provider.setType("photon");
        provider.setUrl("https://example.com/" + name);
        provider.setEnabled(enabled);
        return provider;
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
