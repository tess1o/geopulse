package org.github.tess1o.geopulse.shared.configuration;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Represents a configurable field with metadata for automatic processing.
 *
 * @param <T> The type of the configuration object
 * @param <V> The type of the field value
 */
public record ConfigField<T, V>(String propertyName,
                                String defaultValue,
                                Function<T, V> getter,
                                BiConsumer<T, V> setter,
                                Function<String, V> parser) {
    public V getValue(T config) {
        return getter.apply(config);
    }

    public void setValue(T config, V value) {
        setter.accept(config, value);
    }

    public V parseValue(String stringValue) {
        return parser.apply(stringValue);
    }
}