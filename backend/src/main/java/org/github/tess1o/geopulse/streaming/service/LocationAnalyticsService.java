package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for location analytics operations.
 * Provides city and country level aggregations and search capabilities.
 */
@ApplicationScoped
@Slf4j
public class LocationAnalyticsService {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    ReverseGeocodingLocationRepository geocodingRepository;

    /**
     * Search across places, cities, and countries.
     * Returns mixed results with type discrimination.
     *
     * @param userId     user ID
     * @param query      search term (minimum 2 characters)
     * @param typeFilter filter by type: "place", "city", "country" (optional)
     * @return list of search results
     */
    public List<LocationSearchResultDTO> search(UUID userId, String query, String typeFilter) {
        List<LocationSearchResultDTO> results = new ArrayList<>();

        // Search places (if no type filter or type=place)
        if (typeFilter == null || "place".equalsIgnoreCase(typeFilter)) {
            // Search favorites (limit to 10 results)
            List<FavoritesEntity> favorites = favoritesRepository
                    .findByUserIdAndNameContaining(userId, query, 10);

            for (FavoritesEntity favorite : favorites) {
                long visitCount = stayRepository.countByFavoriteLocationId(favorite.getId(), userId);
                results.add(LocationSearchResultDTO.builder()
                        .type("place")
                        .category("favorite")
                        .id(favorite.getId())
                        .name(favorite.getName())
                        .displayName(favorite.getName())
                        .country(favorite.getCountry())
                        .visitCount((int) visitCount)
                        .build());
            }

            // Search geocoding (limit to 10 results)
            List<ReverseGeocodingLocationEntity> geocodingList =
                    geocodingRepository.findByDisplayNameContaining(userId, query, 10);

            for (ReverseGeocodingLocationEntity geocoding : geocodingList) {
                long visitCount = stayRepository.countByGeocodingLocationId(geocoding.getId(), userId);
                results.add(LocationSearchResultDTO.builder()
                        .type("place")
                        .category("geocoding")
                        .id(geocoding.getId())
                        .name(geocoding.getDisplayName())
                        .displayName(geocoding.getDisplayName())
                        .country(geocoding.getCountry())
                        .visitCount((int) visitCount)
                        .build());
            }
        }

        // Search cities
        if (typeFilter == null || "city".equalsIgnoreCase(typeFilter)) {
            List<Object[]> cities = stayRepository.searchCitiesByName(userId, query, 10);
            for (Object[] row : cities) {
                String cityName = (String) row[0];
                String country = (String) row[1];
                int visitCount = ((Number) row[2]).intValue();

                results.add(LocationSearchResultDTO.builder()
                        .type("city")
                        .name(cityName)
                        .displayName(cityName + ", " + country)
                        .country(country)
                        .visitCount(visitCount)
                        .build());
            }
        }

        // Search countries
        if (typeFilter == null || "country".equalsIgnoreCase(typeFilter)) {
            List<Object[]> countries = stayRepository.searchCountriesByName(userId, query, 10);
            for (Object[] row : countries) {
                String countryName = (String) row[0];
                int visitCount = ((Number) row[1]).intValue();

                results.add(LocationSearchResultDTO.builder()
                        .type("country")
                        .name(countryName)
                        .displayName(countryName)
                        .visitCount(visitCount)
                        .build());
            }
        }

        // Sort by visit count descending, limit to 20 results
        return results.stream()
                .sorted((a, b) -> Integer.compare(b.getVisitCount(), a.getVisitCount()))
                .limit(20)
                .toList();
    }

    /**
     * Get all cities with summary information for a user.
     *
     * @param userId user ID
     * @return list of city summaries
     */
    public List<CitySummaryDTO> getAllCities(UUID userId) {
        List<Object[]> citiesData = stayRepository.getCitiesWithCounts(userId);

        return citiesData.stream()
                .map(row -> CitySummaryDTO.builder()
                        .cityName((String) row[0])
                        .country((String) row[1])
                        .visitCount(((Number) row[2]).longValue())
                        .totalDuration(((Number) row[3]).longValue())
                        .uniquePlaces(((Number) row[4]).intValue())
                        .build())
                .toList();
    }

