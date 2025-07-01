package org.github.tess1o.geopulse.shared.configuration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for PropertyBasedConfigLoader focusing on registry pattern functionality.
 * This tests the load logic with direct config values rather than mocking the MicroProfile Config.
 */
class PropertyBasedConfigLoaderTest {

    @Setter
    @Getter
    @NoArgsConstructor
    static class TestConfig {
        private String name;
        private Integer count;
        private Boolean enabled;
    }

    private ConfigFieldRegistry<TestConfig> registry;

    @BeforeEach
    void setUp() {
        // Create test registry
        registry = new ConfigFieldRegistry<TestConfig>()
            .register(new ConfigField<>(
                "test.name",
                "defaultName",
                TestConfig::getName,
                TestConfig::setName,
                String::valueOf
            ))
            .register(new ConfigField<>(
                "test.count",
                "42",
                TestConfig::getCount,
                TestConfig::setCount,
                Integer::valueOf
            ))
            .register(new ConfigField<>(
                "test.enabled",
                "true",
                TestConfig::getEnabled,
                TestConfig::setEnabled,
                Boolean::valueOf
            ));
    }

    @Test
    void testLoadFromPropertiesWithDefaults() {
        TestConfig config = new TestConfig();
        
        // Test direct field configuration with default values
        for (ConfigField<TestConfig, ?> field : registry.getFields()) {
            setFieldValueFromString(field, config, field.defaultValue());
        }

        // Should use default values
        assertEquals("defaultName", config.getName());
        assertEquals(42, config.getCount());
        assertEquals(true, config.getEnabled());
    }

    @Test
    void testFieldParsing() {
        ConfigField<TestConfig, String> stringField = new ConfigField<>(
            "test.name",
            "default",
            TestConfig::getName,
            TestConfig::setName,
            String::valueOf
        );
        
        ConfigField<TestConfig, Integer> intField = new ConfigField<>(
            "test.count",
            "42",
            TestConfig::getCount,
            TestConfig::setCount,
            Integer::valueOf
        );
        
        ConfigField<TestConfig, Boolean> boolField = new ConfigField<>(
            "test.enabled",
            "true",
            TestConfig::getEnabled,
            TestConfig::setEnabled,
            Boolean::valueOf
        );

        // Test parsing functionality
        assertEquals("test", stringField.parseValue("test"));
        assertEquals(100, intField.parseValue("100"));
        assertEquals(false, boolField.parseValue("false"));
    }

    @Test
    void testFieldParsingExceptions() {
        ConfigField<TestConfig, Integer> intField = new ConfigField<>(
            "test.count",
            "42",
            TestConfig::getCount,
            TestConfig::setCount,
            Integer::valueOf
        );

        // Should throw NumberFormatException for invalid input
        assertThrows(NumberFormatException.class, () -> {
            intField.parseValue("not-a-number");
        });
    }

    @Test
    void testRegistryBasedConfiguration() {
        TestConfig config = new TestConfig();
        
        // Simulate loading with different values
        TestConfig sourceConfig = new TestConfig();
        sourceConfig.setName("configuredName");
        sourceConfig.setCount(100);
        sourceConfig.setEnabled(false);
        
        // Use registry to merge
        registry.mergeUserPreferences(config, sourceConfig);
        
        assertEquals("configuredName", config.getName());
        assertEquals(100, config.getCount());
        assertEquals(false, config.getEnabled());
    }

    @Test
    void testRegistryPartialConfiguration() {
        TestConfig baseConfig = new TestConfig();
        baseConfig.setName("originalName");
        baseConfig.setCount(50);
        baseConfig.setEnabled(true);
        
        TestConfig updates = new TestConfig();
        updates.setName("updatedName");
        updates.setCount(null); // Don't update
        updates.setEnabled(false);
        
        registry.mergeUserPreferences(baseConfig, updates);
        
        assertEquals("updatedName", baseConfig.getName()); // Updated
        assertEquals(50, baseConfig.getCount()); // Preserved
        assertEquals(false, baseConfig.getEnabled()); // Updated
    }

    @Test
    void testEmptyRegistry() {
        ConfigFieldRegistry<TestConfig> emptyRegistry = new ConfigFieldRegistry<>();
        
        TestConfig config = new TestConfig();
        config.setName("original");
        
        TestConfig updates = new TestConfig();
        updates.setName("updated");
        
        // Empty registry should not change anything
        emptyRegistry.mergeUserPreferences(config, updates);
        
        assertEquals("original", config.getName());
    }

    @Test
    void testDefaultValueLoading() {
        TestConfig config = new TestConfig();
        
        // Test that default values are properly parsed and set
        for (ConfigField<TestConfig, ?> field : registry.getFields()) {
            String defaultValue = field.defaultValue();
            Object parsedValue = field.parseValue(defaultValue);
            assertNotNull(parsedValue, "Default value should parse successfully for field: " + field.propertyName());
        }
    }

    // Helper method to set field value from string (simulates config loading)
    @SuppressWarnings("unchecked")
    private void setFieldValueFromString(ConfigField<TestConfig, ?> field, TestConfig config, String value) {
        Object parsedValue = field.parseValue(value);
        ((ConfigField<TestConfig, Object>) field).setValue(config, parsedValue);
    }
}