package org.github.tess1o.geopulse.gps.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Point;

import java.time.Instant;


@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "gps_points")
public class GpsPointEntity implements GpsPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point coordinates;

    private Instant timestamp;
    private Double accuracy;
    private Double battery;
    private Double velocity;
    private Double altitude;

    @Column(name = "source_type")
    @Enumerated(EnumType.STRING)
    private GpsSourceType sourceType;

    @Column(name = "created_at")
    private Instant createdAt;

    // Implementation of GpsPoint interface
    @Override
    public double getLatitude() {
        return coordinates != null ? coordinates.getY() : 0.0;
    }

    @Override
    public double getLongitude() {
        return coordinates != null ? coordinates.getX() : 0.0;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "id = " + id + ", " +
                "userId" + user.getId() + ", " +
                "deviceId = " + deviceId + ", " +
                "coordinates = " + coordinates + ", " +
                "timestamp = " + timestamp + ", " +
                "accuracy = " + accuracy + ", " +
                "battery = " + battery + ", " +
                "velocity = " + velocity + ", " +
                "altitude = " + altitude + ", " +
                "createdAt = " + createdAt + ")";
    }
}