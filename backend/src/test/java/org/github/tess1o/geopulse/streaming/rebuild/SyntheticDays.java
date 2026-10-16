package org.github.tess1o.geopulse.streaming.rebuild;

import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded, realistic-looking phone recordings: noisy nights at home with outliers and signal loss, commutes, lunch
 * walks, errands, park loops, gym visits, and the evening "drift" at home that makes a stay look like a slow trip. The
 * same seed always produces the same messages.
 */
public final class SyntheticDays {

    public record Place(String name, double lat, double lon) {
    }

    public static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");
    public static final Place HOME = new Place("Home", 49.8420, 24.0310);
    public static final Place OFFICE = new Place("Office", 49.8350, 24.0750);
    public static final Place CAFE = new Place("Cafe", 49.8372, 24.0784);
    public static final Place SHOP = new Place("Shop", 49.8505, 24.0205);
    public static final Place GYM = new Place("Gym", 49.8452, 24.0602);
    public static final Place PARK_GATE = new Place("Park", 49.8302, 24.0498);
    /** The places with a favorite area; stays anywhere else get a geocoded name. */
    public static final List<Place> NAMED_PLACES = List.of(HOME, OFFICE, CAFE, SHOP, GYM, PARK_GATE);

    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private final Random random;
    private final List<OwnTracksLocationMessage> messages = new ArrayList<>();
    private double lat;
    private double lon;
    private long clock;

    private SyntheticDays(long seed) {
        this.random = new Random(seed);
    }

    /** {@code days} consecutive days starting at {@code firstDay}, oldest message first. */
    public static List<OwnTracksLocationMessage> generate(long seed, LocalDate firstDay, int days) {
        SyntheticDays generator = new SyntheticDays(seed);
        generator.lat = HOME.lat();
        generator.lon = HOME.lon();
        generator.clock = firstDay.atStartOfDay(ZONE).toEpochSecond();
        for (int day = 0; day < days; day++) {
            LocalDate date = firstDay.plusDays(day);
            if (date.getDayOfWeek().getValue() >= 6) {
                generator.weekend(date);
            } else {
                generator.workday(date);
            }
        }
        return generator.messages;
    }

    private void workday(LocalDate date) {
        night(date, at(date, 7, 20 + random.nextInt(50)));
        drive(OFFICE);
        stay(OFFICE, at(date, 11, 45 + random.nextInt(40)), 3, 10, 15);
        if (random.nextDouble() < 0.7) {
            walk(CAFE);
            stay(CAFE, clock + minutes(25 + random.nextInt(25)), 3, 8, 12);
            walk(OFFICE);
        }
        stay(OFFICE, at(date, 17, 10 + random.nextInt(70)), 4, 12, 15);
        if (random.nextDouble() < 0.5) {
            drive(SHOP);
            stay(SHOP, clock + minutes(12 + random.nextInt(14)), 2, 6, 12);
        }
        if (random.nextDouble() < 0.25) {
            drive(GYM);
            stay(GYM, clock + minutes(50 + random.nextInt(30)), 4, 10, 15);
        }
        drive(HOME);
        evening(date);
    }

    private void weekend(LocalDate date) {
        night(date, at(date, 9, random.nextInt(90)));
        stay(HOME, at(date, 11, random.nextInt(60)), 8, 20, 20);
        if (random.nextDouble() < 0.6) {
            drive(PARK_GATE);
            parkLoop();
            drive(HOME);
        }
        if (random.nextDouble() < 0.6) {
            drive(SHOP);
            stay(SHOP, clock + minutes(15 + random.nextInt(25)), 2, 6, 12);
            drive(HOME);
        }
        evening(date);
    }

    /** Midnight to {@code until} at home: sparse, noisy, sometimes with a long signal loss. */
    private void night(LocalDate date, long until) {
        if (random.nextDouble() < 0.3) {
            long lossStart = Math.max(clock, at(date, 1, random.nextInt(120)));
            stay(HOME, lossStart, 10, 20, 25);
            clock = Math.max(clock, Math.min(until - minutes(30), lossStart + minutes(90 + random.nextInt(150))));
        }
        stay(HOME, until, 10, 20, 25);
    }

