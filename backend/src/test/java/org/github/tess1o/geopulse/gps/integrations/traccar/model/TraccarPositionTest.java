package org.github.tess1o.geopulse.gps.integrations.traccar.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class TraccarPositionTest {

    @Test
    void resolveTimestamp_parsesUtcAndOffsetAndNormalizesToUtc() {
        TraccarPosition utcPosition = new TraccarPosition();
        utcPosition.setFixTime(new TextNode("2026-03-09T12:34:56Z"));
        assertEquals(Instant.parse("2026-03-09T12:34:56Z"), utcPosition.resolveTimestamp());

        TraccarPosition offsetPosition = new TraccarPosition();
        offsetPosition.setFixTime(new TextNode("2026-03-09T14:34:56+02:00"));
        assertEquals(Instant.parse("2026-03-09T12:34:56Z"), offsetPosition.resolveTimestamp());
    }

    @Test
    void resolveTimestamp_treatsLocalDateTimeAsUtc() {
        TraccarPosition position = new TraccarPosition();
        position.setFixTime(new TextNode("2026-03-09 12:34:56"));
        assertEquals(Instant.parse("2026-03-09T12:34:56Z"), position.resolveTimestamp());
    }

    @Test
    void resolveTimestamp_supportsEpochSecondsAndMillis() {
        TraccarPosition seconds = new TraccarPosition();
        seconds.setFixTime(new LongNode(1700000000L));
        assertEquals(Instant.ofEpochSecond(1700000000L), seconds.resolveTimestamp());

        TraccarPosition millis = new TraccarPosition();
        millis.setFixTime(new LongNode(1700000000000L));
        assertEquals(Instant.ofEpochMilli(1700000000000L), millis.resolveTimestamp());
    }

    @Test
    void resolveTimestamp_fallsBackFromFixToDeviceToServer() {
        TraccarPosition position = new TraccarPosition();
        position.setFixTime(null);
        position.setDeviceTime(new TextNode("2026-03-09T12:34:56Z"));
        position.setServerTime(new TextNode("2026-03-09T12:00:00Z"));
        assertEquals(Instant.parse("2026-03-09T12:34:56Z"), position.resolveTimestamp());
    }

    @Test
    void resolveTimestamp_returnsNullForInvalidTimestamp() {
        TraccarPosition position = new TraccarPosition();
        position.setFixTime(new TextNode("not-a-date"));
        assertNull(position.resolveTimestamp());
    }

    @Test
    void deserializesRealTraccarPositionForwardingPayload() throws Exception {
        String payload = """
                {
                  "position": {
                    "id": 0,
                    "attributes": {
                      "motion": false,
                      "odometer": 1,
                      "activity": "still",
                      "batteryLevel": 95,
                      "distance": 0.007320623926311945,
                      "totalDistance": 0.011457419103832786
                    },
                    "deviceId": 1,
                    "protocol": "osmand",
                    "serverTime": "2026-03-09T14:00:01.161+00:00",
                    "deviceTime": "2026-03-09T13:59:50.005+00:00",
                    "fixTime": "2026-03-09T13:59:50.005+00:00",
                    "valid": true,
                    "latitude": 49.54708405872899,
                    "longitude": 25.59591655700441,
                    "altitude": 306.74,
                    "speed": 0.0,
                    "course": 0.0,
                    "address": null,
                    "accuracy": 0.0,
                    "network": null,
                    "geofenceIds": null
                  },
                  "device": {
                    "id": 1,
                    "attributes": {
                      "motionTime": 1773064411006,
                      "motionLat": 49.54708398244619,
                      "motionLon": 25.59591653224464
                    },
                    "groupId": 0,
                    "calendarId": 0,
                    "name": "93872193",
                    "uniqueId": "93872193",
                    "status": "online",
                    "lastUpdate": "2026-03-09T14:00:01.206+00:00",
                    "positionId": 0,
                    "phone": null,
                    "model": null,
                    "contact": null,
                    "category": null,
                    "disabled": false,
                    "expirationTime": null
                  }
                }
                """;

        ObjectMapper objectMapper = new ObjectMapper();
        TraccarPositionData data = objectMapper.readValue(payload, TraccarPositionData.class);

        assertNotNull(data);
        assertNotNull(data.getPosition());
        assertNotNull(data.getDevice());
        assertEquals(49.54708405872899, data.getPosition().getLatitude());
        assertEquals(25.59591655700441, data.getPosition().getLongitude());
        assertEquals("93872193", data.getDevice().getUniqueId());
        assertEquals(95.0, data.getPosition().resolveBatteryLevel());
        assertEquals(Instant.parse("2026-03-09T13:59:50.005Z"), data.getPosition().resolveTimestamp());
    }
}
