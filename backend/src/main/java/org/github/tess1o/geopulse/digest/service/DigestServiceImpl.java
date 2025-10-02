package org.github.tess1o.geopulse.digest.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.digest.model.*;
import org.github.tess1o.geopulse.statistics.StatisticsService;
import org.github.tess1o.geopulse.statistics.model.BarChartData;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.TopPlace;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class DigestServiceImpl implements DigestService {

    @Inject
    StatisticsService statisticsService;

    @Inject
    StreamingTimelineAggregator streamingTimelineAggregator;

    @Inject
    EntityManager entityManager;

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM");

    @Override
    public TimeDigest getMonthlyDigest(UUID userId, int year, int month, String timezone) {
        log.info("Generating monthly digest for user {} - {}/{}", userId, year, month);

        // Calculate time range for the month using user's timezone
        ZoneId zoneId = ZoneId.of(timezone);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        Instant start = firstDay.atStartOfDay(zoneId).toInstant();
        Instant end = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);

        // Get current period statistics - use WEEKS for better chart readability
        UserStatistics currentStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);
        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);

        // Get previous month for comparison
        YearMonth previousMonth = yearMonth.minusMonths(1);
        Instant prevStart = previousMonth.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant prevEnd = previousMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd, ChartGroupMode.WEEKS);

        // Build digest
        return TimeDigest.builder()
                .period(buildPeriodInfo(year, month))
                .metrics(buildMetrics(userId, currentStats, timeline, start, end, zoneId))
                .comparison(buildComparison(currentStats, previousStats))
                .highlights(buildHighlights(currentStats, timeline, start, end, zoneId))
                .topPlaces(currentStats.getPlaces() != null ? currentStats.getPlaces() : List.of())
                .activityChart(buildActivityChartData(currentStats.getDistanceCarChart(), currentStats.getDistanceWalkChart()))
                .milestones(buildMilestones(userId, currentStats, timeline, "monthly", start, end, zoneId))
                .build();
    }

    @Override
    public TimeDigest getYearlyDigest(UUID userId, int year, String timezone) {
        log.info("Generating yearly digest for user {} - {}", userId, year);

        // Calculate time range for the year using user's timezone
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        LocalDate lastDay = LocalDate.of(year, 12, 31);

        Instant start = firstDay.atStartOfDay(zoneId).toInstant();
        Instant end = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);

        // Get current year statistics for overall metrics
        UserStatistics currentStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);
        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);

        // Get previous year for comparison
        Instant prevStart = firstDay.minusYears(1).atStartOfDay(zoneId).toInstant();
        Instant prevEnd = lastDay.minusYears(1).plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd, ChartGroupMode.WEEKS);

        // Generate monthly chart data for yearly view (12 bars)
        ActivityChartData monthlyChart = buildMonthlyChartForYear(userId, year, zoneId);

        // Build digest
        return TimeDigest.builder()
                .period(buildPeriodInfo(year, null))
                .metrics(buildMetrics(userId, currentStats, timeline, start, end, zoneId))
                .comparison(buildComparison(currentStats, previousStats))
                .highlights(buildHighlights(currentStats, timeline, start, end, zoneId))
                .topPlaces(currentStats.getPlaces() != null ? currentStats.getPlaces() : List.of())
                .activityChart(monthlyChart)
                .milestones(buildMilestones(userId, currentStats, timeline, "yearly", start, end, zoneId))
                .build();
    }

    private PeriodInfo buildPeriodInfo(int year, Integer month) {
        if (month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            String displayName = yearMonth.format(MONTH_YEAR_FORMATTER);
            return PeriodInfo.builder()
                    .year(year)
                    .month(month)
                    .displayName(displayName)
                    .type("monthly")
                    .build();
        } else {
            return PeriodInfo.builder()
                    .year(year)
                    .month(null)
                    .displayName(String.valueOf(year))
                    .type("yearly")
                    .build();
        }
    }

    private DigestMetrics buildMetrics(UUID userId, UserStatistics stats, MovementTimelineDTO timeline, Instant start, Instant end, ZoneId zoneId) {
        // Calculate active days from timeline
        // Only count days that fall within the period boundaries
        LocalDate periodStart = LocalDate.ofInstant(start, zoneId);
        LocalDate periodEnd = LocalDate.ofInstant(end, zoneId);
        Set<LocalDate> activeDays = new HashSet<>();

        if (timeline.getStays() != null) {
            timeline.getStays().forEach(stay -> {
                LocalDate stayDate = LocalDate.ofInstant(stay.getTimestamp(), zoneId);
                // Only add if the date falls within the period
                if (!stayDate.isBefore(periodStart) && !stayDate.isAfter(periodEnd)) {
                    activeDays.add(stayDate);
                }
            });
        }

        if (timeline.getTrips() != null) {
            timeline.getTrips().forEach(trip -> {
                LocalDate tripDate = LocalDate.ofInstant(trip.getTimestamp(), zoneId);
                // Only add if the date falls within the period
                if (!tripDate.isBefore(periodStart) && !tripDate.isAfter(periodEnd)) {
                    activeDays.add(tripDate);
                }
            });
        }

        // Calculate unique cities from database (proper city field, not location names)
        int citiesCount = getUniqueCitiesCount(userId, start, end);

        // Calculate car and walk distances from charts
        double carDistance = 0;
        double walkDistance = 0;

        if (stats.getDistanceCarChart() != null && stats.getDistanceCarChart().getData() != null) {
            carDistance = java.util.Arrays.stream(stats.getDistanceCarChart().getData()).sum();
        }

        if (stats.getDistanceWalkChart() != null && stats.getDistanceWalkChart().getData() != null) {
            walkDistance = java.util.Arrays.stream(stats.getDistanceWalkChart().getData()).sum();
        }

        return DigestMetrics.builder()
                .totalDistance(stats.getTotalDistanceMeters())
                .activeDays(activeDays.size())
                .citiesVisited(citiesCount)
                .tripCount(timeline.getTripsCount())
                .stayCount(timeline.getStaysCount())
                // Enhanced metrics
                .carDistance(carDistance * 1000) // charts are in km, convert to meters
                .walkDistance(walkDistance * 1000) // charts are in km, convert to meters
                .timeMoving(stats.getTimeMoving())
                .dailyAverageDistance(stats.getDailyAverageDistanceMeters())
                .build();
    }

    private PeriodComparison buildComparison(UserStatistics current, UserStatistics previous) {
        double currentDistance = current.getTotalDistanceMeters();
        double previousDistance = previous.getTotalDistanceMeters();

        double percentChange = 0;
        String direction = "same";

        if (previousDistance > 0) {
            percentChange = ((currentDistance - previousDistance) / previousDistance) * 100;
            if (percentChange > 0.5) {
                direction = "increase";
            } else if (percentChange < -0.5) {
                direction = "decrease";
            }
        } else if (currentDistance > 0) {
            direction = "increase";
            percentChange = 100;
        }

        return PeriodComparison.builder()
                .totalDistance(previousDistance)
                .percentChange(Math.round(percentChange * 10.0) / 10.0) // Round to 1 decimal
                .direction(direction)
                .activeDays(0) // Could be calculated from previous timeline if needed
                .activeDaysChange(0)
                .build();
    }

    private DigestHighlight buildHighlights(UserStatistics stats, MovementTimelineDTO timeline, Instant start, Instant end, ZoneId zoneId) {
        DigestHighlight.TripHighlight longestTrip = null;
        DigestHighlight.BusiestDay busiestDay = null;

        // Define period boundaries for filtering
        LocalDate periodStart = LocalDate.ofInstant(start, zoneId);
        LocalDate periodEnd = LocalDate.ofInstant(end, zoneId);

        // Find longest trip (within period)
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            var maxTrip = timeline.getTrips().stream()
                    .filter(trip -> {
                        LocalDate tripDate = LocalDate.ofInstant(trip.getTimestamp(), zoneId);
                        return !tripDate.isBefore(periodStart) && !tripDate.isAfter(periodEnd);
                    })
                    .max(Comparator.comparingDouble(TimelineTripDTO::getDistanceMeters))
                    .orElse(null);

            if (maxTrip != null) {
                String destination = "Trip"; // Simplified for now as we don't have destination name

                longestTrip = DigestHighlight.TripHighlight.builder()
                        .distance(maxTrip.getDistanceMeters())
                        .destination(destination)
                        .date(maxTrip.getTimestamp())
                        .build();
            }
        }

        // Find most visited place
        DigestHighlight.PlaceHighlight mostVisited = null;
        if (stats.getPlaces() != null && !stats.getPlaces().isEmpty()) {
            TopPlace topPlace = stats.getPlaces().get(0);
            mostVisited = DigestHighlight.PlaceHighlight.builder()
                    .name(topPlace.getName())
                    .visits((int) topPlace.getVisits())
                    .build();
        }

        // Find busiest day (day with most trips) - only within period
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            Map<LocalDate, Long> tripsByDay = timeline.getTrips().stream()
                    .map(trip -> LocalDate.ofInstant(trip.getTimestamp(), zoneId))
                    .filter(tripDate -> !tripDate.isBefore(periodStart) && !tripDate.isAfter(periodEnd))
                    .collect(Collectors.groupingBy(
                            tripDate -> tripDate,
                            Collectors.counting()
                    ));

            var busiestEntry = tripsByDay.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (busiestEntry != null) {
                LocalDate busiestDate = busiestEntry.getKey();
                double dayDistance = timeline.getTrips().stream()
                        .filter(trip -> LocalDate.ofInstant(trip.getTimestamp(), zoneId).equals(busiestDate))
                        .mapToDouble(TimelineTripDTO::getDistanceMeters)
                        .sum();

                busiestDay = DigestHighlight.BusiestDay.builder()
                        .date(busiestDate.atStartOfDay(zoneId).toInstant())
                        .trips(busiestEntry.getValue().intValue())
                        .distance(dayDistance)
                        .build();
            }
        }

        // Calculate peak hours from trips
        String[] peakHours = calculatePeakHours(timeline, zoneId);

        return DigestHighlight.builder()
                .longestTrip(longestTrip)
                .mostVisited(mostVisited)
                .busiestDay(busiestDay)
                .peakHours(peakHours)
                .build();
    }

    private String[] calculatePeakHours(MovementTimelineDTO timeline, ZoneId zoneId) {
        if (timeline.getTrips() == null || timeline.getTrips().isEmpty()) {
            return new String[0];
        }

        // Count trips by hour of day
        Map<Integer, Long> tripsByHour = timeline.getTrips().stream()
                .collect(Collectors.groupingBy(
                        trip -> LocalDateTime.ofInstant(trip.getTimestamp(), zoneId).getHour(),
                        Collectors.counting()
                ));

        if (tripsByHour.isEmpty()) {
            return new String[0];
        }

        // Get top 4-5 most active hours (to have enough to group)
        List<Integer> topHours = tripsByHour.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .sorted() // Sort by hour (not trip count) to find consecutive hours
                .toList();

        // Group consecutive hours into ranges
        List<HourRange> ranges = new ArrayList<>();
        int rangeStart = topHours.get(0);
        int rangeEnd = topHours.get(0);
        long rangeTripCount = tripsByHour.get(topHours.get(0));

        for (int i = 1; i < topHours.size(); i++) {
            int currentHour = topHours.get(i);
            int prevHour = topHours.get(i - 1);

            // Check if consecutive (handles 23->0 wrap)
            boolean isConsecutive = (currentHour == prevHour + 1) ||
                                   (prevHour == 23 && currentHour == 0);

            if (isConsecutive) {
                // Extend current range
                rangeEnd = currentHour;
                rangeTripCount += tripsByHour.get(currentHour);
            } else {
                // Save current range and start new one
                ranges.add(new HourRange(rangeStart, rangeEnd + 1, rangeTripCount));
                rangeStart = currentHour;
                rangeEnd = currentHour;
                rangeTripCount = tripsByHour.get(currentHour);
            }
        }
        // Don't forget the last range
        ranges.add(new HourRange(rangeStart, rangeEnd + 1, rangeTripCount));

        // Return top 2 ranges by trip count
        return ranges.stream()
                .sorted(Comparator.comparingLong(HourRange::totalTrips).reversed())
                .limit(2)
                .map(range -> formatHourRange(range.start, range.end))
                .toArray(String[]::new);
    }

    // Helper record to store hour ranges
    private record HourRange(int start, int end, long totalTrips) {
    }

    private String formatHourRange(int startHour, int endHour) {
        String startPeriod = startHour < 12 ? "AM" : "PM";
        String endPeriod = endHour < 12 ? "AM" : "PM";

        int displayStartHour = startHour == 0 ? 12 : (startHour > 12 ? startHour - 12 : startHour);
        int displayEndHour = endHour == 0 ? 12 : (endHour > 12 ? endHour - 12 : endHour);

        if (startPeriod.equals(endPeriod)) {
            return displayStartHour + "-" + displayEndHour + " " + endPeriod;
        } else {
            return displayStartHour + " " + startPeriod + "-" + displayEndHour + " " + endPeriod;
        }
    }

    private List<Milestone> buildMilestones(UUID userId, UserStatistics stats, MovementTimelineDTO timeline, String periodType, Instant start, Instant end, ZoneId zoneId) {
        List<Milestone> milestones = new ArrayList<>();

        boolean isMonthly = "monthly".equals(periodType);
        double distanceKm = stats.getTotalDistanceMeters() / 1000.0;

        // Calculate unique places from database - this will count unique location_name entries
        // which is what we want for "places explored" (could be specific locations within cities)
        int placesCount = getUniquePlacesCount(userId, start, end);

        int tripCount = timeline.getTripsCount();

        // Calculate active days - only within period boundaries
        LocalDate periodStart = LocalDate.ofInstant(start, zoneId);
        LocalDate periodEnd = LocalDate.ofInstant(end, zoneId);
        Set<LocalDate> activeDays = new HashSet<>();

        if (timeline.getStays() != null) {
            timeline.getStays().forEach(stay -> {
                LocalDate stayDate = LocalDate.ofInstant(stay.getTimestamp(), zoneId);
                if (!stayDate.isBefore(periodStart) && !stayDate.isAfter(periodEnd)) {
                    activeDays.add(stayDate);
                }
            });
        }
        if (timeline.getTrips() != null) {
            timeline.getTrips().forEach(trip -> {
                LocalDate tripDate = LocalDate.ofInstant(trip.getTimestamp(), zoneId);
                if (!tripDate.isBefore(periodStart) && !tripDate.isAfter(periodEnd)) {
                    activeDays.add(tripDate);
                }
            });
        }
        int activeDaysCount = activeDays.size();

        // Find longest single trip
        double longestTripKm = 0;
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            longestTripKm = timeline.getTrips().stream()
                .mapToDouble(trip -> trip.getDistanceMeters() / 1000.0)
                .max()
                .orElse(0);
        }

        // Distance Milestones
        Milestone distanceMilestone = getDistanceMilestone(distanceKm, isMonthly);
        if (distanceMilestone != null) {
            milestones.add(distanceMilestone);
        }

        // Places Milestones
        Milestone placesMilestone = getPlacesMilestone(placesCount, isMonthly);
        if (placesMilestone != null) {
            milestones.add(placesMilestone);
        }

        // Trip Activity Milestones
        Milestone tripsMilestone = getTripsMilestone(tripCount, isMonthly);
        if (tripsMilestone != null) {
            milestones.add(tripsMilestone);
        }

        // Active Days Milestones
        Milestone activeDaysMilestone = getActiveDaysMilestone(activeDaysCount, isMonthly);
        if (activeDaysMilestone != null) {
            milestones.add(activeDaysMilestone);
        }

        // Epic Journey Milestones
        Milestone epicJourneyMilestone = getEpicJourneyMilestone(longestTripKm, isMonthly);
        if (epicJourneyMilestone != null) {
            milestones.add(epicJourneyMilestone);
        }

        // Sort by tier (Diamond > Gold > Silver > Bronze)
        milestones.sort((m1, m2) -> {
            int tier1 = getTierValue(m1.getTier());
            int tier2 = getTierValue(m2.getTier());
            return Integer.compare(tier2, tier1); // Descending order
        });

        return milestones;
    }

    private int getTierValue(String tier) {
        return switch (tier) {
            case "diamond" -> 4;
            case "gold" -> 3;
            case "silver" -> 2;
            case "bronze" -> 1;
            default -> 0;
        };
    }

    private Milestone getDistanceMilestone(double distanceKm, boolean isMonthly) {
        if (isMonthly) {
            if (distanceKm >= 2000) {
                return Milestone.builder()
                    .id("distance_champion")
                    .title("Distance Champion")
                    .description(String.format("Traveled %.0f km this month", distanceKm))
                    .icon("💎")
                    .tier("diamond")
                    .category("distance")
                    .build();
            } else if (distanceKm >= 1000) {
                return Milestone.builder()
                    .id("road_warrior")
                    .title("Road Warrior")
                    .description(String.format("Traveled %.0f km this month", distanceKm))
                    .icon("🥇")
                    .tier("gold")
                    .category("distance")
                    .build();
            } else if (distanceKm >= 500) {
                return Milestone.builder()
                    .id("active_explorer")
                    .title("Active Explorer")
                    .description(String.format("Traveled %.0f km this month", distanceKm))
                    .icon("🥈")
                    .tier("silver")
                    .category("distance")
                    .build();
            } else if (distanceKm >= 100) {
                return Milestone.builder()
                    .id("local_traveler")
                    .title("Local Traveler")
                    .description(String.format("Traveled %.0f km this month", distanceKm))
                    .icon("🥉")
                    .tier("bronze")
                    .category("distance")
                    .build();
            }
        } else {
            // Yearly thresholds
            if (distanceKm >= 25000) {
                return Milestone.builder()
                    .id("epic_traveler")
                    .title("Epic Traveler")
                    .description(String.format("Traveled %.0f km this year", distanceKm))
                    .icon("💎")
                    .tier("diamond")
                    .category("distance")
                    .build();
            } else if (distanceKm >= 10000) {
                return Milestone.builder()
                    .id("road_warrior")
                    .title("Road Warrior")
                    .description(String.format("Traveled %.0f km this year", distanceKm))
                    .icon("🥇")
                    .tier("gold")
                    .category("distance")
                    .build();
            } else if (distanceKm >= 5000) {
                return Milestone.builder()
                    .id("active_explorer")
                    .title("Active Explorer")
                    .description(String.format("Traveled %.0f km this year", distanceKm))
                    .icon("🥈")
                    .tier("silver")
                    .category("distance")
                    .build();
            } else if (distanceKm >= 1000) {
                return Milestone.builder()
                    .id("casual_traveler")
                    .title("Casual Traveler")
                    .description(String.format("Traveled %.0f km this year", distanceKm))
                    .icon("🥉")
                    .tier("bronze")
                    .category("distance")
                    .build();
            }
        }
        return null;
    }

    private Milestone getPlacesMilestone(int placesCount, boolean isMonthly) {
        if (isMonthly) {
            if (placesCount >= 30) {
                return Milestone.builder()
                    .id("ultimate_explorer")
                    .title("Ultimate Explorer")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("💎")
                    .tier("diamond")
                    .category("places")
                    .build();
            } else if (placesCount >= 20) {
                return Milestone.builder()
                    .id("city_explorer")
                    .title("City Explorer")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("🥇")
                    .tier("gold")
                    .category("places")
                    .build();
            } else if (placesCount >= 10) {
                return Milestone.builder()
                    .id("local_navigator")
                    .title("Local Navigator")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("🥈")
                    .tier("silver")
                    .category("places")
                    .build();
            } else if (placesCount >= 5) {
                return Milestone.builder()
                    .id("place_explorer")
                    .title("Place Explorer")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("🥉")
                    .tier("bronze")
                    .category("places")
                    .build();
            }
        } else {
            // Yearly thresholds
            if (placesCount >= 200) {
                return Milestone.builder()
                    .id("world_explorer")
                    .title("World Explorer")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("💎")
                    .tier("diamond")
                    .category("places")
                    .build();
            } else if (placesCount >= 100) {
                return Milestone.builder()
                    .id("globe_trotter")
                    .title("Globe Trotter")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("🥇")
                    .tier("gold")
                    .category("places")
                    .build();
            } else if (placesCount >= 50) {
                return Milestone.builder()
                    .id("city_navigator")
                    .title("City Navigator")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("🥈")
                    .tier("silver")
                    .category("places")
                    .build();
            } else if (placesCount >= 25) {
                return Milestone.builder()
                    .id("local_explorer")
                    .title("Local Explorer")
                    .description(String.format("Visited %d unique places", placesCount))
                    .icon("🥉")
                    .tier("bronze")
                    .category("places")
                    .build();
            }
        }
        return null;
    }

    private Milestone getTripsMilestone(int tripCount, boolean isMonthly) {
        if (isMonthly) {
            if (tripCount >= 100) {
                return Milestone.builder()
                    .id("road_regular")
                    .title("Road Regular")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("💎")
                    .tier("diamond")
                    .category("trips")
                    .build();
            } else if (tripCount >= 50) {
                return Milestone.builder()
                    .id("frequent_flyer")
                    .title("Frequent Flyer")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("🥇")
                    .tier("gold")
                    .category("trips")
                    .build();
            } else if (tripCount >= 30) {
                return Milestone.builder()
                    .id("regular_traveler")
                    .title("Regular Traveler")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("🥈")
                    .tier("silver")
                    .category("trips")
                    .build();
            } else if (tripCount >= 10) {
                return Milestone.builder()
                    .id("getting_started")
                    .title("Getting Started")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("🥉")
                    .tier("bronze")
                    .category("trips")
                    .build();
            }
        } else {
            // Yearly thresholds
            if (tripCount >= 1000) {
                return Milestone.builder()
                    .id("always_moving")
                    .title("Always Moving")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("💎")
                    .tier("diamond")
                    .category("trips")
                    .build();
            } else if (tripCount >= 500) {
                return Milestone.builder()
                    .id("road_regular")
                    .title("Road Regular")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("🥇")
                    .tier("gold")
                    .category("trips")
                    .build();
            } else if (tripCount >= 300) {
                return Milestone.builder()
                    .id("frequent_traveler")
                    .title("Frequent Traveler")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("🥈")
                    .tier("silver")
                    .category("trips")
                    .build();
            } else if (tripCount >= 100) {
                return Milestone.builder()
                    .id("regular_traveler")
                    .title("Regular Traveler")
                    .description(String.format("Completed %d trips", tripCount))
                    .icon("🥉")
                    .tier("bronze")
                    .category("trips")
                    .build();
            }
        }
        return null;
    }

    private Milestone getActiveDaysMilestone(int activeDaysCount, boolean isMonthly) {
        if (isMonthly) {
            if (activeDaysCount >= 25) {
                return Milestone.builder()
                    .id("full_month")
                    .title("Full Month")
                    .description(String.format("Active %d days", activeDaysCount))
                    .icon("💎")
                    .tier("diamond")
                    .category("activeDays")
                    .build();
            } else if (activeDaysCount >= 20) {
                return Milestone.builder()
                    .id("mostly_active")
                    .title("Mostly Active")
                    .description(String.format("Active %d days", activeDaysCount))
                    .icon("🥇")
                    .tier("gold")
                    .category("activeDays")
                    .build();
            } else if (activeDaysCount >= 15) {
                return Milestone.builder()
                    .id("half_month")
                    .title("Half Month")
                    .description(String.format("Active %d days", activeDaysCount))
                    .icon("🥈")
                    .tier("silver")
                    .category("activeDays")
                    .build();
            } else if (activeDaysCount >= 7) {
                return Milestone.builder()
                    .id("active_week")
                    .title("Active Week")
                    .description(String.format("Active %d days", activeDaysCount))
                    .icon("🥉")
                    .tier("bronze")
                    .category("activeDays")
                    .build();
            }
        } else {
            // Yearly thresholds
            if (activeDaysCount >= 330) {
                return Milestone.builder()
                    .id("year_round")
                    .title("Year Round")
                    .description(String.format("Active %d days (90%%)", activeDaysCount))
                    .icon("💎")
                    .tier("diamond")
                    .category("activeDays")
                    .build();
            } else if (activeDaysCount >= 270) {
                return Milestone.builder()
                    .id("mostly_active")
                    .title("Mostly Active")
                    .description(String.format("Active %d days (75%%)", activeDaysCount))
                    .icon("🥇")
                    .tier("gold")
                    .category("activeDays")
                    .build();
            } else if (activeDaysCount >= 180) {
                return Milestone.builder()
                    .id("half_year")
                    .title("Half Year")
                    .description(String.format("Active %d days (50%%)", activeDaysCount))
                    .icon("🥈")
                    .tier("silver")
                    .category("activeDays")
                    .build();
            } else if (activeDaysCount >= 90) {
                return Milestone.builder()
                    .id("quarterly_active")
                    .title("Quarterly Active")
                    .description(String.format("Active %d days (25%%)", activeDaysCount))
                    .icon("🥉")
                    .tier("bronze")
                    .category("activeDays")
                    .build();
            }
        }
        return null;
    }

    private Milestone getEpicJourneyMilestone(double longestTripKm, boolean isMonthly) {
        if (isMonthly) {
            if (longestTripKm >= 500) {
                return Milestone.builder()
                    .id("epic_adventure")
                    .title("Epic Adventure")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("💎")
                    .tier("diamond")
                    .category("epicJourney")
                    .build();
            } else if (longestTripKm >= 200) {
                return Milestone.builder()
                    .id("road_trip")
                    .title("Road Trip")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("🥇")
                    .tier("gold")
                    .category("epicJourney")
                    .build();
            } else if (longestTripKm >= 100) {
                return Milestone.builder()
                    .id("long_distance")
                    .title("Long Distance")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("🥈")
                    .tier("silver")
                    .category("epicJourney")
                    .build();
            } else if (longestTripKm >= 50) {
                return Milestone.builder()
                    .id("day_trip")
                    .title("Day Trip")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("🥉")
                    .tier("bronze")
                    .category("epicJourney")
                    .build();
            }
        } else {
            // Yearly thresholds
            if (longestTripKm >= 1000) {
                return Milestone.builder()
                    .id("epic_voyage")
                    .title("Epic Voyage")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("💎")
                    .tier("diamond")
                    .category("epicJourney")
                    .build();
            } else if (longestTripKm >= 500) {
                return Milestone.builder()
                    .id("long_journey")
                    .title("Long Journey")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("🥇")
                    .tier("gold")
                    .category("epicJourney")
                    .build();
            } else if (longestTripKm >= 300) {
                return Milestone.builder()
                    .id("road_trip")
                    .title("Road Trip")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("🥈")
                    .tier("silver")
                    .category("epicJourney")
                    .build();
            } else if (longestTripKm >= 100) {
                return Milestone.builder()
                    .id("weekend_trip")
                    .title("Weekend Trip")
                    .description(String.format("Longest trip: %.0f km", longestTripKm))
                    .icon("🥉")
                    .tier("bronze")
                    .category("epicJourney")
                    .build();
            }
        }
        return null;
    }

    private ActivityChartData buildMonthlyChartForYear(UUID userId, int year, ZoneId zoneId) {
        String[] monthLabels = new String[12];
        double[] carDistances = new double[12];
        double[] walkDistances = new double[12];

        // Month names for labels
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                               "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // Fetch data for each month
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate firstDay = yearMonth.atDay(1);
            LocalDate lastDay = yearMonth.atEndOfMonth();

            Instant start = firstDay.atStartOfDay(zoneId).toInstant();
            Instant end = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);

            // Get statistics for this month
            UserStatistics monthStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);

            // Store label
            monthLabels[month - 1] = monthNames[month - 1];

            // Extract car and walk distances from the month's charts
            if (monthStats.getDistanceCarChart() != null && monthStats.getDistanceCarChart().getData() != null) {
                // Sum up all car distances for this month (convert meters to km)
                carDistances[month - 1] = java.util.Arrays.stream(monthStats.getDistanceCarChart().getData()).sum();
            }

            if (monthStats.getDistanceWalkChart() != null && monthStats.getDistanceWalkChart().getData() != null) {
                // Sum up all walk distances for this month (convert meters to km)
                walkDistances[month - 1] = java.util.Arrays.stream(monthStats.getDistanceWalkChart().getData()).sum();
            }
        }

        return ActivityChartData.builder()
                .carChart(new BarChartData(monthLabels, carDistances))
                .walkChart(new BarChartData(monthLabels, walkDistances))
                .build();
    }

    private ActivityChartData buildActivityChartData(BarChartData carChart, BarChartData walkChart) {
        return ActivityChartData.builder()
                .carChart(carChart)
                .walkChart(walkChart)
                .build();
    }

    /**
     * Get count of unique cities visited during the period.
     * Cities are extracted from the city field in favorite_locations or reverse_geocoding_location tables.
     */
    private int getUniqueCitiesCount(UUID userId, Instant start, Instant end) {
        String sql = """
                SELECT COUNT(DISTINCT city_name)
                FROM (
                    SELECT COALESCE(f.city, r.city) as city_name
                    FROM timeline_stays ts
                    LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
                    WHERE ts.user_id = :userId
                    AND ts.timestamp >= :start
                    AND ts.timestamp <= :end
                    AND (f.city IS NOT NULL OR r.city IS NOT NULL)
                ) AS cities
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        Number result = (Number) query.getSingleResult();
        return result != null ? result.intValue() : 0;
    }

    /**
     * Get count of unique places (location names) visited during the period.
     * This counts distinct location_name values which represent specific named locations.
     */
    private int getUniquePlacesCount(UUID userId, Instant start, Instant end) {
        String sql = """
                SELECT COUNT(DISTINCT location_name)
                FROM (
                    SELECT COALESCE(f.name, r.display_name) as location_name
                    FROM timeline_stays ts
                    LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
                    WHERE ts.user_id = :userId
                    AND ts.timestamp >= :start
                    AND ts.timestamp <= :end
                    AND (f.name IS NOT NULL OR r.display_name IS NOT NULL)
                ) AS places
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        Number result = (Number) query.getSingleResult();
        return result != null ? result.intValue() : 0;
    }
}
