package org.github.tess1o.geopulse.sharing.service;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.sharing.SharedLinkMapper;
import org.github.tess1o.geopulse.sharing.exceptions.TooManyLinksException;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.repository.SharedLinkRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.PasswordUtils;

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
    int maxLinksPerUser;

    @Inject
    SharedLinkRepository sharedLinkRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    SharedLinkMapper mapper;

    @Inject
    PasswordUtils passwordUtils;

    @Inject
    @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
    String issuer;

    @Inject
    @ConfigProperty(name = "geopulse.sharing.temp-token.lifespan", defaultValue = "1800")
    Long tempTokenLifespan;


    @Inject
    io.smallrye.jwt.auth.principal.JWTParser jwtParser;

    @Transactional
    public CreateShareLinkResponse createShareLink(CreateShareLinkRequest request, UserEntity user) {
        log.info("Creating share link for user: {}, name: {}, hasPassword: {}, showHistory: {}",
                user.getId(), request.getName(), request.getPassword() != null, request.isShowHistory());
        
        long activeCount = sharedLinkRepository.countActiveByUserId(user.getId());
        if (activeCount >= maxLinksPerUser) {
            log.warn("User {} exceeded max links limit: {} >= {}", user.getId(), activeCount, maxLinksPerUser);
            throw new TooManyLinksException("Maximum number of active links reached (" + maxLinksPerUser + ")");
        }

        SharedLinkEntity entity = mapper.toEntity(request, user);
        
        if (entity.getPassword() != null) {
            entity.setPassword(passwordUtils.hashPassword(entity.getPassword()));
        }

        sharedLinkRepository.persist(entity);
        log.info("Share link created successfully: {}, expires: {}", entity.getId(), entity.getExpiresAt());
        return mapper.toResponse(entity);
    }

    public SharedLinksDto getSharedLinks(UUID userId) {
        List<SharedLinkEntity> entities = sharedLinkRepository.findByUserId(userId);
        List<SharedLinkDto> dtos = entities.stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());

        long activeCount = sharedLinkRepository.countActiveByUserId(userId);

        return new SharedLinksDto(dtos, (int) activeCount, maxLinksPerUser);
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
            updateDto.isShowHistory()
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
            if (password == null || !passwordUtils.verifyPassword(password, entity.getPassword())) {
                log.warn("Invalid password attempt for linkId: {}", linkId);
                throw new ForbiddenException("Invalid password");
            }
        }

        String tempToken = createTemporaryAccessToken(linkId);
        log.info("Password verification successful for linkId: {}", linkId);
        return new AccessTokenResponse(tempToken, tempTokenLifespan);
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
            Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
            List<GpsPointEntity> history = gpsPointRepository.findByUserIdAndTimePeriod(
                    entity.getUser().getId(), oneDayAgo, Instant.now());
            
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

    private String createTemporaryAccessToken(UUID linkId) {
        return Jwt.issuer(issuer)
                .subject("shared-link")
                .claim("linkId", linkId.toString())
                .claim("type", "temp")
                .expiresIn(Duration.ofSeconds(tempTokenLifespan))
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
}