package org.github.tess1o.geopulse.digest.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM");

    @Override
    public TimeDigest getMonthlyDigest(UUID userId, int year, int month) {
        log.info("Generating monthly digest for user {} - {}/{}", userId, year, month);

        // Calculate time range for the month
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        Instant start = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = lastDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Get current period statistics - use WEEKS for better chart readability
        UserStatistics currentStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);
        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);

        // Get previous month for comparison
        YearMonth previousMonth = yearMonth.minusMonths(1);
        Instant prevStart = previousMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant prevEnd = previousMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd, ChartGroupMode.WEEKS);

        // Build digest
        return TimeDigest.builder()
                .period(buildPeriodInfo(year, month))
                .metrics(buildMetrics(currentStats, timeline))
                .comparison(buildComparison(currentStats, previousStats))
                .highlights(buildHighlights(currentStats, timeline, start, end))
                .topPlaces(currentStats.getPlaces() != null ? currentStats.getPlaces() : List.of())
                .activityChart(combineChartData(currentStats.getDistanceCarChart(), currentStats.getDistanceWalkChart()))
                .milestones(buildMilestones(currentStats, timeline, "monthly"))
                .build();
    }

    @Override
    public TimeDigest getYearlyDigest(UUID userId, int year) {
        log.info("Generating yearly digest for user {} - {}", userId, year);

        // Calculate time range for the year
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        LocalDate lastDay = LocalDate.of(year, 12, 31);

        Instant start = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = lastDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Get current year statistics for overall metrics
        UserStatistics currentStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);
        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);

        // Get previous year for comparison
        Instant prevStart = firstDay.minusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant prevEnd = lastDay.minusYears(1).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd, ChartGroupMode.WEEKS);

        // Generate monthly chart data for yearly view (12 bars)
        BarChartData monthlyChart = buildMonthlyChartForYear(userId, year);

        // Build digest
        return TimeDigest.builder()
                .period(buildPeriodInfo(year, null))
                .metrics(buildMetrics(currentStats, timeline))
                .comparison(buildComparison(currentStats, previousStats))
                .highlights(buildHighlights(currentStats, timeline, start, end))
                .topPlaces(currentStats.getPlaces() != null ? currentStats.getPlaces() : List.of())
                .activityChart(monthlyChart)
                .milestones(buildMilestones(currentStats, timeline, "yearly"))
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

    private DigestMetrics buildMetrics(UserStatistics stats, MovementTimelineDTO timeline) {
        // Calculate active days from timeline
        Set<LocalDate> activeDays = new HashSet<>();

        if (timeline.getStays() != null) {
            timeline.getStays().forEach(stay -> {
                LocalDate stayDate = LocalDate.ofInstant(stay.getTimestamp(), ZoneOffset.UTC);
                activeDays.add(stayDate);
            });
        }

        if (timeline.getTrips() != null) {
            timeline.getTrips().forEach(trip -> {
                LocalDate tripDate = LocalDate.ofInstant(trip.getTimestamp(), ZoneOffset.UTC);
                activeDays.add(tripDate);
            });
        }

        // Extract unique cities from places
        Set<String> cities = new HashSet<>();
        if (stats.getPlaces() != null) {
            stats.getPlaces().forEach(place -> {
                if (place.getName() != null && !place.getName().isEmpty()) {
                    cities.add(place.getName());
                }
            });
        }

        return DigestMetrics.builder()
                .totalDistance(stats.getTotalDistanceMeters())
                .activeDays(activeDays.size())
                .citiesVisited(cities.size())
                .tripCount(timeline.getTripsCount())
                .stayCount(timeline.getStaysCount())
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

    private DigestHighlight buildHighlights(UserStatistics stats, MovementTimelineDTO timeline, Instant start, Instant end) {
        DigestHighlight.TripHighlight longestTrip = null;
        DigestHighlight.BusiestDay busiestDay = null;

        // Find longest trip
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            var maxTrip = timeline.getTrips().stream()
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

        // Calculate new discoveries (simplified - just use unique places)
        DigestHighlight.NewDiscoveries newDiscoveries = DigestHighlight.NewDiscoveries.builder()
                .count(stats.getPlaces() != null ? stats.getPlaces().size() : 0)
                .cities(stats.getPlaces() != null ?
                        stats.getPlaces().stream()
                                .map(TopPlace::getName)
                                .limit(5)
                                .toArray(String[]::new) : new String[0])
                .build();

        // Find busiest day (day with most trips)
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            Map<LocalDate, Long> tripsByDay = timeline.getTrips().stream()
                    .collect(Collectors.groupingBy(
                            trip -> LocalDate.ofInstant(trip.getTimestamp(), ZoneOffset.UTC),
                            Collectors.counting()
                    ));

            var busiestEntry = tripsByDay.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (busiestEntry != null) {
                LocalDate busiestDate = busiestEntry.getKey();
                double dayDistance = timeline.getTrips().stream()
                        .filter(trip -> LocalDate.ofInstant(trip.getTimestamp(), ZoneOffset.UTC).equals(busiestDate))
                        .mapToDouble(TimelineTripDTO::getDistanceMeters)
                        .sum();

                busiestDay = DigestHighlight.BusiestDay.builder()
                        .date(busiestDate.atStartOfDay(ZoneOffset.UTC).toInstant())
                        .trips(busiestEntry.getValue().intValue())
                        .distance(dayDistance)
                        .build();
            }
        }

        return DigestHighlight.builder()
                .longestTrip(longestTrip)
                .mostVisited(mostVisited)
                .newDiscoveries(newDiscoveries)
                .busiestDay(busiestDay)
                .peakHours(new String[]{"8-9 AM", "6-7 PM"}) // Simplified for now
                .build();
    }

    private List<Milestone> buildMilestones(UserStatistics stats, MovementTimelineDTO timeline, String periodType) {
        List<Milestone> milestones = new ArrayList<>();

        boolean isMonthly = "monthly".equals(periodType);
        double distanceKm = stats.getTotalDistanceMeters() / 1000.0;
        int placesCount = stats.getPlaces() != null ? stats.getPlaces().size() : 0;
        int tripCount = timeline.getTripsCount();

        // Calculate active days
        Set<LocalDate> activeDays = new HashSet<>();
        if (timeline.getStays() != null) {
            timeline.getStays().forEach(stay ->
                activeDays.add(LocalDate.ofInstant(stay.getTimestamp(), ZoneOffset.UTC)));
        }
        if (timeline.getTrips() != null) {
            timeline.getTrips().forEach(trip ->
                activeDays.add(LocalDate.ofInstant(trip.getTimestamp(), ZoneOffset.UTC)));
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

    private BarChartData buildMonthlyChartForYear(UUID userId, int year) {
        String[] monthLabels = new String[12];
        double[] monthlyDistances = new double[12];

        // Month names for labels
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                               "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // Fetch data for each month
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate firstDay = yearMonth.atDay(1);
            LocalDate lastDay = yearMonth.atEndOfMonth();

            Instant start = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = lastDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

            // Get statistics for this month
            UserStatistics monthStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);

            // Store label and distance (convert meters to km)
            monthLabels[month - 1] = monthNames[month - 1];
            monthlyDistances[month - 1] = monthStats.getTotalDistanceMeters() / 1000.0;
        }

        return new BarChartData(monthLabels, monthlyDistances);
    }

    private BarChartData combineChartData(BarChartData carChart, BarChartData walkChart) {
        // If both charts are null or empty, return empty chart
        if ((carChart == null || carChart.getData() == null || carChart.getData().length == 0) &&
            (walkChart == null || walkChart.getData() == null || walkChart.getData().length == 0)) {
            return new BarChartData(new String[0], new double[0]);
        }

        // Use car chart labels as base (they should be the same)
        String[] labels = carChart != null && carChart.getLabels() != null ?
                carChart.getLabels() :
                (walkChart != null ? walkChart.getLabels() : new String[0]);

        double[] carData = carChart != null && carChart.getData() != null ?
                carChart.getData() : new double[labels.length];

        double[] walkData = walkChart != null && walkChart.getData() != null ?
                walkChart.getData() : new double[labels.length];

        // Combine data by adding car and walk distances
        double[] combinedData = new double[labels.length];
        for (int i = 0; i < labels.length; i++) {
            double car = i < carData.length ? carData[i] : 0;
            double walk = i < walkData.length ? walkData[i] : 0;
            combinedData[i] = car + walk;
        }

        return new BarChartData(labels, combinedData);
    }
}
