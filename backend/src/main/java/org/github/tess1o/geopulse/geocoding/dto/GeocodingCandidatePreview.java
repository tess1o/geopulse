package org.github.tess1o.geopulse.geocoding.dto;

import java.util.List;
import java.util.UUID;

public record GeocodingCandidatePreview(UUID previewId, List<Candidate> candidates) {
    public record Candidate(int id, String displayName, String category, String address, String city,
                            String country, Double latitude, Double longitude, Double distanceMeters) {}
}
