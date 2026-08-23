package org.github.tess1o.geopulse.mapmatching.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;

@ApplicationScoped
public class MapMatchingProfileResolver {

    public String resolveProfile(String movementType) {
        String normalized = movementType == null ? "" : movementType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WALK", "WALKING", "RUN", "RUNNING" -> "pedestrian";
            case "BICYCLE", "CYCLING", "BIKE" -> "bicycle";
            case "CAR", "MOTORCYCLE", "DRIVING" -> "auto";
            default -> null;
        };
    }
}
