package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GeoJSON FeatureCollection - root object containing multiple features.
 * Compliant with RFC 7946.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoJsonFeatureCollection {

    /**
     * Always "FeatureCollection" for GeoJSON compliance
     */
    @Builder.Default
    private String type = "FeatureCollection";

    /**
     * Array of GeoJSON features
     */
    @Builder.Default
    private List<GeoJsonFeature> features = new ArrayList<>();

    /**
     * Get all features with valid geometry
     */
    public List<GeoJsonFeature> getValidFeatures() {
        if (features == null) {
            return new ArrayList<>();
        }
        return features.stream()
                .filter(GeoJsonFeature::hasValidGeometry)
                .collect(Collectors.toList());
    }

    /**
     * Get total count of features
     */
    public int getFeatureCount() {
        return features != null ? features.size() : 0;
    }

    /**
     * Get count of valid features
     */
    public int getValidFeatureCount() {
        return getValidFeatures().size();
    }

    /**
     * Add a feature to the collection
     */
    public void addFeature(GeoJsonFeature feature) {
        if (features == null) {
            features = new ArrayList<>();
        }
        features.add(feature);
    }
}
