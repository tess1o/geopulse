package org.github.tess1o.geopulse.streaming.model.domain;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * One step of how the timeline engine decided on a stay, trip or data gap, such as "ended when a point 230 m away was
 * recorded". {@code code} names the decision and {@code params} hold the values it was based on, so the reason can be
 * shown in the user's language.
 * <p>
 * A full rebuild keeps every event's reasons in memory until the timeline is saved (several per event, hundreds of
 * thousands of events), so {@code params} is an immutable map over one array of alternating names and values rather
 * than an unmodifiable copy of a {@link LinkedHashMap}, and reasons without parameters share one empty map. A reason
 * with three parameters takes about 130 bytes instead of 300. The map keeps the insertion order and accepts null values,
 * like the one it replaces.
 */
public record TimelineReason(String code, Map<String, Object> params) {

    public TimelineReason {
        params = Params.copyOf(params);
    }

    public static TimelineReason of(String code) {
        return new TimelineReason(code, Map.of());
    }

    /** A reason with parameters given as alternating names and values. */
    public static TimelineReason of(String code, Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Parameters must be name/value pairs");
        }
        return new TimelineReason(code, Params.of(namesAndValues));
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        if (!params.isEmpty()) {
            map.put("params", new LinkedHashMap<>(params));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    static TimelineReason fromMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || !(map.get("code") instanceof String code)) {
            return null;
        }
        Object params = map.get("params");
        return new TimelineReason(code, params instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of());
    }

    /** Immutable, insertion-ordered map over {@code [name0, value0, name1, value1, ...]}. */
    private static final class Params extends AbstractMap<String, Object> {

        private static final Params EMPTY = new Params(new Object[0]);

        private final Object[] namesAndValues;

        private Params(Object[] namesAndValues) {
            this.namesAndValues = namesAndValues;
        }

        static Params copyOf(Map<String, Object> params) {
            if (params instanceof Params same) {
                return same;
            }
            if (params == null || params.isEmpty()) {
                return EMPTY;
            }
            Object[] array = new Object[params.size() * 2];
            int i = 0;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                array[i++] = Objects.requireNonNull(entry.getKey(), "Parameter name");
                array[i++] = entry.getValue();
            }
            return new Params(array);
        }

        /** Names are taken as {@code String.valueOf(name)}; a repeated name keeps its first position and last value. */
        static Params of(Object[] namesAndValues) {
            if (namesAndValues.length == 0) {
                return EMPTY;
            }
            Object[] array = new Object[namesAndValues.length];
            int size = 0;
            for (int i = 0; i < namesAndValues.length; i += 2) {
                String name = String.valueOf(namesAndValues[i]);
                int at = indexOf(array, size, name);
                if (at >= 0) {
                    array[at + 1] = namesAndValues[i + 1];
                } else {
                    array[size++] = name;
                    array[size++] = namesAndValues[i + 1];
                }
            }
            return new Params(size == array.length ? array : Arrays.copyOf(array, size));
        }

        private static int indexOf(Object[] array, int length, Object name) {
            for (int i = 0; i < length; i += 2) {
                if (array[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int size() {
            return namesAndValues.length / 2;
        }

        @Override
        public boolean containsKey(Object name) {
            return name != null && indexOf(namesAndValues, namesAndValues.length, name) >= 0;
        }

        @Override
        public Object get(Object name) {
            int at = name == null ? -1 : indexOf(namesAndValues, namesAndValues.length, name);
            return at >= 0 ? namesAndValues[at + 1] : null;
        }

        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return Params.this.size();
                }

                @Override
                public Iterator<Map.Entry<String, Object>> iterator() {
                    return new Iterator<>() {
                        private int next;

                        @Override
                        public boolean hasNext() {
                            return next < namesAndValues.length;
                        }

                        @Override
                        public Map.Entry<String, Object> next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            Map.Entry<String, Object> entry = new SimpleImmutableEntry<>(
                                    (String) namesAndValues[next], namesAndValues[next + 1]);
                            next += 2;
                            return entry;
                        }
                    };
                }
            };
        }
    }
}
