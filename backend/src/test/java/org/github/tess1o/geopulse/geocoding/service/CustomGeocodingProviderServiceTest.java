package org.github.tess1o.geopulse.geocoding.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.NotFoundException;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderRequest;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderResponse;
import org.github.tess1o.geopulse.geocoding.model.CustomGeocodingProviderEntity;
import org.github.tess1o.geopulse.geocoding.repository.CustomGeocodingProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CustomGeocodingProviderServiceTest {

    @Mock
    private CustomGeocodingProviderRepository repository;

    @Mock
    private AIEncryptionService encryptionService;

    @Mock
    private SystemSettingsService settingsService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CustomGeocodingProviderService service;

    @BeforeEach
    void setUp() {
        service = new CustomGeocodingProviderService(repository, encryptionService, objectMapper, settingsService);
    }

    @Test
    void create_shouldNormalizePersistEncryptHeadersAndMaskResponse() throws Exception {
        when(repository.existsByName("local-photon")).thenReturn(false);
        when(encryptionService.encrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(encryptionService.decrypt(anyString(), eq("v1"))).thenAnswer(invocation -> invocation.getArgument(0));
        when(encryptionService.getCurrentKeyId()).thenReturn("v1");

        CustomGeocodingProviderRequest request = request(" Local-Photon ", " Local Photon ", " PHOTON ", "https://example.com/");
        request.setLanguage(" en ");
        request.setDelayMs(250);
        request.setHeaders(new LinkedHashMap<>(Map.of(
                " X-Api-Key ", " secret ",
                " ", "ignored",
                "Empty-Value", " "
        )));

        CustomGeocodingProviderResponse response = service.create(request);

        ArgumentCaptor<CustomGeocodingProviderEntity> entityCaptor = ArgumentCaptor.forClass(CustomGeocodingProviderEntity.class);
        verify(repository).persist(entityCaptor.capture());
        CustomGeocodingProviderEntity entity = entityCaptor.getValue();

        assertThat(entity.getName()).isEqualTo("local-photon");
        assertThat(entity.getDisplayName()).isEqualTo("Local Photon");
        assertThat(entity.getType()).isEqualTo("photon");
        assertThat(entity.getUrl()).isEqualTo("https://example.com");
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.getLanguage()).isEqualTo("en");
        assertThat(entity.getDelayMs()).isEqualTo(250);
        assertThat(entity.getHeadersKeyId()).isEqualTo("v1");

        Map<String, String> storedHeaders = objectMapper.readValue(entity.getHeadersJson(), new TypeReference<>() {});
        assertThat(storedHeaders).containsEntry("X-Api-Key", "secret");
        assertThat(storedHeaders).doesNotContainKeys("", "Empty-Value");
        assertThat(response.getHeaders()).containsEntry("X-Api-Key", "********");
    }

    @Test
    void update_withoutHeaders_shouldKeepExistingEncryptedHeaders() {
        CustomGeocodingProviderEntity entity = entity("local-photon", "Local Photon", "photon");
        entity.setHeadersJson("{\"X-Api-Key\":\"secret\"}");
        entity.setHeadersKeyId("v1");

        when(repository.findByName("local-photon")).thenReturn(Optional.of(entity));
        when(encryptionService.decrypt(entity.getHeadersJson(), "v1")).thenReturn("{\"X-Api-Key\":\"secret\"}");

        CustomGeocodingProviderRequest request = request("local-photon", "Updated Photon", "photon", "https://new.example.com");
        request.setHeaders(null);

        CustomGeocodingProviderResponse response = service.update("local-photon", request);

        assertThat(entity.getDisplayName()).isEqualTo("Updated Photon");
        assertThat(entity.getHeadersJson()).isEqualTo("{\"X-Api-Key\":\"secret\"}");
        assertThat(response.getHeaders()).containsEntry("X-Api-Key", "********");
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test
    void delete_shouldRejectSelectedPrimaryProviderAndNotDelete() {
        CustomGeocodingProviderEntity entity = entity("local-photon", "Local Photon", "photon");
        when(repository.findByName("local-photon")).thenReturn(Optional.of(entity));
        when(settingsService.getString("geocoding.primary-provider")).thenReturn("local-photon");

        assertThatThrownBy(() -> service.delete("local-photon"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot delete custom provider 'local-photon' while it is the primary provider");

        verify(repository, never()).delete(entity);
    }

    @Test
    void update_shouldRejectDisablingSelectedFallbackProviderAndNotPersist() {
        CustomGeocodingProviderEntity entity = entity("backup-photon", "Backup Photon", "photon");
        when(repository.findByName("backup-photon")).thenReturn(Optional.of(entity));
        when(settingsService.getString("geocoding.primary-provider")).thenReturn("nominatim");
        when(settingsService.getString("geocoding.fallback-provider")).thenReturn("backup-photon");

        CustomGeocodingProviderRequest request = request("backup-photon", "Backup Photon", "photon", "https://example.com");
        request.setEnabled(false);

        assertThatThrownBy(() -> service.update("backup-photon", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot disable custom provider 'backup-photon' while it is the fallback provider");

        verify(repository, never()).persist(entity);
    }

    @Test
    void create_shouldRejectBuiltInProviderName() {
        CustomGeocodingProviderRequest request = request("photon", "Photon Clone", "photon", "https://example.com");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Custom provider name cannot match a built-in provider: photon");
    }

    @Test
    void update_shouldReturnNotFoundForMissingProvider() {
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", request("missing", "Missing", "photon", "https://example.com")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Custom geocoding provider not found: missing");
    }

    private CustomGeocodingProviderRequest request(String name, String displayName, String type, String url) {
        CustomGeocodingProviderRequest request = new CustomGeocodingProviderRequest();
        request.setName(name);
        request.setDisplayName(displayName);
        request.setType(type);
        request.setUrl(url);
        request.setEnabled(true);
        return request;
    }

    private CustomGeocodingProviderEntity entity(String name, String displayName, String type) {
        CustomGeocodingProviderEntity entity = new CustomGeocodingProviderEntity();
        entity.setName(name);
        entity.setDisplayName(displayName);
        entity.setType(type);
        entity.setUrl("https://example.com");
        entity.setEnabled(true);
        return entity;
    }
}
