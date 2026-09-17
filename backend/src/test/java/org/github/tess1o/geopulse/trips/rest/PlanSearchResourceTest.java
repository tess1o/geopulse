package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResultDto;
import org.github.tess1o.geopulse.trips.service.TripPlanSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PlanSearchResourceTest {

    @Mock
    CurrentUserService currentUserService;

    @Mock
    TripPlanSearchService tripPlanSearchService;

    private PlanSearchResource resource;

    @BeforeEach
    void setUp() {
        resource = new PlanSearchResource(currentUserService, tripPlanSearchService);
    }

    @Test
    void search_shouldRejectShortQuery() {
        assertThatThrownBy(() -> resource.search("a", null, null, null))
                .hasMessageContaining("q must be at least 2 characters");
    }

    @Test
    void search_shouldRejectIncompleteBiasCoordinates() {
        assertThatThrownBy(() -> resource.search("berlin", 52.52, null, null))
                .hasMessageContaining("lat and lon must be provided together");
    }

    @Test
    void search_shouldReturnServiceResults() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(tripPlanSearchService.search(eq(userId), eq("berlin"), eq(52.52), eq(13.40), eq(12)))
                .thenReturn(List.of(PlanSearchResultDto.builder()
                        .sourceType("external-search")
                        .title("Berlin")
                        .latitude(52.52)
                        .longitude(13.40)
                        .build()));

        List<PlanSearchResultDto> response = resource.search("berlin", 52.52, 13.40, 12);

        assertThat(response).hasSize(1);
    }
}