    /**
     * Get all countries with summary information for a user.
     *
     * @param userId user ID
     * @return list of country summaries
     */
    public List<CountrySummaryDTO> getAllCountries(UUID userId) {
        List<Object[]> countriesData = stayRepository.getCountriesWithCounts(userId);

        return countriesData.stream()
                .map(row -> CountrySummaryDTO.builder()
                        .countryName((String) row[0])
                        .visitCount(((Number) row[1]).longValue())
                        .cityCount(((Number) row[2]).intValue())
                        .totalDuration(((Number) row[3]).longValue())
                        .uniquePlaces(((Number) row[4]).intValue())
                        .build())
                .toList();
    }

    /**
     * Get detailed statistics for a specific city.
     *
     * @param userId   user ID
     * @param cityName city name
     * @return city details or empty if not found
     */
    public Optional<CityDetailsDTO> getCityDetails(UUID userId, String cityName) {
        Object[] statsData = stayRepository.getCityStatistics(userId, cityName);

        // Check if city has any visits
        long totalVisits = ((Number) statsData[0]).longValue();
        if (totalVisits == 0) {
            return Optional.empty();
        }

        // Build statistics
        LocationStatisticsDTO statistics = buildLocationStatistics(statsData);

        // Get time-based visit counts
        Object[] visitCounts = stayRepository.getVisitCountsByCity(userId, cityName);
        statistics.setVisitsThisWeek(((Number) visitCounts[0]).longValue());
        statistics.setVisitsThisMonth(((Number) visitCounts[1]).longValue());
        statistics.setVisitsThisYear(((Number) visitCounts[2]).longValue());

        // Get top places in city (limit to 5)
        List<Object[]> topPlacesData = stayRepository.getTopPlacesInCity(userId, cityName, 5);
        List<TopPlaceInLocationDTO> topPlaces = topPlacesData.stream()
                .map(this::convertToTopPlace)
                .toList();

        // Get city centroid (average coordinates)
        PlaceGeometryDTO geometry = getCityGeometry(userId, cityName);

        // Get country name
        String country = stayRepository.getCountryForCity(userId, cityName);

        return Optional.of(CityDetailsDTO.builder()
                .cityName(cityName)
                .country(country)
                .geometry(geometry)
                .statistics(statistics)
                .topPlaces(topPlaces)
                .build());
    }

    /**
     * Get detailed statistics for a specific country.
     *
     * @param userId      user ID
     * @param countryName country name
     * @return country details or empty if not found
     */
    public Optional<CountryDetailsDTO> getCountryDetails(UUID userId, String countryName) {
        Object[] statsData = stayRepository.getCountryStatistics(userId, countryName);

        // Check if country has any visits
        long totalVisits = ((Number) statsData[0]).longValue();
        if (totalVisits == 0) {
            return Optional.empty();
        }

        // Build statistics (country stats have one extra field: uniqueCities at index 7)
        LocationStatisticsDTO statistics = LocationStatisticsDTO.builder()
                .totalVisits(totalVisits)
                .totalDuration(((Number) statsData[1]).longValue())
                .averageDuration(((Number) statsData[2]).longValue())
                .minDuration(((Number) statsData[3]).longValue())
                .maxDuration(((Number) statsData[4]).longValue())
                .firstVisit((Instant) statsData[5])
                .lastVisit((Instant) statsData[6])
                .uniquePlaces(((Number) statsData[8]).intValue())
                .build();

        // Get time-based visit counts
        Object[] visitCounts = stayRepository.getVisitCountsByCountry(userId, countryName);
        statistics.setVisitsThisWeek(((Number) visitCounts[0]).longValue());
        statistics.setVisitsThisMonth(((Number) visitCounts[1]).longValue());
        statistics.setVisitsThisYear(((Number) visitCounts[2]).longValue());

        // Get cities in country
        List<Object[]> citiesData = stayRepository.getCitiesInCountry(userId, countryName);
        List<CityInCountryDTO> cities = citiesData.stream()
                .map(row -> CityInCountryDTO.builder()
                        .cityName((String) row[0])
                        .visitCount(((Number) row[1]).longValue())
                        .totalDuration(((Number) row[2]).longValue())
                        .uniquePlaces(((Number) row[3]).intValue())
                        .build())
                .toList();

        // Get top places in country (limit to 5)
        List<Object[]> topPlacesData = stayRepository.getTopPlacesInCountry(userId, countryName, 5);
        List<TopPlaceInLocationDTO> topPlaces = topPlacesData.stream()
                .map(this::convertToTopPlace)
                .toList();

        return Optional.of(CountryDetailsDTO.builder()
                .countryName(countryName)
                .statistics(statistics)
                .cities(cities)
                .topPlaces(topPlaces)
                .build());
    }

