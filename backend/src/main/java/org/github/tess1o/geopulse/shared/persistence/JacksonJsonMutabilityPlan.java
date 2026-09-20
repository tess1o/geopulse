package org.github.tess1o.geopulse.shared.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deep-copies a JSON-mapped entity attribute through Jackson instead of Java serialization.
 *
 * <p>Hibernate infers a {@code SerializableJavaType.SerializableMutabilityPlan} for any attribute whose target
 * type implements {@link java.io.Serializable} — see {@code RegistryHelper.determineMutabilityPlan}. That plan
 * snapshots values with an {@code ObjectOutputStream}, which a native image rejects for types it was not built
 * with: {@code EntityInitializerImpl.takeSnapshot} on a loaded {@code UserEntity} failed on the
 * {@code Boolean whatsNewEnabled} field nested in {@code NotificationPreferences}. Registering types for
 * reflection is not a workable answer for the {@code Map<String, Object>} columns, whose nested value types come
 * straight from parsed GPS payloads and cannot be enumerated ahead of time.</p>
 *
 * <p>Hibernate consults an explicit mutability plan before inferring one — {@code BasicValue.mutabilityPlan}
 * checks {@code getExplicitMutabilityPlan()} first — so wiring this per attribute with
 * {@link org.hibernate.annotations.Mutability} takes precedence over the inferred serializing plan. The Jackson
 * round-trip mirrors what {@code HypersistenceJacksonJsonSerializer} did before the entity mapping moved to
 * {@code @JdbcTypeCode(SqlTypes.JSON)}.</p>
 *
 * <p>Note: {@code disassemble}/{@code assemble} are inherited from {@link MutableMutabilityPlan} and still use
 * Java serialization. They are only reached by the second-level and query caches, neither of which GeoPulse
 * enables; enabling either would reintroduce the native-image failure on this path.</p>
 */
public class JacksonJsonMutabilityPlan extends MutableMutabilityPlan<Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    protected Object deepCopyNotNull(Object value) {
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.deepCopy();
        }
        // Containers are rebuilt element by element, recursively, so each element keeps its concrete type.
        // Copying a Map/List wholesale through Jackson would erase element types — a List<GpsTelemetryMappingEntry>
        // came back as a List<LinkedHashMap>, and Hibernate's later JacksonJsonFormatMapper.toString then failed
        // with "Receiver type java.util.LinkedHashMap is not an instance of ... GpsTelemetryMappingEntry".
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(Math.max(16, map.size() * 2));
            map.forEach((key, entry) -> copy.put(key, deepCopy(entry)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepCopy(element));
            }
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>(Math.max(16, set.size() * 2));
            for (Object element : set) {
                copy.add(deepCopy(element));
            }
            return copy;
        }
        // JSON scalars and enums are immutable, so the reference is already a deep copy.
        if (value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?>) {
            return value;
        }
        // Anything else is a POJO. Jackson round-trips it using its declared field types, so a preference
        // object's nested objects survive as themselves rather than degrading to Maps.
        return MAPPER.convertValue(value, value.getClass());
    }
}
