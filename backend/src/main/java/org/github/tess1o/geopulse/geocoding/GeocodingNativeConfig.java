package org.github.tess1o.geopulse.geocoding;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.geocoding.client.GoogleMapsRestClient;
import org.github.tess1o.geopulse.geocoding.client.MapboxRestClient;
import org.github.tess1o.geopulse.geocoding.client.NominatimRestClient;
import org.github.tess1o.geopulse.geocoding.client.PhotonRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.dto.*;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.*;
import org.github.tess1o.geopulse.geocoding.model.mapbox.*;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddress;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.github.tess1o.geopulse.geocoding.rest.ReverseGeocodingResource;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;

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

        GeocodingConfigurationService.class,
        MapboxRestClient.class,
        GoogleMapsRestClient.class,
        NominatimRestClient.class,
        PhotonRestClient.class,

        PhotonResponse.class,
        PhotonResponse.Feature.class,
        PhotonResponse.Geometry.class,
        PhotonResponse.Properties.class,

        // Management DTOs
        ReverseGeocodingDTO.class,
        ReverseGeocodingUpdateDTO.class,
        ReverseGeocodingReconcileRequest.class,
        ReverseGeocodingSummaryDTO.class,
        GeocodingProviderDTO.class,
        ReverseGeocodingReconcileResult.class,
        ReverseGeocodingReconcileResult.ReconcileError.class,

        // Management REST and Service
        ReverseGeocodingResource.class,
        ReverseGeocodingManagementService.class
})
public class GeocodingNativeConfig {
}
