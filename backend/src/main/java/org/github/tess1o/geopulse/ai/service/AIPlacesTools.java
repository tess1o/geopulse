package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AILimitedListDTO;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.model.dto.CitySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CityDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountryDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CountrySummaryDTO;
import org.github.tess1o.geopulse.streaming.model.dto.CurrentStatusDTO;
import org.github.tess1o.geopulse.streaming.model.dto.LocationSearchResultDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.PlaceVisitDTO;
import org.github.tess1o.geopulse.streaming.service.CurrentActivityService;
import org.github.tess1o.geopulse.streaming.service.LocationAnalyticsService;
import org.github.tess1o.geopulse.streaming.service.PlaceDetailsService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.ai.service.AIToolArguments.invalid;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.limit;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.optionalEnum;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.requireText;

/**
 * AI tools for where the user is now and the places, cities and countries they have visited.
 */
@Slf4j
@ApplicationScoped
public class AIPlacesTools {

    static final int DEFAULT_LIST_LIMIT = 100;
    static final int MAX_LIST_LIMIT = 500;
    static final int DEFAULT_VISITS_PAGE_SIZE = 20;
    static final int MAX_VISITS_PAGE_SIZE = 100;
    private static final List<String> SEARCH_TYPES = List.of("PLACE", "CITY", "COUNTRY");
    private static final List<String> PLACE_TYPES = List.of("FAVORITE", "GEOCODING");
    private static final List<String> VISIT_SORT_FIELDS = List.of("TIMESTAMP", "STAYDURATION");

    private final CurrentUserService currentUserService;
    private final CurrentActivityService currentActivityService;
    private final LocationAnalyticsService locationAnalyticsService;
    private final PlaceDetailsService placeDetailsService;
    private final FavoriteLocationService favoriteLocationService;

    @Inject
    public AIPlacesTools(CurrentUserService currentUserService,
                         CurrentActivityService currentActivityService,
                         LocationAnalyticsService locationAnalyticsService,
                         PlaceDetailsService placeDetailsService,
                         FavoriteLocationService favoriteLocationService) {
        this.currentUserService = currentUserService;
        this.currentActivityService = currentActivityService;
        this.locationAnalyticsService = locationAnalyticsService;
        this.placeDetailsService = placeDetailsService;
        this.favoriteLocationService = favoriteLocationService;
    }

    public CurrentStatusDTO getCurrentStatus() {
        log.info("🔧 AI TOOL EXECUTED: getCurrentStatus()");
        UserEntity user = currentUserService.getCurrentUser();
        return currentActivityService.getStatus(user.getId(), user.getTimezone(), Instant.now());
    }

    public List<LocationSearchResultDTO> searchVisitedPlaces(String query, String type) {
        log.info("🔧 AI TOOL EXECUTED: searchVisitedPlaces({}, {})", query, type);
        String text = requireText(query, "query");
        if (text.length() < 2) {
            throw invalid("query must be at least 2 characters", "query");
        }
        String typeFilter = optionalEnum(type, "type", SEARCH_TYPES);
        return locationAnalyticsService.search(currentUserId(), text,
                typeFilter == null ? null : typeFilter.toLowerCase(Locale.ROOT));
    }

    public PlaceDetailsDTO getPlaceDetails(String type, Long id) {
        log.info("🔧 AI TOOL EXECUTED: getPlaceDetails({}, {})", type, id);
        String placeType = requirePlaceType(type);
        Long placeId = requireId(id);
        UserEntity user = currentUserService.getCurrentUser();
        return placeDetailsService.getPlaceDetails(placeType, placeId, user.getId(), user.getTimezone())
                .orElseThrow(() -> placeNotFound(placeType, placeId));
    }

    public PageResponse<PlaceVisitDTO> getPlaceVisits(String type, Long id, Integer page, Integer size,
                                                      String sortBy, String sortDirection) {
        log.info("🔧 AI TOOL EXECUTED: getPlaceVisits({}, {}, page={}, size={})", type, id, page, size);
        String placeType = requirePlaceType(type);
        Long placeId = requireId(id);
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw invalid("page must be 0 or greater", "page");
        }
        String sortField = optionalEnum(sortBy, "sortBy", VISIT_SORT_FIELDS);
        String direction = optionalEnum(sortDirection, "sortDirection", List.of("ASC", "DESC"));
        return placeDetailsService.getPlaceVisits(placeType, placeId, currentUserId(), pageNumber,
                limit(size, DEFAULT_VISITS_PAGE_SIZE, MAX_VISITS_PAGE_SIZE),
                "STAYDURATION".equals(sortField) ? "stayDuration" : "timestamp",
                direction == null ? "desc" : direction.toLowerCase(Locale.ROOT));
    }

    public AILimitedListDTO<CitySummaryDTO> listVisitedCities(Integer limit) {
        log.info("🔧 AI TOOL EXECUTED: listVisitedCities({})", limit);
        List<CitySummaryDTO> cities = locationAnalyticsService.getAllCities(currentUserId()).stream()
                .sorted(Comparator.comparingLong(CitySummaryDTO::getTotalDuration).reversed())
                .toList();
        return AILimitedListDTO.of(cities, limit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT));
    }

    public AILimitedListDTO<CountrySummaryDTO> listVisitedCountries(Integer limit) {
        log.info("🔧 AI TOOL EXECUTED: listVisitedCountries({})", limit);
        List<CountrySummaryDTO> countries = locationAnalyticsService.getAllCountries(currentUserId()).stream()
                .sorted(Comparator.comparingLong(CountrySummaryDTO::getTotalDuration).reversed())
                .toList();
        return AILimitedListDTO.of(countries, limit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT));
    }

    public CityDetailsDTO getCityDetails(String name) {
        log.info("🔧 AI TOOL EXECUTED: getCityDetails({})", name);
        String cityName = requireText(name, "name");
        return locationAnalyticsService.getCityDetails(currentUserId(), cityName)
                .orElseThrow(() -> new AIToolException("CITY_NOT_FOUND",
                        "No visits recorded in this city. Use searchVisitedPlaces or listVisitedCities for exact names.",
                        Map.of("name", cityName)));
    }

    public CountryDetailsDTO getCountryDetails(String name) {
        log.info("🔧 AI TOOL EXECUTED: getCountryDetails({})", name);
        String countryName = requireText(name, "name");
        return locationAnalyticsService.getCountryDetails(currentUserId(), countryName)
                .orElseThrow(() -> new AIToolException("COUNTRY_NOT_FOUND",
                        "No visits recorded in this country. Use searchVisitedPlaces or listVisitedCountries for exact names.",
                        Map.of("name", countryName)));
    }

    public FavoriteLocationsDto listFavoritePlaces() {
        log.info("🔧 AI TOOL EXECUTED: listFavoritePlaces()");
        return favoriteLocationService.getFavorites(currentUserId());
    }

    private UUID currentUserId() {
        return currentUserService.getCurrentUserId();
    }

    private static String requirePlaceType(String type) {
        String placeType = optionalEnum(type, "type", PLACE_TYPES);
        if (placeType == null) {
            throw invalid("type is required: favorite or geocoding", "type");
        }
        return placeType.toLowerCase(Locale.ROOT);
    }

    private static Long requireId(Long id) {
        if (id == null) {
            throw invalid("id is required", "id");
        }
        return id;
    }

    private static AIToolException placeNotFound(String type, Long id) {
        return new AIToolException("PLACE_NOT_FOUND",
                "Place not found. Use searchVisitedPlaces to find a place's type and id.",
                Map.of("type", type, "id", id));
    }
}
