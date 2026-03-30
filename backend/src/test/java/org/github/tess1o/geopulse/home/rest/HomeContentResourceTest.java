package org.github.tess1o.geopulse.home.rest;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;
import org.github.tess1o.geopulse.home.service.HomeContentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class HomeContentResourceTest {

    @Mock
    private HomeContentService homeContentService;

    @InjectMocks
    private HomeContentResource homeContentResource;

    @Test
    void getHomeContent_ReturnsOkResponseWithPayload() {
        HomeContentResponse payload = new HomeContentResponse(
                List.of(new HomeContentResponse.Tip(
                        "tip-1",
                        "Tip title",
                        "Tip description",
                        "pi pi-lightbulb",
                        List.of(),
                        List.of("all")
                )),
                List.of(new HomeContentResponse.WhatsNewItem(
                        "1.0.0",
                        "Release title",
                        List.of("Highlight"),
                        "https://github.com/tess1o/geopulse/releases"
                )),
                new HomeContentResponse.Meta("bundled", "2026-03-30T10:00:00Z")
        );

        when(homeContentService.getContent()).thenReturn(payload);

        Response response = homeContentResource.getHomeContent();

        assertEquals(200, response.getStatus());
        assertEquals(payload, response.getEntity());
        verify(homeContentService).getContent();
    }
}
