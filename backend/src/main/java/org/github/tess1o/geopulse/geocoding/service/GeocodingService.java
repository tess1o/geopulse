package org.github.tess1o.geopulse.geocoding.service;

import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

public interface GeocodingService {
    FormattableGeocodingResult getLocationName(Point point);
}
