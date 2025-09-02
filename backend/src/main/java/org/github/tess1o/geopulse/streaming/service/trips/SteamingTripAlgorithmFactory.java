package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SteamingTripAlgorithmFactory {

    @Inject
    StreamingSingleTripAlgorithm singleTripAlgorithm;

    @Inject
    StreamingMultipleTripAlgorithm multipleTripAlgorithm;

    public StreamTripAlgorithm get(String algorithm) {
        return switch (algorithm) {
            case "single":
                yield singleTripAlgorithm;
            case "multiple":
                yield multipleTripAlgorithm;
            default:
                yield singleTripAlgorithm;
        };
    }
}