    /**
     * Get paginated visits for a city.
     *
     * @param userId        user ID
     * @param cityName      city name
     * @param page          zero-based page number
     * @param pageSize      number of items per page (max 200)
     * @param sortBy        field to sort by
     * @param sortDirection "asc" or "desc"
     * @return paginated visits
     */
    public PagedPlaceVisitsDTO getCityVisits(
            UUID userId, String cityName,
            int page, int pageSize, String sortBy, String sortDirection) {

        int validPageSize = Math.min(Math.max(pageSize, 1), 200);
        boolean ascending = "asc".equalsIgnoreCase(sortDirection);
        String validSortBy = validateSortField(sortBy);

        List<TimelineStayEntity> stays = stayRepository.findByCityPaginated(
                userId, cityName, page, validPageSize, validSortBy, ascending);

        long totalCount = stayRepository.countByCity(userId, cityName);

        List<PlaceVisitDTO> visits = stays.stream()
                .map(this::convertToPlaceVisitDTO)
                .toList();

        int totalPages = (int) Math.ceil((double) totalCount / validPageSize);

        return PagedPlaceVisitsDTO.builder()
                .visits(visits)
                .currentPage(page)
                .pageSize(validPageSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    /**
     * Get paginated visits for a country.
     *
     * @param userId        user ID
     * @param countryName   country name
     * @param page          zero-based page number
     * @param pageSize      number of items per page (max 200)
     * @param sortBy        field to sort by
     * @param sortDirection "asc" or "desc"
     * @return paginated visits
     */
    public PagedPlaceVisitsDTO getCountryVisits(
            UUID userId, String countryName,
            int page, int pageSize, String sortBy, String sortDirection) {

        int validPageSize = Math.min(Math.max(pageSize, 1), 200);
        boolean ascending = "asc".equalsIgnoreCase(sortDirection);
        String validSortBy = validateSortField(sortBy);

        List<TimelineStayEntity> stays = stayRepository.findByCountryPaginated(
                userId, countryName, page, validPageSize, validSortBy, ascending);

        long totalCount = stayRepository.countByCountry(userId, countryName);

        List<PlaceVisitDTO> visits = stays.stream()
                .map(this::convertToPlaceVisitDTO)
                .toList();

        int totalPages = (int) Math.ceil((double) totalCount / validPageSize);

        return PagedPlaceVisitsDTO.builder()
                .visits(visits)
                .currentPage(page)
                .pageSize(validPageSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    /**
     * Get all visits for a city (for CSV export).
     *
     * @param userId        user ID
     * @param cityName      city name
     * @param sortBy        field to sort by
     * @param sortDirection "asc" or "desc"
     * @return all visits
     */
    public List<PlaceVisitDTO> getAllCityVisits(
            UUID userId, String cityName, String sortBy, String sortDirection) {

        boolean ascending = "asc".equalsIgnoreCase(sortDirection);
        String validSortBy = validateSortField(sortBy);

        List<TimelineStayEntity> stays = stayRepository.findByCity(
                userId, cityName, validSortBy, ascending);

        return stays.stream()
                .map(this::convertToPlaceVisitDTO)
                .toList();
    }

    /**
     * Get all visits for a country (for CSV export).
     *
     * @param userId        user ID
     * @param countryName   country name
     * @param sortBy        field to sort by
     * @param sortDirection "asc" or "desc"
     * @return all visits
     */
    public List<PlaceVisitDTO> getAllCountryVisits(
            UUID userId, String countryName, String sortBy, String sortDirection) {

        boolean ascending = "asc".equalsIgnoreCase(sortDirection);
        String validSortBy = validateSortField(sortBy);

        List<TimelineStayEntity> stays = stayRepository.findByCountry(
                userId, countryName, validSortBy, ascending);

        return stays.stream()
                .map(this::convertToPlaceVisitDTO)
                .toList();
    }

    // Helper methods

    /**
     * Build LocationStatisticsDTO from query result array.
     * Expected array: [totalVisits, totalDuration, avgDuration, minDuration, maxDuration,
     *                  firstVisit, lastVisit, uniquePlaces]
     */
    private LocationStatisticsDTO buildLocationStatistics(Object[] statsData) {
        return LocationStatisticsDTO.builder()
                .totalVisits(((Number) statsData[0]).longValue())
                .totalDuration(((Number) statsData[1]).longValue())
                .averageDuration(((Number) statsData[2]).longValue())
                .minDuration(((Number) statsData[3]).longValue())
                .maxDuration(((Number) statsData[4]).longValue())
                .firstVisit((Instant) statsData[5])
                .lastVisit((Instant) statsData[6])
                .uniquePlaces(((Number) statsData[7]).intValue())
                .build();
    }

    /**
     * Convert Object array from repository to TopPlaceInLocationDTO.
     * Expected array: [type, placeId, placeName, visitCount, totalDuration, latitude, longitude]
     */
    private TopPlaceInLocationDTO convertToTopPlace(Object[] row) {
        return TopPlaceInLocationDTO.builder()
                .type((String) row[0])
                .id(((Number) row[1]).longValue())
                .name((String) row[2])
                .visitCount(((Number) row[3]).longValue())
                .totalDuration(((Number) row[4]).longValue())
                .latitude(row[5] != null ? ((Number) row[5]).doubleValue() : null)
                .longitude(row[6] != null ? ((Number) row[6]).doubleValue() : null)
                .build();
    }

    /**
     * Convert TimelineStayEntity to PlaceVisitDTO.
     */
    private PlaceVisitDTO convertToPlaceVisitDTO(TimelineStayEntity stay) {
        double latitude = stay.getLocation() != null ? stay.getLocation().getY() : 0.0;
        double longitude = stay.getLocation() != null ? stay.getLocation().getX() : 0.0;

        // Extract city from either geocoding or favorite location
        String city = null;
        if (stay.getGeocodingLocation() != null) {
            city = stay.getGeocodingLocation().getCity();
        } else if (stay.getFavoriteLocation() != null) {
            city = stay.getFavoriteLocation().getCity();
        }

        return PlaceVisitDTO.builder()
                .id(stay.getId())
                .timestamp(stay.getTimestamp())
                .stayDuration(stay.getStayDuration())
                .latitude(latitude)
                .longitude(longitude)
                .locationName(stay.getLocationName())
                .city(city)
                .build();
    }

    /**
     * Get geometry (centroid) for a city.
     */
    private PlaceGeometryDTO getCityGeometry(UUID userId, String cityName) {
        Object[] centroid = stayRepository.getCityCentroid(userId, cityName);

        Double latitude = centroid[0] != null ? ((Number) centroid[0]).doubleValue() : null;
        Double longitude = centroid[1] != null ? ((Number) centroid[1]).doubleValue() : null;

        if (latitude != null && longitude != null) {
            return PlaceGeometryDTO.builder()
                    .type("point")
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
        }

        return null;
    }

    /**
     * Validate and sanitize sort field to prevent SQL injection.
     * Only allows whitelisted fields.
     */
    private String validateSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "timestamp";
        }

        // Whitelist of allowed sort fields
        return switch (sortBy.toLowerCase()) {
            case "timestamp" -> "timestamp";
            case "stayduration" -> "stayDuration";
            case "locationname" -> "locationName";
            default -> "timestamp";
        };
    }
}
