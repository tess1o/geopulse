package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.shared.configuration.ConfigField;
import org.github.tess1o.geopulse.shared.configuration.ConfigFieldRegistry;
import org.github.tess1o.geopulse.timeline.config.TimelineConfigFieldRegistry;
import org.github.tess1o.geopulse.timeline.config.TimelineConfigurationProperties;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class TimelineConfigFieldRegistryTest {

    private ConfigFieldRegistry<TimelineConfig> registry;

    @BeforeEach
    void setUp() {
        // Create a mock properties object with default values
        TimelineConfigurationProperties mockProperties = Mockito.mock(TimelineConfigurationProperties.class);
        
        // Mock all the property getter methods to match the expected defaults
        when(mockProperties.getStaypointDetectionAlgorithm()).thenReturn("enhanced");
        when(mockProperties.getUseVelocityAccuracy()).thenReturn("true");
        when(mockProperties.getStaypointVelocityThreshold()).thenReturn("2.0");
        when(mockProperties.getStaypointAccuracyThreshold()).thenReturn("60.0");
        when(mockProperties.getStaypointMinAccuracyRatio()).thenReturn("0.5");
        when(mockProperties.getTripDetectionAlgorithm()).thenReturn("single");
        when(mockProperties.getTripMinDistanceMeters()).thenReturn("50");
        when(mockProperties.getTripMinDurationMinutes()).thenReturn("7");
        when(mockProperties.getMergeEnabled()).thenReturn("true");
        when(mockProperties.getMergeMaxDistanceMeters()).thenReturn("150");
        when(mockProperties.getMergeMaxTimeGapMinutes()).thenReturn("10");
        when(mockProperties.getPathSimplificationEnabled()).thenReturn("true");
        when(mockProperties.getPathSimplificationTolerance()).thenReturn("15.0");
        when(mockProperties.getPathMaxPoints()).thenReturn("100");
        when(mockProperties.getPathAdaptiveSimplification()).thenReturn("true");
        when(mockProperties.getDataGapThresholdSeconds()).thenReturn("10800");
        when(mockProperties.getDataGapMinDurationSeconds()).thenReturn("1800");
        
        TimelineConfigFieldRegistry fieldRegistry = new TimelineConfigFieldRegistry(mockProperties);
        registry = fieldRegistry.getRegistry();
    }

    @Test
    void testRegistry_HasCorrectNumberOfFields() {
        List<ConfigField<TimelineConfig, ?>> fields = registry.getFields();

        // Should have all timeline configuration fields (15 original + 2 data gap fields)
        assertEquals(17, fields.size());
    }

    @Test
    void testRegistry_ContainsAllExpectedProperties() {
        List<ConfigField<TimelineConfig, ?>> fields = registry.getFields();
        List<String> propertyNames = fields.stream()
                .map(ConfigField::propertyName)
                .toList();

        // Test all expected property names
        assertTrue(propertyNames.contains("geopulse.timeline.staypoint.detection.algorithm"));
        assertTrue(propertyNames.contains("geopulse.timeline.staypoint.use_velocity_accuracy"));
        assertTrue(propertyNames.contains("geopulse.timeline.staypoint.velocity.threshold"));
        assertTrue(propertyNames.contains("geopulse.timeline.staypoint.accuracy.threshold"));
        assertTrue(propertyNames.contains("geopulse.timeline.staypoint.min_accuracy_ratio"));
        assertTrue(propertyNames.contains("geopulse.timeline.trip.detection.algorithm"));
        assertTrue(propertyNames.contains("geopulse.timeline.trip.min_distance_meters"));
        assertTrue(propertyNames.contains("geopulse.timeline.trip.min_duration_minutes"));
        assertTrue(propertyNames.contains("geopulse.timeline.staypoint.merge.enabled"));
        assertTrue(propertyNames.contains("geopulse.timeline.staypoint.merge.max_distance_meters"));
        assertTrue(propertyNames.contains("geopulse.timeline.path.simplification.enabled"));
        assertTrue(propertyNames.contains("geopulse.timeline.path.simplification.tolerance"));
        assertTrue(propertyNames.contains("geopulse.timeline.path.simplification.max_points"));
        assertTrue(propertyNames.contains("geopulse.timeline.path.simplification.adaptive"));
    }

    @Test
    void testStaypointDetectionAlgorithm_FieldConfiguration() {
        ConfigField<TimelineConfig, ?> field = findFieldByProperty("geopulse.timeline.staypoint.detection.algorithm");

        assertNotNull(field);
        assertEquals("enhanced", field.defaultValue());

        // Test getter/setter functionality
        TimelineConfig config = new TimelineConfig();
        config.setStaypointDetectionAlgorithm("simple");
        assertEquals("simple", field.getValue(config));

        // Test setting value through field
        setFieldValue(field, config, "enhanced");
        assertEquals("enhanced", config.getStaypointDetectionAlgorithm());
    }

    @Test
    void testUseVelocityAccuracy_FieldConfiguration() {
        ConfigField<TimelineConfig, ?> field = findFieldByProperty("geopulse.timeline.staypoint.use_velocity_accuracy");

        assertNotNull(field);
        assertEquals("true", field.defaultValue());

        // Test boolean parsing
        assertEquals(true, field.parseValue("true"));
        assertEquals(false, field.parseValue("false"));

        // Test getter/setter functionality
        TimelineConfig config = new TimelineConfig();
        config.setUseVelocityAccuracy(false);
        assertEquals(false, field.getValue(config));
    }

    @Test
    void testVelocityThreshold_FieldConfiguration() {
        ConfigField<TimelineConfig, ?> field = findFieldByProperty("geopulse.timeline.staypoint.velocity.threshold");

        assertNotNull(field);
        assertEquals("2.0", field.defaultValue());

        // Test double parsing
        assertEquals(12.5, field.parseValue("12.5"));

        // Test getter/setter functionality
        TimelineConfig config = new TimelineConfig();
        config.setStaypointVelocityThreshold(15.0);
        assertEquals(15.0, field.getValue(config));
    }

    @Test
    void testTripMinDistanceMeters_FieldConfiguration() {
        ConfigField<TimelineConfig, ?> field = findFieldByProperty("geopulse.timeline.trip.min_distance_meters");

        assertNotNull(field);
        assertEquals("50", field.defaultValue());

        // Test integer parsing
        assertEquals(100, field.parseValue("100"));

        // Test getter/setter functionality
        TimelineConfig config = new TimelineConfig();
        config.setTripMinDistanceMeters(200);
        assertEquals(200, field.getValue(config));
    }

    @Test
    void testMergeEnabled_FieldConfiguration() {
        ConfigField<TimelineConfig, ?> field = findFieldByProperty("geopulse.timeline.staypoint.merge.enabled");

        assertNotNull(field);
        assertEquals("true", field.defaultValue());

        // Test getter/setter functionality
        TimelineConfig config = new TimelineConfig();
        config.setIsMergeEnabled(false);
        assertEquals(false, field.getValue(config));
    }

    @Test
    void testMergeUserPreferences_Integration() {
        TimelineConfig baseConfig = TimelineConfig.builder()
                .staypointDetectionAlgorithm("original")
                .useVelocityAccuracy(true)
                .staypointVelocityThreshold(8.0)
                .tripMinDistanceMeters(50)
                .isMergeEnabled(true)
                .build();

        TimelineConfig userPrefs = TimelineConfig.builder()
                .staypointDetectionAlgorithm("enhanced") // Override
                .useVelocityAccuracy(null) // Don't override
                .staypointVelocityThreshold(12.0) // Override
                .tripMinDistanceMeters(null) // Don't override
                .isMergeEnabled(false) // Override
                .build();

        registry.mergeUserPreferences(baseConfig, userPrefs);

        // Check merged results
        assertEquals("enhanced", baseConfig.getStaypointDetectionAlgorithm()); // Overridden
        assertEquals(true, baseConfig.getUseVelocityAccuracy()); // Original
        assertEquals(12.0, baseConfig.getStaypointVelocityThreshold()); // Overridden
        assertEquals(50, baseConfig.getTripMinDistanceMeters()); // Original
        assertEquals(false, baseConfig.getIsMergeEnabled()); // Overridden
    }

    @Test
    void testAllFieldTypes_ParseCorrectly() {
        List<ConfigField<TimelineConfig, ?>> fields = registry.getFields();

        for (ConfigField<TimelineConfig, ?> field : fields) {
            // Should not throw exception when parsing default values
            assertDoesNotThrow(() -> {
                Object parsed = field.parseValue(field.defaultValue());
                assertNotNull(parsed);
            }, "Field " + field.propertyName() + " should parse its default value");
        }
    }

    @Test
    void testFieldUniqueness() {
        List<ConfigField<TimelineConfig, ?>> fields = registry.getFields();
        List<String> propertyNames = fields.stream()
                .map(ConfigField::propertyName)
                .toList();

        // All property names should be unique
        assertEquals(fields.size(), propertyNames.stream().distinct().count(),
                "All property names should be unique");
    }

    // Helper methods
    private ConfigField<TimelineConfig, ?> findFieldByProperty(String propertyName) {
        return registry.getFields().stream()
                .filter(field -> field.propertyName().equals(propertyName))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private void setFieldValue(ConfigField<TimelineConfig, ?> field, TimelineConfig config, Object value) {
        ((ConfigField<TimelineConfig, Object>) field).setValue(config, value);
    }
}