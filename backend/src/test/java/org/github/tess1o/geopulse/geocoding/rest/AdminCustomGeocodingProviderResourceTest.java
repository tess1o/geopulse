package org.github.tess1o.geopulse.geocoding.rest;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderRequest;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderResponse;
import org.github.tess1o.geopulse.geocoding.service.CustomGeocodingProviderService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminCustomGeocodingProviderResourceTest {

    @Mock
    private CustomGeocodingProviderService providerService;

    @Test
    void list_shouldReturnProviders() {
        CustomGeocodingProviderResponse provider = CustomGeocodingProviderResponse.builder()
                .name("local-photon")
                .displayName("Local Photon")
                .type("photon")
                .url("https://example.com")
                .enabled(true)
                .build();
        when(providerService.list()).thenReturn(List.of(provider));
        AdminCustomGeocodingProviderResource resource = new AdminCustomGeocodingProviderResource(providerService);

        Response response = resource.list();

        assertThat(response.getStatus()).isEqualTo(200);
        ApiResponse<?> entity = (ApiResponse<?>) response.getEntity();
        assertThat(entity.getStatus()).isEqualTo("success");
        assertThat(entity.getData()).isEqualTo(List.of(provider));
    }

    @Test
    void create_shouldMapValidationFailureToBadRequestMessage() {
        CustomGeocodingProviderRequest request = request();
        when(providerService.create(request)).thenThrow(new IllegalArgumentException("Custom provider name cannot match a built-in provider: photon"));
        AdminCustomGeocodingProviderResource resource = new AdminCustomGeocodingProviderResource(providerService);

        Response response = resource.create(request);

        assertThat(response.getStatus()).isEqualTo(400);
        ApiResponse<?> entity = (ApiResponse<?>) response.getEntity();
        assertThat(entity.getStatus()).isEqualTo("error");
        assertThat(entity.getMessage()).isEqualTo("Custom provider name cannot match a built-in provider: photon");
    }

    @Test
    void update_shouldMapMissingProviderToNotFoundMessage() {
        CustomGeocodingProviderRequest request = request();
        when(providerService.update("missing", request)).thenThrow(new NotFoundException("Custom geocoding provider not found: missing"));
        AdminCustomGeocodingProviderResource resource = new AdminCustomGeocodingProviderResource(providerService);

        Response response = resource.update("missing", request);

        assertThat(response.getStatus()).isEqualTo(404);
        ApiResponse<?> entity = (ApiResponse<?>) response.getEntity();
        assertThat(entity.getStatus()).isEqualTo("error");
        assertThat(entity.getMessage()).isEqualTo("Custom geocoding provider not found: missing");
    }

    @Test
    void delete_shouldMapSelectedProviderFailureToBadRequestMessage() {
        doThrow(new IllegalArgumentException("Cannot delete custom provider 'local-photon' while it is the primary provider"))
                .when(providerService).delete("local-photon");
        AdminCustomGeocodingProviderResource resource = new AdminCustomGeocodingProviderResource(providerService);

        Response response = resource.delete("local-photon");

        assertThat(response.getStatus()).isEqualTo(400);
        ApiResponse<?> entity = (ApiResponse<?>) response.getEntity();
        assertThat(entity.getStatus()).isEqualTo("error");
        assertThat(entity.getMessage()).isEqualTo("Cannot delete custom provider 'local-photon' while it is the primary provider");
    }

    @Test
    void delete_shouldReturnSuccessEnvelope() {
        AdminCustomGeocodingProviderResource resource = new AdminCustomGeocodingProviderResource(providerService);

        Response response = resource.delete("local-photon");

        assertThat(response.getStatus()).isEqualTo(200);
        ApiResponse<?> entity = (ApiResponse<?>) response.getEntity();
        assertThat(entity.getStatus()).isEqualTo("success");
        assertThat(entity.getMessage()).isEqualTo("Custom geocoding provider deleted");
    }

    private CustomGeocodingProviderRequest request() {
        CustomGeocodingProviderRequest request = new CustomGeocodingProviderRequest();
        request.setName("local-photon");
        request.setDisplayName("Local Photon");
        request.setType("photon");
        request.setUrl("https://example.com");
        request.setEnabled(true);
        return request;
    }
}
