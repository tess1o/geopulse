package org.github.tess1o.geopulse.gps.model;

public record GpsIngestionResponse(String status, String data) {

    public static GpsIngestionResponse success() {
        return new GpsIngestionResponse("success", "OK");
    }
}
