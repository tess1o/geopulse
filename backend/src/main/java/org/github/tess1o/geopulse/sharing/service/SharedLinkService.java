package org.github.tess1o.geopulse.sharing.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.sharing.mapper.SharedLinkMapper;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.sharing.exceptions.TooManyLinksException;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.repository.SharedLinkRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class SharedLinkService {

    @Inject
    @ConfigProperty(name = "geopulse.sharing.max-links-per-user", defaultValue = "10")
    @StaticInitSafe
    int maxLinksPerUser;

    @ConfigProperty(name = "geopulse.share.base-url")
    Optional<String> shareBaseUrl;

    @Inject
    SharedLinkRepository sharedLinkRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    SharedLinkMapper mapper;

    @Inject
    StreamingTimelineAggregator timelineAggregator;

    @Inject
    PathSimplificationService pathSimplificationService;

    @Inject
    TimelineConfigurationProvider timelineConfigurationProvider;

    @Inject
    SecurePasswordUtils passwordUtils;

    @Inject
    @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
    @StaticInitSafe
    String issuer;

    @Inject
    @ConfigProperty(name = "geopulse.sharing.temp-token.lifespan", defaultValue = "1800")
    @StaticInitSafe
    Long tempTokenLifespan;

    @Inject
    @ConfigProperty(name = "geopulse.sharing.temp-token.timeline-lifespan", defaultValue = "7200")
    @StaticInitSafe
    Long timelineTokenLifespan;

    @Inject
    io.smallrye.jwt.auth.principal.JWTParser jwtParser;

    @Transactional
    public CreateShareLinkResponse createShareLink(CreateShareLinkRequest request, UserEntity user) {
        ShareType shareType = request.getShareType() != null ? ShareType.valueOf(request.getShareType()) : ShareType.LIVE_LOCATION;
        log.info("Creating {} share link for user: {}, name: {}, hasPassword: {}, showHistory: {}",
                shareType, user.getId(), request.getName(), request.getPassword() != null, request.isShowHistory());

        // Check separate limits for each share type
        long activeCount = sharedLinkRepository.countActiveByUserIdAndType(user.getId(), shareType);
        if (activeCount >= maxLinksPerUser) {
            log.warn("User {} exceeded max {} links limit: {} >= {}", user.getId(), shareType, activeCount, maxLinksPerUser);
            throw new TooManyLinksException("Maximum number of active " + shareType + " links reached (" + maxLinksPerUser + ")");
        }

        // Validate timeline-specific requirements
        if (shareType == ShareType.TIMELINE) {
            if (request.getStartDate() == null || request.getEndDate() == null) {
                throw new IllegalArgumentException("Timeline shares must have start and end dates");
            }
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new IllegalArgumentException("End date must be after start date");
            }
        }

        SharedLinkEntity entity = mapper.toEntity(request, user);

        if (entity.getPassword() != null) {
            entity.setPassword(passwordUtils.hashPassword(entity.getPassword()));
        }

        sharedLinkRepository.persist(entity);
        log.info("{} share link created successfully: {}, expires: {}", shareType, entity.getId(), entity.getExpiresAt());
        return mapper.toResponse(entity);
    }

    public SharedLinksDto getSharedLinks(UUID userId) {
        List<SharedLinkEntity> entities = sharedLinkRepository.findByUserId(userId);
        List<SharedLinkDto> dtos = entities.stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        long activeCount = sharedLinkRepository.countActiveByUserId(userId);

        return SharedLinksDto.builder()
                .links(dtos)
                .activeCount((int) activeCount)
                .maxLinks(maxLinksPerUser)
                .baseUrl(shareBaseUrl.orElse(null))
                .build();
    }

    @Transactional
    public SharedLinkDto updateShareLink(UUID linkId, UpdateShareLinkDto updateDto, UUID userId) {
        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findByIdAndUserId(linkId, userId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Link not found");
        }

        SharedLinkEntity entity = entityOpt.get();

        // Create a copy to avoid modifying the original DTO (prevents password logging)
        UpdateShareLinkDto safeDto = new UpdateShareLinkDto(
                updateDto.getName(),
                updateDto.getExpiresAt(),
                updateDto.getPassword(),
                updateDto.isShowHistory(),
                updateDto.getHistoryHours(),
                updateDto.getShareType(),
                updateDto.getStartDate(),
                updateDto.getEndDate(),
                updateDto.getShowCurrentLocation(),
                updateDto.getShowPhotos()
        );

        if (safeDto.getPassword() != null && !safeDto.getPassword().trim().isEmpty()) {
            String hashedPassword = passwordUtils.hashPassword(safeDto.getPassword());
            safeDto.setPassword(hashedPassword);
        }

        mapper.updateEntityFromDto(entity, safeDto);
        sharedLinkRepository.persist(entity);

        return mapper.toDto(entity);
    }

    @Transactional
    public void deleteShareLink(UUID linkId, UUID userId) {
        log.info("User {} attempting to delete share link: {}", userId, linkId);

        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findByIdAndUserId(linkId, userId);
        if (entityOpt.isEmpty()) {
            log.warn("Share link not found or access denied: linkId={}, userId={}", linkId, userId);
            throw new NotFoundException("Link not found");
        }

        sharedLinkRepository.delete(entityOpt.get());
        log.info("Share link deleted successfully: {}", linkId);
    }

    public SharedLocationInfo getSharedLocationInfo(UUID linkId) {
        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findActiveById(linkId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Link not found or expired");
        }

        return mapper.toLocationInfo(entityOpt.get());
    }

    public AccessTokenResponse verifyPassword(UUID linkId, String password) {
        log.debug("Password verification attempt for linkId: {}", linkId);

        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findActiveById(linkId);
        if (entityOpt.isEmpty()) {
            log.warn("Link not found or expired for verification: {}", linkId);
            throw new NotFoundException("Link not found or expired");
        }

        SharedLinkEntity entity = entityOpt.get();

        if (entity.getPassword() != null) {
            if (password == null || !passwordUtils.isPasswordValid(password, entity.getPassword())) {
                log.warn("Invalid password attempt for linkId: {}", linkId);
                throw new ForbiddenException("Invalid password");
            }
        }

        // Use longer token lifespan for timeline shares
        Long tokenLifespan = entity.getShareType() == ShareType.TIMELINE ? timelineTokenLifespan : tempTokenLifespan;
        String tempToken = createTemporaryAccessToken(linkId, tokenLifespan);
        log.info("Password verification successful for linkId: {}, tokenLifespan: {}", linkId, tokenLifespan);
        return new AccessTokenResponse(tempToken, tokenLifespan);
    }

    @Transactional
    public LocationHistoryResponse getSharedLocation(UUID linkId, String tempToken) {
        log.debug("Location access attempt for linkId: {}", linkId);

        validateTemporaryToken(tempToken, linkId);

        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findActiveById(linkId);
        if (entityOpt.isEmpty()) {
            log.warn("Link not found or expired for location access: {}", linkId);
            throw new NotFoundException("Link not found or expired");
        }

        SharedLinkEntity entity = entityOpt.get();

        sharedLinkRepository.incrementViewCount(linkId);
        log.info("Location accessed successfully for linkId: {}, showHistory: {}", linkId, entity.isShowHistory());

        GpsPointEntity currentLocation = gpsPointRepository.findByUserIdLatestGpsPoint(entity.getUser().getId());

        if (entity.isShowHistory()) {
            Instant startDate = Instant.now().minus(entity.getHistoryHours(), ChronoUnit.HOURS);
            List<GpsPointEntity> history = gpsPointRepository.findByUserIdAndTimePeriod(
                    entity.getUser().getId(), startDate, Instant.now());

            return mapper.toLocationHistoryResponse(currentLocation, history);
        } else {
            if (currentLocation != null) {
                ShareLinkResponse current = mapper.toShareLinkResponse(currentLocation);
                LocationHistoryResponse.CurrentLocationData currentData =
                        new LocationHistoryResponse.CurrentLocationData(
                                current.getLatitude(),
                                current.getLongitude(),
                                current.getTimestamp(),
                                current.getAccuracy()
                        );
                return new LocationHistoryResponse(currentData, List.of());
            }
            return new LocationHistoryResponse(null, List.of());
        }
    }

    private String createTemporaryAccessToken(UUID linkId, Long lifespan) {
        return Jwt.issuer(issuer)
                .subject("shared-link")
                .claim("linkId", linkId.toString())
                .claim("type", "temp")
                .expiresIn(Duration.ofSeconds(lifespan))
                .sign();
    }

    private void validateTemporaryToken(String token, UUID expectedLinkId) {
        try {
            log.debug("Validating temporary token for linkId: {}", expectedLinkId);

            org.eclipse.microprofile.jwt.JsonWebToken jwt = jwtParser.parse(token);

            String tokenType = jwt.getClaim("type");
            if (!"temp".equals(tokenType)) {
                log.warn("Invalid token type '{}' for linkId: {}", tokenType, expectedLinkId);
                throw new ForbiddenException("Invalid token type");
            }

            String tokenLinkId = jwt.getClaim("linkId");
            if (!expectedLinkId.toString().equals(tokenLinkId)) {
                log.warn("Token linkId mismatch. Expected: {}, Got: {}", expectedLinkId, tokenLinkId);
                throw new ForbiddenException("Token not valid for this link");
            }

            log.debug("Temporary token validation successful for linkId: {}", expectedLinkId);

        } catch (Exception e) {
            log.warn("Temporary token validation failed for linkId: {}, error: {}", expectedLinkId, e.getMessage());
            throw new ForbiddenException("Invalid or expired token");
        }
    }

    /**
     * Get current location for timeline share (only during active period)
     */
    public Optional<LocationHistoryResponse.CurrentLocationData> getSharedCurrentLocation(UUID linkId, String tempToken) {
        log.debug("Current location access attempt for timeline share linkId: {}", linkId);

        validateTemporaryToken(tempToken, linkId);

        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findActiveById(linkId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Link not found or expired");
        }

        SharedLinkEntity entity = entityOpt.get();

        // Only return current location for timeline shares
        if (entity.getShareType() != ShareType.TIMELINE) {
            throw new IllegalArgumentException("This endpoint is only for timeline shares");
        }

        // Check if share is configured to show current location
        if (entity.getShowCurrentLocation() == null || !entity.getShowCurrentLocation()) {
            throw new NotFoundException("Current location not available for this share");
        }

        // Check if we're within the active timeline period
        Instant now = Instant.now();
        if (entity.getStartDate() == null || entity.getEndDate() == null ||
            now.isBefore(entity.getStartDate()) || now.isAfter(entity.getEndDate())) {
            throw new NotFoundException("Current location only available during the timeline period");
        }

        GpsPointEntity currentLocation = gpsPointRepository.findByUserIdLatestGpsPoint(entity.getUser().getId());
        if (currentLocation == null) {
            return Optional.empty();
        }

        return Optional.of(mapper.toCurrentLocationData(currentLocation));
    }

    /**
     * Get timeline data for timeline share
     */
    @Transactional
    public MovementTimelineDTO getSharedTimeline(UUID linkId, String tempToken) {
        log.debug("Timeline data access attempt for linkId: {}", linkId);

        validateTemporaryToken(tempToken, linkId);

        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findActiveById(linkId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Link not found or expired");
        }

        SharedLinkEntity entity = entityOpt.get();

        // Only return timeline data for timeline shares
        if (entity.getShareType() != ShareType.TIMELINE) {
            throw new IllegalArgumentException("This endpoint is only for timeline shares");
        }

        if (entity.getStartDate() == null || entity.getEndDate() == null) {
            throw new IllegalStateException("Timeline share missing date range");
        }

        // Increment view count on first timeline access
        sharedLinkRepository.incrementViewCount(linkId);

        // Get timeline data for the date range
        MovementTimelineDTO timeline = timelineAggregator.getTimelineFromDb(
                entity.getUser().getId(),
                entity.getStartDate(),
                entity.getEndDate()
        );

        log.info("Timeline data accessed successfully for linkId: {}, items: {}",
                linkId, timeline.getStaysCount() + timeline.getTripsCount());

        return timeline;
    }

    /**
     * Get GPS path data for timeline share
     */
    public GpsPointPathDTO getSharedPath(UUID linkId, String tempToken) {
        log.debug("Path data access attempt for linkId: {}", linkId);

        validateTemporaryToken(tempToken, linkId);

        Optional<SharedLinkEntity> entityOpt = sharedLinkRepository.findActiveById(linkId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Link not found or expired");
        }

        SharedLinkEntity entity = entityOpt.get();

        // Only return path data for timeline shares
        if (entity.getShareType() != ShareType.TIMELINE) {
            throw new IllegalArgumentException("This endpoint is only for timeline shares");
        }

        if (entity.getStartDate() == null || entity.getEndDate() == null) {
            throw new IllegalStateException("Timeline share missing date range");
        }

        // Get GPS points for the date range
        List<GpsPointEntity> gpsPoints = gpsPointRepository.findByUserIdAndTimePeriod(
                entity.getUser().getId(),
                entity.getStartDate(),
                entity.getEndDate()
        );

        // Convert to GpsPoint interface for simplification
        List<GpsPoint> convertedPoints = gpsPoints.stream()
                .map(gp -> new GpsPointPathPointDTO(
                        gp.getId(),
                        gp.getCoordinates().getX(), // longitude
                        gp.getCoordinates().getY(), // latitude
                        gp.getTimestamp(),
                        gp.getAccuracy(),
                        gp.getAltitude(),
                        gp.getVelocity(),
                        entity.getUser().getId(),
                        gp.getSourceType().name()
                ))
                .collect(Collectors.toList());

        // Get user's timeline config for simplification
        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(entity.getUser().getId());

        // Simplify the path to reduce data size
        List<? extends GpsPoint> simplifiedPoints = pathSimplificationService.simplify(convertedPoints, config);

        log.info("Path data accessed for linkId: {}, original points: {}, simplified: {}",
                linkId, gpsPoints.size(), simplifiedPoints.size());

        return new GpsPointPathDTO(entity.getUser().getId(), (List<GpsPointPathPointDTO>) simplifiedPoints);
    }
}