package org.github.tess1o.geopulse.geocoding;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.geocoding.client.GoogleMapsRestClient;
import org.github.tess1o.geopulse.geocoding.client.MapboxRestClient;
import org.github.tess1o.geopulse.geocoding.client.NominatimRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.*;
import org.github.tess1o.geopulse.geocoding.model.mapbox.*;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddress;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse;

@RegisterForReflection(targets = {
        ReverseGeocodingLocationEntity.class,
        FormattableGeocodingResult.class,
        SimpleFormattableResult.class,
        GoogleMapsAddressComponent.class,
        GoogleMapsGeometry.class,
        GoogleMapsLocation.class,
        GoogleMapsPlusCode.class,
        GoogleMapsResponse.class,
        GoogleMapsResult.class,
        GoogleMapsViewport.class,

        MapboxContext.class,
        MapboxFeature.class,
        MapboxGeometry.class,
        MapboxProperties.class,
        MapboxResponse.class,

        NominatimAddress.class,
        NominatimResponse.class,

        GeocodingConfig.class,
        MapboxRestClient.class,
        GoogleMapsRestClient.class,
        NominatimRestClient.class,
})
public class GeocodingNativeConfig {
}
