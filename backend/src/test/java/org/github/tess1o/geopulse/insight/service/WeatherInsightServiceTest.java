package org.github.tess1o.geopulse.insight.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.WeatherInsights;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WeatherInsightServiceTest {

    @Mock
    EntityManager entityManager;

    @Mock
    Query query;

    @Mock
    WeatherConfigurationService weatherConfigurationService;

    @InjectMocks
    WeatherInsightService service;

    @Test
    void calculateWeatherInsightsReturnsNullWhenWeatherDisabled() {
        UUID userId = UUID.randomUUID();
        when(weatherConfigurationService.isEnabled()).thenReturn(false);

        WeatherInsights insights = service.calculateWeatherInsights(userId);

        assertNull(insights);
        verifyNoInteractions(entityManager);
    }

    @Test
    void calculateWeatherInsightsReturnsNullWhenNoSamplesExist() {
        UUID userId = UUID.randomUUID();
        when(weatherConfigurationService.isEnabled()).thenReturn(true);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("userId"), eq(userId))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        WeatherInsights insights = service.calculateWeatherInsights(userId);

        assertNull(insights);
    }

    @Test
    void calculateWeatherInsightsAggregatesStoredWeatherSamples() {
        UUID userId = UUID.randomUUID();
        when(weatherConfigurationService.isEnabled()).thenReturn(true);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("userId"), eq(userId))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(
                sample("2026-01-15T06:00:00Z", 1, -2.4, 0.0, 1.2, 5.0, LocalDate.parse("2026-01-15")),
                sample("2026-01-15T12:00:00Z", 2, 8.0, 1.5, 0.0, 22.0, LocalDate.parse("2026-01-15")),
                sample("2026-07-20T15:00:00Z", 61, 31.2, 5.0, 0.0, 12.0, LocalDate.parse("2026-07-20"))
        ));

        WeatherInsights insights = service.calculateWeatherInsights(userId);

        assertEquals(3, insights.getSamplesCount());
        assertEquals(-2.4, insights.getColdestTemperature().getTemperature());
        assertEquals(31.2, insights.getHottestTemperature().getTemperature());
        assertEquals(22.0, insights.getWindiestSample().getWindSpeed());
        assertEquals(12.266666666666666, insights.getAverageTemperature(), 0.0001);
        assertEquals(2, insights.getRainySamplesCount());
        assertEquals(1, insights.getSnowySamplesCount());
        assertEquals("2026-07-20", insights.getWettestDay().getDate());
        assertEquals(5.0, insights.getWettestDay().getPrecipitation());
        assertEquals("Partly cloudy", insights.getDominantCondition().getLabel());
        assertEquals(2, insights.getDominantCondition().getSamplesCount());
        assertEquals(Instant.parse("2026-01-15T06:00:00Z"), insights.getWeatherCoverageStart());
        assertEquals(Instant.parse("2026-07-20T15:00:00Z"), insights.getWeatherCoverageEnd());
    }

    private Object[] sample(String observedAt,
                            int weatherCode,
                            double temperature,
                            double precipitation,
                            double snowfall,
                            double windSpeed,
                            LocalDate localDate) {
        return new Object[]{
                49.55,
                25.60,
                Timestamp.from(Instant.parse(observedAt)),
                weatherCode,
                temperature,
                precipitation,
                snowfall,
                windSpeed,
                localDate
        };
    }
}
