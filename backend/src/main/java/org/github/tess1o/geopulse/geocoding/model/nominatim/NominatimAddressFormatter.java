package org.github.tess1o.geopulse.geocoding.model.nominatim;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NominatimAddressFormatter {

    private static final String ADDRESS_NOT_FOUND = "Address not found";

    public String formatAddress(NominatimResponse nominatimResponse, double longitude, double latitude) {
        if (nominatimResponse == null || nominatimResponse.getAddress() == null) {
            return notFoundAddress(longitude, latitude);
        }
        var address = nominatimResponse.getAddress();

        if (address.getRoad() == null && nominatimResponse.getDisplayName() != null && !nominatimResponse.getDisplayName().isBlank()) {
            return nominatimResponse.getDisplayName();
        }

        String locationName = "";

        if (nominatimResponse.getName() != null && !nominatimResponse.getName().isBlank()) {
            locationName = nominatimResponse.getName();
        }

        String addressName;

        if (address.getHouseNumber() == null || address.getHouseNumber().isBlank()) {
            addressName = address.getRoad();
        } else {
            addressName = String.format("%s %s", address.getRoad(), address.getHouseNumber());
        }

        if (locationName.isBlank()) {
            return addressName;
        } else {
            return String.format("%s (%s)", locationName, addressName);
        }
    }

    public String notFoundAddress(double longitude, double latitude) {
        return String.format("%s (%s, %s)", ADDRESS_NOT_FOUND, longitude, latitude);
    }
    
}