package org.github.tess1o.geopulse.testsupport;

import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

public final class ExportTestFixtures {
    private ExportTestFixtures() {
    }

    public static ExportJob exportJob(UUID userId, Instant start, Instant end) {
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(start);
        dateRange.setEndDate(end);
        ExportJob job = new ExportJob();
        job.setUserId(userId);
        job.setJobId(UUID.randomUUID());
        job.setDateRange(dateRange);
        return job;
    }

    public static GpsPointEntity gpsPoint(UserEntity user, Instant timestamp, double lat, double lon) {
        return gpsPoint(user, timestamp, lat, lon, 100.0, 10.0, 5.0, 90.0, "test-device", GpsSourceType.OWNTRACKS);
    }

    public static GpsPointEntity gpsPoint(UserEntity user, Instant timestamp, double lat, double lon,
                                          double altitude, double velocity, double accuracy, double battery,
                                          String deviceId, GpsSourceType sourceType) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(user);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setAltitude(altitude);
        point.setVelocity(velocity);
        point.setAccuracy(accuracy);
        point.setBattery(battery);
        point.setDeviceId(deviceId);
        point.setSourceType(sourceType);
        point.setCreatedAt(timestamp);
        return point;
    }
}
