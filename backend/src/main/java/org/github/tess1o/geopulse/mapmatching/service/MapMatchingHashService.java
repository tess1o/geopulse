package org.github.tess1o.geopulse.mapmatching.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.security.CryptoUtils;

import java.util.List;

@ApplicationScoped
public class MapMatchingHashService {

    public String configHash(String source) {
        return sha256(source == null ? "" : source);
    }

    public String inputHash(List<GpsPointEntity> points, Double maxAccuracy) {
        StringBuilder builder = new StringBuilder();
        builder.append("maxAccuracy=").append(maxAccuracy).append('\n');
        if (points != null) {
            for (GpsPointEntity point : points) {
                builder.append(point.getTimestamp()).append('|')
                        .append(point.getCoordinates() != null ? point.getCoordinates().getY() : null).append('|')
                        .append(point.getCoordinates() != null ? point.getCoordinates().getX() : null).append('|')
                        .append(point.getAccuracy()).append('\n');
            }
        }
        return sha256(builder.toString());
    }

    private String sha256(String value) {
        return CryptoUtils.sha256Hex(value);
    }
}
