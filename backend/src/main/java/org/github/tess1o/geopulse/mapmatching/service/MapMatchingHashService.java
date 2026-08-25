package org.github.tess1o.geopulse.mapmatching.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
