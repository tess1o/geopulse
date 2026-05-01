package org.github.tess1o.geopulse.shared.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.util.JsonSerializer;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.HibernateException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Restores pre-3.15-style JSON deep copy behavior for Hypersistence JsonType.
 *
 * <p>Hypersistence 3.15+ defaults to Java serialization for JSON deep-copy and
 * rejects non-serializable nested values. For dynamic JSON maps coming from GPS
 * payloads, that is too strict and can fail during entity snapshot cloning.
 * This serializer clones JSON values via Jackson, which works for generic
 * Map/List-based JSON content.</p>
 */
public class HypersistenceJacksonJsonSerializer implements JsonSerializer {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T clone(T value) {
        if (value == null || value instanceof String) {
            return value;
        }
        if (value instanceof JsonNode jsonNode) {
            return (T) jsonNode.deepCopy();
        }

        try {
            String json = ObjectMapperWrapper.INSTANCE.toString(value);

            if (value instanceof Map<?, ?>) {
                return (T) ObjectMapperWrapper.INSTANCE.fromString(json, LinkedHashMap.class);
            }
            if (value instanceof List<?>) {
                return (T) ObjectMapperWrapper.INSTANCE.fromString(json, ArrayList.class);
            }
            if (value instanceof Set<?>) {
                return (T) ObjectMapperWrapper.INSTANCE.fromString(json, LinkedHashSet.class);
            }

            return (T) ObjectMapperWrapper.INSTANCE.fromString(json, value.getClass());
        } catch (Exception e) {
            throw new HibernateException("Failed to clone JSON value via Jackson", e);
        }
    }
}
