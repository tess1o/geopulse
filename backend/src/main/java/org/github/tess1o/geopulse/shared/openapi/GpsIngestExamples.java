package org.github.tess1o.geopulse.shared.openapi;

/**
 * Request body examples for the GPS ingest endpoints, written in each tracker app's own payload format. Used as
 * {@code @ExampleObject(value = ...)} so the documentation and code samples show what the app really sends.
 */
public final class GpsIngestExamples {

    public static final String OWNTRACKS = """
            {
              "_type": "location",
              "tid": "jp",
              "topic": "owntracks/jane/phone",
              "lat": 49.8419,
              "lon": 24.0315,
              "acc": 8,
              "alt": 296,
              "vac": 3,
              "vel": 10,
              "cog": 135,
              "batt": 82,
              "bs": 1,
              "conn": "m",
              "m": 1,
              "t": "u",
              "tst": 1749287730,
              "created_at": 1749287731
            }
            """;

    /** GPSLogger substitutes its variables into the body template from the GPSLogger setup guide, as strings. */
    public static final String GPSLOGGER = """
            {
              "_type": "location",
              "t": "u",
              "acc": "8.0",
              "alt": "296.0",
              "batt": "82",
              "bs": "false",
              "lat": "49.8419",
              "lon": "24.0315",
              "tst": "1749287730",
              "vel": "2.7"
            }
            """;

    public static final String COLOTA = """
            {
              "lat": 49.8419,
              "lon": 24.0315,
              "acc": 8,
              "alt": 296,
              "vel": 2.7,
              "batt": 82,
              "bs": 1,
              "tst": 1749287730,
              "bear": 135.0
            }
            """;

    public static final String OVERLAND = """
            {
              "locations": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [24.0315, 49.8419]
                  },
                  "properties": {
                    "timestamp": "2025-06-07T09:15:30Z",
                    "altitude": 296,
                    "speed": 2.7,
                    "horizontal_accuracy": 8,
                    "vertical_accuracy": 3,
                    "speed_accuracy": 0.5,
                    "course": 135,
                    "course_accuracy": 10,
                    "motion": ["walking"],
                    "battery_state": "unplugged",
                    "battery_level": 0.82,
                    "wifi": "",
                    "device_id": "jane-iphone"
                  }
                }
              ]
            }
            """;

    /** The body Traccar's position forwarding sends with {@code forward.type} set to {@code json}. */
    public static final String TRACCAR = """
            {
              "position": {
                "id": 18452,
                "deviceId": 3,
                "protocol": "osmand",
                "serverTime": "2025-06-07T09:15:31.000+00:00",
                "deviceTime": "2025-06-07T09:15:30.000+00:00",
                "fixTime": "2025-06-07T09:15:30.000+00:00",
                "valid": true,
                "latitude": 49.8419,
                "longitude": 24.0315,
                "altitude": 296.0,
                "speed": 5.2,
                "course": 135.0,
                "address": null,
                "accuracy": 8.0,
                "network": null,
                "geofenceIds": null,
                "attributes": {
                  "batteryLevel": 82.0,
                  "distance": 14.6,
                  "totalDistance": 152304.7,
                  "motion": true
                }
              },
              "device": {
                "id": 3,
                "name": "Pixel 8",
                "uniqueId": "864213",
                "status": "online",
                "lastUpdate": "2025-06-07T09:15:31.000+00:00",
                "positionId": 18452,
                "groupId": 0,
                "calendarId": 0,
                "phone": null,
                "model": null,
                "contact": null,
                "category": null,
                "disabled": false,
                "expirationTime": null,
                "attributes": {}
              }
            }
            """;

    /** The body the {@code rest_command} from the Home Assistant setup guide renders. */
    public static final String HOME_ASSISTANT = """
            {
              "device_id": "iphone_16",
              "timestamp": "2025-06-07T09:15:30+00:00",
              "location": {
                "latitude": 49.8419,
                "longitude": 24.0315,
                "accuracy": 8,
                "altitude": 296,
                "speed": 2.7
              },
              "battery": {
                "level": 82
              }
            }
            """;

    public static final String DAWARICH = """
            {
              "locations": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [24.0315, 49.8419]
                  },
                  "properties": {
                    "timestamp": "2025-06-07T09:15:30Z",
                    "altitude": 296,
                    "speed": 2.7,
                    "horizontal_accuracy": 8,
                    "vertical_accuracy": 3,
                    "speed_accuracy": 0.5,
                    "course": 135,
                    "course_accuracy": 10,
                    "device_id": "jane-iphone",
                    "track_id": "7f3c2a1e"
                  }
                }
              ]
            }
            """;

    public static final String MOBILE_APP = """
            {
              "points": [
                {
                  "timestamp": "2025-06-07T09:15:30Z",
                  "coordinates": {"lat": 49.8419, "lng": 24.0315},
                  "accuracy": 8.0,
                  "battery": 82.0,
                  "velocity": 1.4,
                  "altitude": 296.0
                },
                {
                  "timestamp": "2025-06-07T09:16:30Z",
                  "coordinates": {"lat": 49.8412, "lng": 24.0331},
                  "accuracy": 6.0,
                  "battery": 82.0,
                  "velocity": 1.5,
                  "altitude": 294.0
                }
              ]
            }
            """;

    private GpsIngestExamples() {
    }
}
