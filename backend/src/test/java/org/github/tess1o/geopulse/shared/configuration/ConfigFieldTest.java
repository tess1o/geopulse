package org.github.tess1o.geopulse.shared.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFieldTest {

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    static class TestConfig {
        private String stringValue;
        private Integer intValue;
        private Boolean boolValue;
    }

    @Test
    void testConfigField_StringType() {
        ConfigField<TestConfig, String> field = new ConfigField<>(
                "test.string.property",
                "defaultValue",
                TestConfig::getStringValue,
                TestConfig::setStringValue,
                String::valueOf
        );

        // Test getters
        assertEquals("test.string.property", field.propertyName());
        assertEquals("defaultValue", field.defaultValue());

        // Test value operations
        TestConfig config = new TestConfig();

        // Test setValue and getValue
        field.setValue(config, "testValue");
        assertEquals("testValue", field.getValue(config));

        // Test parseValue
        assertEquals("parsedValue", field.parseValue("parsedValue"));
    }

    @Test
    void testConfigField_IntegerType() {
        ConfigField<TestConfig, Integer> field = new ConfigField<>(
                "test.int.property",
                "42",
                TestConfig::getIntValue,
                TestConfig::setIntValue,
                Integer::valueOf
        );

        TestConfig config = new TestConfig();

        // Test setValue and getValue
        field.setValue(config, 100);
        assertEquals(100, field.getValue(config));

        // Test parseValue
        assertEquals(123, field.parseValue("123"));
    }

    @Test
    void testConfigField_BooleanType() {
        ConfigField<TestConfig, Boolean> field = new ConfigField<>(
                "test.bool.property",
                "true",
                TestConfig::getBoolValue,
                TestConfig::setBoolValue,
                Boolean::valueOf
        );

        TestConfig config = new TestConfig();

        // Test setValue and getValue
        field.setValue(config, false);
        assertEquals(false, field.getValue(config));

        // Test parseValue
        assertEquals(true, field.parseValue("true"));
        assertEquals(false, field.parseValue("false"));
    }

    @Test
    void testConfigField_NullHandling() {
        ConfigField<TestConfig, String> field = new ConfigField<>(
                "test.property",
                "default",
                TestConfig::getStringValue,
                TestConfig::setStringValue,
                String::valueOf
        );

        TestConfig config = new TestConfig();

        // Test setting null value
        field.setValue(config, null);
        assertNull(field.getValue(config));
    }

    @Test
    void testConfigField_ParseValueException() {
        ConfigField<TestConfig, Integer> field = new ConfigField<>(
                "test.int.property",
                "42",
                TestConfig::getIntValue,
                TestConfig::setIntValue,
                Integer::valueOf
        );

        // Test invalid parsing
        assertThrows(NumberFormatException.class, () -> {
            field.parseValue("not-a-number");
        });
    }
}