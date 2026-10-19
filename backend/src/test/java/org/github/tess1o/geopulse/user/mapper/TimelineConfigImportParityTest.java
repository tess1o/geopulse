package org.github.tess1o.geopulse.user.mapper;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A debug import applies the exported timeline settings with {@link TimelinePreferencesMapper#configToRequest} and the
 * update request. A timeline setting missing from the request or the stored preferences is silently dropped, and the
 * imported timeline is then generated with a different value than the user had.
 */
@Tag("unit")
class TimelineConfigImportParityTest {

    /** Path simplification only shapes how trip paths are drawn; it is stored as a display preference. */
    private static final Set<String> DISPLAY_SETTINGS = Set.of(
            "pathSimplificationEnabled",
            "pathSimplificationTolerance",
            "pathMaxPoints",
            "pathAdaptiveSimplification");

    private final TimelinePreferencesMapper mapper = Mappers.getMapper(TimelinePreferencesMapper.class);

    @Test
    void everyTimelineSettingCanBeUpdatedAndStored() {
        Map<String, Class<?>> request = fieldTypes(UpdateTimelinePreferencesRequest.class);
        Map<String, Class<?>> stored = fieldTypes(TimelinePreferences.class);

        for (Field setting : generationSettings()) {
            assertThat(request).as("UpdateTimelinePreferencesRequest field for TimelineConfig.%s", setting.getName())
                    .containsEntry(setting.getName(), setting.getType());
            assertThat(stored).as("TimelinePreferences field for TimelineConfig.%s", setting.getName())
                    .containsEntry(setting.getName(), setting.getType());
        }
    }

    @Test
    void everyTimelineSettingSurvivesTheImportMapping() throws Exception {
        TimelineConfig exported = new TimelineConfig();
        List<Field> settings = generationSettings();
        for (int i = 0; i < settings.size(); i++) {
            settings.get(i).set(exported, sampleValue(settings.get(i).getType(), i));
        }

        TimelineConfig imported = mapper.preferencesToConfig(
                mapper.configToPreferences(mapper.requestToConfig(mapper.configToRequest(exported))));

        for (Field setting : settings) {
            assertThat(setting.get(imported)).as("TimelineConfig.%s after import", setting.getName())
                    .isEqualTo(setting.get(exported));
        }
    }

    /** Old exports and stored preferences may carry settings that were retired since; they must still read. */
    @Test
    void retiredSettingsInOldExportsAreIgnored() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        String old = "{\"staypointMaxAccuracyThreshold\": 60.0, \"staypointMinAccuracyRatio\": 0.5}";

        assertThat(json.readValue(old, TimelineConfig.class).getStaypointMaxAccuracyThreshold()).isEqualTo(60.0);
        assertThat(json.readValue(old, TimelinePreferences.class).getStaypointMaxAccuracyThreshold()).isEqualTo(60.0);
    }

    private static List<Field> generationSettings() {
        return Arrays.stream(TimelineConfig.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !DISPLAY_SETTINGS.contains(field.getName()))
                .peek(field -> field.setAccessible(true))
                .toList();
    }

    private static Map<String, Class<?>> fieldTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .collect(Collectors.toMap(Field::getName, Field::getType));
    }

    /** A distinct value per setting, so a mapping that swaps two settings fails too. */
    private static Object sampleValue(Class<?> type, int index) {
        Map<Class<?>, Function<Integer, Object>> samples = Map.of(
                Boolean.class, i -> i % 2 == 0,
                Integer.class, i -> 100 + i,
                Double.class, i -> 100.5 + i,
                String.class, i -> "value-" + i);
        Function<Integer, Object> sample = samples.get(type);
        if (sample == null) {
            throw new IllegalStateException("No sample value for TimelineConfig field type " + type);
        }
        return sample.apply(index);
    }
}
