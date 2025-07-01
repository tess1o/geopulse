package org.github.tess1o.geopulse.shared.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFieldRegistryTest {

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestConfig {
        private String name;
        private Integer value;
        private Boolean enabled;
    }

    private ConfigFieldRegistry<TestConfig> registry;
    private ConfigField<TestConfig, String> nameField;
    private ConfigField<TestConfig, Integer> valueField;
    private ConfigField<TestConfig, Boolean> enabledField;

    @BeforeEach
    void setUp() {
        nameField = new ConfigField<>(
                "test.name",
                "defaultName",
                TestConfig::getName,
                TestConfig::setName,
                String::valueOf
        );

        valueField = new ConfigField<>(
                "test.value",
                "100",
                TestConfig::getValue,
                TestConfig::setValue,
                Integer::valueOf
        );

        enabledField = new ConfigField<>(
                "test.enabled",
                "true",
                TestConfig::getEnabled,
                TestConfig::setEnabled,
                Boolean::valueOf
        );

        registry = new ConfigFieldRegistry<TestConfig>()
                .register(nameField)
                .register(valueField)
                .register(enabledField);
    }

    @Test
    void testRegister_AddsFieldsToRegistry() {
        List<ConfigField<TestConfig, ?>> fields = registry.getFields();

        assertEquals(3, fields.size());
        assertTrue(fields.contains(nameField));
        assertTrue(fields.contains(valueField));
        assertTrue(fields.contains(enabledField));
    }

    @Test
    void testGetFields_ReturnsImmutableCopy() {
        List<ConfigField<TestConfig, ?>> fields1 = registry.getFields();
        List<ConfigField<TestConfig, ?>> fields2 = registry.getFields();

        // Should be different instances (copies)
        assertNotSame(fields1, fields2);
        assertEquals(fields1, fields2);
    }

    @Test
    void testMergeUserPreferences_OnlyOverwritesNonNullValues() {
        TestConfig baseConfig = new TestConfig("baseName", 50, false);
        TestConfig userPrefs = new TestConfig("userName", null, true); // value is null

        registry.mergeUserPreferences(baseConfig, userPrefs);

        // Should overwrite name and enabled, but keep original value
        assertEquals("userName", baseConfig.getName());
        assertEquals(50, baseConfig.getValue()); // Should remain unchanged
        assertEquals(true, baseConfig.getEnabled());
    }

    @Test
    void testMergeUserPreferences_AllNullValues() {
        TestConfig baseConfig = new TestConfig("baseName", 50, false);
        TestConfig userPrefs = new TestConfig(null, null, null);

        registry.mergeUserPreferences(baseConfig, userPrefs);

        // Nothing should change
        assertEquals("baseName", baseConfig.getName());
        assertEquals(50, baseConfig.getValue());
        assertEquals(false, baseConfig.getEnabled());
    }

    @Test
    void testMergeUserPreferences_AllNonNullValues() {
        TestConfig baseConfig = new TestConfig("baseName", 50, false);
        TestConfig userPrefs = new TestConfig("userName", 200, true);

        registry.mergeUserPreferences(baseConfig, userPrefs);

        // Everything should be overwritten
        assertEquals("userName", baseConfig.getName());
        assertEquals(200, baseConfig.getValue());
        assertEquals(true, baseConfig.getEnabled());
    }

    @Test
    void testUpdateConfiguration_OnlyUpdatesNonNullValues() {
        TestConfig config = new TestConfig("originalName", 100, true);
        TestConfig updates = new TestConfig(null, 300, false); // name is null

        registry.updateConfiguration(config, updates);

        // Should update value and enabled, but keep original name
        assertEquals("originalName", config.getName()); // Should remain unchanged
        assertEquals(300, config.getValue());
        assertEquals(false, config.getEnabled());
    }

    @Test
    void testUpdateConfiguration_EmptyUpdates() {
        TestConfig config = new TestConfig("originalName", 100, true);
        TestConfig updates = new TestConfig(null, null, null);

        registry.updateConfiguration(config, updates);

        // Nothing should change
        assertEquals("originalName", config.getName());
        assertEquals(100, config.getValue());
        assertEquals(true, config.getEnabled());
    }

    @Test
    void testChainedRegistration() {
        ConfigFieldRegistry<TestConfig> chainedRegistry = new ConfigFieldRegistry<TestConfig>()
                .register(nameField)
                .register(valueField);

        assertEquals(2, chainedRegistry.getFields().size());
    }

    @Test
    void testEmptyRegistry() {
        ConfigFieldRegistry<TestConfig> emptyRegistry = new ConfigFieldRegistry<>();

        assertTrue(emptyRegistry.getFields().isEmpty());

        // Should not fail with empty registry
        TestConfig config = new TestConfig("test", 1, true);
        TestConfig updates = new TestConfig("updated", 2, false);

        emptyRegistry.mergeUserPreferences(config, updates);
        emptyRegistry.updateConfiguration(config, updates);

        // Config should remain unchanged
        assertEquals("test", config.getName());
        assertEquals(1, config.getValue());
        assertEquals(true, config.getEnabled());
    }
}