package org.github.tess1o.geopulse.shared.geo;

import java.time.Instant;

//An interface for GPS points
public interface GpsPoint {

    double getLatitude();

    double getLongitude();

    Instant getTimestamp();
}
