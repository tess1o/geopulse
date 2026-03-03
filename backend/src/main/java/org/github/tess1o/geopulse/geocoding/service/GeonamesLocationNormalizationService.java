package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.geocoding.model.GeonamesNormalizedLocation;
import org.github.tess1o.geopulse.geocoding.repository.GeonamesCityRepository;

import java.util.Optional;

@ApplicationScoped
public class GeonamesLocationNormalizationService {

    private final GeonamesCityRepository geonamesCityRepository;

    @Inject
    public GeonamesLocationNormalizationService(GeonamesCityRepository geonamesCityRepository) {
        this.geonamesCityRepository = geonamesCityRepository;
    }

    public Optional<GeonamesNormalizedLocation> normalizeByCoordinates(
            double latitude,
            double longitude,
            Double maxDistanceMeters
    ) {
        return geonamesCityRepository.findNearestNormalizedLocation(latitude, longitude, maxDistanceMeters);
    }
}