    /** At home until midnight, with a long low-accuracy drift around the house on some evenings. */
    private void evening(LocalDate date) {
        long midnight = date.plusDays(1).atStartOfDay(ZONE).toEpochSecond();
        if (random.nextDouble() < 0.5) {
            stay(HOME, Math.min(midnight - minutes(200), clock + minutes(30 + random.nextInt(60))), 5, 12, 15);
            long driftEnd = Math.min(midnight - minutes(60), clock + minutes(60 + random.nextInt(90)));
            while (clock < driftEnd) {
                clock += minutes(8 + random.nextInt(12));
                point(offset(HOME, 35 + random.nextInt(55)), 20 + random.nextInt(40), random.nextDouble() * 2.0);
            }
        }
        stay(HOME, midnight - 60, 8, 20, 20);
    }

    private void parkLoop() {
        double[][] loop = {{350, 0}, {350, 400}, {0, 400}, {0, 0}};
        for (double[] corner : loop) {
            walkTo(shift(PARK_GATE.lat(), PARK_GATE.lon(), corner[0], corner[1]));
        }
        stay(PARK_GATE, clock + minutes(5 + random.nextInt(10)), 2, 5, 10);
    }

    private void stay(Place place, long until, int minInterval, int maxInterval, double jitterMeters) {
        while (clock + minutes(minInterval) < until) {
            clock += minutes(minInterval) + random.nextInt((maxInterval - minInterval) * 60 + 1);
            if (random.nextDouble() < 0.03) {
                // A bad fix far from where the phone really is.
                point(offset(place, 150 + random.nextInt(250)), 80 + random.nextInt(220), 0.0);
            } else {
                point(offset(place, random.nextDouble() * jitterMeters), 8 + random.nextInt(20), 0.0);
            }
        }
    }

    private void drive(Place to) {
        move(to.lat(), to.lon(), 9 + random.nextDouble() * 5, 20, 60, 10);
    }

    private void walk(Place to) {
        walkTo(new double[]{to.lat(), to.lon()});
    }

    private void walkTo(double[] to) {
        move(to[0], to[1], 1.25 + random.nextDouble() * 0.3, 30, 60, 6);
    }

    private void move(double toLat, double toLon, double speedMps, int minIntervalSec, int maxIntervalSec,
                      double lateralNoiseMeters) {
        double fromLat = lat;
        double fromLon = lon;
        double distance = distanceMeters(fromLat, fromLon, toLat, toLon);
        long duration = Math.max(60, Math.round(distance / speedMps));
        long start = clock;
        while (clock - start < duration) {
            clock += minIntervalSec + random.nextInt(maxIntervalSec - minIntervalSec + 1);
            double progress = Math.min(1.0, (clock - start) / (double) duration);
            double[] position = shift(fromLat + (toLat - fromLat) * progress, fromLon + (toLon - fromLon) * progress,
                    gaussian(lateralNoiseMeters), gaussian(lateralNoiseMeters));
            point(position, 5 + random.nextInt(10), speedMps * (0.8 + random.nextDouble() * 0.4));
        }
        lat = toLat;
        lon = toLon;
    }

    private void point(double[] position, double accuracy, double speedMps) {
        messages.add(OwnTracksLocationMessage.builder()
                .type("location")
                .tid("synthetic")
                .tst(clock)
                .createdAt(clock)
                .lat(position[0])
                .lon(position[1])
                .acc(accuracy)
                .vel(speedMps * 3.6)
                .alt(280.0)
                .batt(80.0)
                .build());
    }

    private double[] offset(Place place, double meters) {
        double angle = random.nextDouble() * 2 * Math.PI;
        return shift(place.lat(), place.lon(), meters * Math.sin(angle), meters * Math.cos(angle));
    }

    private static double[] shift(double lat, double lon, double northMeters, double eastMeters) {
        return new double[]{
                lat + northMeters / METERS_PER_DEGREE_LAT,
                lon + eastMeters / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)))
        };
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double north = (lat2 - lat1) * METERS_PER_DEGREE_LAT;
        double east = (lon2 - lon1) * METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat1));
        return Math.hypot(north, east);
    }

    private double gaussian(double sigma) {
        return random.nextGaussian() * sigma;
    }

    private static long at(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, 0), ZONE).plusMinutes(minute).toEpochSecond();
    }

    private static long minutes(int minutes) {
        return minutes * 60L;
    }
}
