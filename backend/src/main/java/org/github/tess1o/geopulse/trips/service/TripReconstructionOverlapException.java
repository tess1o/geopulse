package org.github.tess1o.geopulse.trips.service;

/** A reconstruction segment covers time for which GPS points were recorded. */
public class TripReconstructionOverlapException extends RuntimeException {

    public TripReconstructionOverlapException(String message) {
        super(message);
    }
}
